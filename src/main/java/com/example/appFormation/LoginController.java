package com.example.appFormation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

	// Recherche un compte à partir d'un identifiant sensible à la casse.
	private static final String LOGIN_QUERY = "SELECT id, mdp, prenom FROM employe WHERE BINARY login = BINARY ? LIMIT 1";
	// Vérifie si un identifiant est déjà utilisé lors de la création d'un compte.
	private static final String REGISTERED_LOGIN_QUERY =
			"SELECT COUNT(*) FROM employe WHERE BINARY login = BINARY ?";
	// Crée un compte avec le statut employé par défaut.
	private static final String REGISTER_QUERY =
			"INSERT INTO employe (login, mdp, nom, prenom, statut) VALUES (?, ?, ?, ?, 0)";
	// Clés de session contenant l'identité de l'employé connecté.
	static final String AUTHENTICATED_USER_SESSION_KEY = "authenticatedEmployeeId";
	static final String AUTHENTICATED_USER_FIRST_NAME_SESSION_KEY = "authenticatedEmployeeFirstName";

	private final JdbcTemplate jdbcTemplate;

	// Reçoit le composant Spring utilisé pour accéder à la base de données.
	public LoginController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	// Authentifie l'utilisateur, puis renouvelle l'identifiant de session et le jeton CSRF.
	@PostMapping("/api/login")
	public ResponseEntity<LoginResponse> login(
			@RequestBody LoginRequest request,
			HttpServletRequest servletRequest) {
		HttpSession session = servletRequest.getSession(false);
		if (!SessionSecurity.hasValidCsrfToken(servletRequest, session)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(new LoginResponse("Jeton de sécurité invalide. Actualisez la page et réessayez."));
		}
		if (request == null || request.login() == null || request.login().isBlank()
				|| request.password() == null || request.password().isEmpty()) {
			return ResponseEntity.badRequest().body(new LoginResponse("Veuillez renseigner votre identifiant et votre mot de passe."));
		}

		List<LoginCredentials> credentials = jdbcTemplate.query(
				LOGIN_QUERY,
				(resultSet, rowNumber) -> new LoginCredentials(
						resultSet.getLong("id"),
						resultSet.getString("mdp"),
						resultSet.getString("prenom")),
				request.login());

		if (credentials.isEmpty() || !matchesMd5(request.password(), credentials.get(0).passwordHash())) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new LoginResponse("Identifiant ou mot de passe incorrect."));
		}

		// Renouvelle l'identifiant de session après authentification pour éviter sa fixation.
		servletRequest.changeSessionId();
		session.setAttribute(AUTHENTICATED_USER_SESSION_KEY, credentials.get(0).employeeId());
		session.setAttribute(AUTHENTICATED_USER_FIRST_NAME_SESSION_KEY, credentials.get(0).firstName());
		// Le jeton précédent est remplacé afin de le renouveler avec la session authentifiée.
		session.removeAttribute(SessionSecurity.CSRF_TOKEN_SESSION_KEY);
		SessionSecurity.getOrCreateCsrfToken(session);
		return ResponseEntity.ok(new LoginResponse("Connexion réussie."));
	}

	// Crée une session anonyme si nécessaire et fournit son jeton CSRF au client.
	@GetMapping("/api/csrf")
	public ResponseEntity<CsrfResponse> csrfToken(HttpServletRequest request) {
		String token = SessionSecurity.getOrCreateCsrfToken(request.getSession(true));
		return ResponseEntity.ok()
				.header("Cache-Control", "no-store")
				.body(new CsrfResponse(token));
	}

	// Valide les champs et enregistre un nouvel employé avec un mot de passe haché.
	@PostMapping("/api/register")
	public ResponseEntity<LoginResponse> register(
			@RequestBody RegisterRequest request,
			HttpServletRequest servletRequest) {
		if (!SessionSecurity.hasValidCsrfToken(servletRequest, servletRequest.getSession(false))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(new LoginResponse("Jeton de sécurité invalide. Actualisez la page et réessayez."));
		}
		if (request == null || isBlank(request.nom()) || isBlank(request.prenom())
				|| isBlank(request.login()) || request.password() == null || request.password().isEmpty()) {
			return ResponseEntity.badRequest()
					.body(new LoginResponse("Veuillez renseigner tous les champs."));
		}

		Integer existingLogins = jdbcTemplate.queryForObject(
				REGISTERED_LOGIN_QUERY,
				Integer.class,
				request.login().trim());
		if (existingLogins != null && existingLogins > 0) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new LoginResponse("Cet identifiant est déjà utilisé."));
		}

		try {
			jdbcTemplate.update(
					REGISTER_QUERY,
					request.login().trim(),
					hashMd5(request.password()),
					request.nom().trim(),
					request.prenom().trim());
		} catch (DuplicateKeyException exception) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new LoginResponse("Cet identifiant est déjà utilisé."));
		}

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new LoginResponse("Inscription réussie. Vous pouvez vous connecter."));
	}

	// Vérifie le jeton CSRF puis invalide la session de l'utilisateur connecté.
	@PostMapping("/api/logout")
	public ResponseEntity<LoginResponse> logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AUTHENTICATED_USER_SESSION_KEY) instanceof Number)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new LoginResponse("Votre session n'est pas active."));
		}
		if (!SessionSecurity.hasValidCsrfToken(request, session)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(new LoginResponse("Jeton de sécurité invalide. Actualisez la page et réessayez."));
		}

		session.invalidate();
		return ResponseEntity.ok(new LoginResponse("Déconnexion réussie."));
	}

	// Indique si une chaîne est nulle, vide ou composée uniquement d'espaces.
	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	// Produit l'empreinte MD5 hexadécimale attendue par le format historique de la base.
	static String hashMd5(String password) {
		try {
			var digest = MessageDigest.getInstance("MD5");
			return HexFormat.of().formatHex(digest.digest(password.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("L'algorithme MD5 est indisponible.", exception);
		}
	}

	// Compare le mot de passe fourni à l'empreinte stockée sans comparaison sensible au temps.
	static boolean matchesMd5(String password, String storedHash) {
		if (password == null || storedHash == null) {
			return false;
		}

		// Le stockage historique utilise MD5 ; on normalise en minuscules pour rester
		// compatible avec des valeurs déjà présentes dans la base et éviter les faux négatifs.
		var normalizedStoredHash = storedHash.toLowerCase(Locale.ROOT);
		return MessageDigest.isEqual(
				hashMd5(password).getBytes(StandardCharsets.US_ASCII),
				normalizedStoredHash.getBytes(StandardCharsets.US_ASCII));
	}

	// Identifiant et mot de passe reçus lors d'une tentative de connexion.
	public record LoginRequest(String login, String password) {
	}

	// Données d'identification récupérées en base pour authentifier un compte.
	private record LoginCredentials(long employeeId, String passwordHash, String firstName) {
	}

	// Informations fournies pour créer un compte employé.
	public record RegisterRequest(String nom, String prenom, String login, String password) {
	}

	// Message renvoyé après une opération d'authentification.
	public record LoginResponse(String message) {
	}

	// Jeton CSRF renvoyé au client pour protéger les requêtes modifiant des données.
	public record CsrfResponse(String token) {
	}
}

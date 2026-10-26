package com.example.appFormation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

// Utilitaires de génération et de validation des jetons anti-CSRF associés aux sessions.
final class SessionSecurity {

	// Attribut de session utilisé pour stocker le jeton CSRF.
	static final String CSRF_TOKEN_SESSION_KEY = "csrfToken";
	// En-tête HTTP dans lequel le client doit transmettre le jeton CSRF.
	static final String CSRF_TOKEN_HEADER = "X-CSRF-TOKEN";
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	// Cette classe ne contient que des méthodes statiques.
	private SessionSecurity() {
	}

	// Renvoie le jeton CSRF de la session ou en génère un s'il n'existe pas encore.
	static String getOrCreateCsrfToken(HttpSession session) {
		Object currentToken = session.getAttribute(CSRF_TOKEN_SESSION_KEY);
		if (currentToken instanceof String token) {
			return token;
		}

		// 32 octets aléatoires fournissent un jeton imprévisible suffisamment long.
		byte[] tokenBytes = new byte[32];
		SECURE_RANDOM.nextBytes(tokenBytes);
		// L'encodage URL-safe permet de transmettre le jeton simplement dans les en-têtes HTTP.
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
		session.setAttribute(CSRF_TOKEN_SESSION_KEY, token);
		return token;
	}

	// Valide le jeton fourni dans l'en-tête de la requête par rapport à celui de la session.
	static boolean hasValidCsrfToken(HttpServletRequest request, HttpSession session) {
		if (session == null) {
			return false;
		}
		Object expectedToken = session.getAttribute(CSRF_TOKEN_SESSION_KEY);
		String submittedToken = request.getHeader(CSRF_TOKEN_HEADER);
		if (!(expectedToken instanceof String token) || submittedToken == null) {
			return false;
		}
		// La comparaison en temps constant limite les fuites d'information sur le jeton.
		return MessageDigest.isEqual(
				token.getBytes(StandardCharsets.UTF_8),
				submittedToken.getBytes(StandardCharsets.UTF_8));
	}
}

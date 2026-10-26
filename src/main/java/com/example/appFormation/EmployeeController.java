package com.example.appFormation;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

// Contrôleur REST des routes de consultation et de gestion des employés, formations et services.
@RestController
@RequestMapping("/api/employe")
public class EmployeeController {

	// Requête SQL pour vérifier si un employé est administrateur
	private static final String ADMIN_CHECK_QUERY =
			"SELECT COUNT(*) FROM employe WHERE id = ? AND statut = 1";
	// Requête SQL pour récupérer les formations disponibles pour un employé
	private static final String AVAILABLE_TRAININGS_QUERY = """
			SELECT f.id, f.description, f.date_debut, f.nbre_heures, f.departement,
			       p.libelle AS produit_libelle, f.min_inscrits
			FROM formation f
			JOIN produit p ON p.id = f.produit_id
			WHERE f.date_debut >= CURRENT_DATE
			  AND NOT EXISTS (
			      SELECT 1
			      FROM inscription i
			      WHERE i.formation_id = f.id AND i.employe_id = ?
			  )
			ORDER BY f.date_debut, f.id
			""";
	// Requête SQL pour récupérer les inscriptions d'un employé
	private static final String EMPLOYEE_INSCRIPTIONS_QUERY = """
			SELECT f.id AS formation_id, f.description AS formation_nom, i.statut, i.commentaire,
			       (f.date_debut >= CURRENT_DATE AND i.statut = 'En cours') AS annulable
			FROM inscription i
			JOIN formation f ON f.id = i.formation_id
			WHERE i.employe_id = ?
			ORDER BY f.date_debut DESC, f.id
			""";
	// Requête SQL pour charger les formations et compter leurs inscriptions actives.
	private static final String ADMIN_TRAININGS_QUERY = """
			SELECT f.id, f.description, f.date_debut, f.nbre_heures, f.departement,
			       p.libelle AS produit_libelle, f.min_inscrits,
			       (SELECT COUNT(*) FROM inscription i
			        WHERE i.formation_id = f.id AND i.statut IN ('Accepté', 'En cours')) AS nombre_inscrits
			FROM formation f
			LEFT JOIN produit p ON p.id = f.produit_id
			ORDER BY f.date_debut DESC, f.id DESC
			""";
	// Requête SQL pour récupérer les inscriptions acceptées par formation
	private static final String ADMIN_ACCEPTED_ENROLLMENTS_QUERY = """
			SELECT i.id AS inscription_id, i.formation_id, e.nom AS employe_nom, e.prenom AS employe_prenom
			FROM inscription i
			JOIN employe e ON e.id = i.employe_id
			WHERE i.statut = 'Accepté'
			ORDER BY e.nom, e.prenom, i.id
			""";
	// Requête SQL pour récupérer les inscriptions d'un employé pour l'administration
	private static final String ADMIN_ENROLLMENTS_QUERY = """
			SELECT i.id AS inscription_id, i.formation_id, f.description AS formation_nom,
			       e.nom AS employe_nom, e.prenom AS employe_prenom, i.statut, i.commentaire
			FROM inscription i
			JOIN formation f ON f.id = i.formation_id
			JOIN employe e ON e.id = i.employe_id
			WHERE i.statut = 'En cours'
			ORDER BY f.date_debut, i.id
			""";
	// Requête SQL pour charger la liste des employés destinée à l'administration.
	private static final String ADMIN_EMPLOYEES_QUERY = """
			SELECT id, nom, prenom, login, statut
			FROM employe
			ORDER BY nom, prenom, id
			""";
	// Requête SQL pour récupérer les services, employés et produits pour l'administration
	private static final String SERVICE_ASSIGNMENTS_QUERY = """
			SELECT s.id AS affectation_id, s.employe_id, s.nom AS service_nom,
			       e.nom AS employe_nom, e.prenom AS employe_prenom,
			       p.libelle AS produit_libelle
			FROM service s
			LEFT JOIN employe e ON e.id = s.employe_id
			LEFT JOIN produit p ON p.id = s.produit_id
			WHERE s.nom IS NOT NULL
			ORDER BY s.nom, e.nom, e.prenom
			""";
	// Requête SQL pour récupérer les services distincts
	private static final String SERVICES_QUERY = """
			SELECT DISTINCT nom
			FROM service
			WHERE nom IS NOT NULL AND TRIM(nom) <> ''
			ORDER BY nom
			""";
	// Requête SQL pour récupérer les produits distincts
	private static final String PRODUCTS_QUERY = "SELECT id, libelle FROM produit ORDER BY libelle, id";
	// Requête SQL pour annuler l'inscription d'un employé à une formation à venir.
	private static final String ENROLLMENT_DELETE_QUERY = """
			DELETE i
			FROM inscription i
			JOIN formation f ON f.id = i.formation_id
			WHERE i.employe_id = ?
			  AND i.formation_id = ?
			  AND i.statut = 'En cours'
			  AND f.date_debut >= CURRENT_DATE
			""";
	// Requête SQL pour inscrire un employé à une formation
	private static final String ENROLLMENT_INSERT_QUERY = """
			INSERT INTO inscription (employe_id, statut, formation_id, commentaire)
			SELECT ?, 'En cours', f.id, NULL
			FROM formation f
			WHERE f.id = ?
			  AND f.date_debut >= CURRENT_DATE
			  AND NOT EXISTS (
			      SELECT 1
			      FROM inscription i
			      WHERE i.formation_id = f.id AND i.employe_id = ?
			  )
			""";
	// Requête SQL pour ajouter une formation
	private static final String FORMATION_INSERT_QUERY = """
			INSERT INTO formation (date_debut, nbre_heures, departement, produit_id, min_inscrits, description)
			VALUES (?, ?, ?, ?, ?, ?)
			""";
	// Requête SQL pour supprimer une formation si aucune inscription active n'existe
	private static final String FORMATION_DELETE_QUERY = """
			DELETE FROM formation
			WHERE id = ?
			  AND NOT EXISTS (
			      SELECT 1 FROM inscription
			      WHERE formation_id = ? AND statut IN ('Accepté', 'En cours')
			  )
			""";
	// Requête SQL pour verrouiller une formation pour mise à jour
	private static final String LOCK_TRAINING_QUERY =
			"SELECT id FROM formation WHERE id = ? FOR UPDATE";
	// Requête SQL pour compter les inscriptions actives à une formation
	private static final String ACTIVE_ENROLLMENTS_COUNT_QUERY =
			"SELECT COUNT(*) FROM inscription WHERE formation_id = ? AND statut IN ('Accepté', 'En cours')";
	// Requête SQL pour supprimer les inscriptions refusées à une formation
	private static final String DELETE_REFUSED_ENROLLMENTS_QUERY =
			"DELETE FROM inscription WHERE formation_id = ? AND statut = 'Refusé'";
	// Requête SQL pour supprimer une inscription acceptée à une formation
	private static final String DELETE_ACCEPTED_ENROLLMENT_QUERY =
			"DELETE FROM inscription WHERE formation_id = ? AND id = ? AND statut = 'Accepté'";
	// Requête SQL pour supprimer toutes les inscriptions acceptées à une formation
	private static final String DELETE_ALL_ACCEPTED_ENROLLMENTS_QUERY =
			"DELETE FROM inscription WHERE formation_id = ? AND statut = 'Accepté'";
	// Requête SQL pour mettre à jour le statut d'un employé
	private static final String EMPLOYEE_STATUS_UPDATE_QUERY =
			"UPDATE employe SET statut = ? WHERE id = ?";
	// Requête SQL pour mettre à jour le statut et le commentaire d'une inscription
	private static final String ENROLLMENT_REVIEW_QUERY = """
			UPDATE inscription
			SET statut = ?, commentaire = ?
			WHERE id = ? AND statut = 'En cours'
			""";
	// Requête SQL pour supprimer un service si aucun employé n'y est affecté
	private static final String SERVICE_DELETE_QUERY = """
			DELETE service_row
			FROM service AS service_row
			LEFT JOIN service AS employee_assignment
			  ON employee_assignment.nom = service_row.nom
			 AND employee_assignment.employe_id IS NOT NULL
			WHERE service_row.nom = ?
			  AND employee_assignment.id IS NULL
			""";
	// Requête SQL pour compter le nombre d'employés affectés à un service
	private static final String SERVICE_EMPLOYEE_COUNT_QUERY =
			"SELECT COUNT(*) FROM service WHERE nom = ? AND employe_id IS NOT NULL";
	// Requête SQL pour insérer un employé dans un service si aucune affectation n'existe déjà
	private static final String EMPLOYEE_SERVICE_INSERT_QUERY = """
			INSERT INTO service (employe_id, produit_id, nom)
			SELECT ?, ?, ?
			WHERE NOT EXISTS (
			    SELECT 1 FROM service WHERE employe_id = ? AND nom = ?
			)
			""";
	// Requête SQL pour vérifier si un service n'a aucun employé affecté
	private static final String AVAILABLE_SERVICE_SLOT_QUERY = """
			SELECT id
			FROM service
			WHERE nom = ? AND employe_id IS NULL AND produit_id IS NULL
			ORDER BY id
			LIMIT 1
			FOR UPDATE
			""";
	// Requête SQL pour affecter un employé et son produit à une place de service libre.
	private static final String SERVICE_SLOT_ASSIGN_QUERY = """
			UPDATE service
			SET employe_id = ?, produit_id = ?
			WHERE id = ? AND employe_id IS NULL AND produit_id IS NULL
			""";
	// Requête SQL pour supprimer l'affectation d'un employé à un service
	private static final String ASSIGNMENT_DELETE_QUERY = """
			UPDATE service
			SET employe_id = NULL, produit_id = NULL
			WHERE id = ? AND employe_id IS NOT NULL
			""";
	// Requête SQL pour verrouiller un employé pendant la création d'une affectation.
	private static final String EMPLOYEE_SERVICE_LOCK_QUERY =
			"SELECT id FROM employe WHERE id = ? FOR UPDATE";
	// Requête SQL pour vérifier qu'un employé existe.
	private static final String EMPLOYEE_EXISTS_QUERY =
			"SELECT COUNT(*) FROM employe WHERE id = ?";
	// Requête SQL pour vérifier qu'un employé n'est pas déjà affecté au service.
	private static final String EMPLOYEE_SERVICE_EXISTS_QUERY =
			"SELECT COUNT(*) FROM service WHERE employe_id = ? AND nom = ?";
	// Requête SQL pour vérifier qu'un service existe.
	private static final String SERVICE_EXISTS_QUERY =
			"SELECT COUNT(*) FROM service WHERE nom = ?";
	// Requête SQL pour détecter un doublon de service sans tenir compte des espaces ni de la casse.
	private static final String SERVICE_NAME_EXISTS_QUERY =
			"SELECT COUNT(*) FROM service WHERE LOWER(TRIM(nom)) = LOWER(TRIM(?))";
	// Requête SQL pour créer un service.
	private static final String SERVICE_CREATE_QUERY = "INSERT INTO service (nom) VALUES (?)";
	// Requête SQL pour vérifier qu'un produit existe.
	private static final String PRODUCT_EXISTS_QUERY =
			"SELECT COUNT(*) FROM produit WHERE id = ?";

	private final JdbcTemplate jdbcTemplate;

	// Reçoit le composant Spring utilisé pour exécuter les requêtes SQL.
	public EmployeeController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	// Charge le tableau de bord de l'employé connecté, avec les données d'administration si nécessaire.
	@GetMapping("/accueil")
	public ResponseEntity<DashboardResponse> dashboard(HttpServletRequest request) {
		Long employeeId = authenticatedEmployeeId(request);
		if (employeeId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		List<Training> availableTrainings = jdbcTemplate.query(
				AVAILABLE_TRAININGS_QUERY,
				(resultSet, rowNumber) -> {
					Date startDate = resultSet.getDate("date_debut");
					int hours = resultSet.getInt("nbre_heures");
					// JDBC renvoie 0 pour une valeur SQL NULL ; wasNull permet de préserver la nullabilité.
					Integer nullableHours = resultSet.wasNull() ? null : hours;
					int minimum = resultSet.getInt("min_inscrits");
					Integer nullableMinimum = resultSet.wasNull() ? null : minimum;
					return new Training(
							resultSet.getLong("id"),
							resultSet.getString("description"),
							startDate == null ? null : startDate.toLocalDate(),
							nullableHours,
							resultSet.getString("departement"),
							resultSet.getString("produit_libelle"),
							nullableMinimum);
				},
				employeeId);
		List<Enrollment> enrollments = jdbcTemplate.query(
				EMPLOYEE_INSCRIPTIONS_QUERY,
				(resultSet, rowNumber) -> new Enrollment(
						resultSet.getLong("formation_id"),
						resultSet.getString("formation_nom"),
						resultSet.getString("statut"),
						resultSet.getString("commentaire"),
						resultSet.getBoolean("annulable")),
				employeeId);
		boolean administrator = isAdministrator(employeeId);
		AdminData administration = administrator ? loadAdminData() : null;

		return ResponseEntity.ok()
				.header("Cache-Control", "no-store")
				.body(new DashboardResponse(
						availableTrainings,
						enrollments,
						firstName(request),
						SessionSecurity.getOrCreateCsrfToken(request.getSession(false)),
						administrator,
						administration));
	}

	// Inscrit l'employé connecté à une formation encore disponible.
	@PostMapping("/formations/{formationId}/inscription")
	public ResponseEntity<ApiMessage> enroll(
			@PathVariable long formationId,
			HttpServletRequest request) {
		Long employeeId = authenticatedEmployeeId(request);
		if (employeeId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ApiMessage("Veuillez vous connecter pour vous inscrire."));
		}
		if (!SessionSecurity.hasValidCsrfToken(request, request.getSession(false))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(new ApiMessage("Jeton de sécurité invalide. Actualisez la page et réessayez."));
		}

		try {
			int inserted = jdbcTemplate.update(ENROLLMENT_INSERT_QUERY, employeeId, formationId, employeeId);
			if (inserted == 0) {
				return ResponseEntity.status(HttpStatus.CONFLICT)
						.body(new ApiMessage("Cette formation n'est plus disponible ou vous y êtes déjà inscrit."));
			}
		} catch (DuplicateKeyException exception) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ApiMessage("Vous êtes déjà inscrit à cette formation."));
		}

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new ApiMessage("Inscription à la formation enregistrée."));
	}

	// Annule l'inscription de l'employé connecté si la formation n'a pas encore commencé.
	@DeleteMapping("/formations/{formationId}/inscription")
	public ResponseEntity<ApiMessage> unenroll(
			@PathVariable long formationId,
			HttpServletRequest request) {
		Long employeeId = authenticatedEmployeeId(request);
		if (employeeId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ApiMessage("Veuillez vous connecter pour vous désinscrire."));
		}
		if (!SessionSecurity.hasValidCsrfToken(request, request.getSession(false))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(new ApiMessage("Jeton de sécurité invalide. Actualisez la page et réessayez."));
		}

		int deleted = jdbcTemplate.update(ENROLLMENT_DELETE_QUERY, employeeId, formationId);
		if (deleted == 0) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ApiMessage("Cette inscription ne peut plus être annulée."));
		}
		return ResponseEntity.ok(new ApiMessage("Désinscription de la formation enregistrée."));
	}

	// Valide les informations et crée une formation réservée à l'administration.
	@PostMapping("/admin/formations")
	public ResponseEntity<ApiMessage> createTraining(
			@RequestBody CreateTrainingRequest training,
			HttpServletRequest request) {
		ResponseEntity<ApiMessage> accessFailure = authorizeAdminMutation(request);
		if (accessFailure != null) {
			return accessFailure;
		}
		if (training == null || training.dateDebut() == null
				|| training.dateDebut().isBefore(LocalDate.now())
				|| training.nbreHeures() == null || training.nbreHeures() < 1
				|| isBlank(training.departement()) || training.departement().trim().length() > 50
				|| training.produitId() == null || training.produitId() < 1
				|| training.minInscrits() == null || training.minInscrits() < 0
				|| isBlank(training.description()) || training.description().trim().length() > 50) {
			return badRequest("Vérifiez les champs de la formation (description et département : 50 caractères maximum).");
		}
		if (!exists(PRODUCT_EXISTS_QUERY, training.produitId())) {
			return badRequest("Le produit sélectionné n'existe pas.");
		}

		jdbcTemplate.update(
				FORMATION_INSERT_QUERY,
				Date.valueOf(training.dateDebut()),
				training.nbreHeures(),
				training.departement().trim(),
				training.produitId(),
				training.minInscrits(),
				training.description().trim());
		return ResponseEntity.status(HttpStatus.CREATED).body(new ApiMessage("Formation ajoutée."));
	}

	// Supprime une formation sans inscription active et nettoie ses inscriptions refusées.
	@DeleteMapping("/admin/formations/{formationId}")
	@Transactional
	public ResponseEntity<ApiMessage> deleteTraining(
			@PathVariable long formationId,
			HttpServletRequest request) {
		ResponseEntity<ApiMessage> accessFailure = authorizeAdminMutation(request);
		if (accessFailure != null) {
			return accessFailure;
		}
		if (jdbcTemplate.queryForList(LOCK_TRAINING_QUERY, Long.class, formationId).isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiMessage("Formation introuvable."));
		}
		if (exists(ACTIVE_ENROLLMENTS_COUNT_QUERY, formationId)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ApiMessage("Retirez les employés acceptés et traitez les inscriptions en cours avant de supprimer cette formation."));
		}
		jdbcTemplate.update(DELETE_REFUSED_ENROLLMENTS_QUERY, formationId);
		int deleted = jdbcTemplate.update(FORMATION_DELETE_QUERY, formationId, formationId);
		if (deleted == 0) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ApiMessage("La formation n'a pas pu être supprimée."));
		}
		return ResponseEntity.ok(new ApiMessage("Formation supprimée."));
	}

	// Retire une inscription précise de la liste des employés acceptés à une formation.
	@DeleteMapping("/admin/formations/{formationId}/inscriptions/{enrollmentId}")
	public ResponseEntity<ApiMessage> removeAcceptedEnrollment(
			@PathVariable long formationId,
			@PathVariable long enrollmentId,
			HttpServletRequest request) {
		ResponseEntity<ApiMessage> accessFailure = authorizeAdminMutation(request);
		if (accessFailure != null) {
			return accessFailure;
		}
		int deleted = jdbcTemplate.update(DELETE_ACCEPTED_ENROLLMENT_QUERY, formationId, enrollmentId);
		if (deleted == 0) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ApiMessage("Cette inscription n'existe pas ou n'est plus acceptée."));
		}
		return ResponseEntity.ok(new ApiMessage("Inscription acceptée retirée de la formation."));
	}

	// Retire toutes les inscriptions acceptées à la formation indiquée.
	@DeleteMapping("/admin/formations/{formationId}/inscriptions-acceptees")
	public ResponseEntity<ApiMessage> removeAllAcceptedEnrollments(
			@PathVariable long formationId,
			HttpServletRequest request) {
		ResponseEntity<ApiMessage> accessFailure = authorizeAdminMutation(request);
		if (accessFailure != null) {
			return accessFailure;
		}
		int deleted = jdbcTemplate.update(DELETE_ALL_ACCEPTED_ENROLLMENTS_QUERY, formationId);
		return ResponseEntity.ok(new ApiMessage(
				deleted == 0
						? "Aucune inscription acceptée à retirer."
						: deleted + " inscription(s) acceptée(s) retirée(s)."));
	}

	// Modifie le rôle d'un employé après validation du statut demandé.
	@PatchMapping("/admin/employes/{employeeId}/statut")
	public ResponseEntity<ApiMessage> changeEmployeeStatus(
			@PathVariable long employeeId,
			@RequestBody EmployeeStatusRequest body,
			HttpServletRequest request) {
		ResponseEntity<ApiMessage> accessFailure = authorizeAdminMutation(request);
		if (accessFailure != null) {
			return accessFailure;
		}
		if (employeeId < 1 || body == null || body.statut() == null
				|| (body.statut() != 0 && body.statut() != 1)) {
			return badRequest("Le statut doit être 0 (employé) ou 1 (administrateur).");
		}
		int updated = jdbcTemplate.update(EMPLOYEE_STATUS_UPDATE_QUERY, body.statut(), employeeId);
		if (updated == 0 && !exists("SELECT COUNT(*) FROM employe WHERE id = ?", employeeId)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiMessage("Employé introuvable."));
		}
		return ResponseEntity.ok(new ApiMessage("Statut de l'employé mis à jour."));
	}

	// Accepte ou refuse une inscription encore en cours de traitement.
	@PatchMapping("/admin/inscriptions/{enrollmentId}")
	public ResponseEntity<ApiMessage> reviewEnrollment(
			@PathVariable long enrollmentId,
			@RequestBody ReviewEnrollmentRequest body,
			HttpServletRequest request) {
		ResponseEntity<ApiMessage> accessFailure = authorizeAdminMutation(request);
		if (accessFailure != null) {
			return accessFailure;
		}
		if (enrollmentId < 1 || body == null
				|| !("Accepté".equals(body.statut()) || "Refusé".equals(body.statut()))
				|| (body.commentaire() != null && body.commentaire().trim().length() > 60)) {
			return badRequest("Choisissez « Accepté » ou « Refusé » et limitez le commentaire à 60 caractères.");
		}
		String comment = body.commentaire() == null || body.commentaire().isBlank()
				? null
				: body.commentaire().trim();
		int updated = jdbcTemplate.update(ENROLLMENT_REVIEW_QUERY, body.statut(), comment, enrollmentId);
		if (updated == 0) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ApiMessage("Cette inscription n'existe plus ou a déjà été traitée."));
		}
		return ResponseEntity.ok(new ApiMessage("Inscription traitée."));
	}

	// Crée un service après validation et vérification de l'absence de doublon.
	@PostMapping("/admin/services")
	public ResponseEntity<ApiMessage> createService(
			@RequestBody CreateServiceRequest body,
			HttpServletRequest request) {
		ResponseEntity<ApiMessage> accessFailure = authorizeAdminMutation(request);
		if (accessFailure != null) {
			return accessFailure;
		}
		if (body == null || isBlank(body.nom()) || body.nom().trim().length() > 55) {
			return badRequest("Le nom du service est obligatoire (55 caractères maximum).");
		}
		String serviceName = body.nom().trim();
		if (exists(SERVICE_NAME_EXISTS_QUERY, serviceName)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ApiMessage("Ce service existe déjà."));
		}
		jdbcTemplate.update(SERVICE_CREATE_QUERY, serviceName);
		return ResponseEntity.status(HttpStatus.CREATED).body(new ApiMessage("Service ajouté."));
	}

	// Supprime un service uniquement lorsqu'aucun employé ne lui est affecté.
	@DeleteMapping("/admin/services")
	public ResponseEntity<ApiMessage> deleteService(
			@RequestParam String nom,
			HttpServletRequest request) {
		ResponseEntity<ApiMessage> accessFailure = authorizeAdminMutation(request);
		if (accessFailure != null) {
			return accessFailure;
		}
		if (isBlank(nom)) {
			return badRequest("Le nom du service est obligatoire.");
		}
		int deleted = jdbcTemplate.update(SERVICE_DELETE_QUERY, nom.trim());
		if (deleted == 0) {
			if (!exists(SERVICE_EXISTS_QUERY, nom.trim())) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new ApiMessage("Service introuvable."));
			}
			if (exists(SERVICE_EMPLOYEE_COUNT_QUERY, nom.trim())) {
				return ResponseEntity.status(HttpStatus.CONFLICT)
						.body(new ApiMessage("Impossible de supprimer un service auquel des employés sont affectés."));
			}
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ApiMessage("Le service n'a pas pu être supprimé."));
		}
		return ResponseEntity.ok(new ApiMessage("Service supprimé."));
	}

	// Affecte un employé et son produit à une place libre, ou crée une nouvelle affectation.
	@PostMapping("/admin/affectations")
	@Transactional
	public ResponseEntity<ApiMessage> assignEmployeeToService(
			@RequestBody EmployeeServiceAssignmentRequest body,
			HttpServletRequest request) {
		ResponseEntity<ApiMessage> accessFailure = authorizeAdminMutation(request);
		if (accessFailure != null) {
			return accessFailure;
		}
		if (body == null || body.employeId() == null || body.employeId() < 1
				|| isBlank(body.service()) || body.service().trim().length() > 55
				|| body.produitId() == null || body.produitId() < 1) {
			return badRequest("Sélectionnez un employé existant, un service et un produit dédié.");
		}
		String service = body.service().trim();
		if (!exists(EMPLOYEE_EXISTS_QUERY, body.employeId())
				|| !exists(SERVICE_EXISTS_QUERY, service)
				|| !exists(PRODUCT_EXISTS_QUERY, body.produitId())) {
			return badRequest("L'employé, le service ou le produit sélectionné n'existe pas.");
		}
		if (jdbcTemplate.queryForList(EMPLOYEE_SERVICE_LOCK_QUERY, Long.class, body.employeId()).isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiMessage("Employé introuvable."));
		}
		if (exists(EMPLOYEE_SERVICE_EXISTS_QUERY, body.employeId(), service)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ApiMessage("Cet employé est déjà affecté à ce service."));
		}
		List<Long> availableSlots = jdbcTemplate.queryForList(
				AVAILABLE_SERVICE_SLOT_QUERY,
				Long.class,
				service);
		if (availableSlots.isEmpty()) {
			// Aucune place libre : crée une affectation après une dernière vérification anti-doublon.
			int inserted = jdbcTemplate.update(
					EMPLOYEE_SERVICE_INSERT_QUERY,
					body.employeId(),
					body.produitId(),
					service,
					body.employeId(),
					service);
			if (inserted == 0) {
				return ResponseEntity.status(HttpStatus.CONFLICT)
						.body(new ApiMessage("Cet employé est déjà affecté à ce service."));
			}
		} else {
			// Réutilise la première place libre, verrouillée par la requête dans la transaction.
			int updated = jdbcTemplate.update(
					SERVICE_SLOT_ASSIGN_QUERY,
					body.employeId(),
					body.produitId(),
					availableSlots.get(0));
			if (updated == 0) {
				return ResponseEntity.status(HttpStatus.CONFLICT)
						.body(new ApiMessage("Cette place de service vient d'être attribuée. Actualisez et réessayez."));
			}
		}
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new ApiMessage("Employé existant ajouté au service avec son produit dédié."));
	}

	// Libère l'employé et le produit associés à une place de service.
	@DeleteMapping("/admin/affectations/{assignmentId}")
	public ResponseEntity<ApiMessage> removeEmployeeServiceAssignment(
			@PathVariable long assignmentId,
			HttpServletRequest request) {
		ResponseEntity<ApiMessage> accessFailure = authorizeAdminMutation(request);
		if (accessFailure != null) {
			return accessFailure;
		}
		int updated = jdbcTemplate.update(ASSIGNMENT_DELETE_QUERY, assignmentId);
		if (updated == 0) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new ApiMessage("Cette association employé/service n'existe plus."));
		}
		return ResponseEntity.ok(new ApiMessage("Association supprimée. La place de service est de nouveau disponible."));
	}

	// Rassemble les formations, inscriptions, employés et options nécessaires au panneau d'administration.
	private AdminData loadAdminData() {
		List<AdminAcceptedEnrollment> acceptedEnrollments = jdbcTemplate.query(
				ADMIN_ACCEPTED_ENROLLMENTS_QUERY,
				(resultSet, rowNumber) -> new AdminAcceptedEnrollment(
						resultSet.getLong("inscription_id"),
						resultSet.getLong("formation_id"),
						resultSet.getString("employe_prenom"),
						resultSet.getString("employe_nom")));
		// Regroupe les inscriptions acceptées pour les rattacher à chaque formation.
		Map<Long, List<AdminAcceptedEnrollment>> acceptedByTraining = acceptedEnrollments.stream()
				.collect(Collectors.groupingBy(AdminAcceptedEnrollment::formationId));
		List<AdminTraining> trainings = jdbcTemplate.query(
				ADMIN_TRAININGS_QUERY,
				(resultSet, rowNumber) -> {
					Date startDate = resultSet.getDate("date_debut");
					return new AdminTraining(
							resultSet.getLong("id"),
							resultSet.getString("description"),
							startDate == null ? null : startDate.toLocalDate(),
							resultSet.getInt("nbre_heures"),
							resultSet.getString("departement"),
							resultSet.getString("produit_libelle"),
							resultSet.getInt("min_inscrits"),
							resultSet.getInt("nombre_inscrits"),
							acceptedByTraining.getOrDefault(resultSet.getLong("id"), List.of()));
				});
		List<AdminEnrollment> enrollments = jdbcTemplate.query(
				ADMIN_ENROLLMENTS_QUERY,
				(resultSet, rowNumber) -> new AdminEnrollment(
						resultSet.getLong("inscription_id"),
						resultSet.getLong("formation_id"),
						resultSet.getString("formation_nom"),
						resultSet.getString("employe_prenom"),
						resultSet.getString("employe_nom"),
						resultSet.getString("statut"),
						resultSet.getString("commentaire")));
		List<AdminEmployee> employees = jdbcTemplate.query(
				ADMIN_EMPLOYEES_QUERY,
				(resultSet, rowNumber) -> new AdminEmployee(
						resultSet.getLong("id"),
						resultSet.getString("nom"),
						resultSet.getString("prenom"),
						resultSet.getString("login"),
						resultSet.getInt("statut")));
		List<ServiceAssignment> assignments = jdbcTemplate.query(
				SERVICE_ASSIGNMENTS_QUERY,
				(resultSet, rowNumber) -> {
					long employeeId = resultSet.getLong("employe_id");
					// Les services sans employé représentent des places disponibles.
					Long nullableEmployeeId = resultSet.wasNull() ? null : employeeId;
					return new ServiceAssignment(
							resultSet.getLong("affectation_id"),
							nullableEmployeeId,
							resultSet.getString("service_nom"),
							resultSet.getString("employe_prenom"),
							resultSet.getString("employe_nom"),
							resultSet.getString("produit_libelle"));
				});
		List<ServiceOption> services = jdbcTemplate.query(
				SERVICES_QUERY,
				(resultSet, rowNumber) -> new ServiceOption(resultSet.getString("nom")));
		List<ProductOption> products = jdbcTemplate.query(
				PRODUCTS_QUERY,
				(resultSet, rowNumber) -> new ProductOption(
						resultSet.getLong("id"),
						resultSet.getString("libelle")));
		return new AdminData(trainings, enrollments, employees, assignments, services, products);
	}

	// Vérifie l'identité, les droits d'administration et le jeton CSRF d'une action de modification.
	private ResponseEntity<ApiMessage> authorizeAdminMutation(HttpServletRequest request) {
		Long employeeId = authenticatedEmployeeId(request);
		if (employeeId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ApiMessage("Veuillez vous connecter pour accéder à cette action."));
		}
		if (!isAdministrator(employeeId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(new ApiMessage("Cette action est réservée aux administrateurs."));
		}
		if (!SessionSecurity.hasValidCsrfToken(request, request.getSession(false))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(new ApiMessage("Jeton de sécurité invalide. Actualisez la page et réessayez."));
		}
		return null;
	}

	// Indique si l'employé possède le statut administrateur.
	private boolean isAdministrator(long employeeId) {
		Integer count = jdbcTemplate.queryForObject(ADMIN_CHECK_QUERY, Integer.class, employeeId);
		return count != null && count > 0;
	}

	// Exécute une requête de comptage paramétrée et indique si elle trouve au moins une ligne.
	private boolean exists(String query, Object... arguments) {
		Integer count = jdbcTemplate.queryForObject(query, Integer.class, arguments);
		return count != null && count > 0;
	}

	// Construit une réponse HTTP 400 avec le message destiné au client.
	private static ResponseEntity<ApiMessage> badRequest(String message) {
		return ResponseEntity.badRequest().body(new ApiMessage(message));
	}

	// Indique si une chaîne est nulle, vide ou composée uniquement d'espaces.
	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	// Récupère l'identifiant de l'employé dans la session, sans créer de nouvelle session.
	private static Long authenticatedEmployeeId(HttpServletRequest request) {
		var session = request.getSession(false);
		if (session == null) {
			return null;
		}
		Object employeeId = session.getAttribute(LoginController.AUTHENTICATED_USER_SESSION_KEY);
		return employeeId instanceof Number number ? number.longValue() : null;
	}

	// Récupère le prénom mémorisé en session, avec une valeur de remplacement si nécessaire.
	private static String firstName(HttpServletRequest request) {
		Object firstName = request.getSession(false)
				.getAttribute(LoginController.AUTHENTICATED_USER_FIRST_NAME_SESSION_KEY);
		return firstName instanceof String name && !name.isBlank() ? name : "employé";
	}

	// Données d'une formation disponible présentées à l'employé.
	public record Training(
			long id,
			String description,
			LocalDate dateDebut,
			Integer nbreHeures,
			String departement,
			String produitLibelle,
			Integer minInscrits) {
	}

	// Données d'une inscription de l'employé, avec son état et sa possibilité d'annulation.
	public record Enrollment(
			long formationId,
			String formationNom,
			String statut,
			String commentaire,
			boolean annulable) {
	}

	// Réponse complète du tableau de bord de l'employé.
	public record DashboardResponse(
			List<Training> formationsDisponibles,
			List<Enrollment> inscriptions,
			String prenomEmploye,
			String csrfToken,
			boolean administrateur,
			AdminData administration) {
	}

	// Message simple renvoyé par les routes de modification.
	public record ApiMessage(String message) {
	}

	// Ensemble des données chargées pour le panneau d'administration.
	public record AdminData(
			List<AdminTraining> formations,
			List<AdminEnrollment> inscriptionsEnCours,
			List<AdminEmployee> employes,
			List<ServiceAssignment> affectations,
			List<ServiceOption> services,
			List<ProductOption> produits) {
	}

	// Formation affichée dans l'interface d'administration avec ses inscriptions acceptées.
	public record AdminTraining(
			long id,
			String description,
			LocalDate dateDebut,
			int nbreHeures,
			String departement,
			String produitLibelle,
			int minInscrits,
			int nombreInscrits,
			List<AdminAcceptedEnrollment> inscritsAcceptes) {
	}

	// Inscription acceptée associée à une formation dans la vue d'administration.
	public record AdminAcceptedEnrollment(
			long id,
			long formationId,
			String employePrenom,
			String employeNom) {
	}

	// Inscription en attente de traitement par un administrateur.
	public record AdminEnrollment(
			long id,
			long formationId,
			String formationNom,
			String employePrenom,
			String employeNom,
			String statut,
			String commentaire) {
	}

	// Informations d'un employé affichées dans le panneau d'administration.
	public record AdminEmployee(long id, String nom, String prenom, String login, int statut) {
	}

	// Affectation d'un employé ou place libre dans un service.
	public record ServiceAssignment(
			long id,
			Long employeId,
			String service,
			String employePrenom,
			String employeNom,
			String produitLibelle) {
	}

	// Nom d'un service proposé dans les formulaires d'administration.
	public record ServiceOption(String nom) {
	}

	// Produit proposé dans les formulaires d'administration.
	public record ProductOption(long id, String libelle) {
	}

	// Champs requis pour créer une formation.
	public record CreateTrainingRequest(
			LocalDate dateDebut,
			Integer nbreHeures,
			String departement,
			Long produitId,
			Integer minInscrits,
			String description) {
	}

	// Nouveau statut demandé pour un employé.
	public record EmployeeStatusRequest(Integer statut) {
	}

	// Décision et commentaire saisis lors du traitement d'une inscription.
	public record ReviewEnrollmentRequest(String statut, String commentaire) {
	}

	// Nom du service à créer.
	public record CreateServiceRequest(String nom) {
	}

	// Employé, service et produit associés lors d'une nouvelle affectation.
	public record EmployeeServiceAssignmentRequest(Long employeId, String service, Long produitId) {
	}
}

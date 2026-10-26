package com.example.appFormation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class EmployeeControllerTests {

	private JdbcTemplate jdbcTemplate;
	private EmployeeController controller;
	private HttpServletRequest request;
	private HttpSession session;

	@BeforeEach
	void setUp() {
		jdbcTemplate = mock(JdbcTemplate.class);
		controller = new EmployeeController(jdbcTemplate);
		request = mock(HttpServletRequest.class);
		session = mock(HttpSession.class);
		when(request.getSession(false)).thenReturn(session);
		when(session.getAttribute(LoginController.AUTHENTICATED_USER_SESSION_KEY)).thenReturn(42L);
		when(session.getAttribute(SessionSecurity.CSRF_TOKEN_SESSION_KEY)).thenReturn("valid-csrf-token");
		when(request.getHeader(SessionSecurity.CSRF_TOKEN_HEADER)).thenReturn("valid-csrf-token");
	}

	@Test
	void rejectsDashboardAccessWithoutAnAuthenticatedEmployee() {
		when(request.getSession(false)).thenReturn(null);

		var response = controller.dashboard(request);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		verify(jdbcTemplate, never()).query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<EmployeeController.Training>>any(), eq(42L));
	}

	@Test
	void createsAnInProgressEnrollmentForTheAuthenticatedEmployee() {
		when(jdbcTemplate.update(anyString(), eq(42L), eq(7L), eq(42L))).thenReturn(1);

		var response = controller.enroll(7L, request);

		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		verify(jdbcTemplate).update(
				"""
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
				""",
				42L,
				7L,
				42L);
	}

	@Test
	void refusesUnavailableOrAlreadyJoinedTraining() {
		when(jdbcTemplate.update(anyString(), eq(42L), eq(7L), eq(42L))).thenReturn(0);

		var response = controller.enroll(7L, request);

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
	}

	@Test
	void rejectsEnrollmentWithoutAValidCsrfToken() {
		when(request.getHeader(SessionSecurity.CSRF_TOKEN_HEADER)).thenReturn("invalid-token");

		var response = controller.enroll(7L, request);

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		verify(jdbcTemplate, never()).update(anyString(), (Object[]) org.mockito.ArgumentMatchers.any());
	}

	@Test
	void deletesOnlyTheAuthenticatedEmployeesActiveFutureEnrollment() {
		when(jdbcTemplate.update(anyString(), eq(42L), eq(7L))).thenReturn(1);

		var response = controller.unenroll(7L, request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(jdbcTemplate).update(
				"""
				DELETE i
				FROM inscription i
				JOIN formation f ON f.id = i.formation_id
				WHERE i.employe_id = ?
				  AND i.formation_id = ?
				  AND i.statut = 'En cours'
				  AND f.date_debut >= CURRENT_DATE
				""",
				42L,
				7L);
	}

	@Test
	void rejectsUnenrollmentWhenThereIsNoCancelableEnrollment() {
		when(jdbcTemplate.update(anyString(), eq(42L), eq(7L))).thenReturn(0);

		var response = controller.unenroll(7L, request);

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
	}

	@Test
	void preventsRegularEmployeesFromUsingAdminActions() {
		var response = controller.deleteService("Finance", request);

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		verify(jdbcTemplate, never()).update(anyString(), (Object[]) org.mockito.ArgumentMatchers.any());
	}

	@Test
	void deletesAServiceWhenNoEmployeeIsAssigned() {
		authorizeAdministrator();
		when(jdbcTemplate.update(
				eq("""
				DELETE service_row
				FROM service AS service_row
				LEFT JOIN service AS employee_assignment
				  ON employee_assignment.nom = service_row.nom
				 AND employee_assignment.employe_id IS NOT NULL
				WHERE service_row.nom = ?
				  AND employee_assignment.id IS NULL
				"""),
				eq("Finance"))).thenReturn(1);

		var response = controller.deleteService(" Finance ", request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(jdbcTemplate).update(
				"""
				DELETE service_row
				FROM service AS service_row
				LEFT JOIN service AS employee_assignment
				  ON employee_assignment.nom = service_row.nom
				 AND employee_assignment.employe_id IS NOT NULL
				WHERE service_row.nom = ?
				  AND employee_assignment.id IS NULL
				""",
				"Finance");
	}

	@Test
	void refusesToDeleteAServiceWithAssignedEmployees() {
		authorizeAdministrator();
		when(jdbcTemplate.update(anyString(), eq("Finance"))).thenReturn(0);
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM service WHERE nom = ?"),
				eq(Integer.class),
				eq("Finance"))).thenReturn(1);
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM service WHERE nom = ? AND employe_id IS NOT NULL"),
				eq(Integer.class),
				eq("Finance"))).thenReturn(1);

		var response = controller.deleteService("Finance", request);

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
	}

	@Test
	void createsAServiceForAnAdministrator() {
		authorizeAdministrator();
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM service WHERE LOWER(TRIM(nom)) = LOWER(TRIM(?))"),
				eq(Integer.class),
				eq("Finance"))).thenReturn(0);
		when(jdbcTemplate.update("INSERT INTO service (nom) VALUES (?)", "Finance")).thenReturn(1);

		var response = controller.createService(new EmployeeController.CreateServiceRequest(" Finance "), request);

		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		verify(jdbcTemplate).update("INSERT INTO service (nom) VALUES (?)", "Finance");
	}

	@Test
	void assignsAnExistingEmployeeToAnExistingServiceAndProduct() {
		authorizeAdministrator();
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM employe WHERE id = ?"),
				eq(Integer.class),
				eq(7L))).thenReturn(1);
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM service WHERE nom = ?"),
				eq(Integer.class),
				eq("Finance"))).thenReturn(1);
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM produit WHERE id = ?"),
				eq(Integer.class),
				eq(3L))).thenReturn(1);
		when(jdbcTemplate.queryForList(
				eq("SELECT id FROM employe WHERE id = ? FOR UPDATE"),
				eq(Long.class),
				eq(7L))).thenReturn(List.of(7L));
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM service WHERE employe_id = ? AND nom = ?"),
				eq(Integer.class),
				eq(7L),
				eq("Finance"))).thenReturn(0);
		when(jdbcTemplate.update(
				eq("""
				INSERT INTO service (employe_id, produit_id, nom)
				SELECT ?, ?, ?
				WHERE NOT EXISTS (
				    SELECT 1 FROM service WHERE employe_id = ? AND nom = ?
				)
				"""),
				eq(7L),
				eq(3L),
				eq("Finance"),
				eq(7L),
				eq("Finance"))).thenReturn(1);

		var response = controller.assignEmployeeToService(
				new EmployeeController.EmployeeServiceAssignmentRequest(7L, " Finance ", 3L),
				request);

		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		verify(jdbcTemplate).update(
				"""
				INSERT INTO service (employe_id, produit_id, nom)
				SELECT ?, ?, ?
				WHERE NOT EXISTS (
				    SELECT 1 FROM service WHERE employe_id = ? AND nom = ?
				)
				""",
				7L,
				3L,
				"Finance",
				7L,
				"Finance");
	}

	@Test
	void reusesAnEmptyServiceSlotBeforeCreatingANewAssociation() {
		authorizeAdministrator();
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM employe WHERE id = ?"),
				eq(Integer.class),
				eq(7L))).thenReturn(1);
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM service WHERE nom = ?"),
				eq(Integer.class),
				eq("Finance"))).thenReturn(1);
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM produit WHERE id = ?"),
				eq(Integer.class),
				eq(3L))).thenReturn(1);
		when(jdbcTemplate.queryForList(
				eq("SELECT id FROM employe WHERE id = ? FOR UPDATE"),
				eq(Long.class),
				eq(7L))).thenReturn(List.of(7L));
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM service WHERE employe_id = ? AND nom = ?"),
				eq(Integer.class),
				eq(7L),
				eq("Finance"))).thenReturn(0);
		when(jdbcTemplate.queryForList(
				eq("""
				SELECT id
				FROM service
				WHERE nom = ? AND employe_id IS NULL AND produit_id IS NULL
				ORDER BY id
				LIMIT 1
				FOR UPDATE
				"""),
				eq(Long.class),
				eq("Finance"))).thenReturn(List.of(22L));
		when(jdbcTemplate.update(
				eq("""
				UPDATE service
				SET employe_id = ?, produit_id = ?
				WHERE id = ? AND employe_id IS NULL AND produit_id IS NULL
				"""),
				eq(7L),
				eq(3L),
				eq(22L))).thenReturn(1);

		var response = controller.assignEmployeeToService(
				new EmployeeController.EmployeeServiceAssignmentRequest(7L, "Finance", 3L),
				request);

		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		verify(jdbcTemplate).update(
				"""
				UPDATE service
				SET employe_id = ?, produit_id = ?
				WHERE id = ? AND employe_id IS NULL AND produit_id IS NULL
				""",
				7L,
				3L,
				22L);
		verify(jdbcTemplate, never()).update(
				org.mockito.ArgumentMatchers.startsWith("INSERT INTO service"),
				(Object[]) org.mockito.ArgumentMatchers.any());
	}

	@Test
	void removesAnEmployeeServiceAssociationAndPreservesTheServiceSlot() {
		authorizeAdministrator();
		when(jdbcTemplate.update(
				eq("""
				UPDATE service
				SET employe_id = NULL, produit_id = NULL
				WHERE id = ? AND employe_id IS NOT NULL
				"""),
				eq(22L))).thenReturn(1);

		var response = controller.removeEmployeeServiceAssignment(22L, request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(jdbcTemplate).update(
				"""
				UPDATE service
				SET employe_id = NULL, produit_id = NULL
				WHERE id = ? AND employe_id IS NOT NULL
				""",
				22L);
	}

	@Test
	void preventsAssigningTheSameEmployeeToAServiceTwice() {
		authorizeAdministrator();
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM employe WHERE id = ?"),
				eq(Integer.class),
				eq(7L))).thenReturn(1);
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM service WHERE nom = ?"),
				eq(Integer.class),
				eq("Finance"))).thenReturn(1);
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM produit WHERE id = ?"),
				eq(Integer.class),
				eq(3L))).thenReturn(1);
		when(jdbcTemplate.queryForList(
				eq("SELECT id FROM employe WHERE id = ? FOR UPDATE"),
				eq(Long.class),
				eq(7L))).thenReturn(List.of(7L));
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM service WHERE employe_id = ? AND nom = ?"),
				eq(Integer.class),
				eq(7L),
				eq("Finance"))).thenReturn(1);

		var response = controller.assignEmployeeToService(
				new EmployeeController.EmployeeServiceAssignmentRequest(7L, "Finance", 3L),
				request);

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		verify(jdbcTemplate, never()).update(anyString(), (Object[]) org.mockito.ArgumentMatchers.any());
	}

	@Test
	void createsTrainingForAnAdministrator() {
		authorizeAdministrator();
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM produit WHERE id = ?"),
				eq(Integer.class),
				eq(3L))).thenReturn(1);
		var training = new EmployeeController.CreateTrainingRequest(
				LocalDate.now().plusDays(1),
				8,
				"Informatique",
				3L,
				2,
				"Formation Java");
		when(jdbcTemplate.update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(1);

		var response = controller.createTraining(training, request);

		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		verify(jdbcTemplate).update(
				"""
				INSERT INTO formation (date_debut, nbre_heures, departement, produit_id, min_inscrits, description)
				VALUES (?, ?, ?, ?, ?, ?)
				""",
				java.sql.Date.valueOf(training.dateDebut()),
				8,
				"Informatique",
				3L,
				2,
				"Formation Java");
	}

	@Test
	void preventsDeletingTrainingThatHasEnrollments() {
		authorizeAdministrator();
		when(jdbcTemplate.queryForList(
				eq("SELECT id FROM formation WHERE id = ? FOR UPDATE"),
				eq(Long.class),
				eq(12L))).thenReturn(List.of(12L));
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM inscription WHERE formation_id = ? AND statut IN ('Accepté', 'En cours')"),
				eq(Integer.class),
				eq(12L))).thenReturn(1);

		var response = controller.deleteTraining(12L, request);

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		verify(jdbcTemplate, never()).update(anyString(), (Object[]) org.mockito.ArgumentMatchers.any());
	}

	@Test
	void deletesRefusedEnrollmentsBeforeDeletingAnOtherwiseEmptyTraining() {
		authorizeAdministrator();
		when(jdbcTemplate.queryForList(
				eq("SELECT id FROM formation WHERE id = ? FOR UPDATE"),
				eq(Long.class),
				eq(12L))).thenReturn(List.of(12L));
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM inscription WHERE formation_id = ? AND statut IN ('Accepté', 'En cours')"),
				eq(Integer.class),
				eq(12L))).thenReturn(0);
		when(jdbcTemplate.update(
				eq("DELETE FROM inscription WHERE formation_id = ? AND statut = 'Refusé'"),
				eq(12L))).thenReturn(2);
		when(jdbcTemplate.update(
				eq("""
				DELETE FROM formation
				WHERE id = ?
				  AND NOT EXISTS (
				      SELECT 1 FROM inscription
				      WHERE formation_id = ? AND statut IN ('Accepté', 'En cours')
				  )
				"""),
				eq(12L),
				eq(12L))).thenReturn(1);

		var response = controller.deleteTraining(12L, request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(jdbcTemplate).update("DELETE FROM inscription WHERE formation_id = ? AND statut = 'Refusé'", 12L);
		verify(jdbcTemplate).update(
				"""
				DELETE FROM formation
				WHERE id = ?
				  AND NOT EXISTS (
				      SELECT 1 FROM inscription
				      WHERE formation_id = ? AND statut IN ('Accepté', 'En cours')
				  )
				""",
				12L,
				12L);
	}

	@Test
	void removesOnlyAnAcceptedEnrollmentFromItsFormation() {
		authorizeAdministrator();
		when(jdbcTemplate.update(
				eq("DELETE FROM inscription WHERE formation_id = ? AND id = ? AND statut = 'Accepté'"),
				eq(12L),
				eq(19L))).thenReturn(1);

		var response = controller.removeAcceptedEnrollment(12L, 19L, request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(jdbcTemplate).update(
				"DELETE FROM inscription WHERE formation_id = ? AND id = ? AND statut = 'Accepté'",
				12L,
				19L);
	}

	@Test
	void removesAllAcceptedEnrollmentsFromAFormation() {
		authorizeAdministrator();
		when(jdbcTemplate.update(
				eq("DELETE FROM inscription WHERE formation_id = ? AND statut = 'Accepté'"),
				eq(12L))).thenReturn(3);

		var response = controller.removeAllAcceptedEnrollments(12L, request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(jdbcTemplate).update("DELETE FROM inscription WHERE formation_id = ? AND statut = 'Accepté'", 12L);
	}

	@Test
	void changesEmployeeStatusForAnAdministrator() {
		authorizeAdministrator();
		when(jdbcTemplate.update(anyString(), eq(1), eq(5L))).thenReturn(1);

		var response = controller.changeEmployeeStatus(
				5L,
				new EmployeeController.EmployeeStatusRequest(1),
				request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(jdbcTemplate).update("UPDATE employe SET statut = ? WHERE id = ?", 1, 5L);
	}

	@Test
	void acceptsPendingEnrollmentAndSavesItsComment() {
		authorizeAdministrator();
		when(jdbcTemplate.update(anyString(), eq("Accepté"), eq("À confirmer"), eq(17L))).thenReturn(1);

		var response = controller.reviewEnrollment(
				17L,
				new EmployeeController.ReviewEnrollmentRequest("Accepté", " À confirmer "),
				request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(jdbcTemplate).update(
				"""
				UPDATE inscription
				SET statut = ?, commentaire = ?
				WHERE id = ? AND statut = 'En cours'
				""",
				"Accepté",
				"À confirmer",
				17L);
	}

	private void authorizeAdministrator() {
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM employe WHERE id = ? AND statut = 1"),
				eq(Integer.class),
				eq(42L))).thenReturn(1);
	}
}

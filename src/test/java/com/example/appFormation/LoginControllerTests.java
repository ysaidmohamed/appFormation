package com.example.appFormation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class LoginControllerTests {

	private JdbcTemplate jdbcTemplate;
	private LoginController controller;
	private HttpServletRequest request;
	private HttpSession session;

	@BeforeEach
	void setUp() {
		jdbcTemplate = mock(JdbcTemplate.class);
		controller = new LoginController(jdbcTemplate);
		request = mock(HttpServletRequest.class);
		session = mock(HttpSession.class);
		when(request.getSession(false)).thenReturn(session);
		when(session.getAttribute(SessionSecurity.CSRF_TOKEN_SESSION_KEY)).thenReturn("valid-csrf-token");
		when(request.getHeader(SessionSecurity.CSRF_TOKEN_HEADER)).thenReturn("valid-csrf-token");
	}

	@Test
	void matchesMd5PasswordHash() {
		assertTrue(LoginController.matchesMd5("password", "5f4dcc3b5aa765d61d8327deb882cf99"));
		assertTrue(LoginController.matchesMd5("password", "5F4DCC3B5AA765D61D8327DEB882CF99"));
		assertFalse(LoginController.matchesMd5("incorrect", "5f4dcc3b5aa765d61d8327deb882cf99"));
		assertFalse(LoginController.matchesMd5("password", null));
		assertFalse(LoginController.matchesMd5(null, "5f4dcc3b5aa765d61d8327deb882cf99"));
	}

	@Test
	void looksUpLoginUsingTheExactSubmittedCase() {
		var response = controller.login(
				new LoginController.LoginRequest("MiXeD-User", "password"),
				request);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		verify(jdbcTemplate).query(
				eq("SELECT id, mdp, prenom FROM employe WHERE BINARY login = BINARY ? LIMIT 1"),
				org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),
				eq("MiXeD-User"));
	}

	@Test
	void registersEmployeeWithInactiveStatus() {
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM employe WHERE BINARY login = BINARY ?"),
				eq(Integer.class),
				eq("new-User"))).thenReturn(0);

		var response = controller.register(new LoginController.RegisterRequest(
				" Martin ", " Alice ", " new-User ", "password"), request);

		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		verify(jdbcTemplate).update(
				"INSERT INTO employe (login, mdp, nom, prenom, statut) VALUES (?, ?, ?, ?, 0)",
				"new-User",
				"5f4dcc3b5aa765d61d8327deb882cf99",
				"Martin",
				"Alice");
	}

	@Test
	void rejectsAnAlreadyUsedLogin() {
		when(jdbcTemplate.queryForObject(
				eq("SELECT COUNT(*) FROM employe WHERE BINARY login = BINARY ?"),
				eq(Integer.class),
				eq("existing"))).thenReturn(1);

		var response = controller.register(new LoginController.RegisterRequest(
				"Martin", "Alice", "existing", "password"), request);

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		verify(jdbcTemplate, never()).update(anyString(), (Object[]) org.mockito.ArgumentMatchers.any());
	}

	@Test
	void rejectsIncompleteRegistrationWithoutAccessingDatabase() {
		var response = controller.register(new LoginController.RegisterRequest(
				"Martin", " ", "new-user", "password"), request);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class), anyString());
	}

	@Test
	void rejectsRegistrationWithoutAValidCsrfToken() {
		when(request.getHeader(SessionSecurity.CSRF_TOKEN_HEADER)).thenReturn("invalid-token");

		var response = controller.register(new LoginController.RegisterRequest(
				"Martin", "Alice", "new-user", "password"), request);

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class), anyString());
	}

	@Test
	void invalidatesTheAuthenticatedSessionOnLogout() {
		when(session.getAttribute(LoginController.AUTHENTICATED_USER_SESSION_KEY)).thenReturn(42L);

		var response = controller.logout(request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(session).invalidate();
	}

	@Test
	void rejectsLogoutWithoutAValidCsrfToken() {
		when(session.getAttribute(LoginController.AUTHENTICATED_USER_SESSION_KEY)).thenReturn(42L);
		when(request.getHeader(SessionSecurity.CSRF_TOKEN_HEADER)).thenReturn("invalid-token");

		var response = controller.logout(request);

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		verify(session, never()).invalidate();
	}
}

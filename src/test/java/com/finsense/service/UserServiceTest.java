package com.finsense.service;

import com.finsense.dao.UserDAO;
import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.exception.AuthenticationException;
import com.finsense.exception.ValidationException;
import com.finsense.model.User;
import com.finsense.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Business-rule tests for registration, login and logout. They run through the
 * full stack (service -> DAO -> JDBC -> MySQL "finsense_test").
 */
class UserServiceTest {

    private static final String INVALID = "Invalid username or password.";

    private DatabaseConnection db;
    private UserService service;
    private final SessionManager session = SessionManager.getInstance();

    @BeforeEach
    void setUp() {
        db = TestDatabase.freshEmptyDatabase();
        new DatabaseInitializer(db).initialize();
        service = new UserService(new UserDAO());
        session.logout();
    }

    @AfterEach
    void tearDown() {
        session.logout();
        db.close();
    }

    // ---------------------------------------------------------------- register

    @Test
    void registerCreatesUserAndLogsIn() {
        User user = service.register("Asha Rao", "asha_r", "Secret99", "Secret99");

        assertTrue(user.getId() > 0);
        assertEquals("asha_r", user.getUsername());
        assertEquals("Asha Rao", user.getFullName());
        assertTrue(session.isLoggedIn());
        assertEquals(user.getId(), session.requireUserId());
    }

    @Test
    void passwordIsStoredOnlyAsSaltedHash() throws SQLException {
        User user = service.register("Ravi Kumar", "ravi_k", "Password1", "Password1");

        try (PreparedStatement ps = db.getConnection()
                .prepareStatement("SELECT password_hash, salt FROM users WHERE id = ?")) {
            ps.setLong(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                String hash = rs.getString("password_hash");
                assertNotEquals("Password1", hash);
                assertFalse(hash.contains("Password1"));
                assertFalse(rs.getString("salt").isBlank());
            }
        }
    }

    @Test
    void inputIsTrimmed() {
        User user = service.register("  Priya Sharma  ", "  priya  ", "Password1", "Password1");

        assertEquals("priya", user.getUsername());
        assertEquals("Priya Sharma", user.getFullName());
    }

    @Test
    void duplicateUsernameIsRejectedIgnoringCase() {
        service.register("Asha One", "Asha", "Secure12", "Secure12");
        session.logout();

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.register("Asha Two", "asha", "Secure12", "Secure12"));
        assertEquals("Username", e.getField());
        assertEquals("This username is already taken.", e.getMessage());
        assertFalse(session.isLoggedIn(), "a failed registration must not log anyone in");
    }

    @Test
    void passwordsMustMatch() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.register("Test User", "testuser1", "Password1", "Password2"));
        assertEquals("Passwords do not match.", e.getMessage());
    }

    @Test
    void weakPasswordIsRejected() {
        assertThrows(ValidationException.class,
                () -> service.register("Test User", "testuser2", "short1", "short1"));
        assertThrows(ValidationException.class,
                () -> service.register("Test User", "testuser3", "onlyletters", "onlyletters"));
    }

    @Test
    void blankFullNameIsRejected() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.register("   ", "blankname", "Password1", "Password1"));
        assertEquals("Full name", e.getField());
    }

    @Test
    void invalidUsernameIsRejected() {
        assertThrows(ValidationException.class,
                () -> service.register("Test User", "has space", "Password1", "Password1"));
    }

    // ---------------------------------------------------------------- login

    @Test
    void loginWithCorrectPasswordStartsSession() {
        service.register("Login User", "loginuser", "Password1", "Password1");
        session.logout();

        User user = service.login("  LoginUser ", "Password1");   // trimmed, case-insensitive username

        assertTrue(session.isLoggedIn());
        assertEquals(user.getId(), session.requireUserId());
        assertEquals("Login User", session.requireUser().getFullName());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameMessage() {
        service.register("Same Msg", "samemsg", "Password1", "Password1");
        session.logout();

        AuthenticationException wrongPassword = assertThrows(AuthenticationException.class,
                () -> service.login("samemsg", "WrongPass1"));
        AuthenticationException unknownUser = assertThrows(AuthenticationException.class,
                () -> service.login("nobody_here", "Password1"));

        assertEquals(INVALID, wrongPassword.getMessage());
        assertEquals(INVALID, unknownUser.getMessage());
        assertFalse(session.isLoggedIn());
    }

    @Test
    void emptyLoginInputIsRejected() {
        assertThrows(AuthenticationException.class, () -> service.login(null, null));
        assertThrows(AuthenticationException.class, () -> service.login("", ""));
    }

    // ---------------------------------------------------------------- logout

    @Test
    void logoutClearsSession() {
        service.register("Logout User", "logoutuser", "Password1", "Password1");
        assertTrue(session.isLoggedIn());

        service.logout();

        assertFalse(session.isLoggedIn());
    }
}

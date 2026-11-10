package com.finsense.util;

import com.finsense.exception.AuthenticationException;
import com.finsense.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManagerTest {

    private final SessionManager session = SessionManager.getInstance();

    @AfterEach
    void cleanUp() {
        session.logout();
    }

    @Test
    void isSingleton() {
        assertSame(SessionManager.getInstance(), SessionManager.getInstance());
    }

    @Test
    void noUserBeforeLogin() {
        session.logout();
        assertFalse(session.isLoggedIn());
        assertTrue(session.getCurrentUser().isEmpty());
        assertThrows(AuthenticationException.class, session::requireUserId);
    }

    @Test
    void loginAndLogout() {
        session.login(new User(42, "asha", "Asha Rao", LocalDateTime.now()));

        assertTrue(session.isLoggedIn());
        assertEquals(42, session.requireUserId());
        assertEquals("Asha", session.requireUser().getFirstName());

        session.logout();
        assertFalse(session.isLoggedIn());
    }
}

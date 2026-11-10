package com.finsense;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests: prove the toolchain and dependencies are wired correctly.
 */
class SetupSmokeTest {

    @Test
    void runsOnJava21OrNewer() {
        assertTrue(Runtime.version().feature() >= 21,
                "FinSense needs Java 21+, found " + Runtime.version());
    }

    @Test
    void mysqlJdbcDriverIsOnClasspath() {
        assertDoesNotThrow(() -> Class.forName("com.mysql.cj.jdbc.Driver"));
    }

    @Test
    void resourcesArePackaged() {
        assertNotNull(getClass().getResource("/fxml/login.fxml"));
        assertNotNull(getClass().getResource("/fxml/register.fxml"));
        assertNotNull(getClass().getResource("/fxml/main_layout.fxml"));
        assertNotNull(getClass().getResource("/css/auth.css"));
        assertNotNull(getClass().getResource("/db/schema.sql"));
        assertNotNull(getClass().getResource("/db/seed_categories.sql"));
    }
}

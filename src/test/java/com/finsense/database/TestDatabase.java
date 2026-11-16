package com.finsense.database;

import org.junit.jupiter.api.Assumptions;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Test helper: points the app at a separate MySQL database "finsense_test"
 * (same server and login as config/db.properties) and empties it, so tests
 * never touch the real "finsense" data.
 *
 * If MySQL is not reachable, database tests are skipped (reported as
 * "skipped", not "passed") instead of failing the whole build.
 */
public final class TestDatabase {

    public static final String TEST_DB_NAME = "finsense_test";

    /** Child tables first, so dropping never violates a foreign key. */
    private static final String[] TABLES = {
            "purchase_feedback", "category_budgets", "goal_contributions", "savings_goals", "transactions", "purchase_history", "purchases",
            "behaviour_profiles", "questionnaires", "commitments", "financial_profiles", "categories", "users"
    };

    private TestDatabase() {
    }

    /** Connects to finsense_test and drops all FinSense tables, leaving an empty database. */
    public static DatabaseConnection freshEmptyDatabase() {
        DatabaseConfig config = DatabaseConfig.load(DatabaseConfig.DEFAULT_FILE).withDatabaseName(TEST_DB_NAME);
        DatabaseConnection.useConfig(config);
        DatabaseConnection db = DatabaseConnection.getInstance();

        Connection conn;
        try {
            conn = db.getConnection();
        } catch (RuntimeException e) {
            Assumptions.abort("MySQL not reachable - skipping database test: " + e.getMessage());
            return db;
        }
        try (Statement st = conn.createStatement()) {
            for (String table : TABLES) {
                st.execute("DROP TABLE IF EXISTS " + table);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not reset test database", e);
        }
        return db;
    }
}

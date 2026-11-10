package com.finsense.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 tests, run against the MySQL test database "finsense_test",
 * which is emptied before every test so tests never affect each other.
 */
class DatabaseInitializerTest {

    private DatabaseConnection db;

    @BeforeEach
    void setUp() {
        db = TestDatabase.freshEmptyDatabase();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void createsAllElevenTablesOnFirstRun() throws SQLException {
        DatabaseInitializer.InitResult result = new DatabaseInitializer(db).initialize();

        assertTrue(result.freshDatabase());
        assertEquals(11, result.tableCount());
        List<String> expected = List.of("users", "financial_profiles", "commitments", "categories",
                "transactions", "savings_goals", "goal_contributions", "questionnaires",
                "behaviour_profiles", "purchases", "purchase_history");
        List<String> actual = tableNames(db.getConnection());
        assertTrue(actual.containsAll(expected), "Missing tables. Found: " + actual);
    }

    @Test
    void seedsBuiltInCategoriesExactlyOnce() {
        int first = new DatabaseInitializer(db).initialize().categoryCount();
        int second = new DatabaseInitializer(db).initialize().categoryCount();

        assertEquals(23, first);
        assertEquals(first, second, "Re-running the initializer must not duplicate categories");
    }

    @Test
    void existingDataSurvivesRestart() throws SQLException {
        new DatabaseInitializer(db).initialize();
        insertUser(db.getConnection(), "asha");

        // Simulate closing and re-opening the application.
        db.close();
        DatabaseInitializer.InitResult second = new DatabaseInitializer(db).initialize();

        assertFalse(second.freshDatabase());
        assertEquals(1, count(db.getConnection(), "SELECT COUNT(*) FROM users WHERE username = 'asha'"));
    }

    @Test
    void foreignKeysAreEnforced() {
        new DatabaseInitializer(db).initialize();
        // user 999 does not exist, so this row must be rejected
        assertThrows(SQLException.class, () -> execute(db.getConnection(),
                "INSERT INTO transactions (user_id, category_id, type, amount, txn_date) "
                        + "VALUES (999, 1, 'EXPENSE', 5000, '2026-11-01')"));
    }

    @Test
    void checkConstraintRejectsNonPositiveAmounts() throws SQLException {
        new DatabaseInitializer(db).initialize();
        long userId = insertUser(db.getConnection(), "ravi");

        assertThrows(SQLException.class, () -> execute(db.getConnection(),
                "INSERT INTO transactions (user_id, category_id, type, amount, txn_date) "
                        + "VALUES (" + userId + ", 1, 'EXPENSE', -100, '2026-11-01')"));
    }

    @Test
    void enumColumnsRejectUnknownValues() throws SQLException {
        new DatabaseInitializer(db).initialize();
        long userId = insertUser(db.getConnection(), "meera");

        assertThrows(SQLException.class, () -> execute(db.getConnection(),
                "INSERT INTO transactions (user_id, category_id, type, amount, txn_date) "
                        + "VALUES (" + userId + ", 1, 'REFUND', 100, '2026-11-01')"));
    }

    @Test
    void usernamesAreUniqueIgnoringCase() throws SQLException {
        new DatabaseInitializer(db).initialize();
        insertUser(db.getConnection(), "Priya");

        assertThrows(SQLException.class, () -> insertUser(db.getConnection(), "priya"));
    }

    @Test
    void deletingUserCascadesToTheirData() throws SQLException {
        new DatabaseInitializer(db).initialize();
        Connection conn = db.getConnection();
        long userId = insertUser(conn, "kiran");
        execute(conn, "INSERT INTO financial_profiles (user_id, monthly_income) VALUES (" + userId + ", 5000000)");

        execute(conn, "DELETE FROM users WHERE id = " + userId);

        assertEquals(0, count(conn, "SELECT COUNT(*) FROM financial_profiles WHERE user_id = " + userId));
    }

    @Test
    void sqlSplitterIgnoresComments() {
        List<String> statements = DatabaseInitializer.readStatements("/db/seed_categories.sql");
        assertEquals(23, statements.size());
        assertTrue(statements.stream().allMatch(s -> s.startsWith("INSERT") && !s.endsWith(";")));
    }

    // ----- helpers ---------------------------------------------------------

    private static long insertUser(Connection conn, String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (username, password_hash, salt, full_name) VALUES (?, 'h', 's', 'Test')",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void execute(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private static int count(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static List<String> tableNames(Connection conn) throws SQLException {
        List<String> names = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                names.add(rs.getString("TABLE_NAME"));
            }
        }
        return names;
    }
}

package com.finsense.dao;

import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the shared JDBC helpers through a tiny DAO written only for this test.
 */
class BaseDAOTest {

    /** Minimal DAO: shows how real DAOs will use the inherited helpers with lambdas. */
    static class ProbeDAO extends BaseDAO {

        record CategoryRow(long id, String name, String type) { }

        List<CategoryRow> expenseCategories() {
            return queryList("SELECT id, name, type FROM categories WHERE type = ? ORDER BY name",
                    rs -> new CategoryRow(rs.getLong("id"), rs.getString("name"), rs.getString("type")),
                    "EXPENSE");
        }

        Optional<CategoryRow> byName(String name) {
            return queryOne("SELECT id, name, type FROM categories WHERE name = ?",
                    rs -> new CategoryRow(rs.getLong("id"), rs.getString("name"), rs.getString("type")),
                    name);
        }

        long insertUser(String username) {
            return insert("INSERT INTO users (username, password_hash, salt, full_name) VALUES (?, 'h', 's', ?)",
                    username, "Test " + username);
        }

        long countUsers() {
            return queryLong("SELECT COUNT(*) FROM users");
        }

        /** Inserts two users in one transaction; the second insert is a duplicate and fails. */
        void insertTwoUsersAtomically(String first, String second) {
            inTransaction(() -> {
                insertUser(first);
                insertUser(second);
                return null;
            });
        }

        boolean duplicate(RuntimeException e) {
            return isDuplicateKey(e);
        }
    }

    private DatabaseConnection db;
    private final ProbeDAO dao = new ProbeDAO();

    @BeforeEach
    void setUp() {
        db = TestDatabase.freshEmptyDatabase();
        new DatabaseInitializer(db).initialize();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void queryListMapsRowsWithLambda() {
        List<ProbeDAO.CategoryRow> rows = dao.expenseCategories();

        assertEquals(17, rows.size());
        assertTrue(rows.stream().allMatch(r -> r.type().equals("EXPENSE")));
    }

    @Test
    void queryOneReturnsOptional() {
        assertTrue(dao.byName("Groceries").isPresent());
        assertTrue(dao.byName("Does not exist").isEmpty());
    }

    @Test
    void parametersCannotInjectSql() {
        // With string concatenation this would return every row; with ? it is just a strange name.
        assertTrue(dao.byName("x' OR '1'='1").isEmpty());
    }

    @Test
    void insertReturnsGeneratedId() {
        long first = dao.insertUser("asha");
        long second = dao.insertUser("ravi");

        assertTrue(first > 0);
        assertEquals(first + 1, second);
    }

    @Test
    void failedTransactionRollsBackEverything() {
        DataAccessException e = assertThrows(DataAccessException.class,
                () -> dao.insertTwoUsersAtomically("kiran", "kiran"));

        assertTrue(dao.duplicate(e), "should be recognised as a duplicate-key error");
        assertEquals(0, dao.countUsers(), "the first insert must have been rolled back");
    }

    @Test
    void successfulTransactionCommits() {
        dao.insertTwoUsersAtomically("meera", "arjun");
        assertEquals(2, dao.countUsers());
    }
}

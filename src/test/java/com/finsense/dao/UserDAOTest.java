package com.finsense.dao;

import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.exception.DataAccessException;
import com.finsense.model.User;
import com.finsense.model.UserCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DAO-level tests: UserDAO's SQL against the MySQL test database "finsense_test".
 * Business rules (validation, hashing, sessions) are tested in UserServiceTest.
 */
class UserDAOTest {

    private DatabaseConnection db;
    private UserDAO dao;

    @BeforeEach
    void setUp() {
        db = TestDatabase.freshEmptyDatabase();
        new DatabaseInitializer(db).initialize();
        dao = new UserDAO();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void insertThenFindByIdReturnsSameUser() {
        long id = dao.insert("asha", "hash", "salt", "Asha Rao");

        Optional<User> found = dao.findById(id);

        assertTrue(found.isPresent());
        assertEquals("asha", found.get().getUsername());
        assertEquals("Asha Rao", found.get().getFullName());
        assertNotNull(found.get().getCreatedAt(), "created_at is filled by the DEFAULT CURRENT_TIMESTAMP");
    }

    @Test
    void findCredentialsReturnsHashAndSalt() {
        dao.insert("ravi", "the-hash", "the-salt", "Ravi Kumar");

        UserCredentials creds = dao.findCredentialsByUsername("ravi").orElseThrow();

        assertEquals("the-hash", creds.passwordHash());
        assertEquals("the-salt", creds.salt());
        assertEquals("ravi", creds.user().getUsername());
    }

    @Test
    void usernameLookupIgnoresCase() {
        dao.insert("Meera", "h", "s", "Meera Nair");

        assertTrue(dao.existsByUsername("meera"));
        assertTrue(dao.findCredentialsByUsername("MEERA").isPresent());
    }

    @Test
    void missingUserGivesEmptyResults() {
        assertTrue(dao.findById(99_999L).isEmpty());
        assertTrue(dao.findCredentialsByUsername("doesnotexist").isEmpty());
        assertFalse(dao.existsByUsername("ghostuser"));
    }

    @Test
    void duplicateInsertIsReportedAsDuplicateKey() {
        dao.insert("kiran", "h", "s", "Kiran");

        DataAccessException e = assertThrows(DataAccessException.class,
                () -> dao.insert("KIRAN", "h", "s", "Kiran Again"));
        assertTrue(BaseDAO.isDuplicateKey(e));
    }
}

package com.finsense.dao;

import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.model.FinancialProfile;
import com.finsense.model.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for FinancialProfileDAO against MySQL finsense_test database.
 */
class FinancialProfileDAOTest {

    private DatabaseConnection db;
    private UserDAO userDAO;
    private FinancialProfileDAO profileDAO;
    private long testUserId;

    @BeforeEach
    void setUp() {
        db = TestDatabase.freshEmptyDatabase();
        new DatabaseInitializer(db).initialize();
        userDAO = new UserDAO();
        profileDAO = new FinancialProfileDAO();
        testUserId = userDAO.insert("testuser", "hash", "salt", "Test User");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void insertAndFindByUserId() {
        FinancialProfile p = new FinancialProfile(
                testUserId,
                Money.ofRupees(60_000),
                Money.ofRupees(15_000),
                Money.ofRupees(10_000),
                Money.ofRupees(150_000),
                Money.ofRupees(12_000),
                null
        );

        profileDAO.upsert(p);

        Optional<FinancialProfile> found = profileDAO.findByUserId(testUserId);
        assertTrue(found.isPresent());
        FinancialProfile loaded = found.get();
        assertEquals(testUserId, loaded.getUserId());
        assertEquals(Money.ofRupees(60_000), loaded.getMonthlyIncome());
        assertEquals(Money.ofRupees(15_000), loaded.getFixedExpenses());
        assertEquals(Money.ofRupees(10_000), loaded.getVariableExpenses());
        assertEquals(Money.ofRupees(150_000), loaded.getCurrentSavings());
        assertEquals(Money.ofRupees(12_000), loaded.getMonthlySavingsTarget());
        assertEquals(Money.ofRupees(25_000), loaded.totalExpenses());
        assertNotNull(loaded.getUpdatedAt());
    }

    @Test
    void upsertTwiceUpdatesRowWithoutDuplicate() {
        FinancialProfile p1 = new FinancialProfile(
                testUserId,
                Money.ofRupees(50_000),
                Money.ofRupees(10_000),
                Money.ofRupees(5_000),
                Money.ofRupees(20_000),
                Money.ofRupees(5_000),
                null
        );
        profileDAO.upsert(p1);

        FinancialProfile p2 = new FinancialProfile(
                testUserId,
                Money.ofRupees(75_000),
                Money.ofRupees(20_000),
                Money.ofRupees(10_000),
                Money.ofRupees(50_000),
                Money.ofRupees(15_000),
                null
        );
        profileDAO.upsert(p2);

        Optional<FinancialProfile> found = profileDAO.findByUserId(testUserId);
        assertTrue(found.isPresent());
        assertEquals(Money.ofRupees(75_000), found.get().getMonthlyIncome());
        assertEquals(Money.ofRupees(20_000), found.get().getFixedExpenses());
    }

    @Test
    void unknownUserReturnsEmpty() {
        Optional<FinancialProfile> found = profileDAO.findByUserId(99999L);
        assertTrue(found.isEmpty());
    }
}


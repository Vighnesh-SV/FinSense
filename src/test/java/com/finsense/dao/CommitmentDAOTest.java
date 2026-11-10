package com.finsense.dao;

import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.model.Commitment;
import com.finsense.model.Money;
import com.finsense.model.enums.CommitmentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for CommitmentDAO against MySQL finsense_test database.
 * Verifies round trip, open-ended dates, and user isolation.
 */
class CommitmentDAOTest {

    private DatabaseConnection db;
    private UserDAO userDAO;
    private CommitmentDAO commitmentDAO;
    private long userA;
    private long userB;

    @BeforeEach
    void setUp() {
        db = TestDatabase.freshEmptyDatabase();
        new DatabaseInitializer(db).initialize();
        userDAO = new UserDAO();
        commitmentDAO = new CommitmentDAO();

        userA = userDAO.insert("user_a", "hashA", "saltA", "User A");
        userB = userDAO.insert("user_b", "hashB", "saltB", "User B");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void insertFindUpdateDeleteRoundTrip() {
        Commitment c = new Commitment(
                null,
                userA,
                "Car Loan",
                CommitmentType.LOAN,
                Money.ofRupees(15_000),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2028, 12, 31)
        );

        long id = commitmentDAO.insert(c);
        assertTrue(id > 0);

        Optional<Commitment> found = commitmentDAO.findById(id, userA);
        assertTrue(found.isPresent());
        assertEquals("Car Loan", found.get().getName());
        assertEquals(CommitmentType.LOAN, found.get().getType());
        assertEquals(Money.ofRupees(15_000), found.get().getMonthlyAmount());

        // Update
        Commitment updated = new Commitment(
                id,
                userA,
                "Car Loan Refinanced",
                CommitmentType.EMI,
                Money.ofRupees(12_000),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2029, 6, 30)
        );
        boolean updateSuccess = commitmentDAO.update(updated);
        assertTrue(updateSuccess);

        Commitment reloaded = commitmentDAO.findById(id, userA).orElseThrow();
        assertEquals("Car Loan Refinanced", reloaded.getName());
        assertEquals(CommitmentType.EMI, reloaded.getType());
        assertEquals(Money.ofRupees(12_000), reloaded.getMonthlyAmount());

        // Delete
        boolean deleteSuccess = commitmentDAO.delete(id, userA);
        assertTrue(deleteSuccess);
        assertTrue(commitmentDAO.findById(id, userA).isEmpty());
    }

    @Test
    void userIsolationPreventsCrossUserAccess() {
        Commitment cA = new Commitment(
                null,
                userA,
                "User A Rent",
                CommitmentType.RENT,
                Money.ofRupees(20_000),
                LocalDate.of(2026, 1, 1),
                null
        );
        long idA = commitmentDAO.insert(cA);

        // User B cannot find User A's commitment
        assertTrue(commitmentDAO.findById(idA, userB).isEmpty());

        // User B cannot update User A's commitment
        Commitment hackAttempt = new Commitment(
                idA,
                userB,
                "Hacked",
                CommitmentType.OTHER,
                Money.ofRupees(100),
                LocalDate.of(2026, 1, 1),
                null
        );
        assertFalse(commitmentDAO.update(hackAttempt));

        // User B cannot delete User A's commitment
        assertFalse(commitmentDAO.delete(idA, userB));

        // User A's commitment is still safe
        Optional<Commitment> safe = commitmentDAO.findById(idA, userA);
        assertTrue(safe.isPresent());
        assertEquals("User A Rent", safe.get().getName());

        // findByUserId only returns user's own commitments
        List<Commitment> listB = commitmentDAO.findByUserId(userB);
        assertTrue(listB.isEmpty());
        List<Commitment> listA = commitmentDAO.findByUserId(userA);
        assertEquals(1, listA.size());
    }

    @Test
    void nullEndDateRoundTripsAsNull() {
        Commitment openEnded = new Commitment(
                null,
                userA,
                "Health Insurance",
                CommitmentType.INSURANCE,
                Money.ofRupees(2_000),
                LocalDate.of(2025, 5, 10),
                null
        );

        long id = commitmentDAO.insert(openEnded);
        Commitment found = commitmentDAO.findById(id, userA).orElseThrow();
        assertNull(found.getEndDate());
        assertTrue(found.isActiveOn(LocalDate.now()));
    }
}


package com.finsense.service;

import com.finsense.dao.CommitmentDAO;
import com.finsense.dao.FinancialProfileDAO;
import com.finsense.dao.UserDAO;
import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.exception.AuthenticationException;
import com.finsense.exception.IncompleteProfileException;
import com.finsense.exception.ValidationException;
import com.finsense.model.Commitment;
import com.finsense.model.FinancialSummary;
import com.finsense.model.Money;
import com.finsense.model.User;
import com.finsense.model.enums.CommitmentType;
import com.finsense.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for FinancialService against MySQL test DB with SessionManager active.
 */
class FinancialServiceTest {

    private DatabaseConnection db;
    private FinancialService service;
    private User testUser;

    @BeforeEach
    void setUp() {
        db = TestDatabase.freshEmptyDatabase();
        new DatabaseInitializer(db).initialize();

        UserDAO userDAO = new UserDAO();
        long uid = userDAO.insert("finservice_user", "hash", "salt", "Fin User");
        testUser = new User(uid, "finservice_user", "Fin User", LocalDateTime.now());

        SessionManager.getInstance().login(testUser);
        service = new FinancialService(new FinancialProfileDAO(), new CommitmentDAO());
    }

    @AfterEach
    void tearDown() {
        SessionManager.getInstance().logout();
        db.close();
    }

    @Test
    void savingProfileWithTargetGreaterThanIncomeThrowsValidationException() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.saveProfile("50000", "15000", "10000", "50000", "55000")
        );
        assertTrue(ex.getMessage().contains("Savings target cannot exceed monthly income"));
    }

    @Test
    void savingProfileWithNegativeAmountThrowsValidationException() {
        assertThrows(ValidationException.class, () ->
                service.saveProfile("50000", "-15000", "10000", "50000", "10000")
        );
    }

    @Test
    void savingProfileWithNonNumericThrowsValidationException() {
        assertThrows(ValidationException.class, () ->
                service.saveProfile("abc", "15000", "10000", "50000", "10000")
        );
    }

    @Test
    void savingProfileWithZeroIncomeThrowsValidationException() {
        assertThrows(ValidationException.class, () ->
                service.saveProfile("0", "15000", "10000", "50000", "10000")
        );
    }

    @Test
    void calculateSummaryWithNoProfileThrowsIncompleteProfileException() {
        IncompleteProfileException ex = assertThrows(IncompleteProfileException.class, () ->
                service.calculateSummary()
        );
        assertTrue(ex.getMessage().contains("Please complete your financial profile first"));
    }

    @Test
    void calculateSummaryAfterSavingProfileSucceeds() {
        service.saveProfile("50000", "15000", "10000", "50000", "5000");
        service.addCommitment("Gym", CommitmentType.OTHER, "2000", LocalDate.now(), null);

        FinancialSummary summary = service.calculateSummary();
        assertEquals(Money.ofRupees(50_000), summary.income());
        assertEquals(Money.ofRupees(25_000), summary.totalExpenses());
        assertEquals(Money.ofRupees(2_000), summary.activeCommitments());
        assertEquals(Money.ofRupees(18_000), summary.disposableIncome());
    }

    @Test
    void commitmentEndDateBeforeStartDateThrowsValidationException() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.addCommitment(
                        "Test EMI",
                        CommitmentType.EMI,
                        "5000",
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 4, 30) // before start
                )
        );
        assertTrue(ex.getMessage().contains("End date cannot be before start date"));
    }

    @Test
    void deletingMissingCommitmentIdThrowsValidationException() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.deleteCommitment(99999L)
        );
        assertEquals("Commitment not found.", ex.getMessage());
    }

    @Test
    void callingWithNobodyLoggedInThrowsAuthenticationException() {
        SessionManager.getInstance().logout();

        assertThrows(AuthenticationException.class, () -> service.getProfile());
        assertThrows(AuthenticationException.class, () -> service.getCommitments());
        assertThrows(AuthenticationException.class, () ->
                service.saveProfile("50000", "10000", "10000", "0", "0")
        );
    }
}


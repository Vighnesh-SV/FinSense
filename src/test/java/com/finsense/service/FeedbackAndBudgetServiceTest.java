package com.finsense.service;

import com.finsense.dao.FeedbackDAO;
import com.finsense.dao.FinancialProfileDAO;
import com.finsense.dao.UserDAO;
import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.exception.ValidationException;
import com.finsense.model.Category;
import com.finsense.model.FinancialProfile;
import com.finsense.model.Money;
import com.finsense.model.User;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.PurchaseFeedback;
import com.finsense.model.enums.TransactionType;
import com.finsense.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Database tests for purchase feedback (the learning signal) and category budgets. */
class FeedbackAndBudgetServiceTest {

    private DatabaseConnection db;
    private PurchaseService purchases;
    private BudgetService budgets;
    private long userId;
    private Category electronics;

    @BeforeEach
    void setUp() {
        db = TestDatabase.freshEmptyDatabase();
        new DatabaseInitializer(db).initialize();
        userId = new UserDAO().insert("feedback_user", "hash", "salt", "Feedback User");
        SessionManager.getInstance().login(new User(userId, "feedback_user", "Feedback User", LocalDateTime.now()));
        new FinancialProfileDAO().upsert(new FinancialProfile(userId, Money.ofRupees(100000), Money.ofRupees(30000),
                Money.ZERO, Money.ofRupees(300000), Money.ofRupees(10000), null));
        purchases = new PurchaseService();
        budgets = new BudgetService();
        electronics = purchases.categories().stream()
                .filter(c -> c.name().equals("Electronics")).findFirst().orElseThrow();
    }

    @AfterEach
    void tearDown() {
        SessionManager.getInstance().logout();
        db.close();
    }

    @Test
    void onlyPurchasedItemsCanBeRatedAndRatingsChangeLaterAdvice() {
        long first = purchases.evaluate("Headphones", electronics.id(), "5000", Necessity.WANT, null).purchaseId();
        assertThrows(ValidationException.class, () -> purchases.rate(first, PurchaseFeedback.REGRET));

        int scoreBefore = purchases.evaluate("Speaker", electronics.id(), "5000", Necessity.WANT, null)
                .decision().score();
        purchases.markAsPurchased(first, false);
        purchases.rate(first, PurchaseFeedback.REGRET);
        long second = purchases.evaluate("Smartwatch", electronics.id(), "5000", Necessity.WANT, null).purchaseId();
        purchases.markAsPurchased(second, false);
        purchases.rate(second, PurchaseFeedback.REGRET);

        var stats = new FeedbackDAO().statsByCategory(userId).get(electronics.id());
        assertEquals(2, stats.regret());
        int scoreAfter = purchases.evaluate("Tablet", electronics.id(), "5000", Necessity.WANT, null)
                .decision().score();
        assertTrue(scoreAfter < scoreBefore, "Regretted category should lower the score");
        assertEquals(PurchaseFeedback.REGRET, purchases.summaries().stream()
                .filter(s -> s.purchase().id() == first).findFirst().orElseThrow().feedback());
    }

    @Test
    void budgetTracksThisMonthsSpendingAndCanBeReplaced() {
        new TransactionService().add(electronics.id(), TransactionType.EXPENSE, "3000", LocalDate.now(), "cable");
        budgets.save(electronics, "10000");
        budgets.save(electronics, "8000");                                       // upsert, not a duplicate
        var statuses = budgets.statuses(YearMonth.now());
        assertEquals(1, statuses.size());
        assertEquals(Money.ofRupees(8000), statuses.get(0).monthlyLimit());
        assertEquals(Money.ofRupees(3000), statuses.get(0).spentThisMonth());
        assertThrows(ValidationException.class, () -> budgets.save(electronics, "-5"));
    }
}

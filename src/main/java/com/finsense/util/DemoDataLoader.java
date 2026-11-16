package com.finsense.util;

import com.finsense.dao.CategoryDAO;
import com.finsense.dao.CommitmentDAO;
import com.finsense.dao.UserDAO;
import com.finsense.model.Category;
import com.finsense.model.Commitment;
import com.finsense.model.Money;
import com.finsense.model.SavingsGoal;
import com.finsense.model.enums.CommitmentType;
import com.finsense.model.enums.GoalPriority;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.PurchaseFeedback;
import com.finsense.model.enums.TransactionType;
import com.finsense.service.BehaviourService;
import com.finsense.service.BudgetService;
import com.finsense.service.FinancialService;
import com.finsense.service.PurchaseService;
import com.finsense.service.SavingsGoalService;
import com.finsense.service.TransactionService;
import com.finsense.service.UserService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the viva demo account from scratch every time it is opened, so every
 * demonstration starts from the same data: six months of history, an unusual
 * expense, budgets, goals, and purchases that have already been rated.
 *
 * All data goes through the normal services, so it is validated exactly like
 * data typed in by a user.
 */
public final class DemoDataLoader {

    public static final String USERNAME = "demo_viva";
    public static final String PASSWORD = "DemoPass2026";
    private static final int HISTORY_MONTHS = 6;

    private final UserService users = new UserService();
    private final UserDAO userDAO = new UserDAO();
    private final TransactionService transactions = new TransactionService();

    /** Category name -> category, filled after login. */
    private final Map<String, Category> categories = new HashMap<>();

    public void load() {
        userDAO.deleteByUsername(USERNAME);                 // cascades to all of the demo user's data
        users.register("Aarav Mehta", USERNAME, PASSWORD, PASSWORD);   // also logs in
        long uid = SessionManager.getInstance().requireUserId();

        CategoryDAO categoryDAO = new CategoryDAO();
        for (TransactionType type : TransactionType.values()) {
            categoryDAO.findByType(type, uid).forEach(c -> categories.put(c.name(), c));
        }

        LocalDate today = LocalDate.now();
        new FinancialService().saveProfile("95000", "6000", "22000", "480000", "15000");

        CommitmentDAO commitments = new CommitmentDAO();
        commitments.insert(new Commitment(null, uid, "Home rent", CommitmentType.RENT,
                Money.ofRupees(18000), today.minusMonths(14), null));
        commitments.insert(new Commitment(null, uid, "Car EMI", CommitmentType.EMI,
                Money.ofRupees(12500), today.minusMonths(20), today.plusMonths(3)));

        seedHistory(today);
        seedGoals(today);

        new BehaviourService().submitQuestionnaire(List.of(3, 3, 2, 2, 2, 2, 4, 3, 4, 4, 4, 4));

        BudgetService budgets = new BudgetService();
        budgets.save(categories.get("Dining Out"), "6000");
        budgets.save(categories.get("Shopping"), "8000");
        budgets.save(categories.get("Entertainment"), "2500");
        budgets.save(categories.get("Groceries"), "11000");

        seedPurchases();
        new BehaviourService().refreshSpendingClassification();
    }

    /** Six complete months plus the current month so far, with a gentle upward trend in dining. */
    private void seedHistory(LocalDate today) {
        YearMonth current = YearMonth.from(today);
        for (int back = HISTORY_MONTHS; back >= 0; back--) {
            YearMonth m = current.minusMonths(back);
            int step = HISTORY_MONTHS - back;                      // 0 .. 6, grows towards today
            add(m, today, 1, "Salary", 95000, "Monthly salary");
            if (back % 3 == 1) {
                add(m, today, 18, "Freelance", 12000, "Design project");
            }
            add(m, today, 3, "Groceries", 4300 + 90 * step, "Big weekly shop");
            add(m, today, 17, "Groceries", 4800 + 60 * step, "Weekly shop");
            add(m, today, 6, "Utilities", 2600 + (back % 2) * 350, "Electricity and internet");
            add(m, today, 9, "Transport", 3000 + (back % 3) * 200, "Fuel and metro card");
            add(m, today, 8, "Dining Out", 1500 + 120 * step, "Lunch with friends");
            add(m, today, 15, "Dining Out", 1700 + 140 * step, "Weekend dinner");
            add(m, today, 24, "Dining Out", 1300 + 100 * step, "Coffee and snacks");
            add(m, today, 12, "Subscriptions", 649, "Streaming bundle");
            add(m, today, 21, "Entertainment", 1200 + (back % 2) * 600, "Movies");
            add(m, today, 26, "Shopping", 3200 + (back % 3) * 900, "Clothes");
        }
        // One clearly unusual expense a few days ago, for the anomaly detector.
        LocalDate unusual = today.minusDays(Math.min(4, today.getDayOfMonth() - 1));
        addOn(unusual, "Dining Out", 11800, "Birthday party for 12 people");
    }

    private void seedGoals(LocalDate today) {
        SavingsGoalService goals = new SavingsGoalService();
        SavingsGoal emergency = goals.create("Emergency fund", "300000", today.plusMonths(12), GoalPriority.HIGH);
        SavingsGoal trip = goals.create("Family trip to Kerala", "90000", today.plusMonths(7), GoalPriority.MEDIUM);
        goals.create("New laptop", "85000", today.plusMonths(10), GoalPriority.LOW);
        goals.contribute(emergency.id(), "140000", today, "Opening balance");
        goals.contribute(trip.id(), "25000", today, "Bonus");
    }

    /** Earlier decisions, three of them bought and rated, so the learning rule has something to use. */
    private void seedPurchases() {
        PurchaseService purchases = new PurchaseService();
        long electronics = categories.get("Electronics").id();
        long shopping = categories.get("Shopping").id();

        long headphones = purchases.evaluate("Noise-cancelling headphones", electronics, "6000",
                Necessity.WANT, "For the commute").purchaseId();
        long watch = purchases.evaluate("Smartwatch", electronics, "9000",
                Necessity.WANT, "Fitness tracking").purchaseId();
        long jacket = purchases.evaluate("Leather jacket", shopping, "4500",
                Necessity.WANT, "Winter").purchaseId();
        purchases.evaluate("Running shoes", shopping, "6500", Necessity.NEED, "Old pair worn out");
        purchases.evaluate("Gaming console", electronics, "48000", Necessity.WANT, "");

        purchases.markAsPurchased(headphones, true);
        purchases.rate(headphones, PurchaseFeedback.WORTH_IT);
        purchases.markAsPurchased(watch, true);
        purchases.rate(watch, PurchaseFeedback.REGRET);
        purchases.markAsPurchased(jacket, false);
        purchases.rate(jacket, PurchaseFeedback.REGRET);
    }

    /** Adds a transaction on a day of month m, skipping days that are still in the future. */
    private void add(YearMonth m, LocalDate today, int day, String category, long rupees, String note) {
        LocalDate date = m.atDay(Math.min(day, m.lengthOfMonth()));
        if (!date.isAfter(today)) {
            addOn(date, category, rupees, note);
        }
    }

    private void addOn(LocalDate date, String category, long rupees, String note) {
        Category c = categories.get(category);
        if (c != null) {
            transactions.add(c.id(), c.type(), String.valueOf(rupees), date, note);
        }
    }
}

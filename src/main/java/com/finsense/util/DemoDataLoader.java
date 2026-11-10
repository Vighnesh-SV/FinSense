package com.finsense.util;

import com.finsense.dao.CategoryDAO;
import com.finsense.dao.CommitmentDAO;
import com.finsense.dao.UserDAO;
import com.finsense.model.Commitment;
import com.finsense.model.Money;
import com.finsense.model.enums.CommitmentType;
import com.finsense.model.enums.GoalPriority;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.TransactionType;
import com.finsense.service.BehaviourService;
import com.finsense.service.FinancialService;
import com.finsense.service.PurchaseService;
import com.finsense.service.SavingsGoalService;
import com.finsense.service.TransactionService;
import com.finsense.service.UserService;

import java.time.LocalDate;
import java.util.List;

/** Seeds one reusable viva account. The profile row acts as the completion marker for idempotence. */
public final class DemoDataLoader {
    public static final String USERNAME = "demo_viva";
    public static final String PASSWORD = "DemoPass2026";

    private final UserService users = new UserService();
    private final UserDAO userDAO = new UserDAO();

    /** Logs into the demo account and creates its sample data once, using existing service validation. */
    public void load() {
        if (userDAO.findCredentialsByUsername(USERNAME).isPresent()) {
            users.login(USERNAME, PASSWORD);
        } else {
            users.register("FinSense Demo", USERNAME, PASSWORD, PASSWORD);
        }
        long uid = SessionManager.getInstance().requireUserId();
        if (new FinancialService().hasProfile()) return;

        LocalDate today = LocalDate.now();
        new FinancialService().saveProfile("95000", "18000", "22000", "480000", "15000");
        CommitmentDAO commitments = new CommitmentDAO();
        commitments.insert(new Commitment(null, uid, "Home rent", CommitmentType.RENT, Money.ofRupees(18000), today.minusMonths(8), null));
        commitments.insert(new Commitment(null, uid, "Car EMI", CommitmentType.EMI, Money.ofRupees(12500), today.minusMonths(20), today.plusMonths(2)));

        SavingsGoalService goals = new SavingsGoalService();
        goals.create("Emergency fund", "600000", today.plusMonths(12), GoalPriority.HIGH);
        goals.create("Family trip", "120000", today.plusMonths(8), GoalPriority.MEDIUM);
        goals.create("New laptop", "90000", today.plusMonths(10), GoalPriority.LOW);

        CategoryDAO categories = new CategoryDAO();
        var income = categories.findByType(TransactionType.INCOME, uid);
        var expense = categories.findByType(TransactionType.EXPENSE, uid);
        var transactions = new TransactionService();
        addIfPresent(transactions, income, "Salary", "95000", today.minusMonths(3).withDayOfMonth(1), "Monthly salary");
        addIfPresent(transactions, income, "Salary", "95000", today.minusMonths(2).withDayOfMonth(1), "Monthly salary");
        addIfPresent(transactions, income, "Salary", "95000", today.minusMonths(1).withDayOfMonth(1), "Monthly salary");
        addIfPresent(transactions, expense, "Groceries", "9200", today.minusMonths(3).withDayOfMonth(5), "Weekly groceries");
        addIfPresent(transactions, expense, "Dining Out", "4200", today.minusMonths(3).withDayOfMonth(14), "Dinner and coffee");
        addIfPresent(transactions, expense, "Transport", "3100", today.minusMonths(3).withDayOfMonth(20), "Fuel and transit");
        addIfPresent(transactions, expense, "Groceries", "10100", today.minusMonths(2).withDayOfMonth(5), "Weekly groceries");
        addIfPresent(transactions, expense, "Dining Out", "5200", today.minusMonths(2).withDayOfMonth(14), "Meals out");
        addIfPresent(transactions, expense, "Transport", "3300", today.minusMonths(2).withDayOfMonth(20), "Fuel and transit");
        addIfPresent(transactions, expense, "Groceries", "9800", today.minusMonths(1).withDayOfMonth(5), "Weekly groceries");
        addIfPresent(transactions, expense, "Dining Out", "3600", today.minusMonths(1).withDayOfMonth(14), "Meals out");
        addIfPresent(transactions, expense, "Transport", "2900", today.minusMonths(1).withDayOfMonth(20), "Fuel and transit");

        List<Integer> answers = List.of(2, 2, 2, 2, 2, 2, 4, 4, 4, 4, 4, 4);
        new BehaviourService().submitQuestionnaire(answers);
        var purchaseService = new PurchaseService();
        var shopping = expense.stream().filter(c -> c.name().equalsIgnoreCase("Shopping")).findFirst()
                .orElse(expense.isEmpty() ? null : expense.get(0));
        if (shopping != null) {
            purchaseService.evaluate("Wireless headphones", shopping.id(), "12000", Necessity.WANT, "Demo evaluation");
            purchaseService.evaluate("Phone upgrade", shopping.id(), "25000", Necessity.NEED, "Demo evaluation");
            purchaseService.evaluate("Laptop", shopping.id(), "60000", Necessity.WANT, "Demo evaluation");
        }
        new BehaviourService().refreshSpendingClassification();
    }

    private static void addIfPresent(TransactionService service, List<com.finsense.model.Category> categories,
                                     String name, String amount, LocalDate date, String note) {
        categories.stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst()
                .ifPresent(c -> service.add(c.id(), c.type(), amount, date, note));
    }
}

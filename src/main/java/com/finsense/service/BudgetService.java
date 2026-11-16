package com.finsense.service;

import com.finsense.dao.BudgetDAO;
import com.finsense.dao.CategoryDAO;
import com.finsense.dao.TransactionDAO;
import com.finsense.exception.ValidationException;
import com.finsense.model.BudgetStatus;
import com.finsense.model.Category;
import com.finsense.model.Money;
import com.finsense.model.enums.TransactionType;
import com.finsense.util.SessionManager;
import com.finsense.util.ValidationUtil;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Monthly category budgets: set a limit, see how much of it is used this month. */
public class BudgetService {

    private final BudgetDAO budgetDAO;
    private final CategoryDAO categoryDAO;
    private final TransactionDAO transactionDAO;

    public BudgetService() {
        this(new BudgetDAO(), new CategoryDAO(), new TransactionDAO());
    }

    public BudgetService(BudgetDAO budgetDAO, CategoryDAO categoryDAO, TransactionDAO transactionDAO) {
        this.budgetDAO = budgetDAO;
        this.categoryDAO = categoryDAO;
        this.transactionDAO = transactionDAO;
    }

    private long userId() {
        return SessionManager.getInstance().requireUserId();
    }

    /** Expense categories a budget can be set for (fixed commitments like EMI are excluded). */
    public List<Category> budgetableCategories() {
        return categoryDAO.findByType(TransactionType.EXPENSE, userId()).stream()
                .filter(c -> !c.commitment())
                .toList();
    }

    /** Every budget with this month's spending against it. */
    public List<BudgetStatus> statuses(YearMonth month) {
        long uid = userId();
        return budgetDAO.findByUserId(uid).stream()
                .map(b -> new BudgetStatus(b.id(), b.category(), b.monthlyLimit(),
                        Money.ofPaise(transactionDAO.categorySpend(uid, b.category().id(),
                                month.atDay(1), month.atEndOfMonth()))))
                .toList();
    }

    /** Category id -> status, for the decision snapshot. */
    public Map<Long, BudgetStatus> statusesByCategory(YearMonth month) {
        return statuses(month).stream()
                .collect(Collectors.toMap(s -> s.category().id(), Function.identity()));
    }

    public void save(Category category, String limit) {
        if (category == null) {
            throw new ValidationException("Choose a category for the budget.");
        }
        Category visible = categoryDAO.findVisibleById(category.id(), userId())
                .orElseThrow(() -> new ValidationException("Category not found."));
        if (visible.type() != TransactionType.EXPENSE || visible.commitment()) {
            throw new ValidationException("Budgets can only be set for spending categories.");
        }
        Money amount = ValidationUtil.parsePositiveAmount(limit, "Monthly limit");
        budgetDAO.save(userId(), visible.id(), amount);
    }

    public void delete(long budgetId) {
        if (!budgetDAO.delete(budgetId, userId())) {
            throw new ValidationException("Budget not found.");
        }
    }
}

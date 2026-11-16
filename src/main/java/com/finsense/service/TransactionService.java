package com.finsense.service;

import com.finsense.dao.CategoryDAO;
import com.finsense.dao.TransactionDAO;
import com.finsense.exception.ValidationException;
import com.finsense.model.Category;
import com.finsense.model.Money;
import com.finsense.model.MonthlySummary;
import com.finsense.model.Transaction;
import com.finsense.model.enums.TransactionType;
import com.finsense.util.SessionManager;
import com.finsense.util.ValidationUtil;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Validation and monthly summaries for income and expense transactions (constructor-injected DAOs). */
public class TransactionService {

    private final TransactionDAO transactionDAO;
    private final CategoryDAO categoryDAO;

    public TransactionService() {
        this(new TransactionDAO(), new CategoryDAO());
    }

    public TransactionService(TransactionDAO transactionDAO, CategoryDAO categoryDAO) {
        this.transactionDAO = transactionDAO;
        this.categoryDAO = categoryDAO;
    }

    private long userId() {
        return SessionManager.getInstance().requireUserId();
    }

    public List<Category> categories(TransactionType type) {
        return categoryDAO.findByType(ValidationUtil.requireSelection(type, "Type"), userId());
    }

    public List<Transaction> find(LocalDate from, LocalDate through, TransactionType type, Long categoryId,
                                  String text) {
        if (from != null && through != null && through.isBefore(from)) {
            throw new ValidationException("End date cannot be before start date.");
        }
        return transactionDAO.find(userId(), from, through, type, categoryId, text);
    }

    public Transaction add(Long categoryId, TransactionType type, String amount, LocalDate date, String description) {
        long uid = userId();
        Category category = visibleCategory(categoryId, uid, type);
        Money value = ValidationUtil.parsePositiveAmount(amount, "Amount");
        LocalDate on = ValidationUtil.requireDate(date, "Date");
        String note = ValidationUtil.optionalText(description, "Description", 255);
        long id = transactionDAO.insert(uid, category.id(), type, value, on, note);
        return new Transaction(id, uid, category, type, value, on, note, null);
    }

    public Transaction update(long id, Long categoryId, TransactionType type, String amount, LocalDate date,
                              String description) {
        long uid = userId();
        if (transactionDAO.findById(id, uid).isEmpty()) {
            throw new ValidationException("Transaction not found.");
        }
        Category category = visibleCategory(categoryId, uid, type);
        Money value = ValidationUtil.parsePositiveAmount(amount, "Amount");
        LocalDate on = ValidationUtil.requireDate(date, "Date");
        String note = ValidationUtil.optionalText(description, "Description", 255);
        transactionDAO.update(id, uid, category.id(), type, value, on, note);
        return new Transaction(id, uid, category, type, value, on, note, null);
    }

    public void delete(long id) {
        if (!transactionDAO.delete(id, userId())) {
            throw new ValidationException("Transaction not found.");
        }
    }

    private Category visibleCategory(Long categoryId, long uid, TransactionType type) {
        if (categoryId == null) {
            throw new ValidationException("Please select a category.");
        }
        Category category = categoryDAO.findVisibleById(categoryId, uid)
                .orElseThrow(() -> new ValidationException("Category not found."));
        if (category.type() != type) {
            throw new ValidationException("Choose a category matching the transaction type.");
        }
        return category;
    }

    public MonthlySummary monthlySummary(YearMonth month) {
        long uid = userId();
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        Money income = Money.ofPaise(transactionDAO.sum(uid, TransactionType.INCOME, start, end, false));
        Money expense = Money.ofPaise(transactionDAO.sum(uid, TransactionType.EXPENSE, start, end, false));
        return new MonthlySummary(income, expense, income.minus(expense));
    }

    /** Total per category for one month (Streams: groupingBy + summingLong over exact paise). */
    public Map<Category, Money> categoryBreakdown(YearMonth month, TransactionType type) {
        List<Transaction> rows = find(month.atDay(1), month.atEndOfMonth(), type, null, null);
        return rows.stream()
                .collect(Collectors.groupingBy(Transaction::category,
                        Collectors.summingLong(t -> t.amount().getPaise())))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Money.ofPaise(e.getValue())));
    }

    /**
     * Average monthly expense over the last {@code months} COMPLETE calendar months, excluding
     * commitment categories. The current (partial) month is never included, and months before
     * the user's first transaction are not counted - otherwise a new user's spending would be
     * divided by months in which nothing was recorded, understating expenses.
     */
    public Money averageMonthlyExpenses(int months) {
        if (months < 1 || months > 36) {
            throw new ValidationException("Months must be between 1 and 36.");
        }
        long uid = userId();
        Optional<LocalDate> first = transactionDAO.firstTransactionDate(uid);
        if (first.isEmpty()) {
            return Money.ZERO;
        }
        YearMonth lastComplete = YearMonth.now().minusMonths(1);
        YearMonth start = YearMonth.from(first.get());
        YearMonth earliestAllowed = lastComplete.minusMonths(months - 1L);
        if (start.isBefore(earliestAllowed)) {
            start = earliestAllowed;
        }
        if (start.isAfter(lastComplete)) {
            return Money.ZERO;                              // no complete month recorded yet
        }
        long counted = ChronoUnit.MONTHS.between(start, lastComplete) + 1;
        long total = transactionDAO.sum(uid, TransactionType.EXPENSE, start.atDay(1),
                lastComplete.atEndOfMonth(), true);
        return Money.ofPaise(total).divide(counted);
    }

    /** Non-commitment spending from the 1st of this month up to today. */
    public Money thisMonthExpenses() {
        YearMonth month = YearMonth.now();
        return Money.ofPaise(transactionDAO.sum(userId(), TransactionType.EXPENSE, month.atDay(1),
                LocalDate.now(), true));
    }

    /** True when at least one complete calendar month (before this one) has recorded transactions. */
    public boolean hasAtLeastOneFullMonthOfData() {
        return transactionDAO.firstTransactionDate(userId())
                .map(first -> YearMonth.from(first).isBefore(YearMonth.now()))
                .orElse(false);
    }
}

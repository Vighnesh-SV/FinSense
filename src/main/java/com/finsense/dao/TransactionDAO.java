package com.finsense.dao;

import com.finsense.model.Category;
import com.finsense.model.Money;
import com.finsense.model.Transaction;
import com.finsense.model.enums.TransactionType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC persistence for income and expense transactions.
 * Every query filters on user_id, so one user can never read another user's rows.
 */
public class TransactionDAO extends BaseDAO {

    /** One category's total for one calendar month - the raw input for spending forecasts. */
    public record CategoryMonthTotal(Category category, YearMonth month, Money total) { }

    private static final RowMapper<Category> CATEGORY_MAPPER = rs -> new Category(
            rs.getLong("category_id"),
            getNullableLong(rs, "category_user_id"),
            rs.getString("category_name"),
            getEnum(rs, "category_type", TransactionType.class),
            rs.getString("nature"),
            rs.getBoolean("is_commitment"));

    private static final RowMapper<Transaction> MAPPER = rs -> new Transaction(
            rs.getLong("id"),
            rs.getLong("user_id"),
            CATEGORY_MAPPER.map(rs),
            getEnum(rs, "type", TransactionType.class),
            getMoney(rs, "amount"),
            getDate(rs, "txn_date"),
            rs.getString("description"),
            getNullableLong(rs, "purchase_id"));

    private static final String SELECT =
            "SELECT t.id, t.user_id, t.category_id, c.user_id category_user_id, c.name category_name, "
            + "c.type category_type, c.nature, c.is_commitment, t.type, t.amount, t.txn_date, "
            + "t.description, t.purchase_id "
            + "FROM transactions t JOIN categories c ON c.id = t.category_id ";

    /** Flexible search: every filter is optional (null = not filtered). */
    public List<Transaction> find(long userId, LocalDate from, LocalDate through, TransactionType type,
                                  Long categoryId, String text) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE t.user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (from != null) {
            sql.append(" AND t.txn_date >= ?");
            args.add(from);
        }
        if (through != null) {
            sql.append(" AND t.txn_date <= ?");
            args.add(through);
        }
        if (type != null) {
            sql.append(" AND t.type = ?");
            args.add(type);
        }
        if (categoryId != null) {
            sql.append(" AND t.category_id = ?");
            args.add(categoryId);
        }
        if (text != null && !text.isBlank()) {
            String pattern = "%" + text.trim() + "%";
            sql.append(" AND (t.description LIKE ? OR c.name LIKE ?)");
            args.add(pattern);
            args.add(pattern);
        }
        sql.append(" ORDER BY t.txn_date DESC, t.id DESC");
        return queryList(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<Transaction> findById(long id, long userId) {
        return queryOne(SELECT + "WHERE t.id = ? AND t.user_id = ?", MAPPER, id, userId);
    }

    public long insert(long userId, long categoryId, TransactionType type, Money amount,
                       LocalDate date, String description) {
        return insert("INSERT INTO transactions (user_id, category_id, type, amount, txn_date, description) "
                + "VALUES (?, ?, ?, ?, ?, ?)", userId, categoryId, type, amount, date, description);
    }

    public boolean update(long id, long userId, long categoryId, TransactionType type, Money amount,
                          LocalDate date, String description) {
        return update("UPDATE transactions SET category_id = ?, type = ?, amount = ?, txn_date = ?, description = ? "
                + "WHERE id = ? AND user_id = ?", categoryId, type, amount, date, description, id, userId) > 0;
    }

    public boolean delete(long id, long userId) {
        return update("DELETE FROM transactions WHERE id = ? AND user_id = ?", id, userId) > 0;
    }

    /** Date of the user's earliest transaction, or empty when there is none. */
    public Optional<LocalDate> firstTransactionDate(long userId) {
        List<LocalDate> rows = queryList("SELECT MIN(txn_date) first_date FROM transactions WHERE user_id = ?",
                rs -> getDate(rs, "first_date"), userId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    /** Total in paise of one transaction type between two dates (inclusive). */
    public long sum(long userId, TransactionType type, LocalDate from, LocalDate through, boolean excludeCommitments) {
        String sql = "SELECT COALESCE(SUM(t.amount), 0) FROM transactions t JOIN categories c ON c.id = t.category_id "
                + "WHERE t.user_id = ? AND t.type = ? AND t.txn_date BETWEEN ? AND ?"
                + (excludeCommitments ? " AND c.is_commitment = FALSE" : "");
        return queryLong(sql, userId, type, from, through);
    }

    /** Expense spent in one category between two dates - used for budget tracking. */
    public long categorySpend(long userId, long categoryId, LocalDate from, LocalDate through) {
        return queryLong("SELECT COALESCE(SUM(amount), 0) FROM transactions "
                + "WHERE user_id = ? AND category_id = ? AND type = 'EXPENSE' AND txn_date BETWEEN ? AND ?",
                userId, categoryId, from, through);
    }

    /**
     * Expense totals grouped by category and calendar month (SQL GROUP BY).
     * Commitment categories (EMI, rent...) are fixed amounts, so they are left out of forecasting.
     */
    public List<CategoryMonthTotal> monthlyExpenseTotals(long userId, LocalDate from, LocalDate through) {
        String sql = "SELECT t.category_id, c.user_id category_user_id, c.name category_name, c.type category_type, "
                + "c.nature, c.is_commitment, YEAR(t.txn_date) y, MONTH(t.txn_date) m, SUM(t.amount) total "
                + "FROM transactions t JOIN categories c ON c.id = t.category_id "
                + "WHERE t.user_id = ? AND t.type = 'EXPENSE' AND c.is_commitment = FALSE "
                + "AND t.txn_date BETWEEN ? AND ? "
                + "GROUP BY t.category_id, c.user_id, c.name, c.type, c.nature, c.is_commitment, y, m "
                + "ORDER BY c.name, y, m";
        return queryList(sql, rs -> new CategoryMonthTotal(
                CATEGORY_MAPPER.map(rs),
                YearMonth.of(rs.getInt("y"), rs.getInt("m")),
                getMoney(rs, "total")), userId, from, through);
    }
}

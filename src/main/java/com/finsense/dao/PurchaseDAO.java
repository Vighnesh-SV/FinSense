package com.finsense.dao;

import com.finsense.decision.DecisionResult;
import com.finsense.decision.FinancialSnapshot;
import com.finsense.decision.Recommendation;
import com.finsense.exception.ValidationException;
import com.finsense.model.Category;
import com.finsense.model.Money;
import com.finsense.model.Purchase;
import com.finsense.model.PurchaseHistory;
import com.finsense.model.PurchaseSummary;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.PurchaseFeedback;
import com.finsense.model.enums.PurchaseStatus;
import com.finsense.model.enums.TransactionType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for purchase ideas and their evaluation history.
 * Every read joins or filters on user_id (user isolation); multi-step writes
 * run inside one JDBC transaction (inTransaction) so they succeed or fail together.
 */
public class PurchaseDAO extends BaseDAO {

    private static final String COLUMNS =
            "p.id, p.user_id, p.product_name, p.category_id, c.user_id category_user_id, c.name category_name, "
            + "c.type category_type, c.nature, c.is_commitment, p.price, p.description, p.necessity, "
            + "p.status, p.created_at";

    private static final String SELECT =
            "SELECT " + COLUMNS + " FROM purchases p JOIN categories c ON c.id = p.category_id ";

    private static final RowMapper<Purchase> MAPPER = rs -> new Purchase(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("product_name"),
            new Category(rs.getLong("category_id"), getNullableLong(rs, "category_user_id"),
                    rs.getString("category_name"), getEnum(rs, "category_type", TransactionType.class),
                    rs.getString("nature"), rs.getBoolean("is_commitment")),
            getMoney(rs, "price"),
            rs.getString("description"),
            getEnum(rs, "necessity", Necessity.class),
            getEnum(rs, "status", PurchaseStatus.class),
            getDateTime(rs, "created_at"));

    private static final RowMapper<PurchaseHistory> HISTORY_MAPPER = rs -> new PurchaseHistory(
            rs.getLong("id"),
            rs.getLong("purchase_id"),
            getEnum(rs, "recommendation", Recommendation.class),
            rs.getInt("score"),
            rs.getString("explanation"),
            rs.getString("warnings"),
            getMoney(rs, "snapshot_income"),
            getMoney(rs, "snapshot_expenses"),
            getMoney(rs, "snapshot_disposable"),
            getMoney(rs, "snapshot_free_savings"),
            rs.getString("snapshot_behaviour"),
            getDateTime(rs, "evaluated_at"));

    /** Saves a new purchase idea together with its first evaluation, atomically. */
    public long saveEvaluation(long userId, Purchase purchase, DecisionResult result) {
        return inTransaction(() -> {
            long id = insert("INSERT INTO purchases (user_id, product_name, category_id, price, description, necessity) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", userId, purchase.productName(), purchase.category().id(),
                    purchase.price(), purchase.description(), purchase.necessity());
            insertHistory(id, userId, result);
            return id;
        });
    }

    /** Appends one evaluation; the snapshot columns freeze the finances used for that verdict. */
    public void insertHistory(long purchaseId, long userId, DecisionResult result) {
        if (findById(purchaseId, userId).isEmpty()) {
            throw new ValidationException("Purchase not found.");
        }
        FinancialSnapshot snapshot = result.snapshot();
        String explanation = String.join("\n", result.explanationLines());
        String warnings = result.warnings().isEmpty() ? null : String.join("\n", result.warnings());
        String behaviour = snapshot.behaviour() == null ? "UNCLASSIFIED" : snapshot.behaviour().name();
        insert("INSERT INTO purchase_history (purchase_id, recommendation, score, explanation, warnings, "
                + "snapshot_income, snapshot_expenses, snapshot_disposable, snapshot_free_savings, "
                + "snapshot_behaviour, evaluated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                purchaseId, result.recommendation(), result.score(), explanation, warnings,
                snapshot.income(), snapshot.monthlyExpenses(), snapshot.disposableIncome(),
                snapshot.freeSavings(), behaviour, result.evaluatedAt());
    }

    public List<Purchase> findByUserId(long userId) {
        return queryList(SELECT + "WHERE p.user_id = ? ORDER BY p.created_at DESC, p.id DESC", MAPPER, userId);
    }

    public Optional<Purchase> findById(long id, long userId) {
        return queryOne(SELECT + "WHERE p.id = ? AND p.user_id = ?", MAPPER, id, userId);
    }

    public boolean updateStatus(long id, long userId, PurchaseStatus status) {
        return update("UPDATE purchases SET status = ? WHERE id = ? AND user_id = ?", status, id, userId) > 0;
    }

    public long countHistory(long purchaseId, long userId) {
        return queryLong("SELECT COUNT(*) FROM purchase_history h JOIN purchases p ON p.id = h.purchase_id "
                + "WHERE h.purchase_id = ? AND p.user_id = ?", purchaseId, userId);
    }

    public List<PurchaseHistory> history(long purchaseId, long userId) {
        return queryList("SELECT h.id, h.purchase_id, h.recommendation, h.score, h.explanation, h.warnings, "
                + "h.snapshot_income, h.snapshot_expenses, h.snapshot_disposable, h.snapshot_free_savings, "
                + "h.snapshot_behaviour, h.evaluated_at FROM purchase_history h "
                + "JOIN purchases p ON p.id = h.purchase_id "
                + "WHERE h.purchase_id = ? AND p.user_id = ? ORDER BY h.evaluated_at DESC, h.id DESC",
                HISTORY_MAPPER, purchaseId, userId);
    }

    /**
     * Every purchase with its latest verdict and feedback, in ONE query.
     * (Previously each row ran a second query while the first result set was still open.)
     */
    public List<PurchaseSummary> summaries(long userId) {
        String latest = "(SELECT h.id FROM purchase_history h WHERE h.purchase_id = p.id "
                + "ORDER BY h.evaluated_at DESC, h.id DESC LIMIT 1)";
        String sql = "SELECT " + COLUMNS + ", lh.recommendation, lh.score, f.feedback "
                + "FROM purchases p JOIN categories c ON c.id = p.category_id "
                + "LEFT JOIN purchase_history lh ON lh.id = " + latest + " "
                + "LEFT JOIN purchase_feedback f ON f.purchase_id = p.id "
                + "WHERE p.user_id = ? ORDER BY p.created_at DESC, p.id DESC";
        return queryList(sql, rs -> {
            Integer score = rs.getObject("score") == null ? null : rs.getInt("score");
            return new PurchaseSummary(MAPPER.map(rs),
                    getEnum(rs, "recommendation", Recommendation.class),
                    score,
                    getEnum(rs, "feedback", PurchaseFeedback.class));
        }, userId);
    }

    /** Marking purchased records the expense, optionally deducts free savings, and updates status - atomically. */
    public void markAsPurchased(long purchaseId, long userId, boolean deductFromSavings, LocalDate date) {
        inTransaction(() -> {
            Purchase purchase = findById(purchaseId, userId)
                    .orElseThrow(() -> new ValidationException("Purchase not found."));
            if (purchase.status() == PurchaseStatus.PURCHASED) {
                throw new ValidationException("Purchase is already marked as purchased.");
            }
            if (purchase.status() == PurchaseStatus.DROPPED) {
                throw new ValidationException("A dropped purchase cannot be marked as purchased.");
            }
            if (deductFromSavings) {
                deductFromFreeSavings(userId, purchase.price());
            }
            insert("INSERT INTO transactions (user_id, category_id, type, amount, txn_date, description, purchase_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)", userId, purchase.category().id(), TransactionType.EXPENSE,
                    purchase.price(), date, "Purchase: " + purchase.productName(), purchaseId);
            int changed = update("UPDATE purchases SET status = 'PURCHASED' "
                    + "WHERE id = ? AND user_id = ? AND status = 'EVALUATED'", purchaseId, userId);
            if (changed == 0) {
                throw new ValidationException("Purchase could not be marked as purchased.");
            }
            return null;
        });
    }

    /** Runs inside markAsPurchased's transaction; FOR UPDATE locks the profile row. */
    private void deductFromFreeSavings(long userId, Money price) {
        List<Money> rows = queryList("SELECT current_savings FROM financial_profiles WHERE user_id = ? FOR UPDATE",
                rs -> getMoney(rs, "current_savings"), userId);
        if (rows.isEmpty()) {
            throw new ValidationException("Complete your financial profile before deducting savings.");
        }
        Money current = rows.get(0);
        Money reserved = Money.ofPaise(queryLong("SELECT COALESCE(SUM(c.amount), 0) FROM goal_contributions c "
                + "JOIN savings_goals g ON g.id = c.goal_id WHERE g.user_id = ? AND g.status = 'ACTIVE'", userId));
        Money free = Money.max(Money.ZERO, current.minus(reserved));
        if (price.isGreaterThan(free)) {
            throw new ValidationException("Purchase exceeds free savings of " + free.format() + ".");
        }
        update("UPDATE financial_profiles SET current_savings = ? WHERE user_id = ?", current.minus(price), userId);
    }
}

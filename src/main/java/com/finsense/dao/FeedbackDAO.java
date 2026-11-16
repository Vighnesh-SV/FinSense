package com.finsense.dao;

import com.finsense.model.FeedbackStats;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.PurchaseFeedback;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores how the user felt about purchases they actually made (worth it / neutral / regret)
 * and aggregates those ratings for the learning rule.
 */
public class FeedbackDAO extends BaseDAO {

    private static final String COUNTS =
            "SUM(f.feedback = 'WORTH_IT') worth, SUM(f.feedback = 'NEUTRAL') neutral, "
            + "SUM(f.feedback = 'REGRET') regret ";

    /** Insert or replace the rating (MySQL upsert). Only PURCHASED items owned by the user can be rated. */
    public boolean save(long purchaseId, long userId, PurchaseFeedback feedback) {
        return update("INSERT INTO purchase_feedback (purchase_id, user_id, feedback) "
                + "SELECT p.id, p.user_id, ? FROM purchases p "
                + "WHERE p.id = ? AND p.user_id = ? AND p.status = 'PURCHASED' "
                + "ON DUPLICATE KEY UPDATE feedback = VALUES(feedback)", feedback, purchaseId, userId) > 0;
    }

    /** Rating counts per category id. */
    public Map<Long, FeedbackStats> statsByCategory(long userId) {
        Map<Long, FeedbackStats> result = new HashMap<>();
        queryList("SELECT p.category_id, c.name, " + COUNTS
                + "FROM purchase_feedback f JOIN purchases p ON p.id = f.purchase_id "
                + "JOIN categories c ON c.id = p.category_id "
                + "WHERE f.user_id = ? GROUP BY p.category_id, c.name", rs -> {
                    result.put(rs.getLong("category_id"), new FeedbackStats(rs.getString("name") + " purchases",
                            rs.getInt("worth"), rs.getInt("neutral"), rs.getInt("regret")));
                    return null;
                }, userId);
        return result;
    }

    /** Rating counts per NEED / WANT. */
    public Map<Necessity, FeedbackStats> statsByNecessity(long userId) {
        Map<Necessity, FeedbackStats> result = new EnumMap<>(Necessity.class);
        queryList("SELECT p.necessity, " + COUNTS
                + "FROM purchase_feedback f JOIN purchases p ON p.id = f.purchase_id "
                + "WHERE f.user_id = ? GROUP BY p.necessity", rs -> {
                    Necessity necessity = getEnum(rs, "necessity", Necessity.class);
                    result.put(necessity, new FeedbackStats(necessity.getLabel() + " purchases",
                            rs.getInt("worth"), rs.getInt("neutral"), rs.getInt("regret")));
                    return null;
                }, userId);
        return result;
    }

    public long regretCount(long userId) {
        return queryLong("SELECT COUNT(*) FROM purchase_feedback WHERE user_id = ? AND feedback = 'REGRET'", userId);
    }

    public long ratedCount(long userId) {
        return queryLong("SELECT COUNT(*) FROM purchase_feedback WHERE user_id = ?", userId);
    }
}

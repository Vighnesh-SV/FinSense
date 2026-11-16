package com.finsense.dao;

import com.finsense.model.Category;
import com.finsense.model.Money;
import com.finsense.model.enums.TransactionType;

import java.util.List;

/** Monthly spending limits per expense category (at most one limit per user and category). */
public class BudgetDAO extends BaseDAO {

    /** A stored limit; spending against it is calculated from transactions. */
    public record Budget(long id, Category category, Money monthlyLimit) { }

    private static final RowMapper<Budget> MAPPER = rs -> new Budget(
            rs.getLong("id"),
            new Category(rs.getLong("category_id"), getNullableLong(rs, "category_user_id"),
                    rs.getString("name"), getEnum(rs, "type", TransactionType.class),
                    rs.getString("nature"), rs.getBoolean("is_commitment")),
            getMoney(rs, "monthly_limit"));

    public List<Budget> findByUserId(long userId) {
        return queryList("SELECT b.id, b.category_id, b.monthly_limit, c.user_id category_user_id, c.name, c.type, "
                + "c.nature, c.is_commitment FROM category_budgets b JOIN categories c ON c.id = b.category_id "
                + "WHERE b.user_id = ? ORDER BY c.name", MAPPER, userId);
    }

    /** Creates the budget, or replaces the limit if the category already has one (upsert). */
    public void save(long userId, long categoryId, Money limit) {
        update("INSERT INTO category_budgets (user_id, category_id, monthly_limit) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE monthly_limit = VALUES(monthly_limit)", userId, categoryId, limit);
    }

    public boolean delete(long id, long userId) {
        return update("DELETE FROM category_budgets WHERE id = ? AND user_id = ?", id, userId) > 0;
    }
}

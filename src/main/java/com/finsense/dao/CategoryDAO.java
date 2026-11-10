package com.finsense.dao;

import com.finsense.model.Category;
import com.finsense.model.enums.TransactionType;
import java.util.List;
import java.util.Optional;

/** Loads shared built-in categories together with the signed-in user's own categories. */
public class CategoryDAO extends BaseDAO {
    private static final RowMapper<Category> MAPPER = rs -> new Category(
            rs.getLong("id"), getNullableLong(rs, "user_id"), rs.getString("name"),
            getEnum(rs, "type", TransactionType.class), rs.getString("nature"), rs.getBoolean("is_commitment"));

    public List<Category> findByType(TransactionType type, long userId) {
        return queryList("SELECT id,user_id,name,type,nature,is_commitment FROM categories " +
                "WHERE type=? AND (user_id IS NULL OR user_id=?) ORDER BY user_id IS NULL DESC,name",
                MAPPER, type, userId);
    }

    public Optional<Category> findVisibleById(long id, long userId) {
        return queryOne("SELECT id,user_id,name,type,nature,is_commitment FROM categories " +
                "WHERE id=? AND (user_id IS NULL OR user_id=?)", MAPPER, id, userId);
    }
}

package com.finsense.dao;

import com.finsense.model.Commitment;
import com.finsense.model.enums.CommitmentType;

import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for recurring monthly {@code commitments}.
 *
 * OOP concept: Inheritance and User Isolation.
 * Extends BaseDAO. Every query, update, and delete is strictly scoped by {@code user_id}
 * to guarantee complete data isolation between different users.
 */
public class CommitmentDAO extends BaseDAO {

    private static final RowMapper<Commitment> COMMITMENT_MAPPER = rs -> new Commitment(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("name"),
            getEnum(rs, "type", CommitmentType.class),
            getMoney(rs, "monthly_amount"),
            getDate(rs, "start_date"),
            getDate(rs, "end_date")
    );

    /**
     * Finds all commitments for a user, ordered by start_date DESC, id DESC.
     */
    public List<Commitment> findByUserId(long userId) {
        return queryList(
                "SELECT id, user_id, name, type, monthly_amount, start_date, end_date " +
                        "FROM commitments WHERE user_id = ? ORDER BY start_date DESC, id DESC",
                COMMITMENT_MAPPER,
                userId
        );
    }

    /**
     * Finds a single commitment by id and user_id (User isolation).
     */
    public Optional<Commitment> findById(long id, long userId) {
        return queryOne(
                "SELECT id, user_id, name, type, monthly_amount, start_date, end_date " +
                        "FROM commitments WHERE id = ? AND user_id = ?",
                COMMITMENT_MAPPER,
                id,
                userId
        );
    }

    /**
     * Inserts a new commitment and returns the generated primary key id.
     */
    public long insert(Commitment c) {
        return insert(
                "INSERT INTO commitments (user_id, name, type, monthly_amount, start_date, end_date) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                c.getUserId(),
                c.getName(),
                c.getType(),
                c.getMonthlyAmount(),
                c.getStartDate(),
                c.getEndDate()
        );
    }

    /**
     * Updates an existing commitment scoped by id and user_id.
     * Returns true if a row was updated.
     */
    public boolean update(Commitment c) {
        int affected = update(
                "UPDATE commitments SET name = ?, type = ?, monthly_amount = ?, start_date = ?, end_date = ? " +
                        "WHERE id = ? AND user_id = ?",
                c.getName(),
                c.getType(),
                c.getMonthlyAmount(),
                c.getStartDate(),
                c.getEndDate(),
                c.getId(),
                c.getUserId()
        );
        return affected > 0;
    }

    /**
     * Deletes a commitment scoped by id and user_id.
     * Returns true if a row was deleted.
     */
    public boolean delete(long id, long userId) {
        int affected = update(
                "DELETE FROM commitments WHERE id = ? AND user_id = ?",
                id,
                userId
        );
        return affected > 0;
    }
}


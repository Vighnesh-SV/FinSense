package com.finsense.dao;

import com.finsense.database.DatabaseConnection;
import com.finsense.exception.DataAccessException;
import com.finsense.exception.FinSenseException;
import com.finsense.model.Money;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Common JDBC plumbing shared by every DAO (Data Access Object).
 *
 * Every concrete DAO (UserDAO, TransactionDAO ...) *extends* this class and
 * inherits ready-made, safe helpers, so each DAO only contains its own SQL and
 * row mapping. This is genuine inheritance: the shared code is written once.
 *
 * Safety rules built in here:
 *  - Only PreparedStatement with ? placeholders is used, so user input is sent
 *    separately from the SQL text and SQL injection is impossible.
 *  - try-with-resources closes every Statement and ResultSet automatically.
 *  - SQLException (checked) is wrapped in DataAccessException (unchecked), so
 *    JDBC details do not leak into the service and controller layers.
 */
public abstract class BaseDAO {

    /**
     * Converts the current row of a ResultSet into a Java object.
     * A functional interface (one abstract method) - DAOs pass it as a lambda:
     *   queryList(sql, rs -> new Category(rs.getLong("id"), ...), userId)
     */
    @FunctionalInterface
    protected interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /** A unit of work that must succeed or fail as a whole (see {@link #inTransaction}). */
    @FunctionalInterface
    protected interface TransactionWork<T> {
        T execute() throws SQLException;
    }

    protected Connection connection() {
        return DatabaseConnection.getInstance().getConnection();
    }

    // ---------------------------------------------------------------- queries

    /** Runs a SELECT and maps every row. */
    protected <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    /** Runs a SELECT expected to return at most one row. */
    protected <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = queryList(sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Runs a SELECT returning a single number, e.g. COUNT(*) or SUM(amount). NULL counts as 0. */
    protected long queryLong(String sql, Object... params) {
        return queryOne(sql, rs -> rs.getLong(1), params).orElse(0L);
    }

    // ---------------------------------------------------------------- updates

    /** Runs an UPDATE or DELETE and returns the number of affected rows. */
    protected int update(String sql, Object... params) {
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    /** Runs an INSERT and returns the AUTO_INCREMENT id MySQL generated for the new row. */
    protected long insert(String sql, Object... params) {
        try (PreparedStatement ps = connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new DataAccessException("Insert did not return a generated id");
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    /**
     * Runs several statements as ONE database transaction: if any of them fails,
     * every change made inside the work is rolled back. Example: marking a
     * purchase as bought = insert an expense AND update the purchase status.
     */
    protected <T> T inTransaction(TransactionWork<T> work) {
        Connection conn = connection();
        try {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T result = work.execute();
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                if (e instanceof FinSenseException fe) {
                    throw fe;                       // keep validation / data errors as they are
                }
                if (e instanceof SQLException se) {
                    throw wrap(se);
                }
                throw (RuntimeException) e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    // ---------------------------------------------------------------- parameter binding

    /**
     * Fills the ? placeholders in order. Converts FinSense types to SQL types:
     * Money -> BIGINT paise, enums -> their name, LocalDate -> DATE, null -> SQL NULL.
     */
    protected static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            int index = i + 1;                      // JDBC parameters start at 1, not 0
            Object value = params[i];
            if (value == null) {
                ps.setNull(index, Types.NULL);
            } else if (value instanceof Money money) {
                ps.setLong(index, money.getPaise());
            } else if (value instanceof Enum<?> e) {
                ps.setString(index, e.name());
            } else if (value instanceof LocalDate || value instanceof LocalDateTime) {
                ps.setObject(index, value);         // JDBC 4.2 maps java.time types directly
            } else if (value instanceof Boolean b) {
                ps.setBoolean(index, b);
            } else {
                ps.setObject(index, value);
            }
        }
    }

    // ---------------------------------------------------------------- reading columns

    protected static Money getMoney(ResultSet rs, String column) throws SQLException {
        return Money.ofPaise(rs.getLong(column));
    }

    protected static LocalDate getDate(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    protected static LocalDateTime getDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }

    /** Reads an ENUM/VARCHAR column into a Java enum constant (null stays null). */
    protected static <E extends Enum<E>> E getEnum(ResultSet rs, String column, Class<E> type) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }

    /** Reads a nullable BIGINT foreign key (rs.getLong would turn NULL into 0). */
    protected static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    // ---------------------------------------------------------------- errors

    /**
     * MySQL error 1062 = duplicate key (e.g. a username that already exists).
     * Public so services can turn this database error into a friendly message.
     */
    public static boolean isDuplicateKey(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SQLException se && se.getErrorCode() == 1062) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static DataAccessException wrap(SQLException e) {
        return new DataAccessException("Database error: " + e.getMessage(), e);
    }
}

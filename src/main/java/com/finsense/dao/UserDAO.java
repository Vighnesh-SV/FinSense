package com.finsense.dao;

import com.finsense.model.User;
import com.finsense.model.UserCredentials;

import java.util.Optional;

/**
 * Data Access Object for the {@code users} table.
 *
 * Extends BaseDAO to inherit the JDBC helpers (queryList, queryOne, insert …),
 * so this class only needs to supply SQL strings and row-mapper lambdas.
 * Controllers and services never call JDBC directly — they call this DAO,
 * which keeps the database details in one place (Single Responsibility).
 *
 * OOP concept used: inheritance. UserDAO IS-A BaseDAO and reuses every
 * protected helper without duplicating a single line of JDBC plumbing.
 */
public class UserDAO extends BaseDAO {

    // ---------------------------------------------------------------- row mapper

    /**
     * Converts the current ResultSet row into a User.
     * Written as a private method so both findById and findCredentialsByUsername
     * share it — no duplicated column names.
     */
    private static final RowMapper<User> USER_MAPPER = rs -> new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("full_name"),
            getDateTime(rs, "created_at")
    );

    // ---------------------------------------------------------------- write

    /**
     * Inserts a new user row and returns the AUTO_INCREMENT id MySQL assigned.
     *
     * @param username     already validated and trimmed
     * @param passwordHash PBKDF2 hash (Base64)
     * @param salt         random salt (Base64)
     * @param fullName     display name
     * @return the generated primary key
     */
    public long insert(String username, String passwordHash, String salt, String fullName) {
        return insert(
                "INSERT INTO users (username, password_hash, salt, full_name) VALUES (?, ?, ?, ?)",
                username, passwordHash, salt, fullName
        );
    }

    // ---------------------------------------------------------------- reads

    /**
     * Looks up a user by primary key.
     *
     * Returns {@code Optional.empty()} instead of null so callers are forced
     * by the type system to handle the "user not found" case — the Optional
     * monad makes absence explicit.
     */
    public Optional<User> findById(long id) {
        return queryOne(
                "SELECT id, username, full_name, created_at FROM users WHERE id = ?",
                USER_MAPPER,
                id
        );
    }

    /**
     * Loads the User plus the credential columns needed for password verification.
     * Username comparison is case-insensitive because the column uses the
     * utf8mb4 default collation (see schema.sql, table users).
     */
    public Optional<UserCredentials> findCredentialsByUsername(String username) {
        return queryOne(
                "SELECT id, username, full_name, created_at, password_hash, salt " +
                        "FROM users WHERE username = ?",
                rs -> new UserCredentials(
                        USER_MAPPER.map(rs),
                        rs.getString("salt"),
                        rs.getString("password_hash")
                ),
                username
        );
    }

    /**
     * Fast existence check; used by UserService to detect duplicate usernames
     * before attempting an insert.
     *
     * COUNT(*) returns 0 or 1; queryLong returns 0 when the row is absent.
     */
    public boolean existsByUsername(String username) {
        return queryLong(
                "SELECT COUNT(*) FROM users WHERE username = ?",
                username
        ) > 0;
    }

    /**
     * Deletes a user. Every table that references users uses ON DELETE CASCADE,
     * so MySQL removes the user's transactions, goals, purchases, etc. as well.
     */
    public boolean deleteByUsername(String username) {
        return update("DELETE FROM users WHERE username = ?", username) > 0;
    }
}


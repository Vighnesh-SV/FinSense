package com.finsense.database;

import com.finsense.exception.DataAccessException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the database structure on start-up.
 *
 * - Runs db/schema.sql. Every statement uses IF NOT EXISTS, so running it on
 *   an existing database changes nothing and never deletes data.
 * - Inserts the built-in categories only if the categories table is empty.
 *   The inserts run inside one JDBC transaction: either all 23 categories are
 *   saved, or (on any error) none are.
 *
 * Note for the viva: in MySQL, DDL statements such as CREATE TABLE cause an
 * implicit commit and cannot be rolled back. That is why only the data inserts
 * (DML) are wrapped in the transaction, while the schema relies on IF NOT EXISTS
 * to be safe to re-run.
 */
public class DatabaseInitializer {

    private static final String SCHEMA_SCRIPT = "/db/schema.sql";
    private static final String SEED_SCRIPT = "/db/seed_categories.sql";

    private final DatabaseConnection database;

    public DatabaseInitializer(DatabaseConnection database) {
        this.database = database;
    }

    /** Summary of what initialisation did, shown at start-up. */
    public record InitResult(boolean freshDatabase, int tableCount, int categoryCount) { }

    public InitResult initialize() {
        Connection conn = database.getConnection();
        try {
            boolean fresh = !tableExists(conn, "users");

            // 1. Schema (DDL) - safe to repeat thanks to IF NOT EXISTS
            try (Statement st = conn.createStatement()) {
                for (String sql : readStatements(SCHEMA_SCRIPT)) {
                    st.execute(sql);
                }
            }

            // 2. Seed data (DML) - all-or-nothing inside a transaction
            if (countRows(conn, "categories") == 0) {
                conn.setAutoCommit(false);
                try (Statement st = conn.createStatement()) {
                    for (String sql : readStatements(SEED_SCRIPT)) {
                        st.executeUpdate(sql);
                    }
                    conn.commit();
                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }

            return new InitResult(fresh, countTables(conn), countRows(conn, "categories"));
        } catch (SQLException e) {
            throw new DataAccessException("Database initialisation failed: " + e.getMessage(), e);
        }
    }

    /**
     * DatabaseMetaData is the standard JDBC way to ask a database about its own
     * structure, so this works the same on any database, not only MySQL.
     */
    private boolean tableExists(Connection conn, String table) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(conn.getCatalog(), null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private int countTables(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        int count = 0;
        try (ResultSet rs = meta.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                count++;
            }
        }
        return count;
    }

    /** Table names cannot be bound as ? parameters; only internal constants are passed here. */
    private int countRows(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Reads a .sql file from the classpath and splits it into single statements.
     * Lines starting with "--" are comments and are skipped; a statement ends
     * with a semicolon at the end of a line. (Our scripts contain no stored
     * procedures or triggers, so this simple splitter is sufficient.)
     */
    static List<String> readStatements(String resourcePath) {
        InputStream in = DatabaseInitializer.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new DataAccessException("SQL script not found on classpath: " + resourcePath);
        }
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                current.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    String sql = current.toString().trim();
                    statements.add(sql.substring(0, sql.length() - 1));   // drop the ';'
                    current.setLength(0);
                }
            }
        } catch (IOException e) {
            throw new DataAccessException("Could not read SQL script " + resourcePath, e);
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString().trim());
        }
        return statements;
    }
}

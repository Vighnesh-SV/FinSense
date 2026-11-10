package com.finsense.database;

import com.finsense.exception.DataAccessException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Provides the single shared JDBC connection to the MySQL database.
 *
 * Design pattern: Singleton - exactly one instance exists (private constructor
 * + static getInstance()). A desktop app used by one person at a time needs
 * only one connection; sharing it avoids repeatedly logging in to MySQL.
 *
 * Settings come from config/db.properties (see {@link DatabaseConfig}).
 * Tests call {@link #useConfig(DatabaseConfig)} to switch to a test database.
 */
public final class DatabaseConnection {

    private static DatabaseConnection instance;

    private final DatabaseConfig config;
    private Connection connection;

    private DatabaseConnection(DatabaseConfig config) {
        this.config = config;
    }

    /** Returns the one shared instance, reading config/db.properties on first use. */
    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection(DatabaseConfig.load(DatabaseConfig.DEFAULT_FILE));
        }
        return instance;
    }

    /**
     * Switches the application to other settings (used by unit tests).
     * Any open connection made with the previous settings is closed first.
     */
    public static synchronized void useConfig(DatabaseConfig config) {
        if (instance != null) {
            instance.close();
        }
        instance = new DatabaseConnection(config);
    }

    /** Returns an open connection, (re)connecting if necessary. */
    public synchronized Connection getConnection() {
        try {
            // isValid() also detects connections MySQL dropped after a long idle time
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                connection = DriverManager.getConnection(config.jdbcUrl(), config.user(), config.password());
            }
            return connection;
        } catch (SQLException e) {
            throw new DataAccessException("Could not connect to MySQL database " + config.describe()
                    + ".\nCheck that the MySQL server is running and that config/db.properties is correct.\n"
                    + "Details: " + e.getMessage(), e);
        }
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    /** Closes the connection; called when the application exits. */
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.err.println("Warning: failed to close database connection: " + e.getMessage());
            } finally {
                connection = null;
            }
        }
    }
}

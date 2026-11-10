package com.finsense.database;

import com.finsense.exception.DataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * MySQL connection settings, read from config/db.properties.
 *
 * Credentials are kept in a separate, git-ignored file instead of being
 * hard-coded in Java source - a basic security practice, and it lets each
 * developer/examiner use their own MySQL login without editing code.
 *
 * A Java "record" is a compact immutable data class: the compiler generates the
 * constructor, accessors (host(), port() ...), equals(), hashCode() and toString().
 */
public record DatabaseConfig(String host, int port, String databaseName, String user, String password) {

    public static final Path DEFAULT_FILE = Path.of(System.getProperty("user.dir"), "config", "db.properties");

    /** Loads settings from the given .properties file. */
    public static DatabaseConfig load(Path file) {
        if (!Files.exists(file)) {
            throw new DataAccessException("Database settings not found: " + file.toAbsolutePath()
                    + "\nCopy config/db.properties.example to config/db.properties and enter your MySQL username and password.");
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new DataAccessException("Could not read " + file.toAbsolutePath(), e);
        }
        try {
            return new DatabaseConfig(
                    props.getProperty("db.host", "localhost").trim(),
                    Integer.parseInt(props.getProperty("db.port", "3306").trim()),
                    props.getProperty("db.name", "finsense").trim(),
                    props.getProperty("db.user", "root").trim(),
                    props.getProperty("db.password", ""));
        } catch (NumberFormatException e) {
            throw new DataAccessException("db.port in " + file.toAbsolutePath() + " must be a number", e);
        }
    }

    /** Same server and login, different database (used by tests: finsense_test). */
    public DatabaseConfig withDatabaseName(String name) {
        return new DatabaseConfig(host, port, name, user, password);
    }

    /**
     * Builds the JDBC URL.
     * createDatabaseIfNotExist=true - Connector/J creates the database on first connect.
     * allowPublicKeyRetrieval=true  - needed by MySQL 8's default login plugin on local servers.
     * serverTimezone                - so DATE/DATETIME values are read in Indian time.
     */
    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                + "?createDatabaseIfNotExist=true"
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Kolkata"
                + "&characterEncoding=UTF-8";
    }

    /** Description without the password, safe to print or show on screen. */
    public String describe() {
        return databaseName + " on " + host + ":" + port + " (user " + user + ")";
    }

    /** Never print the password, even by accident. */
    @Override
    public String toString() {
        return "DatabaseConfig[" + describe() + "]";
    }
}

package com.finsense.service;

import com.finsense.dao.BaseDAO;
import com.finsense.dao.UserDAO;
import com.finsense.exception.AuthenticationException;
import com.finsense.exception.ValidationException;
import com.finsense.model.User;
import com.finsense.model.UserCredentials;
import com.finsense.util.PasswordUtil;
import com.finsense.util.SessionManager;
import com.finsense.util.ValidationUtil;

/**
 * Business rules for user accounts: registration, login and logout.
 *
 * Layered architecture: this service sits between the controllers (UI layer)
 * and the DAO (database layer). Controllers call service methods; the service
 * validates, hashes passwords and delegates persistence to the DAO. JDBC never
 * appears here and UI code never appears in the DAO.
 *
 * OOP concept: constructor injection. The DAO is passed in rather than created
 * inside the class, so tests can supply a DAO that points at finsense_test
 * without changing any service code (Dependency Injection / IoC principle).
 */
public class UserService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password.";

    private final UserDAO dao;

    /**
     * Production constructor: accepts any UserDAO (real or test).
     * Tests call {@code new UserService(testDao)} to inject a DAO backed by
     * the test database; the production app calls the no-arg constructor below.
     */
    public UserService(UserDAO dao) {
        this.dao = dao;
    }

    /**
     * Convenience constructor for production use.
     * Creates a default UserDAO that talks to the configured MySQL database.
     */
    public UserService() {
        this(new UserDAO());
    }

    // ---------------------------------------------------------------- register

    /**
     * Validates the registration form, hashes the password and persists the user.
     *
     * Validation order mirrors what the user sees top-to-bottom:
     *   full name → username → password strength → confirm match → uniqueness.
     *
     * After a successful insert, the new user is logged in immediately.
     *
     * @param fullName        display name, max 100 characters
     * @param username        3-30 chars, letters/digits/underscore/dot
     * @param password        at least 8 chars with one letter and one digit
     * @param confirmPassword must match {@code password}
     * @return the newly created, logged-in User
     * @throws ValidationException if any field fails validation or the username is already taken
     */
    public User register(String fullName, String username, String password, String confirmPassword) {
        // 1. Field validation (throws ValidationException on the first failure)
        String cleanName = ValidationUtil.requireText(fullName, "Full name", 100);
        String cleanUsername = ValidationUtil.requireUsername(username);
        ValidationUtil.requireStrongPassword(password);

        if (!password.equals(confirmPassword)) {
            throw new ValidationException("Confirm password", "Passwords do not match.");
        }

        // 2. Duplicate-username check (pre-flight, before hashing which is slow)
        if (dao.existsByUsername(cleanUsername)) {
            throw new ValidationException("Username", "This username is already taken.");
        }

        // 3. Hash the password (PBKDF2 — this takes ~0.3 s intentionally)
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash(password, salt);

        // 4. Persist — also catches a duplicate-key race condition (two users
        //    registering the same username at the exact same millisecond).
        long id;
        try {
            id = dao.insert(cleanUsername, hash, salt, cleanName);
        } catch (RuntimeException e) {
            if (BaseDAO.isDuplicateKey(e)) {
                throw new ValidationException("Username", "This username is already taken.");
            }
            throw e;
        }

        // 5. Build the User and start the session
        User user = dao.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found after insert: " + id));
        SessionManager.getInstance().login(user);
        System.out.println("[UserService] Registered new user: " + user);
        return user;
    }

    // ---------------------------------------------------------------- login

    /**
     * Verifies credentials and starts a session.
     *
     * Unknown user and wrong password return the SAME error message so an
     * attacker cannot tell which usernames exist in the database (enumeration
     * defence). Password comparison uses constant-time {@link PasswordUtil#verify}
     * to prevent timing attacks.
     *
     * @param username the typed username (trimmed by the controller)
     * @param password the typed plain-text password (never stored)
     * @return the authenticated User
     * @throws AuthenticationException with a generic message on any failure
     */
    public User login(String username, String password) {
        // Trim username for robustness (same trimming as registration)
        String trimmed = username == null ? "" : username.trim();

        UserCredentials creds = dao.findCredentialsByUsername(trimmed)
                .orElseThrow(() -> new AuthenticationException(INVALID_CREDENTIALS));

        if (!PasswordUtil.verify(password, creds.salt(), creds.passwordHash())) {
            throw new AuthenticationException(INVALID_CREDENTIALS);
        }

        SessionManager.getInstance().login(creds.user());
        System.out.println("[UserService] Logged in: " + creds.user());
        return creds.user();
    }

    // ---------------------------------------------------------------- logout

    /**
     * Clears the current session.
     * Controllers call this method so that the logout business rule (logging,
     * future token invalidation …) stays in the service, not scattered in UI code.
     */
    public void logout() {
        SessionManager.getInstance().getCurrentUser()
                .ifPresent(u -> System.out.println("[UserService] Logged out: " + u));
        SessionManager.getInstance().logout();
    }
}


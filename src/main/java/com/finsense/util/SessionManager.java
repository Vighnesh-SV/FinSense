package com.finsense.util;

import com.finsense.exception.AuthenticationException;
import com.finsense.model.User;

import java.util.Optional;

/**
 * Remembers who is logged in.
 *
 * Singleton: there is one session for the whole desktop application.
 * Every service asks the session for the current user's id, so every database
 * query is automatically limited to that user's own records - one user can
 * never see another user's data.
 */
public final class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();

    private User currentUser;

    private SessionManager() {
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    public void login(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        this.currentUser = user;
    }

    public void logout() {
        this.currentUser = null;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * Optional makes "there may be no user" explicit in the method signature,
     * instead of returning null and risking a NullPointerException.
     */
    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    /** For services: the logged-in user, or an AuthenticationException if nobody is logged in. */
    public User requireUser() {
        if (currentUser == null) {
            throw new AuthenticationException("Please log in to continue.");
        }
        return currentUser;
    }

    public long requireUserId() {
        return requireUser().getId();
    }
}

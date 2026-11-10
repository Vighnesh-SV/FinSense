package com.finsense.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A registered FinSense user (the person currently logged in is kept in the
 * SessionManager).
 *
 * Encapsulation: fields are private and final; they can only be read through
 * getters, so a User object cannot be changed after it is created. Password
 * hash and salt are deliberately NOT part of this class - they are needed only
 * while logging in and should never travel around the application.
 */
public class User {

    private final long id;
    private final String username;
    private final String fullName;
    private final LocalDateTime createdAt;

    public User(long id, String username, String fullName, LocalDateTime createdAt) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "username");
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    /** First word of the full name, for friendly greetings ("Welcome back, Asha"). */
    public String getFirstName() {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof User u && u.id == id);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "User[" + id + ", " + username + "]";
    }
}

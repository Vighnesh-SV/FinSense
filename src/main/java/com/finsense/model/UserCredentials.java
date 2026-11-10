package com.finsense.model;

/**
 * Pairs a User with the credentials needed to verify a login attempt.
 *
 * Java record (Java 16+): a compact, immutable data carrier. The compiler
 * auto-generates the constructor, getters (user(), salt(), passwordHash()),
 * equals, hashCode and toString. Records cannot be subclassed and their
 * fields are final, making them perfect for transferring data between layers.
 *
 * This record lives only in the DAO ↔ service boundary: once the service
 * verifies the password it throws away the credentials and passes only the
 * User to SessionManager. The hash and salt never reach a controller.
 */
public record UserCredentials(User user, String salt, String passwordHash) {
}


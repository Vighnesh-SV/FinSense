package com.finsense.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilTest {

    @Test
    void correctPasswordVerifies() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("Secret123", salt);

        assertTrue(PasswordUtil.verify("Secret123", salt, hash));
    }

    @Test
    void wrongPasswordIsRejected() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("Secret123", salt);

        assertFalse(PasswordUtil.verify("secret123", salt, hash));   // case matters
        assertFalse(PasswordUtil.verify("", salt, hash));
        assertFalse(PasswordUtil.verify(null, salt, hash));
    }

    @Test
    void samePasswordWithDifferentSaltsGivesDifferentHashes() {
        String salt1 = PasswordUtil.generateSalt();
        String salt2 = PasswordUtil.generateSalt();

        assertNotEquals(salt1, salt2);
        assertNotEquals(PasswordUtil.hash("Secret123", salt1), PasswordUtil.hash("Secret123", salt2));
    }

    @Test
    void hashDoesNotContainThePassword() {
        String hash = PasswordUtil.hash("Secret123", PasswordUtil.generateSalt());
        assertFalse(hash.contains("Secret123"));
    }
}

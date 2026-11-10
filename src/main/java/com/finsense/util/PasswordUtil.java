package com.finsense.util;

import com.finsense.exception.FinSenseException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Secure password hashing with PBKDF2 (built into Java - no external library).
 *
 * - A password is NEVER stored. We store only a one-way hash of it.
 * - Each user gets a random "salt", so two users with the same password get
 *   different hashes, and pre-computed "rainbow tables" are useless.
 * - PBKDF2 repeats the hash function many thousands of times (ITERATIONS),
 *   which makes guessing passwords by brute force very slow.
 * - At login we hash the typed password with the stored salt and compare.
 */
public final class PasswordUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 310_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    /** Creates a new random salt, Base64-encoded so it can be stored as text. */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** Hashes a password with the given salt; returns the hash as Base64 text. */
    public static String hash(String password, String salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt),
                ITERATIONS, KEY_LENGTH_BITS);
        try {
            byte[] hash = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new FinSenseException("Password hashing is not available on this Java installation", e);
        } finally {
            spec.clearPassword();   // wipe the password copy from memory
        }
    }

    /**
     * Checks a typed password against the stored salt and hash.
     * MessageDigest.isEqual compares in constant time, so an attacker cannot
     * learn anything from how long the comparison takes.
     */
    public static boolean verify(String password, String salt, String expectedHash) {
        if (password == null || salt == null || expectedHash == null) {
            return false;
        }
        byte[] actual = Base64.getDecoder().decode(hash(password, salt));
        byte[] expected = Base64.getDecoder().decode(expectedHash);
        return MessageDigest.isEqual(actual, expected);
    }
}

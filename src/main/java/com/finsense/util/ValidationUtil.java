package com.finsense.util;

import com.finsense.exception.ValidationException;
import com.finsense.model.Money;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Reusable input checks used by the service layer.
 *
 * Each method either returns the cleaned value or throws a ValidationException
 * whose message can be shown to the user as-is. Keeping all rules here avoids
 * repeating the same checks in every service (no duplicate business logic).
 *
 * The class only has static methods, so its constructor is private: it is a
 * utility class that is never instantiated.
 */
public final class ValidationUtil {

    /** Upper sanity limit for any single amount: ₹100 crore. */
    public static final Money MAX_AMOUNT = Money.ofRupees(1_000_000_000L);

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.]{3,30}");
    private static final int MIN_PASSWORD_LENGTH = 8;

    private ValidationUtil() {
    }

    /** Required text: trimmed, not empty, not longer than maxLength. */
    public static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field, field + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new ValidationException(field, field + " must be at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    /** Optional text: returns null when blank, otherwise the trimmed value (length-checked). */
    public static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, field, maxLength);
    }

    /** Parses an amount that must be greater than zero (prices, transactions, goal targets). */
    public static Money parsePositiveAmount(String text, String field) {
        Money amount = parseAmount(text, field);
        if (!amount.isPositive()) {
            throw new ValidationException(field, field + " must be greater than ₹0.");
        }
        return amount;
    }

    /** Parses an amount that may be zero but not negative (income, savings, expenses). Blank = ₹0. */
    public static Money parseNonNegativeAmount(String text, String field) {
        if (text == null || text.isBlank()) {
            return Money.ZERO;
        }
        Money amount = parseAmount(text, field);
        if (amount.isNegative()) {
            throw new ValidationException(field, field + " cannot be negative.");
        }
        return amount;
    }

    /** Checks an amount that is already a Money object (used by services). */
    public static Money requirePositive(Money amount, String field) {
        if (amount == null || !amount.isPositive()) {
            throw new ValidationException(field, field + " must be greater than ₹0.");
        }
        requireWithinLimit(amount, field);
        return amount;
    }

    public static Money requireNonNegative(Money amount, String field) {
        if (amount == null || amount.isNegative()) {
            throw new ValidationException(field, field + " cannot be negative.");
        }
        requireWithinLimit(amount, field);
        return amount;
    }

    public static LocalDate requireDate(LocalDate date, String field) {
        if (date == null) {
            throw new ValidationException(field, field + " is required.");
        }
        return date;
    }

    public static <T> T requireSelection(T value, String field) {
        if (value == null) {
            throw new ValidationException(field, "Please select " + field.toLowerCase() + ".");
        }
        return value;
    }

    /** 3-30 characters: letters, digits, underscore or dot. */
    public static String requireUsername(String username) {
        String value = requireText(username, "Username", 30);
        if (!USERNAME.matcher(value).matches()) {
            throw new ValidationException("Username",
                    "Username must be 3-30 characters using only letters, digits, '_' or '.'.");
        }
        return value;
    }

    /** At least 8 characters with at least one letter and one digit. */
    public static void requireStrongPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Password",
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ValidationException("Password", "Password must contain at least one letter and one digit.");
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Money parseAmount(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new ValidationException(field, field + " is required.");
        }
        Money amount;
        try {
            amount = Money.parse(text);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(field, field + ": " + e.getMessage() + ".");
        }
        requireWithinLimit(amount, field);
        return amount;
    }

    private static void requireWithinLimit(Money amount, String field) {
        if (amount.isGreaterThan(MAX_AMOUNT)) {
            throw new ValidationException(field, field + " cannot exceed " + MAX_AMOUNT.format() + ".");
        }
    }
}

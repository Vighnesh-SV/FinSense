package com.finsense.util;

import com.finsense.exception.ValidationException;
import com.finsense.model.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationUtilTest {

    @Test
    void requiredTextIsTrimmedAndChecked() {
        assertEquals("Asha Rao", ValidationUtil.requireText("  Asha Rao ", "Full name", 100));

        ValidationException e = assertThrows(ValidationException.class,
                () -> ValidationUtil.requireText("   ", "Full name", 100));
        assertEquals("Full name", e.getField());
        assertEquals("Full name is required.", e.getMessage());

        assertThrows(ValidationException.class, () -> ValidationUtil.requireText("x".repeat(101), "Full name", 100));
    }

    @Test
    void optionalTextReturnsNullWhenBlank() {
        assertNull(ValidationUtil.optionalText("  ", "Note", 50));
        assertEquals("ok", ValidationUtil.optionalText(" ok ", "Note", 50));
    }

    @Test
    void positiveAmounts() {
        assertEquals(Money.ofRupees(25_000), ValidationUtil.parsePositiveAmount("25,000", "Price"));

        assertThrows(ValidationException.class, () -> ValidationUtil.parsePositiveAmount("0", "Price"));
        assertThrows(ValidationException.class, () -> ValidationUtil.parsePositiveAmount("-10", "Price"));
        assertThrows(ValidationException.class, () -> ValidationUtil.parsePositiveAmount("ten", "Price"));
        assertThrows(ValidationException.class, () -> ValidationUtil.parsePositiveAmount("", "Price"));
    }

    @Test
    void nonNegativeAmountsAllowZeroAndBlank() {
        assertEquals(Money.ZERO, ValidationUtil.parseNonNegativeAmount("", "Savings"));
        assertEquals(Money.ZERO, ValidationUtil.parseNonNegativeAmount("0", "Savings"));
        assertThrows(ValidationException.class, () -> ValidationUtil.parseNonNegativeAmount("-1", "Savings"));
    }

    @Test
    void amountsAboveTheLimitAreRejected() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> ValidationUtil.parsePositiveAmount("2000000000", "Price"));
        assertTrue(e.getMessage().contains("cannot exceed"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"asha", "ravi_k", "priya.s", "user123"})
    void validUsernames(String username) {
        assertEquals(username, ValidationUtil.requireUsername(username));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "has space", "bad!char", ""})
    void invalidUsernames(String username) {
        assertThrows(ValidationException.class, () -> ValidationUtil.requireUsername(username));
    }

    @Test
    void passwordStrength() {
        ValidationUtil.requireStrongPassword("Secret123");     // ok - no exception

        assertThrows(ValidationException.class, () -> ValidationUtil.requireStrongPassword("short1"));
        assertThrows(ValidationException.class, () -> ValidationUtil.requireStrongPassword("onlyletters"));
        assertThrows(ValidationException.class, () -> ValidationUtil.requireStrongPassword("12345678"));
        assertThrows(ValidationException.class, () -> ValidationUtil.requireStrongPassword(null));
    }
}

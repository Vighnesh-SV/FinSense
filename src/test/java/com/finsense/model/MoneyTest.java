package com.finsense.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void storesExactPaiseWithoutFloatingPointErrors() {
        // With double: 0.10 + 0.20 = 0.30000000000000004
        Money sum = Money.parse("0.10").plus(Money.parse("0.20"));
        assertEquals(Money.parse("0.30"), sum);
        assertEquals(30, sum.getPaise());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "25000          | 2500000",
            "25,000         | 2500000",
            "1,25,000.50    | 12500050",
            "₹ 60,000       | 6000000",
            "Rs 499         | 49900",
            "rs.99.9        | 9990",
            "0              | 0"
    })
    void parsesUserInput(String text, long expectedPaise) {
        assertEquals(expectedPaise, Money.parse(text).getPaise());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12.345", "", "   ", "1.2.3"})
    void rejectsInvalidInput(String text) {
        assertThrows(IllegalArgumentException.class, () -> Money.parse(text));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0          | ₹0",
            "99900      | ₹999",
            "100000     | ₹1,000",
            "12500000   | ₹1,25,000",
            "1234567890 | ₹1,23,45,678.90",
            "-500000    | -₹5,000",
            "5          | ₹0.05"
    })
    void formatsWithIndianGrouping(long paise, String expected) {
        assertEquals(expected, Money.ofPaise(paise).format());
    }

    @Test
    void arithmeticReturnsNewObjectsAndKeepsOriginalUnchanged() {
        Money price = Money.ofRupees(25_000);
        Money discounted = price.minus(Money.ofRupees(5_000));

        assertNotSame(price, discounted);
        assertEquals(Money.ofRupees(25_000), price);        // immutability
        assertEquals(Money.ofRupees(20_000), discounted);
        assertEquals(Money.ofRupees(75_000), price.times(3));
        assertEquals(Money.ofRupees(6_250), price.multiply(new BigDecimal("0.25")));
        assertEquals(Money.ofPaise(3333), Money.ofRupees(100).divide(3));
    }

    @Test
    void ratioHandlesZeroDenominator() {
        assertEquals(2.5, Money.ofRupees(25_000).ratioTo(Money.ofRupees(10_000)), 1e-9);
        assertEquals(Double.POSITIVE_INFINITY, Money.ofRupees(1).ratioTo(Money.ZERO));
        assertEquals(0.0, Money.ZERO.ratioTo(Money.ZERO));
    }

    @Test
    void comparisonAndSorting() {
        List<Money> amounts = new ArrayList<>(List.of(Money.ofRupees(500), Money.ofRupees(50), Money.ofRupees(5000)));
        amounts.sort(null);    // uses compareTo (natural ordering)

        assertEquals(List.of(Money.ofRupees(50), Money.ofRupees(500), Money.ofRupees(5000)), amounts);
        assertTrue(Money.ofRupees(2).isGreaterThan(Money.ofRupees(1)));
        assertEquals(Money.ofRupees(9), Money.max(Money.ofRupees(9), Money.ofRupees(3)));
        assertFalse(Money.ZERO.isPositive());
        assertTrue(Money.ofRupees(-1).isNegative());
    }

    @Test
    void plainStringForEditing() {
        assertEquals("25000", Money.ofRupees(25_000).toPlainString());
        assertEquals("25000.50", Money.ofPaise(2_500_050).toPlainString());
    }

    @Test
    void overflowIsDetected() {
        assertThrows(ArithmeticException.class, () -> Money.ofPaise(Long.MAX_VALUE).plus(Money.ofPaise(1)));
    }
}

package com.finsense.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount of Indian Rupees, stored exactly as a whole number of paise.
 *
 * Why not double? Floating point cannot represent most decimal fractions
 * exactly: 0.1 + 0.2 == 0.30000000000000004. For money such rounding errors
 * are unacceptable, so FinSense keeps every amount as a long count of paise
 * (Rs 1 = 100 paise), exactly like the BIGINT columns in the database.
 *
 * Money is an immutable "value object": its state never changes after
 * construction, and every operation (plus, minus ...) returns a NEW object.
 * Immutable objects are safe to share and can never be modified by accident.
 *
 * Implements Comparable so amounts can be sorted and compared with compareTo().
 */
public final class Money implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    private static final String RUPEE = "₹";   // the ₹ symbol

    private final long paise;

    private Money(long paise) {
        this.paise = paise;
    }

    // ---------------------------------------------------------------- factories

    public static Money ofPaise(long paise) {
        return paise == 0 ? ZERO : new Money(paise);
    }

    public static Money ofRupees(long rupees) {
        return ofPaise(Math.multiplyExact(rupees, 100L));
    }

    /** Converts a rupee amount such as 1250.50; more than 2 decimal places is rejected. */
    public static Money of(BigDecimal rupees) {
        Objects.requireNonNull(rupees, "rupees");
        try {
            return ofPaise(rupees.movePointRight(2).longValueExact());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("An amount can have at most 2 decimal places: " + rupees.toPlainString());
        }
    }

    /**
     * Parses text typed by a user: "25000", "25,000", "₹ 1,25,000.50", "Rs 499".
     * @throws IllegalArgumentException if the text is not a valid amount
     */
    public static Money parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Amount is empty");
        }
        String cleaned = text.trim()
                .replace(RUPEE, "")
                .replaceFirst("(?i)^rs\\.?", "")
                .replace(",", "")
                .replace(" ", "");
        try {
            return of(new BigDecimal(cleaned));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text.trim() + "' is not a valid amount");
        }
    }

    // ---------------------------------------------------------------- arithmetic
    // Math.addExact etc. throw ArithmeticException on overflow instead of silently wrapping around.

    public Money plus(Money other) {
        return ofPaise(Math.addExact(paise, other.paise));
    }

    public Money minus(Money other) {
        return ofPaise(Math.subtractExact(paise, other.paise));
    }

    public Money times(long factor) {
        return ofPaise(Math.multiplyExact(paise, factor));
    }

    /** Multiplies by a decimal factor (e.g. 0.25 for 25 %), rounding to the nearest paisa. */
    public Money multiply(BigDecimal factor) {
        BigDecimal result = BigDecimal.valueOf(paise).multiply(factor).setScale(0, RoundingMode.HALF_UP);
        return ofPaise(result.longValueExact());
    }

    /** Divides into equal parts (e.g. a yearly amount / 12), rounding to the nearest paisa. */
    public Money divide(long parts) {
        if (parts == 0) {
            throw new ArithmeticException("Cannot divide money into 0 parts");
        }
        BigDecimal result = BigDecimal.valueOf(paise).divide(BigDecimal.valueOf(parts), 0, RoundingMode.HALF_UP);
        return ofPaise(result.longValueExact());
    }

    public Money negate() {
        return ofPaise(Math.negateExact(paise));
    }

    /**
     * How many times larger this amount is than another, e.g. price.ratioTo(income).
     * A ratio is not money, so double is fine here. Dividing by zero returns
     * +Infinity for a positive amount (or 0 when both are zero), so rules that
     * compare the ratio against a threshold still behave sensibly.
     */
    public double ratioTo(Money other) {
        if (other.paise == 0) {
            return paise == 0 ? 0.0 : Double.POSITIVE_INFINITY;
        }
        return (double) paise / other.paise;
    }

    public static Money max(Money a, Money b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static Money min(Money a, Money b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    // ---------------------------------------------------------------- queries

    public long getPaise() {
        return paise;
    }

    public boolean isZero() {
        return paise == 0;
    }

    public boolean isPositive() {
        return paise > 0;
    }

    public boolean isNegative() {
        return paise < 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(paise, 2);
    }

    // ---------------------------------------------------------------- formatting

    /** Plain number for editable text fields: "25000" or "25000.50" (no symbol, no commas). */
    public String toPlainString() {
        return paise % 100 == 0 ? Long.toString(paise / 100) : toBigDecimal().toPlainString();
    }

    /**
     * Indian-style formatting: ₹1,25,000 or ₹1,23,45,678.90 (lakh / crore grouping).
     * Java's DecimalFormat only supports a fixed group size, so the grouping
     * (last 3 digits, then groups of 2) is done by hand.
     */
    public String format() {
        long abs = Math.absExact(paise);
        String digits = Long.toString(abs / 100);
        int fraction = (int) (abs % 100);

        String grouped;
        if (digits.length() <= 3) {
            grouped = digits;
        } else {
            String lastThree = digits.substring(digits.length() - 3);
            String rest = digits.substring(0, digits.length() - 3);
            StringBuilder sb = new StringBuilder();
            int end = rest.length();
            while (end > 2) {
                sb.insert(0, "," + rest.substring(end - 2, end));
                end -= 2;
            }
            sb.insert(0, rest.substring(0, end));
            grouped = sb + "," + lastThree;
        }

        String text = RUPEE + grouped + (fraction != 0 ? String.format(".%02d", fraction) : "");
        return paise < 0 ? "-" + text : text;
    }

    @Override
    public String toString() {
        return format();
    }

    // ---------------------------------------------------------------- equality

    @Override
    public int compareTo(Money other) {
        return Long.compare(paise, other.paise);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Money m && m.paise == paise);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(paise);
    }
}

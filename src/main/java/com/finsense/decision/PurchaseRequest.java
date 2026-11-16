package com.finsense.decision;

import com.finsense.model.Category;
import com.finsense.model.Money;
import com.finsense.model.enums.Necessity;

/**
 * Validated purchase inputs handed to each polymorphic decision rule.
 *
 * {@code instalments} is 1 for an upfront payment. Rules that judge month-to-month
 * cash flow (affordability, emergency reserve, hard stop) use {@link #monthlyPayment()};
 * rules about total cost (savings impact, goal delay) use {@link #price()}.
 */
public record PurchaseRequest(String productName, Category category, Money price, Necessity necessity,
                              String description, int instalments) {

    /** Compact constructor: validation that runs for every new request. */
    public PurchaseRequest {
        if (instalments < 1) {
            throw new IllegalArgumentException("Instalments must be at least 1.");
        }
    }

    /** Upfront purchase (one payment). */
    public PurchaseRequest(String productName, Category category, Money price, Necessity necessity,
                           String description) {
        this(productName, category, price, necessity, description, 1);
    }

    /** Amount due in the first month: the whole price, or one instalment. */
    public Money monthlyPayment() {
        return instalments == 1 ? price : price.divide(instalments);
    }
}

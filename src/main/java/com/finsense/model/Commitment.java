package com.finsense.model;

import com.finsense.model.enums.CommitmentType;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A recurring financial obligation (e.g. Rent, Car Loan EMI, Term Insurance).
 *
 * Encapsulation: Fields are private and accessed via getters. The end date
 * may be null indicating an ongoing / open-ended obligation.
 */
public class Commitment {

    private final Long id;
    private final long userId;
    private final String name;
    private final CommitmentType type;
    private final Money monthlyAmount;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public Commitment(Long id,
                      long userId,
                      String name,
                      CommitmentType type,
                      Money monthlyAmount,
                      LocalDate startDate,
                      LocalDate endDate) {
        this.id = id;
        this.userId = userId;
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.monthlyAmount = Objects.requireNonNull(monthlyAmount, "monthlyAmount");
        this.startDate = Objects.requireNonNull(startDate, "startDate");
        this.endDate = endDate;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public CommitmentType getType() {
        return type;
    }

    public Money getMonthlyAmount() {
        return monthlyAmount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    /**
     * Checks if this commitment is active on the specified date.
     * Active when startDate <= date and (endDate == null or date <= endDate).
     */
    public boolean isActiveOn(LocalDate date) {
        if (date == null) {
            return false;
        }
        boolean started = !date.isBefore(startDate);
        boolean notEnded = (endDate == null) || !date.isAfter(endDate);
        return started && notEnded;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Commitment that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Commitment[" + id + ", " + name + ", " + monthlyAmount + "]";
    }
}


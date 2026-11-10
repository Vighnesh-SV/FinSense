package com.finsense.service;

import com.finsense.model.Commitment;
import com.finsense.model.FinancialProfile;
import com.finsense.model.FinancialSummary;
import com.finsense.model.Money;
import com.finsense.model.enums.CommitmentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure unit tests for FinancialService.summarize(...) arithmetic without database or session.
 */
class FinancialSummaryTest {

    @Test
    void standardCalculation() {
        // Income 50,000, fixed 15,000, variable 10,000, active EMI of 5,000, target 8,000
        // -> disposable 12,000, reserve 90,000, ratio 0.10
        FinancialProfile profile = new FinancialProfile(
                1L,
                Money.ofRupees(50_000),
                Money.ofRupees(15_000),
                Money.ofRupees(10_000),
                Money.ofRupees(100_000),
                Money.ofRupees(8_000),
                null
        );

        LocalDate today = LocalDate.of(2026, 6, 15);
        Commitment emi = new Commitment(
                10L, 1L, "Car EMI", CommitmentType.EMI,
                Money.ofRupees(5_000),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );

        FinancialSummary summary = FinancialService.summarize(profile, List.of(emi), today);

        assertEquals(Money.ofRupees(50_000), summary.income());
        assertEquals(Money.ofRupees(25_000), summary.totalExpenses());
        assertEquals(Money.ofRupees(5_000), summary.activeCommitments());
        assertEquals(Money.ofRupees(8_000), summary.savingsTarget());
        assertEquals(Money.ofRupees(12_000), summary.disposableIncome());
        assertEquals(Money.ofRupees(90_000), summary.emergencyReserve());
        assertEquals(0.10, summary.commitmentRatio(), 0.001);
    }

    @Test
    void commitmentEndedLastMonthIsNotCounted() {
        FinancialProfile profile = new FinancialProfile(
                1L, Money.ofRupees(50_000), Money.ofRupees(10_000), Money.ofRupees(10_000),
                Money.ZERO, Money.ZERO, null
        );

        LocalDate today = LocalDate.of(2026, 6, 15);
        Commitment ended = new Commitment(
                1L, 1L, "Past Loan", CommitmentType.LOAN,
                Money.ofRupees(5_000),
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 5, 31)
        );

        FinancialSummary summary = FinancialService.summarize(profile, List.of(ended), today);

        assertEquals(Money.ZERO, summary.activeCommitments());
        assertEquals(Money.ofRupees(30_000), summary.disposableIncome());
        assertEquals(0.0, summary.commitmentRatio(), 0.001);
    }

    @Test
    void commitmentStartingNextMonthIsNotCounted() {
        FinancialProfile profile = new FinancialProfile(
                1L, Money.ofRupees(50_000), Money.ofRupees(10_000), Money.ofRupees(10_000),
                Money.ZERO, Money.ZERO, null
        );

        LocalDate today = LocalDate.of(2026, 6, 15);
        Commitment future = new Commitment(
                1L, 1L, "Future Rent", CommitmentType.RENT,
                Money.ofRupees(10_000),
                LocalDate.of(2026, 7, 1),
                null
        );

        FinancialSummary summary = FinancialService.summarize(profile, List.of(future), today);

        assertEquals(Money.ZERO, summary.activeCommitments());
        assertEquals(Money.ofRupees(30_000), summary.disposableIncome());
    }

    @Test
    void openEndedCommitmentIsCounted() {
        FinancialProfile profile = new FinancialProfile(
                1L, Money.ofRupees(40_000), Money.ofRupees(10_000), Money.ofRupees(5_000),
                Money.ZERO, Money.ZERO, null
        );

        LocalDate today = LocalDate.of(2026, 6, 15);
        Commitment ongoing = new Commitment(
                1L, 1L, "Rent", CommitmentType.RENT,
                Money.ofRupees(12_000),
                LocalDate.of(2025, 1, 1),
                null
        );

        FinancialSummary summary = FinancialService.summarize(profile, List.of(ongoing), today);

        assertEquals(Money.ofRupees(12_000), summary.activeCommitments());
        assertEquals(Money.ofRupees(13_000), summary.disposableIncome());
    }

    @Test
    void negativeDisposableIncomeNotClamped() {
        // Income 20,000, expenses 18,000, EMI 5,000, target 0 -> -3,000
        FinancialProfile profile = new FinancialProfile(
                1L, Money.ofRupees(20_000), Money.ofRupees(10_000), Money.ofRupees(8_000),
                Money.ZERO, Money.ZERO, null
        );

        LocalDate today = LocalDate.of(2026, 6, 15);
        Commitment emi = new Commitment(
                1L, 1L, "Bike Loan", CommitmentType.LOAN,
                Money.ofRupees(5_000),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );

        FinancialSummary summary = FinancialService.summarize(profile, List.of(emi), today);

        assertEquals(Money.ofRupees(-3_000), summary.disposableIncome());
    }

    @Test
    void zeroCommitmentsGivesZeroRatio() {
        FinancialProfile profile = new FinancialProfile(
                1L, Money.ofRupees(50_000), Money.ofRupees(15_000), Money.ofRupees(10_000),
                Money.ZERO, Money.ofRupees(5_000), null
        );

        FinancialSummary summary = FinancialService.summarize(profile, Collections.emptyList(), LocalDate.now());

        assertEquals(0.0, summary.commitmentRatio(), 0.001);
        assertEquals(Money.ZERO, summary.activeCommitments());
        assertEquals(Money.ofRupees(20_000), summary.disposableIncome());
        assertEquals(Money.ofRupees(75_000), summary.emergencyReserve());
    }

    @Test
    void boundaryDateEndingOnDateIsActive() {
        FinancialProfile profile = new FinancialProfile(
                1L, Money.ofRupees(50_000), Money.ofRupees(10_000), Money.ofRupees(10_000),
                Money.ZERO, Money.ZERO, null
        );

        LocalDate today = LocalDate.of(2026, 6, 30);
        Commitment endingToday = new Commitment(
                1L, 1L, "Last Day EMI", CommitmentType.EMI,
                Money.ofRupees(4_000),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30) // ends exactly on 'today'
        );

        FinancialSummary summary = FinancialService.summarize(profile, List.of(endingToday), today);

        assertEquals(Money.ofRupees(4_000), summary.activeCommitments());
    }
}


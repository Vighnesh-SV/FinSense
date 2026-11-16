package com.finsense.decision;

import com.finsense.exception.IncompleteProfileException;
import com.finsense.exception.ValidationException;
import com.finsense.model.Money;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The pure decision engine: no database, no JavaFX - only rules and arithmetic.
 *
 * 1. Every rule (Strategy pattern, called polymorphically) returns points and an optional cap.
 * 2. Score = 100 + all points, clamped to 0..100, then mapped to a band (BUY/CONSIDER/WAIT/AVOID).
 * 3. The most restrictive cap wins over the band; a hard stop forces AVOID.
 * 4. For WAIT/AVOID it works out the first month the purchase becomes safe ("smart timing").
 */
public final class DecisionEngine {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final List<DecisionRule> rules;

    public DecisionEngine(List<DecisionRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public DecisionResult evaluate(PurchaseRequest request, FinancialSnapshot snapshot) {
        if (request == null || request.price() == null
                || request.price().getPaise() < DecisionThresholds.NO_PROFILE_PRICE_MIN) {
            throw new ValidationException("Price must be greater than ₹0.");
        }
        if (snapshot == null) {
            throw new IncompleteProfileException("Please complete your financial profile first.");
        }

        List<RuleResult> results = rules.stream().map(rule -> rule.evaluate(request, snapshot)).toList();

        int score = DecisionThresholds.START_SCORE + results.stream().mapToInt(RuleResult::points).sum();
        score = Math.max(DecisionThresholds.SCORE_MIN, Math.min(DecisionThresholds.SCORE_MAX, score));

        Recommendation band = band(score);
        Recommendation verdict = band;
        for (RuleResult result : results) {
            Optional<Recommendation> cap = result.cap();
            if (cap.isPresent() && cap.get().ordinal() > verdict.ordinal()) {
                verdict = cap.get();                 // enum order = restrictiveness order
            }
        }

        Money affordable = snapshot.freeSavings().plus(Money.max(snapshot.disposableIncome(), Money.ZERO));
        boolean hardStop = request.monthlyPayment().isGreaterThan(affordable);
        if (hardStop) {
            verdict = Recommendation.AVOID;
        }

        YearMonth affordableFrom = null;
        if (verdict == Recommendation.WAIT || verdict == Recommendation.AVOID) {
            affordableFrom = affordableFrom(request, snapshot);
        }

        List<String> explanation = explanation(request, results, score, band, verdict, hardStop, affordableFrom);
        List<Reason> reasons = results.stream()
                .filter(r -> !r.message().isBlank())
                .sorted(Comparator.comparing(RuleResult::severity).reversed())
                .map(r -> new Reason(r.severity(), r.message()))
                .toList();
        return new DecisionResult(verdict, score, explanation, warnings(snapshot), snapshot,
                LocalDateTime.now(), affordableFrom, reasons);
    }

    private static List<String> explanation(PurchaseRequest request, List<RuleResult> results, int score,
                                            Recommendation band, Recommendation verdict, boolean hardStop,
                                            YearMonth affordableFrom) {
        List<String> lines = new ArrayList<>();
        lines.add("FinSense suggests " + verdict + " for " + request.productName() + " (score " + score + "/100).");

        // Most serious reasons first.
        results.stream()
                .filter(r -> !r.message().isBlank())
                .sorted(Comparator.comparing(RuleResult::severity).reversed())
                .map(RuleResult::message)
                .forEach(lines::add);

        if (verdict != band && !hardStop) {
            String reason = results.stream()
                    .filter(r -> r.cap().filter(c -> c == verdict).isPresent())
                    .map(RuleResult::message)
                    .findFirst()
                    .orElse("a safety rule");
            lines.add("Score suggested " + band + ", limited to " + verdict + " because " + reason);
        }
        if (hardStop) {
            lines.add((request.instalments() > 1 ? "Hard stop: one instalment" : "Hard stop: the price")
                    + " exceeds free savings plus non-negative disposable income.");
        }
        if (affordableFrom != null) {
            lines.add("Smart timing: at your current saving rate this fits safely from "
                    + affordableFrom.format(MONTH) + ".");
        }
        lines.add("This is general guidance, not financial advice.");
        return lines;
    }

    private static List<String> warnings(FinancialSnapshot snapshot) {
        List<String> warnings = new ArrayList<>();
        if (snapshot.fallbackDataUsed()) {
            warnings.add("Spending history is limited; declared monthly expenses were used.");
        }
        if (snapshot.activeGoals().isEmpty()) {
            warnings.add("No active savings goals are set.");
        }
        return warnings;
    }

    /**
     * Smart timing: the first future month in which free savings (growing by the monthly saving
     * capacity) cover the price and still leave the emergency reserve untouched.
     */
    static YearMonth affordableFrom(PurchaseRequest request, FinancialSnapshot snapshot) {
        Money capacity = snapshot.monthlySavingCapacity();
        if (!capacity.isPositive()) {
            return null;
        }
        Money needed = request.price().plus(snapshot.emergencyReserve()).minus(snapshot.freeSavings());
        long months = Math.max(1, SavingsPlan.monthsToSave(needed, capacity));
        if (months > DecisionThresholds.AFFORDABLE_FROM_HORIZON_MONTHS) {
            return null;
        }
        return YearMonth.from(snapshot.asOf()).plusMonths(months);
    }

    private static Recommendation band(int score) {
        if (score >= DecisionThresholds.BUY_MIN_SCORE) {
            return Recommendation.BUY;
        }
        if (score >= DecisionThresholds.CONSIDER_MIN_SCORE) {
            return Recommendation.CONSIDER;
        }
        if (score >= DecisionThresholds.WAIT_MIN_SCORE) {
            return Recommendation.WAIT;
        }
        return Recommendation.AVOID;
    }
}

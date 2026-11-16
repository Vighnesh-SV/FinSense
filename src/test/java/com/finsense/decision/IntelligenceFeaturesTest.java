package com.finsense.decision;

import com.finsense.decision.rules.BudgetRule;
import com.finsense.decision.rules.EmergencyReserveRule;
import com.finsense.decision.rules.GoalImpactRule;
import com.finsense.decision.rules.LearnedPreferenceRule;
import com.finsense.decision.rules.SpendingTrendRule;
import com.finsense.model.BudgetStatus;
import com.finsense.model.Category;
import com.finsense.model.FeedbackStats;
import com.finsense.model.Money;
import com.finsense.model.enums.BehaviourType;
import com.finsense.model.enums.GoalPriority;
import com.finsense.model.enums.Necessity;
import com.finsense.model.enums.TransactionType;
import com.finsense.service.AnomalyService;
import com.finsense.service.ForecastService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the learning, forecasting, anomaly, budget and smart-timing features. */
class IntelligenceFeaturesTest {

    private static final Category GADGETS =
            new Category(7L, null, "Electronics", TransactionType.EXPENSE, "DISCRETIONARY", false);
    private static final LocalDate MID_MONTH = LocalDate.of(2026, 3, 15);

    private static PurchaseRequest want(long rupees) {
        return new PurchaseRequest("Item", GADGETS, Money.ofRupees(rupees), Necessity.WANT, null);
    }

    private static PurchaseRequest need(long rupees) {
        return new PurchaseRequest("Item", GADGETS, Money.ofRupees(rupees), Necessity.NEED, null);
    }

    /** Healthy finances with optional extras for the feature under test. */
    private static FinancialSnapshot snapshot(Money free, Money reserve, List<GoalSnapshot> goals,
                                              Money thisMonth, Money forecast,
                                              Map<Long, BudgetStatus> budgets,
                                              Map<Long, FeedbackStats> byCategory) {
        return new FinancialSnapshot(Money.ofRupees(100000), Money.ofRupees(30000), Money.ofRupees(5000),
                Money.ofRupees(10000), Money.ofRupees(55000), reserve, free, goals, BehaviourType.SMART_SHOPPER,
                thisMonth, forecast, false, MID_MONTH, forecast, budgets, byCategory, Map.of());
    }

    private static FinancialSnapshot plain() {
        return snapshot(Money.ofRupees(300000), Money.ofRupees(100000), List.of(),
                Money.ofRupees(15000), Money.ofRupees(30000), Map.of(), Map.of());
    }

    // ---------------------------------------------------------------- learning

    @Test
    void regrettedCategoryLowersScoreAndSatisfiedCategoryRaisesIt() {
        var regret = snapshot(Money.ofRupees(300000), Money.ofRupees(100000), List.of(), Money.ofRupees(15000),
                Money.ofRupees(30000), Map.of(), Map.of(7L, new FeedbackStats("Electronics purchases", 0, 0, 3)));
        var happy = snapshot(Money.ofRupees(300000), Money.ofRupees(100000), List.of(), Money.ofRupees(15000),
                Money.ofRupees(30000), Map.of(), Map.of(7L, new FeedbackStats("Electronics purchases", 3, 0, 0)));
        var rule = new LearnedPreferenceRule();
        assertEquals(-DecisionThresholds.FEEDBACK_MAX_POINTS, rule.evaluate(want(1000), regret).points());
        assertEquals(DecisionThresholds.FEEDBACK_MAX_POINTS, rule.evaluate(want(1000), happy).points());
        assertEquals(0, rule.evaluate(want(1000), plain()).points());
    }

    // ---------------------------------------------------------------- forecasting

    @Test
    void linearForecastFollowsARisingTrendAndStaysNonNegative() {
        assertEquals(500, ForecastService.linearForecast(new long[]{100, 200, 300, 400}));
        assertEquals(0, ForecastService.linearForecast(new long[]{300, 200, 100, 0}));
        assertEquals(250, ForecastService.linearForecast(new long[]{250, 250, 250}));
    }

    // ---------------------------------------------------------------- anomalies

    @Test
    void anomalyNeedsAClearOutlierAndEnoughHistory() {
        long[] usual = {1000, 1200, 900, 1100, 1000};
        assertTrue(AnomalyService.isAnomalous(5000, usual));
        assertFalse(AnomalyService.isAnomalous(1300, usual));
        assertFalse(AnomalyService.isAnomalous(5000, new long[]{1000, 1100}));    // too little history
    }

    // ---------------------------------------------------------------- budgets

    @Test
    void budgetRuleWarnsNearLimitAndCapsFarOverIt() {
        var status = new BudgetStatus(1L, GADGETS, Money.ofRupees(10000), Money.ofRupees(7000));
        var budgets = Map.of(7L, status);
        var s = snapshot(Money.ofRupees(300000), Money.ofRupees(100000), List.of(), Money.ofRupees(15000),
                Money.ofRupees(30000), budgets, Map.of());
        var rule = new BudgetRule();
        assertEquals(DecisionThresholds.BUDGET_NEAR_PENALTY, rule.evaluate(want(1500), s).points());
        assertEquals(DecisionThresholds.BUDGET_OVER_PENALTY, rule.evaluate(want(4000), s).points());
        assertEquals(Recommendation.WAIT, rule.evaluate(want(9000), s).cap().orElseThrow());
    }

    // ---------------------------------------------------------------- fixed rules

    @Test
    void smallNeedIsNotBlockedByAShortEmergencyReserve() {
        var shortReserve = snapshot(Money.ofRupees(50000), Money.ofRupees(60000), List.of(), Money.ofRupees(15000),
                Money.ofRupees(30000), Map.of(), Map.of());
        var rule = new EmergencyReserveRule();
        assertTrue(rule.evaluate(need(2000), shortReserve).cap().isEmpty());       // medicine-sized need
        assertEquals(Recommendation.WAIT, rule.evaluate(want(2000), shortReserve).cap().orElseThrow());
    }

    @Test
    void goalDelayIsCountedOnceNotOncePerGoal() {
        var goal = new GoalSnapshot(Money.ofRupees(50000), LocalDate.of(2030, 1, 1), GoalPriority.LOW);
        var oneGoal = snapshot(Money.ofRupees(300000), Money.ofRupees(100000), List.of(goal),
                Money.ofRupees(15000), Money.ofRupees(30000), Map.of(), Map.of());
        var threeGoals = snapshot(Money.ofRupees(300000), Money.ofRupees(100000), List.of(goal, goal, goal),
                Money.ofRupees(15000), Money.ofRupees(30000), Map.of(), Map.of());
        var rule = new GoalImpactRule();
        assertEquals(rule.evaluate(want(10000), oneGoal).points(), rule.evaluate(want(10000), threeGoals).points());
    }

    @Test
    void spendingTrendComparesWithTheShareOfTheMonthElapsed() {
        // Day 15 of 31: about ₹14.5k of a ₹30k forecast "should" be spent; ₹25k is far ahead of pace.
        var fast = snapshot(Money.ofRupees(300000), Money.ofRupees(100000), List.of(), Money.ofRupees(25000),
                Money.ofRupees(30000), Map.of(), Map.of());
        var rule = new SpendingTrendRule();
        assertEquals(DecisionThresholds.SPENDING_TREND_PENALTY, rule.evaluate(want(100), fast).points());
        assertEquals(0, rule.evaluate(want(100), plain()).points());
    }

    // ---------------------------------------------------------------- smart timing

    @Test
    void waitVerdictSuggestsTheMonthItBecomesSafe() {
        // ₹50k free, ₹40k reserve, saving ₹10k/month: a ₹30k purchase fits after 2 months.
        var tight = snapshot(Money.ofRupees(50000), Money.ofRupees(40000), List.of(), Money.ofRupees(15000),
                Money.ofRupees(30000), Map.of(), Map.of());
        YearMonth when = DecisionEngine.affordableFrom(want(30000), tight);
        assertNotNull(when);
        assertEquals(YearMonth.from(MID_MONTH).plusMonths(2), when);

        DecisionResult result = DecisionEngineFactory.create().evaluate(want(30000), tight);
        assertTrue(result.recommendation().ordinal() >= Recommendation.WAIT.ordinal());
        assertEquals(when, result.affordableFrom());
        assertTrue(result.explanationLines().stream().anyMatch(l -> l.startsWith("Smart timing")));
    }
}

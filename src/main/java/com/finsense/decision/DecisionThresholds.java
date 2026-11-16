package com.finsense.decision;

/**
 * Every tunable number of the decision engine in one place ("no magic numbers").
 * Penalties are negative points subtracted from a starting score of 100.
 */
public final class DecisionThresholds {

    private DecisionThresholds() {
    }

    // ---- Score and verdict bands ----
    public static final int START_SCORE = 100;
    public static final int SCORE_MIN = 0;
    public static final int SCORE_MAX = 100;
    public static final int BUY_MIN_SCORE = 75;
    public static final int CONSIDER_MIN_SCORE = 55;
    public static final int WAIT_MIN_SCORE = 35;
    public static final int NO_PROFILE_PRICE_MIN = 1;          // paise

    // ---- Affordability: monthly payment / disposable income ----
    public static final double AFFORDABLE_RATIO = 0.25;
    public static final double MODERATE_RATIO = 0.50;
    public static final double FULL_DISPOSABLE_RATIO = 1.0;
    public static final double HIGH_DISPOSABLE_RATIO = 3.0;
    public static final int AFFORDABLE_PENALTY = 0;
    public static final int MODERATE_PENALTY = -10;
    public static final int FULL_PENALTY = -20;
    public static final int HIGH_PENALTY = -35;
    public static final int UNAFFORDABLE_PENALTY = -50;

    // ---- Savings impact: price / free savings ----
    public static final double SAVINGS_SMALL_RATIO = 0.10;
    public static final double SAVINGS_MID_RATIO = 0.25;
    public static final double SAVINGS_LARGE_RATIO = 0.50;
    public static final int SAVINGS_MID_PENALTY = -10;
    public static final int SAVINGS_LARGE_PENALTY = -20;
    public static final int SAVINGS_HIGH_PENALTY = -30;

    // ---- Emergency reserve ----
    public static final int EMERGENCY_RESERVE_MONTHS = 3;
    /** A NEED costing at most this share of monthly income is not blocked by a short reserve. */
    public static final double SMALL_NEED_INCOME_RATIO = 0.05;
    public static final int SMALL_NEED_RESERVE_PENALTY = -10;

    // ---- Goals ----
    public static final int GOAL_DELAY_PENALTY_PER_MONTH = -5;
    public static final int GOAL_PENALTY_CAP = -20;

    // ---- Commitments: monthly commitments / income ----
    public static final int COMMITMENT_CAUTION_PERCENT = 40;
    public static final int COMMITMENT_WAIT_PERCENT = 60;
    public static final double COMMITMENT_CAUTION_RATIO = COMMITMENT_CAUTION_PERCENT / 100.0;
    public static final double COMMITMENT_WAIT_RATIO = COMMITMENT_WAIT_PERCENT / 100.0;
    public static final int COMMITMENT_PENALTY = -10;

    // ---- Spending trend: this month's pace vs the forecast ----
    public static final int SPENDING_TREND_ABOVE_PERCENT = 20;
    public static final double SPENDING_TREND_RATIO = 1.0 + SPENDING_TREND_ABOVE_PERCENT / 100.0;
    /** Pace is not judged in the first days of a month - too little data. */
    public static final int SPENDING_TREND_MIN_DAY = 7;
    public static final int SPENDING_TREND_PENALTY = -10;

    // ---- Necessity and behaviour ----
    public static final int NEED_BONUS = 10;
    public static final int WANT_PENALTY = -5;
    public static final int IMPULSE_WANT_PENALTY = -15;
    public static final int HEAVY_DISCRETIONARY_PENALTY = -10;
    public static final int SMART_SHOPPER_BONUS = 5;
    public static final int IMPULSE_WAIT_HOURS = 48;

    // ---- Budgets ----
    public static final double BUDGET_WARNING_RATIO = 0.80;
    public static final double BUDGET_HARD_RATIO = 1.50;
    public static final int BUDGET_NEAR_PENALTY = -5;
    public static final int BUDGET_OVER_PENALTY = -15;

    // ---- Learning from feedback ----
    /** Ratings needed before the same category is trusted; otherwise NEED/WANT ratings are used. */
    public static final int FEEDBACK_MIN_CATEGORY = 2;
    public static final int FEEDBACK_MIN_NECESSITY = 3;
    /** Points at full satisfaction (+) or full regret (-). */
    public static final int FEEDBACK_MAX_POINTS = 15;

    // ---- Data windows ----
    public static final int SPENDING_AVERAGE_MONTHS = 3;
    public static final int AFFORDABLE_FROM_HORIZON_MONTHS = 24;
}

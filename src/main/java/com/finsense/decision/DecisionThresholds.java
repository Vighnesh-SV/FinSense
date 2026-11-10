package com.finsense.decision;

/** Central named thresholds for scoring and verdict bands. */
public final class DecisionThresholds {
    private DecisionThresholds() { }
    public static final int START_SCORE=100, SCORE_MIN=0, SCORE_MAX=100;
    public static final double AFFORDABLE_RATIO=0.25, MODERATE_RATIO=0.50, FULL_DISPOSABLE_RATIO=1.0, HIGH_DISPOSABLE_RATIO=3.0;
    public static final int AFFORDABLE_PENALTY=0, MODERATE_PENALTY=-10, FULL_PENALTY=-20, HIGH_PENALTY=-35, UNAFFORDABLE_PENALTY=-50;
    public static final double SAVINGS_SMALL_RATIO=0.10, SAVINGS_MID_RATIO=0.25, SAVINGS_LARGE_RATIO=0.50;
    public static final int SAVINGS_MID_PENALTY=-10, SAVINGS_LARGE_PENALTY=-20, SAVINGS_HIGH_PENALTY=-30;
    public static final int EMERGENCY_RESERVE_MONTHS=3;
    public static final int GOAL_DELAY_PENALTY_PER_MONTH=-5, GOAL_PENALTY_CAP=-20;
    public static final int COMMITMENT_CAUTION_PERCENT=40, COMMITMENT_WAIT_PERCENT=60;
    public static final double COMMITMENT_CAUTION_RATIO=COMMITMENT_CAUTION_PERCENT/100.0, COMMITMENT_WAIT_RATIO=COMMITMENT_WAIT_PERCENT/100.0;
    public static final int COMMITMENT_PENALTY=-10;
    public static final double SPENDING_TREND_RATIO=1.20;
    public static final int SPENDING_TREND_PERCENT=120, SPENDING_TREND_ABOVE_PERCENT=20;
    public static final int SPENDING_TREND_PENALTY=-10;
    public static final int NEED_BONUS=10, WANT_PENALTY=-5, IMPULSE_WANT_PENALTY=-15, HEAVY_DISCRETIONARY_PENALTY=-10, SMART_SHOPPER_BONUS=5;
    public static final int BUY_MIN_SCORE=75, CONSIDER_MIN_SCORE=55, WAIT_MIN_SCORE=35;
    public static final int NO_PROFILE_PRICE_MIN=1, IMPULSE_WAIT_HOURS=48;
    public static final int DECLARED_PROFILE_FALLBACK_MONTHS=1, SPENDING_AVERAGE_MONTHS=3;
}

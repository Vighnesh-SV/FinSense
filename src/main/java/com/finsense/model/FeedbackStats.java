package com.finsense.model;

/**
 * How the user rated past purchases that resemble a new one - the input for
 * personalised (learned) advice. {@code scope} says what "similar" meant:
 * the same category, or the same NEED/WANT type when the category has too few ratings.
 */
public record FeedbackStats(String scope, int worthIt, int neutral, int regret) {

    public static final FeedbackStats NONE = new FeedbackStats("", 0, 0, 0);

    public int total() {
        return worthIt + neutral + regret;
    }

    /** +1.0 when every similar purchase was worth it, -1.0 when every one was regretted. */
    public double satisfaction() {
        return total() == 0 ? 0.0 : (double) (worthIt - regret) / total();
    }
}

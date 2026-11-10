package com.finsense.controller;

/**
 * Every screen reachable from the sidebar.
 *
 * An enum is ideal for a fixed set of values: each constant carries its own
 * data (title, FXML file, description), the compiler rejects unknown screens,
 * and switch/EnumMap can be used on it.
 */
public enum Screen {

    DASHBOARD("Dashboard", "/fxml/dashboard.fxml",
            "Your monthly income, expenses, savings, goal progress and latest insights at a glance."),
    TRANSACTIONS("Transactions", "/fxml/transactions.fxml",
            "Record income and expenses, filter your history and see monthly summaries."),
    SAVINGS_GOALS("Savings Goals", "/fxml/savings.fxml",
            "Create goals with targets and deadlines, add contributions and track progress."),
    PURCHASE_ADVISOR("Purchase Advisor", "/fxml/purchase.fxml",
            "Ask FinSense whether to BUY, CONSIDER, WAIT or AVOID a purchase - with reasons."),
    PURCHASE_HISTORY("Purchase History", "/fxml/purchase_history.fxml",
            "Review and compare your previous purchase evaluations."),
    SIMULATOR("What-if Simulator", "/fxml/simulator.fxml",
            "See how a hypothetical purchase would affect your savings and goals - nothing is saved."),
    INSIGHTS("Financial Insights", "/fxml/insights.fxml",
            "Spending patterns, top categories, month-to-month comparisons and personalised tips."),
    PROFILE("My Profile", "/fxml/profile.fxml",
            "Your income, expenses, savings, commitments and behaviour profile.");

    private final String title;
    private final String fxml;
    private final String description;

    Screen(String title, String fxml, String description) {
        this.title = title;
        this.fxml = fxml;
        this.description = description;
    }

    public String title() {
        return title;
    }

    public String fxml() {
        return fxml;
    }

    public String description() {
        return description;
    }
}

package com.finsense.controller;

/**
 * Every screen reachable from the sidebar.
 *
 * An enum is ideal for a fixed set of values: each constant carries its own data
 * (title, FXML file, subtitle shown in the header), the compiler rejects unknown
 * screens, and it can be used as an EnumMap key.
 */
public enum Screen {

    DASHBOARD("Dashboard", "/fxml/dashboard.fxml",
            "This month at a glance - money in, money out, goals and anything unusual."),
    INSIGHTS("Insights & forecast", "/fxml/insights.fxml",
            "Where your money goes, what next month probably looks like, and a printable report."),
    PURCHASE_ADVISOR("Purchase advisor", "/fxml/purchase.fxml",
            "Describe something you want to buy and FinSense explains whether now is a good time."),
    SIMULATOR("What-if simulator", "/fxml/simulator.fxml",
            "Try a purchase or an instalment plan and see twelve months ahead. Nothing is saved."),
    PURCHASE_HISTORY("Purchase history", "/fxml/purchase_history.fxml",
            "Past decisions, how they changed over time, and how you felt about what you bought."),
    TRANSACTIONS("Transactions", "/fxml/transactions.fxml",
            "Record income and expenses, filter them, and export to CSV."),
    BUDGETS("Budgets", "/fxml/budgets.fxml",
            "Monthly limits per category. The advisor warns you before a purchase breaks one."),
    SAVINGS_GOALS("Savings goals", "/fxml/savings.fxml",
            "Set targets with deadlines and put money aside for them."),
    PROFILE("My profile", "/fxml/profile.fxml",
            "Your income, regular expenses, commitments and spending personality.");

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

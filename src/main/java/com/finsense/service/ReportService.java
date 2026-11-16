package com.finsense.service;

import com.finsense.model.BudgetStatus;
import com.finsense.model.CategoryForecast;
import com.finsense.model.CategorySpending;
import com.finsense.model.InsightReport;
import com.finsense.model.MonthlySummary;
import com.finsense.model.PurchaseSummary;
import com.finsense.model.SavingsGoal;
import com.finsense.model.SpendingAnomaly;
import com.finsense.model.enums.GoalStatus;
import com.finsense.util.SessionManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds a printable monthly report as a single HTML file (open it in a browser and
 * "Print -> Save as PDF"). It only reads data through the other services - it adds
 * no new business logic - and uses a StringBuilder to assemble the page.
 */
public class ReportService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final TransactionService transactions = new TransactionService();
    private final InsightService insights = new InsightService();
    private final ForecastService forecasts = new ForecastService();
    private final AnomalyService anomalies = new AnomalyService();
    private final BudgetService budgets = new BudgetService();
    private final SavingsGoalService goals = new SavingsGoalService();
    private final PurchaseService purchases = new PurchaseService();

    public void export(YearMonth month, Path file) throws IOException {
        Files.writeString(file, build(month), StandardCharsets.UTF_8);
    }

    public String build(YearMonth month) {
        String name = SessionManager.getInstance().getCurrentUser().map(u -> u.getFullName()).orElse("FinSense user");
        MonthlySummary summary = transactions.monthlySummary(month);
        InsightReport report = insights.report(month);

        StringBuilder html = new StringBuilder(8_000);
        html.append("<!doctype html><html><head><meta charset='utf-8'><title>FinSense report - ")
                .append(month.format(MONTH)).append("</title><style>").append(CSS).append("</style></head><body>")
                .append("<header><div class='brand'>FinSense</div><div class='sub'>Financial Intelligent Decision System</div>")
                .append("<h1>Monthly report · ").append(month.format(MONTH)).append("</h1>")
                .append("<p class='muted'>Prepared for ").append(escape(name)).append(" on ")
                .append(LocalDate.now().format(DAY)).append("</p></header>");

        html.append("<section class='kpis'>")
                .append(kpi("Income", summary.income().format()))
                .append(kpi("Expenses", summary.expenses().format()))
                .append(kpi("Net", summary.net().format()))
                .append(kpi("Savings rate", report.savingsRatePercent() + "%"))
                .append("</section>");

        html.append("<h2>Where the money went</h2><table><tr><th>Category</th><th>Type</th>"
                + "<th class='r'>This month</th><th class='r'>Last month</th><th class='r'>Change</th></tr>");
        for (CategorySpending c : report.topCategories()) {
            html.append("<tr><td>").append(escape(c.categoryName())).append("</td><td>")
                    .append("DISCRETIONARY".equals(c.nature()) ? "Discretionary" : "Essential")
                    .append("</td><td class='r'>").append(c.current().format())
                    .append("</td><td class='r'>").append(c.previous().format())
                    .append("</td><td class='r'>").append(c.changePercent() > 0 ? "+" : "")
                    .append(c.changePercent()).append("%</td></tr>");
        }
        html.append(report.topCategories().isEmpty() ? empty(5, "No spending recorded.") : "").append("</table>");

        List<CategoryForecast> forecast = forecasts.forecastNextMonth();
        html.append("<h2>Next month forecast</h2><table><tr><th>Category</th><th class='r'>Last month</th>"
                + "<th class='r'>Forecast</th><th>Method</th></tr>");
        for (CategoryForecast f : forecast) {
            html.append("<tr><td>").append(escape(f.category().name())).append("</td><td class='r'>")
                    .append(f.lastMonth().format()).append("</td><td class='r'>").append(f.forecast().format())
                    .append("</td><td>").append(f.method()).append(" (").append(f.monthsOfData())
                    .append(" mo)</td></tr>");
        }
        html.append(forecast.isEmpty() ? empty(4, "Needs at least one complete month of data.") : "")
                .append("</table>");

        List<BudgetStatus> budgetRows = budgets.statuses(month);
        if (!budgetRows.isEmpty()) {
            html.append("<h2>Budgets</h2><table><tr><th>Category</th><th class='r'>Limit</th>"
                    + "<th class='r'>Spent</th><th class='r'>Used</th></tr>");
            for (BudgetStatus b : budgetRows) {
                html.append("<tr").append(b.isOver() ? " class='over'" : "").append("><td>")
                        .append(escape(b.category().name())).append("</td><td class='r'>")
                        .append(b.monthlyLimit().format()).append("</td><td class='r'>")
                        .append(b.spentThisMonth().format()).append("</td><td class='r'>")
                        .append(Math.round(b.usedRatio() * 100)).append("%</td></tr>");
            }
            html.append("</table>");
        }

        List<SpendingAnomaly> unusual = anomalies.recentAnomalies();
        html.append("<h2>Unusual spending</h2>");
        if (unusual.isEmpty()) {
            html.append("<p class='muted'>Nothing unusual in the last 60 days.</p>");
        } else {
            html.append("<ul>");
            for (SpendingAnomaly a : unusual) {
                html.append("<li><b>").append(a.transaction().amount().format()).append("</b> on ")
                        .append(escape(a.transaction().category().name())).append(", ")
                        .append(a.transaction().date().format(DAY)).append(" — ")
                        .append(String.format("%.1f", a.timesTypical())).append("× your typical ")
                        .append(a.typicalAmount().format()).append("</li>");
            }
            html.append("</ul>");
        }

        html.append("<h2>Savings goals</h2><table><tr><th>Goal</th><th class='r'>Saved</th>"
                + "<th class='r'>Target</th><th class='r'>Progress</th><th>Deadline</th></tr>");
        List<SavingsGoal> active = goals.goals().stream().filter(g -> g.status() == GoalStatus.ACTIVE).toList();
        for (SavingsGoal g : active) {
            html.append("<tr><td>").append(escape(g.name())).append("</td><td class='r'>")
                    .append(g.savedAmount().format()).append("</td><td class='r'>").append(g.targetAmount().format())
                    .append("</td><td class='r'>").append(Math.round(g.progressPercent())).append("%</td><td>")
                    .append(g.deadline().format(DAY)).append("</td></tr>");
        }
        html.append(active.isEmpty() ? empty(5, "No active goals.") : "").append("</table>");

        List<PurchaseSummary> monthPurchases = purchases.summaries().stream()
                .filter(p -> YearMonth.from(p.purchase().createdAt()).equals(month))
                .toList();
        html.append("<h2>Purchase decisions this month</h2><table><tr><th>Item</th><th class='r'>Price</th>"
                + "<th>Verdict</th><th>Status</th><th>Your rating</th></tr>");
        for (PurchaseSummary p : monthPurchases) {
            String verdict = p.latestRecommendation() == null ? "—" : p.latestRecommendation().name();
            html.append("<tr><td>").append(escape(p.purchase().productName())).append("</td><td class='r'>")
                    .append(p.purchase().price().format()).append("</td><td><span class='v v-")
                    .append(verdict.toLowerCase()).append("'>").append(verdict).append("</span></td><td>")
                    .append(p.purchase().status()).append("</td><td>")
                    .append(p.feedback() == null ? "—" : p.feedback().toString()).append("</td></tr>");
        }
        html.append(monthPurchases.isEmpty() ? empty(5, "No purchases evaluated this month.") : "").append("</table>");

        html.append("<h2>Suggestions</h2><ul>");
        report.suggestions().forEach(s -> html.append("<li>").append(escape(s)).append("</li>"));
        html.append("</ul><footer>Generated by FinSense. General guidance, not financial advice.</footer>")
                .append("</body></html>");
        return html.toString();
    }

    private static String kpi(String label, String value) {
        return "<div class='kpi'><div class='label'>" + label + "</div><div class='value'>" + value + "</div></div>";
    }

    private static String empty(int columns, String text) {
        return "<tr><td colspan='" + columns + "' class='muted'>" + text + "</td></tr>";
    }

    /** Escapes user-entered text so it cannot break the HTML. */
    static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static final String CSS = """
            body{font-family:'Segoe UI',Arial,sans-serif;color:#1e2722;background:#f4f1ea;margin:0;padding:40px 56px;}
            header{border-bottom:2px solid #1e2722;padding-bottom:16px;margin-bottom:28px}
            .brand{font-family:Georgia,serif;font-size:28px;font-weight:bold;color:#2d5a45}
            .sub{color:#7b827c;font-size:12px;letter-spacing:.06em;text-transform:uppercase}
            h1{font-family:Georgia,serif;font-weight:normal;font-size:26px;margin:18px 0 4px}
            h2{font-family:Georgia,serif;font-weight:normal;font-size:19px;margin:32px 0 10px;color:#2d5a45}
            .muted{color:#7b827c}
            .kpis{display:flex;gap:14px}
            .kpi{flex:1;background:#fffdf8;border:1px solid #e2dccd;border-radius:8px;padding:14px 16px}
            .kpi .label{font-size:11px;text-transform:uppercase;letter-spacing:.06em;color:#7b827c}
            .kpi .value{font-size:22px;font-weight:600;margin-top:4px}
            table{width:100%;border-collapse:collapse;background:#fffdf8;border:1px solid #e2dccd}
            th{font-size:11px;text-transform:uppercase;letter-spacing:.05em;color:#7b827c;text-align:left;
               padding:9px 12px;border-bottom:1px solid #e2dccd;background:#efebe1}
            td{padding:9px 12px;border-bottom:1px solid #efebe1;font-size:14px}
            .r{text-align:right;font-variant-numeric:tabular-nums}
            tr.over td{color:#a3412d}
            .v{font-size:11px;font-weight:700;padding:2px 8px;border-radius:4px;color:#fff;background:#7b827c}
            .v-buy{background:#3f7d58}.v-consider{background:#b07d2b}.v-wait{background:#5d6688}.v-avoid{background:#a3412d}
            li{margin:6px 0}
            footer{margin-top:40px;color:#7b827c;font-size:12px;border-top:1px solid #e2dccd;padding-top:12px}
            @media print{body{background:#fff;padding:0}}
            """;
}

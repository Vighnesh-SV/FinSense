package com.finsense.controller;

import com.finsense.model.BudgetStatus;
import com.finsense.model.Category;
import com.finsense.model.FinancialSummary;
import com.finsense.model.Money;
import com.finsense.model.MonthlySummary;
import com.finsense.model.PurchaseSummary;
import com.finsense.model.SavingsGoal;
import com.finsense.model.SpendingAnomaly;
import com.finsense.model.Transaction;
import com.finsense.model.enums.GoalStatus;
import com.finsense.model.enums.TransactionType;
import com.finsense.service.AnomalyService;
import com.finsense.service.BehaviourService;
import com.finsense.service.BudgetService;
import com.finsense.service.FinancialService;
import com.finsense.service.ForecastService;
import com.finsense.service.PurchaseService;
import com.finsense.service.SavingsGoalService;
import com.finsense.service.TransactionService;
import com.finsense.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Controller of dashboard.fxml - the landing screen.
 *
 * All reads happen on a background thread (loadData) and are packed into one
 * immutable DashboardData record; display() then updates the controls on the
 * JavaFX thread. The dashboard only reads - it never writes to the database.
 */
public class DashboardController extends BaseController {

    private static final int RECENT_TRANSACTIONS = 6;
    private static final int RECENT_PURCHASES = 5;
    private static final int MAX_ALERTS = 4;

    @FXML private Label incomeKpi;
    @FXML private Label expenseKpi;
    @FXML private Label forecastNote;
    @FXML private Label disposableKpi;
    @FXML private Label savingsKpi;
    @FXML private Label behaviourKpi;
    @FXML private Label statusLabel;

    @FXML private VBox attentionBox;
    @FXML private VBox goalsBox;
    @FXML private PieChart expenseChart;

    @FXML private TableView<Transaction> transactionTable;
    @FXML private TableColumn<Transaction, String> txnDateCol;
    @FXML private TableColumn<Transaction, String> txnCategoryCol;
    @FXML private TableColumn<Transaction, String> txnAmountCol;

    @FXML private TableView<PurchaseSummary> purchaseTable;
    @FXML private TableColumn<PurchaseSummary, String> purchaseNameCol;
    @FXML private TableColumn<PurchaseSummary, String> purchaseVerdictCol;

    private final FinancialService financial = new FinancialService();
    private final TransactionService transactions = new TransactionService();
    private final SavingsGoalService goals = new SavingsGoalService();
    private final BehaviourService behaviour = new BehaviourService();
    private final PurchaseService purchases = new PurchaseService();
    private final AnomalyService anomalies = new AnomalyService();
    private final BudgetService budgets = new BudgetService();
    private final ForecastService forecasts = new ForecastService();

    /** Everything the dashboard shows, loaded in one go (record = immutable data carrier). */
    private record DashboardData(FinancialSummary summary, MonthlySummary actual, Money freeSavings,
                                 String behaviour, Money forecast, List<SavingsGoal> goals,
                                 List<Transaction> transactions, List<PurchaseSummary> purchases,
                                 Map<Category, Money> breakdown, List<SpendingAnomaly> anomalies,
                                 List<BudgetStatus> budgets) { }

    @FXML
    private void initialize() {
        UiKit.text(txnDateCol, t -> t.date().format(UiKit.DAY));
        UiKit.text(txnCategoryCol, t -> t.category().name());
        txnAmountCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                (c.getValue().type() == TransactionType.INCOME ? "+ " : "− ") + c.getValue().amount().format()));
        txnAmountCol.getStyleClass().add("money");
        UiKit.text(purchaseNameCol, p -> p.purchase().productName());
        UiKit.text(purchaseVerdictCol, p -> p.latestRecommendation() == null ? "" : p.latestRecommendation().name());
        purchaseVerdictCol.setCellFactory(column -> new VerdictCell<>());

        if (SessionManager.getInstance().isLoggedIn()) {
            runInBackground(this::loadData, this::display, message -> {
                statusLabel.setText(message);
                UiKit.show(statusLabel, true);
            });
        } else {
            statusLabel.setText("Log in to view your dashboard.");
            UiKit.show(statusLabel, true);
        }
    }

    /** Runs on a background thread - must not touch any JavaFX control. */
    private DashboardData loadData() {
        YearMonth month = YearMonth.now();
        List<SavingsGoal> activeGoals = goals.goals().stream()
                .filter(g -> g.status() == GoalStatus.ACTIVE)
                .toList();
        List<Transaction> recent = transactions.find(month.minusMonths(2).atDay(1), LocalDate.now(), null, null, null)
                .stream().limit(RECENT_TRANSACTIONS).toList();
        String type = behaviour.currentProfile()
                .map(p -> p.type().toString())
                .orElse("Spending personality not set");

        return new DashboardData(
                financial.calculateSummary(),
                transactions.monthlySummary(month),
                goals.freeSavings(),
                type,
                forecasts.totalForecast(),
                activeGoals,
                recent,
                purchases.summaries().stream().limit(RECENT_PURCHASES).toList(),
                transactions.categoryBreakdown(month, TransactionType.EXPENSE),
                anomalies.recentAnomalies(),
                budgets.statuses(month));
    }

    /** Runs on the JavaFX thread with the loaded data. */
    private void display(DashboardData data) {
        incomeKpi.setText(data.actual().income().format());
        expenseKpi.setText(data.actual().expenses().format());
        forecastNote.setText(data.forecast().isPositive()
                ? "Forecast for a full month: " + data.forecast().format()
                : "Forecast needs a complete month of data");
        disposableKpi.setText(data.summary().disposableIncome().format());
        savingsKpi.setText(data.freeSavings().format());
        behaviourKpi.setText(data.behaviour());

        transactionTable.setItems(FXCollections.observableArrayList(data.transactions()));
        purchaseTable.setItems(FXCollections.observableArrayList(data.purchases()));

        showAttention(data);
        showGoals(data.goals());

        expenseChart.setData(FXCollections.observableArrayList(data.breakdown().entrySet().stream()
                .map(e -> new PieChart.Data(e.getKey().name(), UiKit.rupees(e.getValue())))
                .toList()));
    }

    /** Unusual expenses and budgets that are over or close to their limit. */
    private void showAttention(DashboardData data) {
        attentionBox.getChildren().clear();
        int shown = 0;
        for (SpendingAnomaly a : data.anomalies()) {
            if (shown++ == MAX_ALERTS) {
                break;
            }
            attentionBox.getChildren().add(alert(
                    a.transaction().amount().format() + " on " + a.transaction().category().name(),
                    String.format("%s · %.1f× your typical %s", a.transaction().date().format(UiKit.DAY),
                            a.timesTypical(), a.typicalAmount().format())));
        }
        for (BudgetStatus b : data.budgets()) {
            if (b.usedRatio() >= 0.8 && shown++ < MAX_ALERTS) {
                attentionBox.getChildren().add(alert(
                        b.category().name() + " budget " + (b.isOver() ? "exceeded" : "almost used"),
                        b.spentThisMonth().format() + " of " + b.monthlyLimit().format() + " spent this month"));
            }
        }
        if (attentionBox.getChildren().isEmpty()) {
            Label ok = new Label("All clear - no unusual spending and every budget is on track.");
            ok.setWrapText(true);
            ok.getStyleClass().add("info-note");
            ok.setMaxWidth(Double.MAX_VALUE);
            attentionBox.getChildren().add(ok);
        }
    }

    private static VBox alert(String title, String detail) {
        Label heading = new Label(title);
        heading.getStyleClass().add("alert-amount");
        Label sub = new Label(detail);
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);
        VBox box = new VBox(heading, sub);
        box.getStyleClass().add("alert-row");
        return box;
    }

    private void showGoals(List<SavingsGoal> active) {
        goalsBox.getChildren().clear();
        if (active.isEmpty()) {
            Label empty = new Label("No active goals yet - add one under Savings goals.");
            empty.getStyleClass().add("muted");
            goalsBox.getChildren().add(empty);
        }
        for (SavingsGoal goal : active) {
            Label name = new Label(goal.name());
            name.getStyleClass().add("section-title");
            Region spacer = new Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            Label amount = new Label(goal.savedAmount().format() + " of " + goal.targetAmount().format());
            amount.getStyleClass().add("muted");
            ProgressBar progress = new ProgressBar(goal.progressPercent() / 100.0);
            progress.setMaxWidth(Double.MAX_VALUE);
            goalsBox.getChildren().add(new VBox(6, new HBox(name, spacer, amount), progress));
        }
    }

    /** Table cell that draws the verdict as a coloured badge. */
    static class VerdictCell<T> extends TableCell<T, String> {
        @Override
        protected void updateItem(String verdict, boolean empty) {
            super.updateItem(verdict, empty);
            setText(null);
            setGraphic(empty || verdict == null || verdict.isEmpty()
                    ? null
                    : UiKit.badge(com.finsense.decision.Recommendation.valueOf(verdict)));
        }
    }

    @FXML
    private void onTransactions() {
        goTo(Screen.TRANSACTIONS);
    }

    @FXML
    private void onProfile() {
        goTo(Screen.PROFILE);
    }

    @FXML
    private void onPurchase() {
        goTo(Screen.PURCHASE_ADVISOR);
    }

    @FXML
    private void onInsights() {
        goTo(Screen.INSIGHTS);
    }

    private void goTo(Screen screen) {
        if (main() != null) {
            main().navigate(screen);
        }
    }
}

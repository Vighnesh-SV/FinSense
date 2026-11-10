package com.finsense.controller;

import com.finsense.model.Category;
import com.finsense.model.FinancialSummary;
import com.finsense.model.Money;
import com.finsense.model.MonthlySummary;
import com.finsense.model.PurchaseSummary;
import com.finsense.model.SavingsGoal;
import com.finsense.model.Transaction;
import com.finsense.model.enums.GoalStatus;
import com.finsense.model.enums.TransactionType;
import com.finsense.service.BehaviourService;
import com.finsense.service.FinancialService;
import com.finsense.service.PurchaseService;
import com.finsense.service.SavingsGoalService;
import com.finsense.service.TransactionService;
import com.finsense.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Controller of dashboard.fxml - the landing screen.
 *
 * All the reads happen on a background thread (loadData) and are packed into one
 * immutable DashboardData record; display() then updates the controls on the
 * JavaFX thread.
 */
public class DashboardController extends BaseController {

    private static final int RECENT_TRANSACTIONS = 6;
    private static final int RECENT_PURCHASES = 5;

    @FXML private Label incomeKpi;
    @FXML private Label expenseKpi;
    @FXML private Label disposableKpi;
    @FXML private Label savingsKpi;
    @FXML private Label behaviourKpi;
    @FXML private Label statusLabel;

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

    /** Everything the dashboard shows, loaded in one go (record = immutable data carrier). */
    private record DashboardData(FinancialSummary summary, MonthlySummary actual, String freeSavings,
                                 String behaviour, List<SavingsGoal> goals, List<Transaction> transactions,
                                 List<PurchaseSummary> purchases, Map<Category, Money> breakdown) { }

    @FXML
    private void initialize() {
        txnDateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().date().toString()));
        txnCategoryCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().category().name()));
        txnAmountCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().amount().format()));
        purchaseNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().purchase().productName()));
        purchaseVerdictCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().latestRecommendation() == null ? "—" : c.getValue().latestRecommendation().name()));

        if (SessionManager.getInstance().isLoggedIn()) {
            statusLabel.setText("Loading…");
            runInBackground(this::loadData, this::display, statusLabel::setText);
        } else {
            statusLabel.setText("Log in to view your dashboard.");
        }
    }

    /** Runs on a background thread - must not touch any JavaFX control. */
    private DashboardData loadData() {
        behaviour.refreshSpendingClassification();
        YearMonth month = YearMonth.now();

        List<SavingsGoal> activeGoals = goals.goals().stream()
                .filter(g -> g.status() == GoalStatus.ACTIVE)
                .toList();
        List<Transaction> recent = transactions.find(month.minusMonths(2).atDay(1), LocalDate.now(), null, null, null)
                .stream()
                .limit(RECENT_TRANSACTIONS)
                .toList();
        List<PurchaseSummary> recentPurchases = purchases.summaries().stream()
                .limit(RECENT_PURCHASES)
                .toList();
        String type = behaviour.currentProfile()
                .map(p -> p.type().name().replace('_', ' '))
                .orElse("Not classified");

        return new DashboardData(
                financial.calculateSummary(),
                transactions.monthlySummary(month),
                goals.freeSavings().format(),
                type,
                activeGoals,
                recent,
                recentPurchases,
                transactions.categoryBreakdown(month, TransactionType.EXPENSE));
    }

    /** Runs on the JavaFX thread with the loaded data. */
    private void display(DashboardData data) {
        incomeKpi.setText(data.actual().income().format());
        expenseKpi.setText(data.actual().expenses().format());
        disposableKpi.setText(data.summary().disposableIncome().format());
        savingsKpi.setText(data.freeSavings());
        behaviourKpi.setText(data.behaviour());

        transactionTable.setItems(FXCollections.observableArrayList(data.transactions()));
        purchaseTable.setItems(FXCollections.observableArrayList(data.purchases()));

        goalsBox.getChildren().clear();
        if (data.goals().isEmpty()) {
            Label empty = new Label("No active goals yet.");
            empty.getStyleClass().add("muted");
            goalsBox.getChildren().add(empty);
        }
        for (SavingsGoal goal : data.goals()) {
            Label label = new Label(goal.name() + " · " + goal.savedAmount().format()
                    + " of " + goal.targetAmount().format());
            ProgressBar progress = new ProgressBar(goal.progressPercent() / 100.0);
            progress.setMaxWidth(Double.MAX_VALUE);
            VBox row = new VBox(5, label, progress);
            row.getStyleClass().add("kpi-card");
            goalsBox.getChildren().add(row);
        }

        expenseChart.setData(FXCollections.observableArrayList(data.breakdown().entrySet().stream()
                .map(e -> new PieChart.Data(e.getKey().name(), e.getValue().getPaise() / 100.0))
                .toList()));
        statusLabel.setText("");
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

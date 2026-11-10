package com.finsense.controller;

import com.finsense.model.BehaviourProfile;
import com.finsense.model.CategorySpending;
import com.finsense.model.InsightReport;
import com.finsense.model.Money;
import com.finsense.model.MonthlyMetrics;
import com.finsense.service.InsightService;
import com.finsense.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.YearMonth;

/**
 * Controller of insights.fxml: month-to-month comparison, top categories,
 * suggestions and the behaviour change history. The report is built on a
 * background thread by InsightService so the window never freezes.
 */
public class InsightsController extends BaseController {

    @FXML private Label statusLabel;
    @FXML private BarChart<String, Number> comparisonChart;

    @FXML private Label essentialValue;
    @FXML private Label discretionaryValue;
    @FXML private Label savingsRateValue;

    @FXML private TableView<CategorySpending> categoriesTable;
    @FXML private TableColumn<CategorySpending, String> categoryNameCol;
    @FXML private TableColumn<CategorySpending, String> currentSpendCol;
    @FXML private TableColumn<CategorySpending, String> priorSpendCol;
    @FXML private TableColumn<CategorySpending, String> changeCol;
    @FXML private TableColumn<CategorySpending, String> natureCol;

    @FXML private ListView<String> suggestionsList;

    @FXML private TableView<BehaviourProfile> behaviourTable;
    @FXML private TableColumn<BehaviourProfile, String> behaviourDateCol;
    @FXML private TableColumn<BehaviourProfile, String> behaviourTypeCol;
    @FXML private TableColumn<BehaviourProfile, String> behaviourSourceCol;
    @FXML private TableColumn<BehaviourProfile, String> behaviourReasonCol;

    private final InsightService service = new InsightService();

    @FXML
    private void initialize() {
        categoryNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().categoryName()));
        currentSpendCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().current().format()));
        priorSpendCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().previous().format()));
        changeCol.setCellValueFactory(c -> new SimpleStringProperty(formatChange(c.getValue().changePercent())));
        natureCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nature()));

        behaviourDateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().createdAt().toString()));
        behaviourTypeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().type().name().replace('_', ' ')));
        behaviourSourceCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().source().name()));
        behaviourReasonCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().reason() == null ? "" : c.getValue().reason()));

        if (SessionManager.getInstance().isLoggedIn()) {
            statusLabel.setText("Loading insights…");
            runInBackground(() -> service.report(YearMonth.now()), this::display, statusLabel::setText);
        } else {
            statusLabel.setText("Log in to view financial insights.");
        }
    }

    /** Runs on the JavaFX thread once the background report is ready. */
    private void display(InsightReport report) {
        XYChart.Series<String, Number> income = new XYChart.Series<>();
        XYChart.Series<String, Number> expenses = new XYChart.Series<>();
        income.setName("Income");
        expenses.setName("Expenses");
        for (MonthlyMetrics month : report.monthlyComparison()) {
            String label = month.month().toString();
            income.getData().add(new XYChart.Data<>(label, rupees(month.income())));
            expenses.getData().add(new XYChart.Data<>(label, rupees(month.expenses())));
        }
        comparisonChart.getData().setAll(income, expenses);

        essentialValue.setText(report.essentialExpenses().format());
        discretionaryValue.setText(report.discretionaryExpenses().format());
        savingsRateValue.setText(report.savingsRatePercent() + "%");

        categoriesTable.setItems(FXCollections.observableArrayList(report.topCategories()));
        suggestionsList.setItems(FXCollections.observableArrayList(report.suggestions()));
        behaviourTable.setItems(FXCollections.observableArrayList(report.behaviourHistory()));
        statusLabel.setText("");
    }

    private static String formatChange(Number percent) {
        return (percent.doubleValue() > 0 ? "+" : "") + percent + "%";
    }

    /** Charts plot rupees; Money stores paise internally. */
    private static double rupees(Money money) {
        return money.getPaise() / 100.0;
    }
}

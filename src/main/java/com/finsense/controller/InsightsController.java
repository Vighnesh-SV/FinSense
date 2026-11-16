package com.finsense.controller;

import com.finsense.model.BehaviourProfile;
import com.finsense.model.CategoryForecast;
import com.finsense.model.CategorySpending;
import com.finsense.model.InsightReport;
import com.finsense.model.Money;
import com.finsense.model.MonthlyMetrics;
import com.finsense.model.SpendingAnomaly;
import com.finsense.service.AnomalyService;
import com.finsense.service.ForecastService;
import com.finsense.service.InsightService;
import com.finsense.service.ReportService;
import com.finsense.util.SceneManager;
import com.finsense.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.YearMonth;
import java.util.List;

/**
 * Controller of insights.fxml: trends, the next-month forecast, unusual spending,
 * suggestions, behaviour history and the monthly report export. Everything is
 * loaded on a background thread so the window never freezes.
 */
public class InsightsController extends BaseController {

    @FXML private Label statusLabel;
    @FXML private Button exportButton;
    @FXML private BarChart<String, Number> comparisonChart;

    @FXML private Label essentialValue;
    @FXML private Label discretionaryValue;
    @FXML private Label savingsRateValue;
    @FXML private Label forecastValue;

    @FXML private TableView<CategoryForecast> forecastTable;
    @FXML private TableColumn<CategoryForecast, String> forecastCategoryCol;
    @FXML private TableColumn<CategoryForecast, String> forecastLastCol;
    @FXML private TableColumn<CategoryForecast, String> forecastNextCol;
    @FXML private TableColumn<CategoryForecast, String> forecastChangeCol;

    @FXML private VBox anomalyBox;

    @FXML private TableView<CategorySpending> categoriesTable;
    @FXML private TableColumn<CategorySpending, String> categoryNameCol;
    @FXML private TableColumn<CategorySpending, String> currentSpendCol;
    @FXML private TableColumn<CategorySpending, String> priorSpendCol;
    @FXML private TableColumn<CategorySpending, String> changeCol;
    @FXML private TableColumn<CategorySpending, String> natureCol;

    @FXML private VBox suggestionsBox;

    @FXML private TableView<BehaviourProfile> behaviourTable;
    @FXML private TableColumn<BehaviourProfile, String> behaviourDateCol;
    @FXML private TableColumn<BehaviourProfile, String> behaviourTypeCol;
    @FXML private TableColumn<BehaviourProfile, String> behaviourSourceCol;
    @FXML private TableColumn<BehaviourProfile, String> behaviourReasonCol;

    private final InsightService insights = new InsightService();
    private final ForecastService forecasts = new ForecastService();
    private final AnomalyService anomalies = new AnomalyService();

    /** Everything this screen shows, loaded together. */
    private record InsightsData(InsightReport report, List<CategoryForecast> forecast,
                                List<SpendingAnomaly> anomalies) { }

    @FXML
    private void initialize() {
        UiKit.text(forecastCategoryCol, f -> f.category().name());
        UiKit.money(forecastLastCol, CategoryForecast::lastMonth);
        UiKit.money(forecastNextCol, CategoryForecast::forecast);
        UiKit.text(forecastChangeCol, f -> signed(f.changePercent()));

        UiKit.text(categoryNameCol, CategorySpending::categoryName);
        UiKit.money(currentSpendCol, CategorySpending::current);
        UiKit.money(priorSpendCol, CategorySpending::previous);
        UiKit.text(changeCol, c -> signed(c.changePercent()));
        UiKit.text(natureCol, c -> "DISCRETIONARY".equals(c.nature()) ? "Discretionary" : "Essential");

        UiKit.text(behaviourDateCol, b -> b.createdAt().format(UiKit.DAY));
        UiKit.text(behaviourTypeCol, b -> b.type().toString());
        UiKit.text(behaviourSourceCol, b -> b.source().toString());
        UiKit.text(behaviourReasonCol, b -> b.reason() == null ? "" : b.reason());

        if (SessionManager.getInstance().isLoggedIn()) {
            statusLabel.setText("Loading…");
            runInBackground(this::load, this::display, statusLabel::setText);
        } else {
            statusLabel.setText("Log in to view insights.");
            exportButton.setDisable(true);
        }
    }

    private InsightsData load() {
        return new InsightsData(insights.report(YearMonth.now()), forecasts.forecastNextMonth(),
                anomalies.recentAnomalies());
    }

    private void display(InsightsData data) {
        InsightReport report = data.report();
        statusLabel.setText(YearMonth.now().format(UiKit.MONTH));

        XYChart.Series<String, Number> income = new XYChart.Series<>();
        XYChart.Series<String, Number> expenses = new XYChart.Series<>();
        income.setName("Income");
        expenses.setName("Expenses");
        for (MonthlyMetrics month : report.monthlyComparison()) {
            String label = month.month().format(UiKit.SHORT_MONTH);
            income.getData().add(new XYChart.Data<>(label, UiKit.rupees(month.income())));
            expenses.getData().add(new XYChart.Data<>(label, UiKit.rupees(month.expenses())));
        }
        comparisonChart.getData().setAll(List.of(income, expenses));

        essentialValue.setText(report.essentialExpenses().format());
        discretionaryValue.setText(report.discretionaryExpenses().format());
        savingsRateValue.setText(report.savingsRatePercent() + "%");
        Money total = data.forecast().stream().map(CategoryForecast::forecast).reduce(Money.ZERO, Money::plus);
        forecastValue.setText(data.forecast().isEmpty() ? "—" : total.format());

        forecastTable.setItems(FXCollections.observableArrayList(data.forecast()));
        categoriesTable.setItems(FXCollections.observableArrayList(report.topCategories()));
        behaviourTable.setItems(FXCollections.observableArrayList(report.behaviourHistory()));

        anomalyBox.getChildren().clear();
        if (data.anomalies().isEmpty()) {
            anomalyBox.getChildren().add(UiKit.emptyState("Nothing unusual - your recent spending looks normal."));
        }
        for (SpendingAnomaly a : data.anomalies()) {
            Label amount = new Label(a.transaction().amount().format() + "  ·  " + a.transaction().category().name());
            amount.getStyleClass().add("alert-amount");
            Label detail = new Label(String.format("%s - %.1f× your typical %s", a.transaction().date().format(UiKit.DAY),
                    a.timesTypical(), a.typicalAmount().format()));
            detail.getStyleClass().add("muted");
            detail.setWrapText(true);
            VBox row = new VBox(amount, detail);
            row.getStyleClass().add("alert-row");
            anomalyBox.getChildren().add(row);
        }

        suggestionsBox.getChildren().clear();
        for (String suggestion : report.suggestions()) {
            Label note = new Label(suggestion);
            note.setWrapText(true);
            note.setMaxWidth(Double.MAX_VALUE);
            note.getStyleClass().add("info-note");
            suggestionsBox.getChildren().add(note);
        }
    }

    /** Saves this month's report as an HTML file the user can open and print. */
    @FXML
    private void onExportReport() {
        YearMonth month = YearMonth.now();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save monthly report");
        chooser.setInitialFileName("FinSense-report-" + month + ".html");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Web page (*.html)", "*.html"));
        File file = chooser.showSaveDialog(SceneManager.getStage());
        if (file == null) {
            return;
        }
        runInBackground(() -> {
            new ReportService().export(month, file.toPath());
            return file;
        }, saved -> showInfo("Report saved",
                "Saved to " + saved.getAbsolutePath() + "\nOpen it in a browser; use Print to save it as a PDF."),
                message -> showError("Could not save report", message), exportButton);
    }

    private static String signed(int percent) {
        return (percent > 0 ? "+" : "") + percent + "%";
    }
}

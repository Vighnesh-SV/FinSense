package com.finsense.controller;

import com.finsense.decision.Recommendation;
import com.finsense.model.Category;
import com.finsense.model.CommitmentEnding;
import com.finsense.model.GoalImpact;
import com.finsense.model.Money;
import com.finsense.model.SimulationResult;
import com.finsense.model.enums.Necessity;
import com.finsense.service.PurchaseService;
import com.finsense.service.SimulationService;
import com.finsense.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller of simulator.fxml: a "what-if" view of a purchase.
 * It calls only read-only simulation code - nothing is written to the database.
 */
public class SimulatorController extends BaseController {

    private static final List<Integer> INSTALMENT_OPTIONS = List.of(1, 2, 3, 6, 12);

    @FXML private TextField productNameField;
    @FXML private TextField priceField;
    @FXML private ComboBox<Category> categoryCombo;
    @FXML private ComboBox<Necessity> necessityCombo;
    @FXML private ComboBox<Integer> instalmentsCombo;
    @FXML private Button simulateButton;
    @FXML private Label errorLabel;

    @FXML private VBox resultCard;
    @FXML private Label verdictLabel;
    @FXML private Label scoreLabel;
    @FXML private LineChart<Number, Number> savingsChart;
    @FXML private ListView<String> warningsList;

    @FXML private TableView<GoalImpact> goalsTable;
    @FXML private TableColumn<GoalImpact, String> goalNameCol;
    @FXML private TableColumn<GoalImpact, String> goalEtaCol;
    @FXML private TableColumn<GoalImpact, String> goalDelayCol;
    @FXML private TableColumn<GoalImpact, String> goalDeadlineCol;

    @FXML private TableView<CommitmentEnding> commitmentTable;
    @FXML private TableColumn<CommitmentEnding, String> commitmentNameCol;
    @FXML private TableColumn<CommitmentEnding, String> commitmentEndCol;
    @FXML private TableColumn<CommitmentEnding, String> commitmentAmountCol;

    private final SimulationService simulationService = new SimulationService();
    private final PurchaseService purchaseService = new PurchaseService();

    @FXML
    private void initialize() {
        necessityCombo.setItems(FXCollections.observableArrayList(Necessity.values()));
        necessityCombo.setValue(Necessity.WANT);
        instalmentsCombo.setItems(FXCollections.observableArrayList(INSTALMENT_OPTIONS));
        instalmentsCombo.setValue(1);

        goalNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().goalName()));
        goalEtaCol.setCellValueFactory(c -> new SimpleStringProperty(
                eta(c.getValue().baselineEta()) + " → " + eta(c.getValue().purchaseEta())));
        goalDelayCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().delayMonths() + " month(s)"
                        + (c.getValue().missesDeadline() ? " · Misses deadline" : "")));
        goalDeadlineCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().deadline().toString()));

        commitmentNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        commitmentEndCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().endDate() + " · month " + c.getValue().monthNumber()));
        commitmentAmountCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().monthlyAmount().format()));

        if (SessionManager.getInstance().isLoggedIn()) {
            runSafely(() -> categoryCombo.setItems(
                    FXCollections.observableArrayList(purchaseService.categories())));
        }
    }

    @FXML
    private void onSimulate() {
        hideInlineError(errorLabel);
        resultCard.setVisible(false);
        resultCard.setManaged(false);

        // Read every control on the JavaFX thread before going to the background thread.
        String name = productNameField.getText();
        String price = priceField.getText();
        Category category = categoryCombo.getValue();
        Necessity necessity = necessityCombo.getValue();
        int instalments = instalmentsCombo.getValue() == null ? 1 : instalmentsCombo.getValue();

        if (category == null) {
            showInlineError(errorLabel, "Choose an expense category.");
            return;
        }
        runInBackground(() -> simulationService.simulate(name, category, price, necessity, instalments),
                this::showResult,
                message -> showInlineError(errorLabel, message),
                simulateButton);
    }

    private void showResult(SimulationResult result) {
        Recommendation recommendation = result.decision().recommendation();
        verdictLabel.setText(recommendation.name());
        verdictLabel.getStyleClass().removeIf(style -> style.startsWith("badge-"));
        verdictLabel.getStyleClass().add("badge-" + recommendation.name().toLowerCase());
        scoreLabel.setText(result.decision().score() + " / 100 · " + result.instalments() + " instalment(s)");
        warningsList.setItems(FXCollections.observableArrayList(result.decision().warnings()));

        XYChart.Series<Number, Number> baseline = new XYChart.Series<>();
        XYChart.Series<Number, Number> withPurchase = new XYChart.Series<>();
        baseline.setName("Without purchase");
        withPurchase.setName("With purchase");
        result.projection().forEach(point -> {
            baseline.getData().add(new XYChart.Data<>(point.monthNumber(), rupees(point.baselineSavings())));
            withPurchase.getData().add(new XYChart.Data<>(point.monthNumber(), rupees(point.withPurchaseSavings())));
        });
        savingsChart.getData().setAll(baseline, withPurchase);

        goalsTable.setItems(FXCollections.observableArrayList(result.goalImpacts()));
        commitmentTable.setItems(FXCollections.observableArrayList(result.commitmentEndings()));
        resultCard.setVisible(true);
        resultCard.setManaged(true);
    }

    private static String eta(LocalDate date) {
        return date == null ? "Unknown" : date.toString();
    }

    /** Charts plot rupees; Money stores paise internally. */
    private static double rupees(Money money) {
        return money.getPaise() / 100.0;
    }
}

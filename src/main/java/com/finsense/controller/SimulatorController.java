package com.finsense.controller;

import com.finsense.decision.DecisionResult;
import com.finsense.decision.Recommendation;
import com.finsense.model.Category;
import com.finsense.model.CommitmentEnding;
import com.finsense.model.GoalImpact;
import com.finsense.model.SimulationResult;
import com.finsense.model.enums.Necessity;
import com.finsense.service.PurchaseService;
import com.finsense.service.SimulationService;
import com.finsense.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

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

    @FXML private VBox emptyState;
    @FXML private VBox resultCard;
    @FXML private HBox verdictHero;
    @FXML private Label verdictLabel;
    @FXML private Label planLabel;
    @FXML private Label scoreLabel;
    @FXML private Label timingLabel;
    @FXML private LineChart<Number, Number> savingsChart;
    @FXML private VBox warningsBox;

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
        // StringConverter controls how each Integer is shown in the combo box.
        instalmentsCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer n) {
                return n == null ? "" : n == 1 ? "One payment" : n + " monthly instalments";
            }

            @Override
            public Integer fromString(String text) {
                return null;
            }
        });

        UiKit.text(goalNameCol, GoalImpact::goalName);
        UiKit.text(goalEtaCol, g -> eta(g.baselineEta()) + "  →  " + eta(g.purchaseEta()));
        UiKit.text(goalDelayCol, g -> (g.delayMonths() == 0 ? "No delay" : g.delayMonths() + " month(s) later")
                + (g.missesDeadline() ? " · misses deadline" : ""));
        UiKit.text(goalDeadlineCol, g -> g.deadline().format(UiKit.DAY));

        UiKit.text(commitmentNameCol, CommitmentEnding::name);
        UiKit.text(commitmentEndCol, c -> c.endDate().format(UiKit.DAY) + "  (month " + c.monthNumber() + ")");
        UiKit.money(commitmentAmountCol, CommitmentEnding::monthlyAmount);

        if (SessionManager.getInstance().isLoggedIn()) {
            runSafely(() -> categoryCombo.setItems(
                    FXCollections.observableArrayList(purchaseService.categories())));
        }
    }

    @FXML
    private void onSimulate() {
        hideInlineError(errorLabel);

        // Read every control on the JavaFX thread before going to the background thread.
        String name = productNameField.getText();
        String price = priceField.getText();
        Category category = categoryCombo.getValue();
        Necessity necessity = necessityCombo.getValue();
        int instalments = instalmentsCombo.getValue() == null ? 1 : instalmentsCombo.getValue();

        if (category == null) {
            showInlineError(errorLabel, "Choose a category.");
            return;
        }
        runInBackground(() -> simulationService.simulate(name, category, price, necessity, instalments),
                this::showResult,
                message -> showInlineError(errorLabel, message),
                simulateButton);
    }

    private void showResult(SimulationResult result) {
        DecisionResult decision = result.decision();
        Recommendation recommendation = decision.recommendation();
        verdictLabel.setText(recommendation.name());
        UiKit.applyVerdict(verdictLabel, recommendation);
        UiKit.applyVerdict(verdictHero, recommendation);
        scoreLabel.setText(String.valueOf(decision.score()));
        planLabel.setText(result.instalments() == 1
                ? "Paid in one go."
                : "Paid in " + result.instalments() + " monthly instalments.");

        boolean hasTiming = decision.affordableFrom() != null;
        if (hasTiming) {
            timingLabel.setText("Smart timing: this fits safely from "
                    + decision.affordableFrom().format(UiKit.MONTH) + " if you keep saving at your current rate.");
        }
        UiKit.show(timingLabel, hasTiming);

        XYChart.Series<Number, Number> baseline = new XYChart.Series<>();
        XYChart.Series<Number, Number> withPurchase = new XYChart.Series<>();
        baseline.setName("Without the purchase");
        withPurchase.setName("With the purchase");
        result.projection().forEach(point -> {
            baseline.getData().add(new XYChart.Data<>(point.monthNumber(), UiKit.rupees(point.baselineSavings())));
            withPurchase.getData().add(new XYChart.Data<>(point.monthNumber(),
                    UiKit.rupees(point.withPurchaseSavings())));
        });
        savingsChart.getData().setAll(List.of(baseline, withPurchase));

        goalsTable.setItems(FXCollections.observableArrayList(result.goalImpacts()));
        commitmentTable.setItems(FXCollections.observableArrayList(result.commitmentEndings()));

        warningsBox.getChildren().clear();
        for (String warning : decision.warnings()) {
            Label note = new Label(warning);
            note.setWrapText(true);
            note.setMaxWidth(Double.MAX_VALUE);
            note.getStyleClass().add("info-note");
            warningsBox.getChildren().add(note);
        }

        UiKit.show(emptyState, false);
        UiKit.show(resultCard, true);
    }

    private static String eta(LocalDate date) {
        return date == null ? "not reachable" : date.format(UiKit.SHORT_MONTH);
    }
}

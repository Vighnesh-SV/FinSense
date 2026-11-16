package com.finsense.controller;

import com.finsense.model.PurchaseHistory;
import com.finsense.model.PurchaseSummary;
import com.finsense.model.enums.PurchaseFeedback;
import com.finsense.model.enums.PurchaseStatus;
import com.finsense.service.PurchaseService;
import com.finsense.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

/**
 * Controller of purchase_history.fxml: review earlier evaluations, compare two
 * of them, re-evaluate, and mark a purchase as bought or dropped. Status changes
 * go through PurchaseService, which runs them inside a JDBC transaction.
 */
public class PurchaseHistoryController extends BaseController {

    private static final String NO_COMPARISON = "Select two evaluations to compare.";

    @FXML private TableView<PurchaseSummary> purchaseTable;
    @FXML private TableColumn<PurchaseSummary, String> productCol;
    @FXML private TableColumn<PurchaseSummary, String> priceCol;
    @FXML private TableColumn<PurchaseSummary, String> verdictCol;
    @FXML private TableColumn<PurchaseSummary, String> scoreCol;
    @FXML private TableColumn<PurchaseSummary, String> statusCol;
    @FXML private TableColumn<PurchaseSummary, String> feedbackCol;

    @FXML private HBox feedbackBar;
    @FXML private Button worthItButton;
    @FXML private Button neutralButton;
    @FXML private Button regretButton;

    @FXML private Button reevaluateButton;
    @FXML private Button purchasedButton;
    @FXML private Button droppedButton;
    @FXML private CheckBox deductSavingsCheck;

    @FXML private TableView<PurchaseHistory> historyTable;
    @FXML private TableColumn<PurchaseHistory, String> evaluatedCol;
    @FXML private TableColumn<PurchaseHistory, String> historyVerdictCol;
    @FXML private TableColumn<PurchaseHistory, String> historyScoreCol;
    @FXML private TableColumn<PurchaseHistory, String> expenseCol;
    @FXML private TableColumn<PurchaseHistory, String> disposableCol;
    @FXML private TableColumn<PurchaseHistory, String> freeSavingsCol;
    @FXML private TableColumn<PurchaseHistory, String> behaviourCol;

    @FXML private ComboBox<PurchaseHistory> compareLeft;
    @FXML private ComboBox<PurchaseHistory> compareRight;
    @FXML private Label leftComparison;
    @FXML private Label rightComparison;

    private final PurchaseService service = new PurchaseService();

    @FXML
    private void initialize() {
        UiKit.text(productCol, p -> p.purchase().productName());
        UiKit.money(priceCol, p -> p.purchase().price());
        UiKit.text(verdictCol, p -> p.latestRecommendation() == null ? "" : p.latestRecommendation().name());
        verdictCol.setCellFactory(column -> new DashboardController.VerdictCell<>());
        UiKit.text(scoreCol, p -> p.latestScore() == null ? "—" : p.latestScore().toString());
        UiKit.text(statusCol, p -> p.purchase().status().toString());
        UiKit.text(feedbackCol, p -> p.feedback() == null ? "—" : p.feedback().toString());

        UiKit.text(evaluatedCol, h -> h.evaluatedAt().format(UiKit.DAY_TIME));
        UiKit.text(historyVerdictCol, h -> h.recommendation().name());
        historyVerdictCol.setCellFactory(column -> new DashboardController.VerdictCell<>());
        UiKit.text(historyScoreCol, h -> Integer.toString(h.score()));
        UiKit.money(expenseCol, PurchaseHistory::snapshotExpenses);
        UiKit.money(disposableCol, PurchaseHistory::snapshotDisposable);
        UiKit.money(freeSavingsCol, PurchaseHistory::snapshotFreeSavings);
        UiKit.text(behaviourCol, PurchaseHistory::snapshotBehaviour);

        // StringConverter (anonymous class) decides how an evaluation appears in the combo boxes.
        StringConverter<PurchaseHistory> display = new StringConverter<>() {
            @Override
            public String toString(PurchaseHistory h) {
                return h == null ? "" : h.evaluatedAt().format(UiKit.DAY_TIME) + " · " + h.recommendation() + " · " + h.score();
            }

            @Override
            public PurchaseHistory fromString(String value) {
                return null;                          // combo boxes are not editable
            }
        };
        compareLeft.setConverter(display);
        compareRight.setConverter(display);
        compareLeft.valueProperty().addListener((obs, oldValue, value) -> updateComparison());
        compareRight.valueProperty().addListener((obs, oldValue, value) -> updateComparison());

        purchaseTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldRow, row) -> selectPurchase(row));

        if (SessionManager.getInstance().isLoggedIn()) {
            reload();
        }
    }

    private void selectPurchase(PurchaseSummary summary) {
        boolean selected = summary != null;
        PurchaseStatus status = selected ? summary.purchase().status() : null;
        reevaluateButton.setDisable(!selected);
        purchasedButton.setDisable(status != PurchaseStatus.EVALUATED);
        droppedButton.setDisable(!selected || status == PurchaseStatus.PURCHASED);
        UiKit.show(feedbackBar, status == PurchaseStatus.PURCHASED);
        highlightFeedback(selected ? summary.feedback() : null);

        if (!selected) {
            historyTable.getItems().clear();
            compareLeft.getItems().clear();
            compareRight.getItems().clear();
            leftComparison.setText(NO_COMPARISON);
            rightComparison.setText("");
            return;
        }
        runSafely(() -> {
            ObservableList<PurchaseHistory> history =
                    FXCollections.observableArrayList(service.history(summary.purchase().id()));
            historyTable.setItems(history);
            compareLeft.setItems(FXCollections.observableArrayList(history));
            compareRight.setItems(FXCollections.observableArrayList(history));
            // Default comparison: oldest evaluation (left) against the newest (right).
            compareLeft.setValue(history.isEmpty() ? null : history.get(history.size() - 1));
            compareRight.setValue(history.size() < 2 ? null : history.get(0));
            updateComparison();
        });
    }

    private void updateComparison() {
        leftComparison.setText(formatComparison(compareLeft.getValue()));
        rightComparison.setText(formatComparison(compareRight.getValue()));
    }

    private String formatComparison(PurchaseHistory h) {
        if (h == null) {
            return "Choose an evaluation";
        }
        return h.recommendation() + " · " + h.score() + "/100"
                + "\nIncome: " + h.snapshotIncome().format()
                + "\nExpenses: " + h.snapshotExpenses().format()
                + "\nDisposable: " + h.snapshotDisposable().format()
                + "\nFree savings: " + h.snapshotFreeSavings().format()
                + "\nBehaviour: " + h.snapshotBehaviour();
    }

    private PurchaseSummary selectedPurchase() {
        return purchaseTable.getSelectionModel().getSelectedItem();
    }

    @FXML
    private void onReevaluate() {
        PurchaseSummary selected = selectedPurchase();
        if (selected == null) {
            return;
        }
        long id = selected.purchase().id();
        runInBackground(() -> service.reevaluate(id),
                result -> reloadAndReselect(id),
                message -> showError("Could not re-evaluate", message),
                reevaluateButton);
    }

    @FXML
    private void onMarkPurchased() {
        PurchaseSummary selected = selectedPurchase();
        if (selected == null) {
            return;
        }
        boolean deduct = deductSavingsCheck.isSelected();
        String message = "Record this as an expense and mark it purchased?"
                + (deduct ? " The cost will also be deducted from free savings." : "");
        if (confirm("Mark as purchased", message)) {
            runSafely(() -> {
                service.markAsPurchased(selected.purchase().id(), deduct);
                reload();
            });
        }
    }

    @FXML
    private void onMarkDropped() {
        PurchaseSummary selected = selectedPurchase();
        if (selected == null) {
            return;
        }
        if (confirm("Mark as dropped", "Mark this purchase idea as dropped?")) {
            runSafely(() -> {
                service.markAsDropped(selected.purchase().id());
                reload();
            });
        }
    }

    /** Marks the chosen rating chip with the "selected" style class. */
    private void highlightFeedback(PurchaseFeedback feedback) {
        worthItButton.getStyleClass().remove("selected");
        neutralButton.getStyleClass().remove("selected");
        regretButton.getStyleClass().remove("selected");
        if (feedback != null) {
            Button chosen = switch (feedback) {
                case WORTH_IT -> worthItButton;
                case NEUTRAL -> neutralButton;
                case REGRET -> regretButton;
            };
            chosen.getStyleClass().add("selected");
        }
    }

    @FXML
    private void onWorthIt() {
        rate(PurchaseFeedback.WORTH_IT);
    }

    @FXML
    private void onNeutral() {
        rate(PurchaseFeedback.NEUTRAL);
    }

    @FXML
    private void onRegret() {
        rate(PurchaseFeedback.REGRET);
    }

    private void rate(PurchaseFeedback feedback) {
        PurchaseSummary selected = selectedPurchase();
        if (selected == null) {
            return;
        }
        long id = selected.purchase().id();
        runSafely(() -> {
            service.rate(id, feedback);
            reloadAndReselect(id);
        });
    }

    private void reload() {
        runSafely(() -> purchaseTable.setItems(FXCollections.observableArrayList(service.summaries())));
    }

    private void reloadAndReselect(long purchaseId) {
        reload();
        purchaseTable.getItems().stream()
                .filter(row -> row.purchase().id() == purchaseId)
                .findFirst()
                .ifPresent(purchaseTable.getSelectionModel()::select);
    }
}

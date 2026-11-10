package com.finsense.controller;

import com.finsense.model.PurchaseHistory;
import com.finsense.model.PurchaseSummary;
import com.finsense.model.enums.PurchaseStatus;
import com.finsense.service.PurchaseService;
import com.finsense.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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
        productCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().purchase().productName()));
        priceCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().purchase().price().format()));
        verdictCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().latestRecommendation() == null ? "—" : c.getValue().latestRecommendation().name()));
        scoreCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().latestScore() == null ? "—" : c.getValue().latestScore().toString()));
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().purchase().status().name()));

        evaluatedCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().evaluatedAt().toString()));
        historyVerdictCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().recommendation().name()));
        historyScoreCol.setCellValueFactory(c -> new SimpleStringProperty(Integer.toString(c.getValue().score())));
        expenseCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().snapshotExpenses().format()));
        disposableCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().snapshotDisposable().format()));
        freeSavingsCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().snapshotFreeSavings().format()));
        behaviourCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().snapshotBehaviour()));

        // StringConverter (anonymous class) decides how an evaluation appears in the combo boxes.
        StringConverter<PurchaseHistory> display = new StringConverter<>() {
            @Override
            public String toString(PurchaseHistory h) {
                return h == null ? "" : h.evaluatedAt() + " · " + h.recommendation() + " · " + h.score();
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

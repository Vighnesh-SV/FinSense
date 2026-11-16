package com.finsense.controller;

import com.finsense.model.GoalContribution;
import com.finsense.model.SavingsGoal;
import com.finsense.model.enums.GoalPriority;
import com.finsense.model.enums.GoalStatus;
import com.finsense.service.SavingsGoalService;
import com.finsense.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.LocalDate;

/**
 * Controller of savings.fxml: create, edit and cancel goals and add contributions.
 * It only adapts SavingsGoalService data to JavaFX controls.
 */
public class SavingsController extends BaseController {

    private static final int DEFAULT_DEADLINE_MONTHS = 6;

    @FXML private Label freeSavingsLabel;

    @FXML private TableView<SavingsGoal> goalTable;
    @FXML private TableColumn<SavingsGoal, String> nameCol;
    @FXML private TableColumn<SavingsGoal, String> targetCol;
    @FXML private TableColumn<SavingsGoal, String> savedCol;
    @FXML private TableColumn<SavingsGoal, String> remainingCol;
    @FXML private TableColumn<SavingsGoal, String> progressCol;
    @FXML private TableColumn<SavingsGoal, String> deadlineCol;
    @FXML private TableColumn<SavingsGoal, String> priorityCol;
    @FXML private TableColumn<SavingsGoal, String> statusCol;
    @FXML private TableColumn<SavingsGoal, String> monthlyCol;

    // Goal form
    @FXML private TextField nameField;
    @FXML private TextField targetField;
    @FXML private DatePicker deadlinePicker;
    @FXML private ComboBox<GoalPriority> priorityCombo;
    @FXML private Button updateButton;
    @FXML private Button cancelButton;

    // Contributions
    @FXML private TextField contributionAmountField;
    @FXML private DatePicker contributionDatePicker;
    @FXML private TextField contributionNoteField;
    @FXML private Button contributeButton;
    @FXML private TableView<GoalContribution> contributionTable;
    @FXML private TableColumn<GoalContribution, String> contributionDateCol;
    @FXML private TableColumn<GoalContribution, String> contributionAmountCol;
    @FXML private TableColumn<GoalContribution, String> contributionNoteCol;

    private final SavingsGoalService service = new SavingsGoalService();

    @FXML
    private void initialize() {
        priorityCombo.setItems(FXCollections.observableArrayList(GoalPriority.values()));
        priorityCombo.setValue(GoalPriority.MEDIUM);
        deadlinePicker.setValue(LocalDate.now().plusMonths(DEFAULT_DEADLINE_MONTHS));
        contributionDatePicker.setValue(LocalDate.now());

        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        UiKit.money(targetCol, SavingsGoal::targetAmount);
        UiKit.money(savedCol, SavingsGoal::savedAmount);
        UiKit.money(remainingCol, SavingsGoal::remainingAmount);
        progressCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%.0f%%", c.getValue().progressPercent())));
        deadlineCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().deadline().format(UiKit.SHORT_DAY)));
        priorityCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().priority().toString()));
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status().toString()));
        monthlyCol.setCellValueFactory(c -> new SimpleStringProperty(
                service.requiredMonthlyAmount(c.getValue(), LocalDate.now()).format()));

        contributionDateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().contributedOn().format(UiKit.DAY)));
        UiKit.money(contributionAmountCol, c -> c.amount());
        contributionNoteCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().note() == null ? "" : c.getValue().note()));

        goalTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldGoal, goal) -> selectGoal(goal));

        if (SessionManager.getInstance().isLoggedIn()) {
            reload();
        }
    }

    /** Fills the form with the selected goal; only ACTIVE goals can be changed. */
    private void selectGoal(SavingsGoal goal) {
        boolean active = goal != null && goal.status() == GoalStatus.ACTIVE;
        updateButton.setDisable(!active);
        cancelButton.setDisable(!active);
        contributeButton.setDisable(!active);

        if (goal == null) {
            contributionTable.getItems().clear();
            return;
        }
        nameField.setText(goal.name());
        targetField.setText(goal.targetAmount().toPlainString());
        deadlinePicker.setValue(goal.deadline());
        priorityCombo.setValue(goal.priority());
        runSafely(() -> contributionTable.setItems(
                FXCollections.observableArrayList(service.contributions(goal.id()))));
    }

    private SavingsGoal selectedGoal() {
        return goalTable.getSelectionModel().getSelectedItem();
    }

    @FXML
    private void onCreate() {
        runSafely(() -> {
            service.create(nameField.getText(), targetField.getText(),
                    deadlinePicker.getValue(), priorityCombo.getValue());
            clearForm();
            reload();
        });
    }

    @FXML
    private void onUpdate() {
        SavingsGoal goal = selectedGoal();
        if (goal == null) {
            return;
        }
        runSafely(() -> {
            service.update(goal.id(), nameField.getText(), targetField.getText(),
                    deadlinePicker.getValue(), priorityCombo.getValue());
            reloadAndReselect(goal.id());
        });
    }

    @FXML
    private void onCancel() {
        SavingsGoal goal = selectedGoal();
        if (goal == null) {
            return;
        }
        String message = "Cancel '" + goal.name() + "'? Its contributions return to free savings.";
        if (confirm("Cancel goal", message)) {
            runSafely(() -> {
                service.cancel(goal.id());
                clearForm();
                reload();
            });
        }
    }

    @FXML
    private void onContribute() {
        SavingsGoal goal = selectedGoal();
        if (goal == null) {
            return;
        }
        runSafely(() -> {
            service.contribute(goal.id(), contributionAmountField.getText(),
                    contributionDatePicker.getValue(), contributionNoteField.getText());
            contributionAmountField.clear();
            contributionNoteField.clear();
            reloadAndReselect(goal.id());
        });
    }

    @FXML
    private void onClear() {
        clearForm();
    }

    private void clearForm() {
        goalTable.getSelectionModel().clearSelection();
        nameField.clear();
        targetField.clear();
        priorityCombo.setValue(GoalPriority.MEDIUM);
        deadlinePicker.setValue(LocalDate.now().plusMonths(DEFAULT_DEADLINE_MONTHS));
        contributionAmountField.clear();
        contributionNoteField.clear();
    }

    private void reload() {
        runSafely(() -> {
            goalTable.setItems(FXCollections.observableArrayList(service.goals()));
            freeSavingsLabel.setText("Free savings: " + service.freeSavings().format());
        });
    }

    /** Reloads the table and selects the same goal again (rows are new objects after a reload). */
    private void reloadAndReselect(long goalId) {
        reload();
        goalTable.getItems().stream()
                .filter(row -> row.id() == goalId)
                .findFirst()
                .ifPresent(goalTable.getSelectionModel()::select);
    }
}

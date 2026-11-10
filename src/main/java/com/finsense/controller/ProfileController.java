package com.finsense.controller;

import com.finsense.exception.IncompleteProfileException;
import com.finsense.exception.ValidationException;
import com.finsense.model.Commitment;
import com.finsense.model.FinancialProfile;
import com.finsense.model.FinancialSummary;
import com.finsense.model.enums.CommitmentType;
import com.finsense.service.FinancialService;
import com.finsense.service.BehaviourService;
import com.finsense.util.SceneManager;
import com.finsense.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller for the Financial Profile and Commitments screen (profile.fxml).
 *
 * OOP concepts:
 * 1. Inheritance: Extends BaseController to inherit common dialogs and runSafely error handling.
 * 2. Event-driven UI programming: JavaFX ObservableLists, TableView SelectionModel listeners,
 *    and form state management.
 */
public class ProfileController extends BaseController {

    // --- 1. Summary Card ---
    @FXML private Label summaryPlaceholder;
    @FXML private javafx.scene.layout.VBox summaryContent;
    @FXML private Label kpiIncome;
    @FXML private Label kpiExpenses;
    @FXML private Label kpiCommitments;
    @FXML private Label kpiTarget;
    @FXML private Label kpiDisposable;
    @FXML private Label kpiReserve;
    @FXML private Label summaryFootnote;

    // --- 2. Profile Card ---
    @FXML private TextField incomeField;
    @FXML private TextField fixedExpensesField;
    @FXML private TextField variableExpensesField;
    @FXML private TextField savingsField;
    @FXML private TextField savingsTargetField;
    @FXML private Label profileErrorLabel;
    @FXML private Button saveProfileButton;
    @FXML private Label behaviourSummaryLabel;

    // --- 3. Commitments Card ---
    @FXML private TableView<Commitment> commitmentsTable;
    @FXML private TableColumn<Commitment, String> colName;
    @FXML private TableColumn<Commitment, String> colType;
    @FXML private TableColumn<Commitment, String> colAmount;
    @FXML private TableColumn<Commitment, String> colStart;
    @FXML private TableColumn<Commitment, String> colEnd;
    @FXML private TableColumn<Commitment, String> colStatus;

    @FXML private TextField commitmentNameField;
    @FXML private ComboBox<CommitmentType> commitmentTypeCombo;
    @FXML private TextField commitmentAmountField;
    @FXML private DatePicker commitmentStartDatePicker;
    @FXML private DatePicker commitmentEndDatePicker;
    @FXML private Label commitmentErrorLabel;
    @FXML private Button addCommitmentButton;
    @FXML private Button updateCommitmentButton;
    @FXML private Button deleteCommitmentButton;

    private final FinancialService financialService = new FinancialService();
    private final BehaviourService behaviourService = new BehaviourService();

    @FXML
    private void initialize() {
        // Setup ComboBox
        commitmentTypeCombo.setItems(FXCollections.observableArrayList(CommitmentType.values()));
        commitmentStartDatePicker.setValue(LocalDate.now());

        // Setup Table Columns
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colType.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getType().getLabel()));
        colAmount.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getMonthlyAmount().format()));
        colStart.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStartDate().toString()));
        colEnd.setCellValueFactory(data -> {
            LocalDate end = data.getValue().getEndDate();
            return new SimpleStringProperty(end != null ? end.toString() : "Ongoing");
        });
        colStatus.setCellValueFactory(data -> {
            Commitment c = data.getValue();
            LocalDate today = LocalDate.now();
            String status;
            if (today.isBefore(c.getStartDate())) {
                status = "Upcoming";
            } else if (c.getEndDate() != null && today.isAfter(c.getEndDate())) {
                status = "Ended";
            } else {
                status = "Active";
            }
            return new SimpleStringProperty(status);
        });

        // Table selection listener
        commitmentsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                populateCommitmentForm(newVal);
                updateCommitmentButton.setDisable(false);
                deleteCommitmentButton.setDisable(false);
            } else {
                updateCommitmentButton.setDisable(true);
                deleteCommitmentButton.setDisable(true);
            }
        });

        // FXML loading is also used by the unauthenticated screen smoke test.
        // Do not call user-scoped services until a session exists; otherwise
        // runSafely would open a modal login alert while FXMLLoader is running.
        if (SessionManager.getInstance().isLoggedIn()) {
            loadProfileData();
            loadCommitmentsData();
            refreshSummary();
            refreshBehaviourSummary();
        }
    }

    // ---------------------------------------------------------------- Profile Actions

    private void loadProfileData() {
        runSafely(() -> {
            financialService.getProfile().ifPresent(profile -> {
                incomeField.setText(profile.getMonthlyIncome().toPlainString());
                fixedExpensesField.setText(profile.getFixedExpenses().toPlainString());
                variableExpensesField.setText(profile.getVariableExpenses().toPlainString());
                savingsField.setText(profile.getCurrentSavings().toPlainString());
                savingsTargetField.setText(profile.getMonthlySavingsTarget().toPlainString());
            });
        });
    }

    private void refreshBehaviourSummary() {
        runSafely(() -> behaviourSummaryLabel.setText(behaviourService.currentProfile()
                .map(profile -> profile.type().name().replace('_',' '))
                .orElse("Questionnaire not completed yet.")));
    }

    @FXML private void onContinueToQuestionnaire() {
        runSafely(() -> {
            if (!financialService.hasProfile()) {
                showProfileError("Save a financial profile before continuing to the questionnaire.");
                return;
            }
            SceneManager.showAppScreen("/fxml/behaviour.fxml");
        });
    }

    @FXML private void onRetakeQuestionnaire() {
        runSafely(() -> SceneManager.showAppScreen("/fxml/behaviour.fxml"));
    }

    @FXML
    private void onSaveProfile() {
        hideProfileError();
        runSafely(() -> {
            try {
                FinancialProfile saved = financialService.saveProfile(
                        incomeField.getText(),
                        fixedExpensesField.getText(),
                        variableExpensesField.getText(),
                        savingsField.getText(),
                        savingsTargetField.getText()
                );
                showInfo("Profile Saved", "Your financial baseline profile has been updated.");
                refreshSummary();
            } catch (ValidationException e) {
                showProfileError(e.getMessage());
            }
        });
    }

    // ---------------------------------------------------------------- Commitments Actions

    private void loadCommitmentsData() {
        runSafely(() -> {
            List<Commitment> list = financialService.getCommitments();
            commitmentsTable.setItems(FXCollections.observableArrayList(list));
        });
    }

    private void populateCommitmentForm(Commitment c) {
        hideCommitmentError();
        commitmentNameField.setText(c.getName());
        commitmentTypeCombo.setValue(c.getType());
        commitmentAmountField.setText(c.getMonthlyAmount().toPlainString());
        commitmentStartDatePicker.setValue(c.getStartDate());
        commitmentEndDatePicker.setValue(c.getEndDate());
    }

    @FXML
    private void onClearCommitmentForm() {
        commitmentsTable.getSelectionModel().clearSelection();
        commitmentNameField.clear();
        commitmentTypeCombo.getSelectionModel().clearSelection();
        commitmentAmountField.clear();
        commitmentStartDatePicker.setValue(LocalDate.now());
        commitmentEndDatePicker.setValue(null);
        hideCommitmentError();
    }

    @FXML
    private void onAddCommitment() {
        hideCommitmentError();
        runSafely(() -> {
            try {
                financialService.addCommitment(
                        commitmentNameField.getText(),
                        commitmentTypeCombo.getValue(),
                        commitmentAmountField.getText(),
                        commitmentStartDatePicker.getValue(),
                        commitmentEndDatePicker.getValue()
                );
                onClearCommitmentForm();
                loadCommitmentsData();
                refreshSummary();
            } catch (ValidationException e) {
                showCommitmentError(e.getMessage());
            }
        });
    }

    @FXML
    private void onUpdateCommitment() {
        hideCommitmentError();
        Commitment selected = commitmentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        runSafely(() -> {
            try {
                financialService.updateCommitment(
                        selected.getId(),
                        commitmentNameField.getText(),
                        commitmentTypeCombo.getValue(),
                        commitmentAmountField.getText(),
                        commitmentStartDatePicker.getValue(),
                        commitmentEndDatePicker.getValue()
                );
                loadCommitmentsData();
                refreshSummary();
                showInfo("Commitment Updated", "The commitment details have been saved.");
            } catch (ValidationException e) {
                showCommitmentError(e.getMessage());
            }
        });
    }

    @FXML
    private void onDeleteCommitment() {
        hideCommitmentError();
        Commitment selected = commitmentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (confirm("Delete Commitment", "Are you sure you want to delete '" + selected.getName() + "'?")) {
            runSafely(() -> {
                financialService.deleteCommitment(selected.getId());
                onClearCommitmentForm();
                loadCommitmentsData();
                refreshSummary();
            });
        }
    }

    // ---------------------------------------------------------------- Summary

    private void refreshSummary() {
        try {
            FinancialSummary summary = financialService.calculateSummary();

            summaryPlaceholder.setVisible(false);
            summaryPlaceholder.setManaged(false);

            summaryContent.setVisible(true);
            summaryContent.setManaged(true);

            kpiIncome.setText(summary.income().format());
            kpiExpenses.setText(summary.totalExpenses().format());
            kpiCommitments.setText(summary.activeCommitments().format());
            kpiTarget.setText(summary.savingsTarget().format());

            // Disposable income formatting & warning style if negative
            kpiDisposable.setText(summary.disposableIncome().format());
            if (summary.disposableIncome().isNegative()) {
                kpiDisposable.getStyleClass().removeAll("text-positive");
                if (!kpiDisposable.getStyleClass().contains("text-danger")) {
                    kpiDisposable.getStyleClass().add("text-danger");
                }
            } else {
                kpiDisposable.getStyleClass().removeAll("text-danger");
                if (!kpiDisposable.getStyleClass().contains("text-positive")) {
                    kpiDisposable.getStyleClass().add("text-positive");
                }
            }

            kpiReserve.setText(summary.emergencyReserve().format());

            int ratioPct = (int) Math.round(summary.commitmentRatio() * 100);
            summaryFootnote.setText(String.format("Commitments use %d%% of your income", ratioPct));

        } catch (IncompleteProfileException e) {
            summaryPlaceholder.setVisible(true);
            summaryPlaceholder.setManaged(true);
            summaryContent.setVisible(false);
            summaryContent.setManaged(false);
        }
    }

    // ---------------------------------------------------------------- Error UI Helpers

    private void showProfileError(String message) {
        profileErrorLabel.setText(message);
        profileErrorLabel.setVisible(true);
        profileErrorLabel.setManaged(true);
    }

    private void hideProfileError() {
        profileErrorLabel.setVisible(false);
        profileErrorLabel.setManaged(false);
    }

    private void showCommitmentError(String message) {
        commitmentErrorLabel.setText(message);
        commitmentErrorLabel.setVisible(true);
        commitmentErrorLabel.setManaged(true);
    }

    private void hideCommitmentError() {
        commitmentErrorLabel.setVisible(false);
        commitmentErrorLabel.setManaged(false);
    }
}


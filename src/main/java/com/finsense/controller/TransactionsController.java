package com.finsense.controller;

import com.finsense.model.Category;
import com.finsense.model.MonthlySummary;
import com.finsense.model.Transaction;
import com.finsense.model.enums.TransactionType;
import com.finsense.service.TransactionService;
import com.finsense.util.CsvExporter;
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
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Controller of transactions.fxml: record, edit, filter and export income/expenses.
 *
 * It is only a view adapter - every rule (validation, user isolation) lives in
 * TransactionService, so the controller just moves values between controls and the service.
 */
public class TransactionsController extends BaseController {

    @FXML private TableView<Transaction> transactionTable;
    @FXML private TableColumn<Transaction, String> dateCol;
    @FXML private TableColumn<Transaction, String> typeCol;
    @FXML private TableColumn<Transaction, String> categoryCol;
    @FXML private TableColumn<Transaction, String> amountCol;
    @FXML private TableColumn<Transaction, String> descriptionCol;

    // Filter bar
    @FXML private DatePicker fromDate;
    @FXML private DatePicker throughDate;
    @FXML private ComboBox<TransactionType> filterType;
    @FXML private ComboBox<Category> filterCategory;
    @FXML private TextField searchField;

    // Add / edit form
    @FXML private ComboBox<TransactionType> typeField;
    @FXML private ComboBox<Category> categoryField;
    @FXML private TextField amountField;
    @FXML private DatePicker dateField;
    @FXML private TextField descriptionField;
    @FXML private Button updateButton;
    @FXML private Button deleteButton;
    @FXML private Label errorLabel;

    // Monthly totals
    @FXML private Label incomeTotal;
    @FXML private Label expenseTotal;
    @FXML private Label netTotal;

    private final TransactionService service = new TransactionService();

    /** Rows currently shown - exported as CSV exactly as the user filtered them. */
    private List<Transaction> displayed = List.of();

    @FXML
    private void initialize() {
        filterType.setItems(FXCollections.observableArrayList(TransactionType.values()));
        typeField.setItems(FXCollections.observableArrayList(TransactionType.values()));
        typeField.setValue(TransactionType.EXPENSE);
        dateField.setValue(LocalDate.now());

        // Each column turns a Transaction record into display text (lambda = cell value factory).
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().date().toString()));
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().type().getLabel()));
        categoryCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().category().name()));
        amountCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().amount().format()));
        descriptionCol.setCellValueFactory(c -> new SimpleStringProperty(orEmpty(c.getValue().description())));

        // The category list depends on the chosen type (income vs expense categories).
        typeField.valueProperty().addListener((obs, oldType, newType) -> loadCategories(categoryField, newType));
        filterType.valueProperty().addListener((obs, oldType, newType) -> loadCategories(filterCategory, newType));

        transactionTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldRow, row) -> fillForm(row));

        if (SessionManager.getInstance().isLoggedIn()) {
            loadCategories(categoryField, TransactionType.EXPENSE);
            reload();
        }
    }

    /** Copies the selected row into the form so it can be edited. */
    private void fillForm(Transaction row) {
        boolean selected = row != null;
        updateButton.setDisable(!selected);
        deleteButton.setDisable(!selected);
        if (!selected) {
            return;
        }
        typeField.setValue(row.type());
        loadCategories(categoryField, row.type());
        categoryField.setValue(row.category());
        amountField.setText(row.amount().toPlainString());
        dateField.setValue(row.date());
        descriptionField.setText(row.description());
    }

    private void loadCategories(ComboBox<Category> box, TransactionType type) {
        if (type == null || !SessionManager.getInstance().isLoggedIn()) {
            box.getItems().clear();
            return;
        }
        runSafely(() -> {
            Category previous = box.getValue();
            box.setItems(FXCollections.observableArrayList(service.categories(type)));
            // Keep the previous choice if it is still valid for this type.
            if (previous != null && previous.type() == type) {
                box.setValue(box.getItems().stream()
                        .filter(c -> c.id().equals(previous.id()))
                        .findFirst()
                        .orElse(null));
            }
        });
    }

    @FXML
    private void onFilter() {
        reload();
    }

    private void reload() {
        runSafely(() -> {
            displayed = service.find(fromDate.getValue(), throughDate.getValue(), filterType.getValue(),
                    idOf(filterCategory.getValue()), searchField.getText());
            transactionTable.setItems(FXCollections.observableArrayList(displayed));

            MonthlySummary summary = service.monthlySummary(YearMonth.now());
            incomeTotal.setText(summary.income().format());
            expenseTotal.setText(summary.expenses().format());
            netTotal.setText(summary.net().format());
        });
    }

    @FXML
    private void onAdd() {
        save(false);
    }

    @FXML
    private void onUpdate() {
        save(true);
    }

    private void save(boolean update) {
        hideInlineError(errorLabel);
        Transaction selected = transactionTable.getSelectionModel().getSelectedItem();
        runSafely(() -> {
            Long categoryId = idOf(categoryField.getValue());
            if (update && selected != null) {
                service.update(selected.id(), categoryId, typeField.getValue(), amountField.getText(),
                        dateField.getValue(), descriptionField.getText());
            } else {
                service.add(categoryId, typeField.getValue(), amountField.getText(),
                        dateField.getValue(), descriptionField.getText());
            }
            clearForm();
            reload();
        });
    }

    @FXML
    private void onDelete() {
        Transaction row = transactionTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            return;
        }
        if (confirm("Delete transaction", "Delete this transaction?")) {
            runSafely(() -> {
                service.delete(row.id());
                clearForm();
                reload();
            });
        }
    }

    @FXML
    private void onClear() {
        clearForm();
    }

    private void clearForm() {
        transactionTable.getSelectionModel().clearSelection();
        amountField.clear();
        descriptionField.clear();
        typeField.setValue(TransactionType.EXPENSE);
        dateField.setValue(LocalDate.now());
        categoryField.setValue(null);
        updateButton.setDisable(true);
        deleteButton.setDisable(true);
        hideInlineError(errorLabel);
    }

    @FXML
    private void onExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export transactions as CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        chooser.setInitialFileName("transactions.csv");

        File file = chooser.showSaveDialog(transactionTable.getScene().getWindow());
        if (file == null) {
            return;                                   // user cancelled the dialog
        }
        try {
            CsvExporter.exportTransactions(file.toPath(), displayed);
            showInfo("CSV exported", "Transactions saved to " + file.getName());
        } catch (IOException e) {
            showInlineError(errorLabel, "Could not write the CSV file.");
        }
    }

    private static Long idOf(Category category) {
        return category == null ? null : category.id();
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }
}

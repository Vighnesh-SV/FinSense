package com.finsense.controller;

import com.finsense.model.BudgetStatus;
import com.finsense.model.Category;
import com.finsense.model.Money;
import com.finsense.service.BudgetService;
import com.finsense.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.YearMonth;
import java.util.List;

/** Controller of budgets.fxml - monthly spending limits per category. */
public class BudgetsController extends BaseController {

    private static final double NEAR_LIMIT = 0.8;

    @FXML private Label monthTitle;
    @FXML private Label totalLabel;
    @FXML private VBox budgetList;
    @FXML private ComboBox<Category> categoryCombo;
    @FXML private TextField limitField;
    @FXML private Label errorLabel;
    @FXML private Button saveButton;

    private final BudgetService service = new BudgetService();

    @FXML
    private void initialize() {
        monthTitle.setText(YearMonth.now().format(UiKit.MONTH));
        if (SessionManager.getInstance().isLoggedIn()) {
            runSafely(() -> categoryCombo.setItems(
                    FXCollections.observableArrayList(service.budgetableCategories())));
            reload();
        }
    }

    private void reload() {
        runInBackground(() -> service.statuses(YearMonth.now()), this::show,
                message -> showInlineError(errorLabel, message));
    }

    private void show(List<BudgetStatus> budgets) {
        budgetList.getChildren().clear();
        if (budgets.isEmpty()) {
            budgetList.getChildren().add(UiKit.emptyState(
                    "No budgets yet. Pick a category on the right - Dining Out or Shopping are good places to start."));
            totalLabel.setText("");
            return;
        }
        Money limit = Money.ZERO;
        Money spent = Money.ZERO;
        for (BudgetStatus b : budgets) {
            limit = limit.plus(b.monthlyLimit());
            spent = spent.plus(b.spentThisMonth());
            budgetList.getChildren().add(row(b));
        }
        totalLabel.setText(spent.format() + " of " + limit.format() + " budgeted");
    }

    /** One budget: name, amounts, coloured bar and a remove button. */
    private VBox row(BudgetStatus b) {
        Label name = new Label(b.category().name());
        name.getStyleClass().add("section-title");
        Label state = new Label(b.isOver()
                ? "Over by " + b.spentThisMonth().minus(b.monthlyLimit()).format()
                : b.remaining().format() + " left");
        state.getStyleClass().add(b.isOver() ? "text-danger" : "muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button remove = new Button("Remove");
        remove.getStyleClass().add("btn-ghost");
        remove.setOnAction(e -> onDelete(b));
        HBox top = new HBox(12, name, spacer, state, remove);
        top.setAlignment(Pos.CENTER_LEFT);

        ProgressBar bar = new ProgressBar(Math.min(1.0, b.usedRatio()));
        bar.setMaxWidth(Double.MAX_VALUE);
        if (b.isOver()) {
            bar.getStyleClass().add("over");
        } else if (b.usedRatio() >= NEAR_LIMIT) {
            bar.getStyleClass().add("near");
        }
        Label detail = new Label(b.spentThisMonth().format() + " spent of " + b.monthlyLimit().format()
                + "  ·  " + Math.round(b.usedRatio() * 100) + "%");
        detail.getStyleClass().add("muted");

        VBox row = new VBox(8, top, bar, detail);
        row.getStyleClass().add("card-quiet");
        return row;
    }

    @FXML
    private void onSave() {
        hideInlineError(errorLabel);
        Category category = categoryCombo.getValue();
        String limit = limitField.getText();
        runInBackground(() -> {
            service.save(category, limit);
            return true;
        }, ok -> {
            limitField.clear();
            categoryCombo.setValue(null);
            reload();
        }, message -> showInlineError(errorLabel, message), saveButton);
    }

    private void onDelete(BudgetStatus budget) {
        if (confirm("Remove budget", "Remove the " + budget.category().name() + " budget?")) {
            runSafely(() -> {
                service.delete(budget.budgetId());
                reload();
            });
        }
    }
}

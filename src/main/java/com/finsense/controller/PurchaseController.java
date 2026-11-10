package com.finsense.controller;

import com.finsense.decision.DecisionResult;
import com.finsense.decision.Recommendation;
import com.finsense.model.Category;
import com.finsense.model.PurchaseEvaluation;
import com.finsense.model.enums.Necessity;
import com.finsense.service.PurchaseService;
import com.finsense.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Controller of purchase.fxml - the Purchase Advisor.
 * The scoring itself lives in the pure-Java decision package; this class only
 * collects the input and shows the BUY / CONSIDER / WAIT / AVOID verdict.
 */
public class PurchaseController extends BaseController {

    @FXML private TextField productNameField;
    @FXML private TextField priceField;
    @FXML private ComboBox<Category> categoryCombo;
    @FXML private ComboBox<Necessity> necessityCombo;
    @FXML private TextArea descriptionField;
    @FXML private Button evaluateButton;
    @FXML private Label errorLabel;

    @FXML private VBox resultCard;
    @FXML private Label verdictLabel;
    @FXML private Label scoreLabel;
    @FXML private ListView<String> explanationList;
    @FXML private ListView<String> warningsList;

    private final PurchaseService service = new PurchaseService();

    @FXML
    private void initialize() {
        necessityCombo.setItems(FXCollections.observableArrayList(Necessity.values()));
        necessityCombo.setValue(Necessity.WANT);
        if (SessionManager.getInstance().isLoggedIn()) {
            runSafely(() -> categoryCombo.setItems(FXCollections.observableArrayList(service.categories())));
        }
    }

    @FXML
    private void onEvaluate() {
        hideInlineError(errorLabel);
        resultCard.setVisible(false);
        resultCard.setManaged(false);

        // Read the controls here, on the JavaFX thread, before starting the background task.
        String name = productNameField.getText();
        String price = priceField.getText();
        String description = descriptionField.getText();
        Category category = categoryCombo.getValue();
        Necessity necessity = necessityCombo.getValue();
        Long categoryId = category == null ? null : category.id();

        runInBackground(() -> service.evaluate(name, categoryId, price, necessity, description),
                this::showResult,
                message -> showInlineError(errorLabel, message),
                evaluateButton);
    }

    private void showResult(PurchaseEvaluation evaluation) {
        DecisionResult result = evaluation.decision();
        Recommendation recommendation = result.recommendation();

        verdictLabel.setText(recommendation.name());
        verdictLabel.getStyleClass().removeIf(style -> style.startsWith("badge-"));
        verdictLabel.getStyleClass().add("badge-" + recommendation.name().toLowerCase());
        scoreLabel.setText(result.score() + " / 100 · Evaluation #" + evaluation.purchaseId());

        explanationList.setItems(FXCollections.observableArrayList(result.explanationLines()));
        warningsList.setItems(FXCollections.observableArrayList(result.warnings()));
        resultCard.setVisible(true);
        resultCard.setManaged(true);
    }
}

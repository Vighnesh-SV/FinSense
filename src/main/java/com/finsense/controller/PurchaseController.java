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
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Controller of purchase.fxml - the Purchase advisor.
 * The scoring lives in the pure-Java decision package; this class only collects
 * the input and presents the verdict, its reasons and the smart-timing hint.
 */
public class PurchaseController extends BaseController {

    @FXML private TextField productNameField;
    @FXML private TextField priceField;
    @FXML private ComboBox<Category> categoryCombo;
    @FXML private ComboBox<Necessity> necessityCombo;
    @FXML private TextArea descriptionField;
    @FXML private Button evaluateButton;
    @FXML private Label errorLabel;

    @FXML private VBox emptyState;
    @FXML private VBox resultCard;
    @FXML private HBox verdictHero;
    @FXML private Label verdictLabel;
    @FXML private Label verdictSummary;
    @FXML private Label scoreLabel;
    @FXML private ProgressBar scoreBar;
    @FXML private Label timingLabel;
    @FXML private VBox reasonsBox;
    @FXML private VBox warningsBox;
    @FXML private Label savedLabel;

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
        UiKit.applyVerdict(verdictLabel, recommendation);
        UiKit.applyVerdict(verdictHero, recommendation);
        verdictSummary.setText(summary(recommendation));
        scoreLabel.setText(result.score() + " / 100");
        scoreBar.setProgress(result.score() / 100.0);

        boolean hasTiming = result.affordableFrom() != null;
        if (hasTiming) {
            timingLabel.setText("Smart timing: at your current saving rate this fits safely from "
                    + result.affordableFrom().format(UiKit.MONTH) + ".");
        }
        UiKit.show(timingLabel, hasTiming);

        UiKit.fillReasons(reasonsBox, result);
        warningsBox.getChildren().clear();
        for (String warning : result.warnings()) {
            Label note = new Label(warning);
            note.setWrapText(true);
            note.setMaxWidth(Double.MAX_VALUE);
            note.getStyleClass().add("info-note");
            warningsBox.getChildren().add(note);
        }
        savedLabel.setText("Saved as evaluation #" + evaluation.purchaseId()
                + ". Mark it bought or dropped in Purchase history.");

        UiKit.show(emptyState, false);
        UiKit.show(resultCard, true);
    }

    private static String summary(Recommendation recommendation) {
        return switch (recommendation) {
            case BUY -> "This fits comfortably in your finances right now.";
            case CONSIDER -> "Affordable, but think it over - something below deserves a look.";
            case WAIT -> "Better to hold off for now. The reasons below explain why.";
            case AVOID -> "This would strain your finances. Not recommended at the moment.";
        };
    }

    @FXML
    private void onOpenHistory() {
        if (main() != null) {
            main().navigate(Screen.PURCHASE_HISTORY);
        }
    }

    @FXML
    private void onOpenSimulator() {
        if (main() != null) {
            main().navigate(Screen.SIMULATOR);
        }
    }
}

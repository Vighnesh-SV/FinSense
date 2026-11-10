package com.finsense.controller;

import com.finsense.model.QuestionnaireResult;
import com.finsense.model.enums.BehaviourType;
import com.finsense.service.BehaviourService;
import com.finsense.util.SceneManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Controller of behaviour.fxml: the 12-question spending-habits questionnaire.
 * It collects the Likert answers (1-5); BehaviourService does the classification.
 */
public class BehaviourController extends BaseController {

    @FXML private ComboBox<Integer> q1;
    @FXML private ComboBox<Integer> q2;
    @FXML private ComboBox<Integer> q3;
    @FXML private ComboBox<Integer> q4;
    @FXML private ComboBox<Integer> q5;
    @FXML private ComboBox<Integer> q6;
    @FXML private ComboBox<Integer> q7;
    @FXML private ComboBox<Integer> q8;
    @FXML private ComboBox<Integer> q9;
    @FXML private ComboBox<Integer> q10;
    @FXML private ComboBox<Integer> q11;
    @FXML private ComboBox<Integer> q12;

    @FXML private Label errorLabel;
    @FXML private Label resultType;
    @FXML private Label resultScores;
    @FXML private Label resultExplanation;
    @FXML private Button continueButton;

    private final BehaviourService service = new BehaviourService();

    @FXML
    private void initialize() {
        List<Integer> scale = IntStream.rangeClosed(1, 5).boxed().toList();
        for (ComboBox<Integer> answer : answers()) {
            answer.setItems(FXCollections.observableArrayList(scale));
        }
    }

    private List<ComboBox<Integer>> answers() {
        return List.of(q1, q2, q3, q4, q5, q6, q7, q8, q9, q10, q11, q12);
    }

    @FXML
    private void onSubmit() {
        hideInlineError(errorLabel);
        List<Integer> values = answers().stream().map(ComboBox::getValue).toList();
        if (values.stream().anyMatch(value -> value == null)) {
            showInlineError(errorLabel, "Answer all 12 questions first.");
            return;
        }
        runSafely(() -> {
            QuestionnaireResult result = service.submitQuestionnaire(values);
            BehaviourType type = service.currentProfile().orElseThrow().type();
            resultType.setText(type.name().replace('_', ' '));
            resultScores.setText("Impulsivity " + result.impulsivityScore()
                    + " · Volume " + result.volumeScore()
                    + " · Discipline " + result.disciplineScore());
            resultExplanation.setText(explanation(type));
            continueButton.setDisable(false);
        });
    }

    /** Switch expression over an enum - the compiler checks every case is covered. */
    private String explanation(BehaviourType type) {
        return switch (type) {
            case IMPULSE_BUYER ->
                    "Your answers point to a tendency to buy quickly. A pause before non-essential purchases can help.";
            case HEAVY_BUYER ->
                    "Your answers show frequent or unnecessary purchases. Reviewing spending patterns may help.";
            case SMART_SHOPPER ->
                    "Your answers show considered spending and helpful savings habits.";
        };
    }

    @FXML
    private void onDashboard() {
        SceneManager.showMainScreen("/fxml/main_layout.fxml");
    }
}

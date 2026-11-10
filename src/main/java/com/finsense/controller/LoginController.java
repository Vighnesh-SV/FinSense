package com.finsense.controller;

import com.finsense.service.UserService;
import com.finsense.service.FinancialService;
import com.finsense.service.BehaviourService;
import com.finsense.util.SceneManager;
import com.finsense.util.DemoDataLoader;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controller for login.fxml - the first screen of the application.
 *
 * The controller only reads the form and reacts to the result; checking the
 * password is the job of UserService (layered architecture). Because PBKDF2
 * hashing takes ~0.3 s, the login runs on a background thread via the
 * inherited BaseController.runInBackground(...) so the window never freezes.
 */
public class LoginController extends BaseController {

    private static final String MAIN_LAYOUT = "/fxml/main_layout.fxml";
    private static final String REGISTER_SCREEN = "/fxml/register.fxml";

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private Button demoButton;

    private final UserService userService = new UserService();

    @FXML
    private void onLoadDemo() {
        hideInlineError(errorLabel);
        runInBackground(() -> {
            new DemoDataLoader().load();
            return MAIN_LAYOUT;
        }, screen -> SceneManager.showMainScreen(screen),
                message -> showInlineError(errorLabel, message), demoButton, loginButton);
    }

    @FXML
    private void onLogin() {
        hideInlineError(errorLabel);

        // Read the fields on the JavaFX thread; the worker thread must not touch controls.
        String username = usernameField.getText();
        String password = passwordField.getText();

        // Quick checks that need no database - give instant feedback.
        if (username == null || username.isBlank()) {
            showInlineError(errorLabel, "Username is required.");
            return;
        }
        if (password == null || password.isEmpty()) {
            showInlineError(errorLabel, "Password is required.");
            return;
        }

        runInBackground(
                () -> {
                    var user = userService.login(username, password);
                    new BehaviourService().refreshSpendingClassification();
                    if (!new FinancialService().hasProfile()) return "/fxml/profile.fxml";
                    if (!new BehaviourService().hasQuestionnaire()) return "/fxml/behaviour.fxml";
                    return MAIN_LAYOUT;
                },
                screen -> { if (MAIN_LAYOUT.equals(screen)) SceneManager.showMainScreen(screen); else SceneManager.showAppScreen(screen); },
                message -> {
                    passwordField.clear();
                    showInlineError(errorLabel, message);
                },
                loginButton, usernameField, passwordField);
    }

    @FXML
    private void onGoToRegister() {
        SceneManager.showAuthScreen(REGISTER_SCREEN);
    }
}

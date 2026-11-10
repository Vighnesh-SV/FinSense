package com.finsense.controller;

import com.finsense.service.UserService;
import com.finsense.util.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controller for register.fxml.
 *
 * All rules (name required, username format, password strength, matching
 * confirmation, unique username) are enforced by UserService.register - the
 * controller only passes the typed values and shows the outcome. A successful
 * registration logs the user in and opens the main application.
 */
public class RegisterController extends BaseController {

    private static final String PROFILE_SCREEN = "/fxml/profile.fxml";
    private static final String LOGIN_SCREEN = "/fxml/login.fxml";

    @FXML private TextField fullNameField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;
    @FXML private Button registerButton;

    private final UserService userService = new UserService();

    @FXML
    private void onRegister() {
        hideInlineError(errorLabel);

        // Snapshot the field values on the JavaFX thread before starting the worker.
        String fullName = fullNameField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        runInBackground(
                () -> userService.register(fullName, username, password, confirm),
                user -> SceneManager.showAppScreen(PROFILE_SCREEN),
                message -> showInlineError(errorLabel, message),
                registerButton);
    }

    @FXML
    private void onGoToLogin() {
        SceneManager.showAuthScreen(LOGIN_SCREEN);
    }
}

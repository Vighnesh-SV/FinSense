package com.finsense.controller;

import com.finsense.exception.AuthenticationException;
import com.finsense.exception.FinSenseException;
import com.finsense.exception.IncompleteProfileException;
import com.finsense.exception.ValidationException;
import com.finsense.util.SceneManager;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Parent class of every JavaFX controller.
 *
 * Gives all screens the same dialogs and the same error handling, written
 * once. It is abstract because a "generic controller" on its own has no screen
 * - only its subclasses (LoginController, DashboardController ...) are used.
 */
public abstract class BaseController {

    private MainController mainController;

    /** Called by MainController after loading a screen into the content area. */
    void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    /** The main layout, so a screen can navigate elsewhere (e.g. "Go to Profile"). */
    protected MainController main() {
        return mainController;
    }

    /**
     * Runs a UI action and turns any FinSense exception into a friendly dialog.
     * Controllers call:  runSafely(() -> service.save(...));
     * so no screen ever crashes because of invalid input or a database problem.
     */
    protected void runSafely(Runnable action) {
        try {
            action.run();
        } catch (ValidationException e) {
            showError("Please check your input", e.getMessage());
        } catch (IncompleteProfileException e) {
            showError("Profile incomplete", e.getMessage());
            if (mainController != null) {
                mainController.navigate(Screen.PROFILE);
            }
        } catch (AuthenticationException e) {
            showError("Not signed in", e.getMessage());
        } catch (FinSenseException e) {
            e.printStackTrace();
            showError("Something went wrong", e.getMessage());
        }
    }

    /**
     * Runs slow work (password hashing, large queries) on a background thread.
     *
     * Multithreading in JavaFX: the JavaFX Application Thread draws the window
     * and handles clicks. If it is busy for 0.3 s the window freezes. A
     * javafx.concurrent.Task runs the work on a separate worker thread, and its
     * setOnSucceeded / setOnFailed handlers are called back on the JavaFX
     * thread - the only thread allowed to change controls.
     *
     * @param work      what to do in the background (must NOT touch JavaFX controls)
     * @param onSuccess called on the JavaFX thread with the result
     * @param onError   called on the JavaFX thread with a user-friendly message
     * @param disable   controls (e.g. the submit button) disabled while running,
     *                  so the user cannot submit twice
     */
    protected <T> void runInBackground(Callable<T> work, Consumer<T> onSuccess,
                                       Consumer<String> onError, Node... disable) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        setDisabled(disable, true);
        task.setOnSucceeded(event -> {
            setDisabled(disable, false);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            setDisabled(disable, false);
            Throwable error = task.getException();
            if (error instanceof FinSenseException) {
                onError.accept(error.getMessage());
            } else {
                error.printStackTrace();       // a programming error - log it for the developer
                onError.accept("Unexpected error: " + error.getClass().getSimpleName());
            }
        });
        Thread worker = new Thread(task, "finsense-worker");
        worker.setDaemon(true);                // never keeps the app alive after the window closes
        worker.start();
    }

    private static void setDisabled(Node[] nodes, boolean disabled) {
        for (Node node : nodes) {
            node.setDisable(disabled);
        }
    }

    /** Shows a message in an inline error label (hidden labels take no space: managed = false). */
    protected void showInlineError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    protected void hideInlineError(Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }

    protected void showError(String header, String message) {
        alert(Alert.AlertType.ERROR, "Error", header, message).showAndWait();
    }

    protected void showInfo(String header, String message) {
        alert(Alert.AlertType.INFORMATION, "FinSense", header, message).showAndWait();
    }

    /** Shows an OK / Cancel dialog; true when the user pressed OK. */
    protected boolean confirm(String header, String message) {
        Optional<ButtonType> answer = alert(Alert.AlertType.CONFIRMATION, "Please confirm", header, message)
                .showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }

    private Alert alert(Alert.AlertType type, String title, String header, String message) {
        Alert alert = new Alert(type);
        if (SceneManager.getStage() != null) {
            alert.initOwner(SceneManager.getStage());
        }
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        return alert;
    }
}

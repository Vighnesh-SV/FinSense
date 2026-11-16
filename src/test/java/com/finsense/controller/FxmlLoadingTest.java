package com.finsense.controller;

import com.finsense.util.SceneManager;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads the real FXML files with their controllers. This catches mistakes such as
 * a misspelt fx:id, a missing @FXML field, or a wrong fx:controller class name -
 * errors that otherwise only appear when the screen is opened.
 *
 * JavaFX objects must be created on the JavaFX Application Thread, so each check
 * is run there with Platform.runLater and the test waits for the result.
 */
class FxmlLoadingTest {

    @BeforeAll
    static void startJavaFx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // toolkit already running - fine
        }
    }

    private static <T> T onFxThread(java.util.concurrent.Callable<T> task) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future.get(20, TimeUnit.SECONDS);
    }

    @Test
    void mainLayoutLoadsAndShowsDashboardPlaceholder() throws Exception {
        SceneManager.View<Object> view = onFxThread(() -> SceneManager.load("/fxml/main_layout.fxml"));

        assertInstanceOf(MainController.class, view.controller());
        Label title = (Label) view.root().lookup(".page-title");
        assertEquals("Dashboard", title.getText());
        Button active = (Button) view.root().lookup(".nav-button.active");
        assertEquals("Dashboard", active.getText());
    }

    @Test
    void navigationSwitchesScreenAndHighlight() throws Exception {
        String title = onFxThread(() -> {
            SceneManager.View<MainController> view = SceneManager.load("/fxml/main_layout.fxml");
            view.controller().navigate(Screen.PURCHASE_ADVISOR);
            Button active = (Button) view.root().lookup(".nav-button.active");
            assertEquals("Purchase advisor", active.getText());
            return ((Label) view.root().lookup(".page-title")).getText();
        });
        assertEquals("Purchase advisor", title);
    }

    @Test
    void loginAndRegisterScreensLoadWithCorrectControllers() throws Exception {
        SceneManager.View<Object> login = onFxThread(() -> SceneManager.load("/fxml/login.fxml"));
        assertInstanceOf(LoginController.class, login.controller());

        SceneManager.View<Object> register = onFxThread(() -> SceneManager.load("/fxml/register.fxml"));
        assertInstanceOf(RegisterController.class, register.controller());

        SceneManager.View<Object> profile = onFxThread(() -> SceneManager.load("/fxml/profile.fxml"));
        assertInstanceOf(ProfileController.class, profile.controller());

        SceneManager.View<Object> transactions = onFxThread(() -> SceneManager.load("/fxml/transactions.fxml"));
        assertInstanceOf(TransactionsController.class, transactions.controller());

        SceneManager.View<Object> savings = onFxThread(() -> SceneManager.load("/fxml/savings.fxml"));
        assertInstanceOf(SavingsController.class, savings.controller());

        SceneManager.View<Object> behaviour = onFxThread(() -> SceneManager.load("/fxml/behaviour.fxml"));
        assertInstanceOf(BehaviourController.class, behaviour.controller());

        SceneManager.View<Object> purchase = onFxThread(() -> SceneManager.load("/fxml/purchase.fxml"));
        assertInstanceOf(PurchaseController.class, purchase.controller());

        SceneManager.View<Object> history = onFxThread(() -> SceneManager.load("/fxml/purchase_history.fxml"));
        assertInstanceOf(PurchaseHistoryController.class, history.controller());

        SceneManager.View<Object> simulator = onFxThread(() -> SceneManager.load("/fxml/simulator.fxml"));
        assertInstanceOf(SimulatorController.class, simulator.controller());

        SceneManager.View<Object> dashboard = onFxThread(() -> SceneManager.load("/fxml/dashboard.fxml"));
        assertInstanceOf(DashboardController.class, dashboard.controller());

        SceneManager.View<Object> insights = onFxThread(() -> SceneManager.load("/fxml/insights.fxml"));
        assertInstanceOf(InsightsController.class, insights.controller());

        SceneManager.View<Object> budgets = onFxThread(() -> SceneManager.load("/fxml/budgets.fxml"));
        assertInstanceOf(BudgetsController.class, budgets.controller());
    }

    @Test
    void everySidebarScreenHasAButton() throws Exception {
        int buttons = onFxThread(() -> SceneManager.load("/fxml/main_layout.fxml").root()
                .lookupAll(".nav-button").size());
        assertEquals(Screen.values().length, buttons);
        assertTrue(buttons >= 8);
    }
}

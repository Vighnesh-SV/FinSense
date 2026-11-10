package com.finsense.controller;

import com.finsense.model.User;
import com.finsense.service.UserService;
import com.finsense.util.SceneManager;
import com.finsense.util.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.util.EnumMap;
import java.util.Map;

/**
 * Controller of main_layout.fxml: the sidebar, the header and the content area.
 *
 * The sidebar is defined only once. Clicking a sidebar button loads that
 * screen's FXML and puts it into the centre (contentArea) - the rest of the
 * window stays the same.
 */
public class MainController extends BaseController {

    private static final String LOGIN_SCREEN = "/fxml/login.fxml";

    @FXML private StackPane contentArea;
    @FXML private Label pageTitle;
    @FXML private Label userName;
    @FXML private Label userInitial;

    @FXML private Button navDashboard;
    @FXML private Button navTransactions;
    @FXML private Button navSavings;
    @FXML private Button navAdvisor;
    @FXML private Button navHistory;
    @FXML private Button navSimulator;
    @FXML private Button navInsights;
    @FXML private Button navProfile;

    /** EnumMap: a Map whose keys are enum constants - compact and fast. */
    private final Map<Screen, Button> navButtons = new EnumMap<>(Screen.class);

    private Screen currentScreen;

    /** Called automatically by FXMLLoader after all @FXML fields are injected. */
    @FXML
    private void initialize() {
        navButtons.put(Screen.DASHBOARD, navDashboard);
        navButtons.put(Screen.TRANSACTIONS, navTransactions);
        navButtons.put(Screen.SAVINGS_GOALS, navSavings);
        navButtons.put(Screen.PURCHASE_ADVISOR, navAdvisor);
        navButtons.put(Screen.PURCHASE_HISTORY, navHistory);
        navButtons.put(Screen.SIMULATOR, navSimulator);
        navButtons.put(Screen.INSIGHTS, navInsights);
        navButtons.put(Screen.PROFILE, navProfile);

        // Event handling with lambdas: each button navigates to its own screen.
        navButtons.forEach((screen, button) -> button.setOnAction(event -> navigate(screen)));

        String name = SessionManager.getInstance().getCurrentUser()
                .map(User::getFullName)
                .orElse("Guest (preview)");
        userName.setText(name);
        userInitial.setText(name.substring(0, 1).toUpperCase());

        navigate(Screen.DASHBOARD);
    }

    /** Shows a screen in the content area and highlights its sidebar button. */
    public void navigate(Screen screen) {
        runSafely(() -> {
            SceneManager.View<Object> view = SceneManager.load(screen.fxml());
            if (view.controller() instanceof BaseController controller) {   // polymorphism
                controller.setMainController(this);
            }
            contentArea.getChildren().setAll(view.root());
            currentScreen = screen;
            pageTitle.setText(screen.title());
            navButtons.values().forEach(b -> b.getStyleClass().remove("active"));
            navButtons.get(screen).getStyleClass().add("active");
        });
    }

    /** Reloads the current screen, e.g. after data changed elsewhere. */
    public void refresh() {
        if (currentScreen != null) {
            navigate(currentScreen);
        }
    }

    @FXML
    private void onLogout() {
        if (confirm("Log out", "Do you want to log out of FinSense?")) {
            new UserService().logout();   // delegates SessionManager.logout() via the service layer
            SceneManager.showAuthScreen(LOGIN_SCREEN);
        }
    }
}

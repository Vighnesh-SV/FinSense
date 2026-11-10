package com.finsense.util;

import com.finsense.exception.FinSenseException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Loads FXML screens and switches what the main window shows.
 *
 * The window (Stage) keeps ONE Scene for its whole life; switching screens
 * only replaces the Scene's root node. That keeps the window's size and
 * position when the user moves from the login screen to the dashboard.
 *
 * Two looks exist:
 *  - auth screens (login / register): base.css + auth.css
 *  - the main application layout:     base.css + style.css
 */
public final class SceneManager {

    private static final String BASE_CSS = "/css/base.css";
    private static final String AUTH_CSS = "/css/auth.css";
    private static final String MAIN_CSS = "/css/style.css";

    private static Stage stage;
    private static Scene scene;

    private SceneManager() {
    }

    /** A loaded FXML file: its root node plus the controller object FXMLLoader created for it. */
    public record View<C>(Parent root, C controller) { }

    public static void init(Stage primaryStage) {
        stage = Objects.requireNonNull(primaryStage);
    }

    public static Stage getStage() {
        return stage;
    }

    /** Loads an FXML file from src/main/resources and returns its root and controller. */
    public static <C> View<C> load(String fxmlPath) {
        URL url = SceneManager.class.getResource(fxmlPath);
        if (url == null) {
            throw new FinSenseException("Screen not found: " + fxmlPath);
        }
        try {
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            return new View<>(root, loader.getController());
        } catch (IOException e) {
            throw new FinSenseException("Could not load screen " + fxmlPath + ": " + e.getMessage(), e);
        }
    }

    /** True if an FXML file exists (screens of later phases do not exist yet). */
    public static boolean exists(String fxmlPath) {
        return SceneManager.class.getResource(fxmlPath) != null;
    }

    /** Shows a login/registration style screen and returns its controller. */
    public static <C> C showAuthScreen(String fxmlPath) {
        View<C> view = load(fxmlPath);
        setRoot(view.root(), BASE_CSS, AUTH_CSS);
        return view.controller();
    }

    /** Shows the main application layout (sidebar + content) and returns its controller. */
    public static <C> C showMainScreen(String fxmlPath) {
        View<C> view = load(fxmlPath);
        setRoot(view.root(), BASE_CSS, MAIN_CSS);
        return view.controller();
    }

    /** Shows a signed-in application step outside the sidebar during onboarding. */
    public static <C> C showAppScreen(String fxmlPath) {
        View<C> view = load(fxmlPath);
        setRoot(view.root(), BASE_CSS, MAIN_CSS);
        return view.controller();
    }

    private static void setRoot(Parent root, String... stylesheets) {
        if (stage == null) {
            throw new IllegalStateException("SceneManager.init(stage) must be called first");
        }
        List<String> css = Arrays.stream(stylesheets)
                .map(path -> Objects.requireNonNull(SceneManager.class.getResource(path),
                        "Missing stylesheet " + path).toExternalForm())
                .toList();
        if (scene == null) {
            scene = new Scene(root, 1200, 760);
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
        scene.getStylesheets().setAll(css);
    }
}

package com.finsense.controller;

import com.finsense.database.DatabaseInitializer;
import com.finsense.database.TestDatabase;
import com.finsense.util.DemoDataLoader;
import com.finsense.util.SceneManager;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Not a normal test: renders every screen with the demo data into PNG files so the
 * design can be reviewed. Runs only when -Dfinsense.shots=<folder> is given, and
 * uses the finsense_test database.
 */
@EnabledIfSystemProperty(named = "finsense.shots", matches = ".+")
class ScreenshotTour {

    @Test
    void captureAllScreens() throws Exception {
        File dir = new File(System.getProperty("finsense.shots"));
        dir.mkdirs();
        new DatabaseInitializer(TestDatabase.freshEmptyDatabase()).initialize();
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // fine
        }

        Stage stage = onFx(() -> {
            Stage s = new Stage();
            SceneManager.init(s);
            return s;
        });
        onFx(() -> {
            SceneManager.showAuthScreen("/fxml/login.fxml");
            stage.show();
            return null;
        });
        shoot(stage, dir, "00-login");
        onFx(() -> SceneManager.showAuthScreen("/fxml/register.fxml"));
        shoot(stage, dir, "01-register");

        new DemoDataLoader().load();

        MainController main = onFx(() -> SceneManager.showMainScreen("/fxml/main_layout.fxml"));
        int i = 2;
        for (Screen screen : Screen.values()) {
            onFx(() -> {
                main.navigate(screen);
                return null;
            });
            Thread.sleep(1500);                          // let background loads finish
            shoot(stage, dir, String.format("%02d-%s", i++, screen.name().toLowerCase()));
        }

        // The advisor with a real verdict on screen.
        onFx(() -> {
            main.navigate(Screen.PURCHASE_ADVISOR);
            Parent root = stage.getScene().getRoot();
            root.applyCss();                             // builds the ScrollPane skin so lookup works
            root.layout();
            ((javafx.scene.control.TextField) root.lookup("#productNameField")).setText("Smart TV");
            ((javafx.scene.control.TextField) root.lookup("#priceField")).setText("42000");
            @SuppressWarnings("unchecked")
            javafx.scene.control.ComboBox<com.finsense.model.Category> combo =
                    (javafx.scene.control.ComboBox<com.finsense.model.Category>) root.lookup("#categoryCombo");
            combo.getItems().stream().filter(c -> c.name().equals("Electronics")).findFirst()
                    .ifPresent(combo::setValue);
            ((javafx.scene.control.Button) root.lookup("#evaluateButton")).fire();
            return null;
        });
        Thread.sleep(2500);
        shoot(stage, dir, "20-advisor-result");

        // The calendar popup of a DatePicker (it lives in its own window).
        onFx(() -> {
            main.navigate(Screen.SAVINGS_GOALS);
            Parent root = stage.getScene().getRoot();
            root.applyCss();
            root.layout();
            ((javafx.scene.control.DatePicker) root.lookup("#deadlinePicker")).show();
            return null;
        });
        Thread.sleep(800);
        onFx(() -> {
            javafx.stage.Window popup = javafx.stage.Window.getWindows().get(javafx.stage.Window.getWindows().size() - 1);
            WritableImage image = popup.getScene().snapshot(null);
            int w = (int) image.getWidth();
            int h = (int) image.getHeight();
            java.awt.image.BufferedImage png = new java.awt.image.BufferedImage(w, h,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
            }
            ImageIO.write(png, "png", new File(dir, "21-calendar.png"));
            return null;
        });
    }

    private static void shoot(Stage stage, File dir, String name) throws Exception {
        Thread.sleep(400);
        onFx(() -> {
            Scene scene = stage.getScene();
            Parent root = scene.getRoot();
            root.applyCss();
            root.layout();
            WritableImage image = scene.snapshot(null);
            int w = (int) image.getWidth();
            int h = (int) image.getHeight();
            java.awt.image.BufferedImage png = new java.awt.image.BufferedImage(w, h,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
            }
            ImageIO.write(png, "png", new File(dir, name + ".png"));
            return null;
        });
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> task) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future.get(60, TimeUnit.SECONDS);
    }
}

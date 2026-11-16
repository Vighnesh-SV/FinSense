package com.finsense;

import com.finsense.database.DatabaseConnection;
import com.finsense.database.DatabaseInitializer;
import com.finsense.exception.FinSenseException;
import com.finsense.util.SceneManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * The JavaFX application class.
 *
 * JavaFX life cycle: launch() -> init() -> start(Stage) -> ... -> stop().
 * start() receives the primary Stage (the window). Screens are loaded from
 * FXML files and swapped by SceneManager.
 */
public class FinSenseApp extends Application {

    public static final String APP_TITLE = "FinSense – Financial Intelligent Decision System";

    /** First screen shown after start-up. */
    public static final String START_SCREEN = "/fxml/login.fxml";

    @Override
    public void start(Stage stage) {
        stage.setTitle(APP_TITLE);
        stage.setMinWidth(1100);
        stage.setMinHeight(640);

        // 1. Prepare the database before showing any screen.
        try {
            DatabaseInitializer.InitResult db =
                    new DatabaseInitializer(DatabaseConnection.getInstance()).initialize();
            System.out.println("[FinSense] MySQL database " + DatabaseConnection.getInstance().getConfig().describe()
                    + (db.freshDatabase() ? " created" : " opened")
                    + " - " + db.tableCount() + " tables, " + db.categoryCount() + " categories");
        } catch (FinSenseException e) {
            showFatalError("The database could not be prepared.", e);
            return;
        }

        // 2. Show the first screen.
        SceneManager.init(stage);
        SceneManager.showAuthScreen(START_SCREEN);
        stage.show();
    }

    /** Called by JavaFX when the last window closes. */
    @Override
    public void stop() {
        DatabaseConnection.getInstance().close();
    }

    private void showFatalError(String headline, Exception e) {
        e.printStackTrace();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(APP_TITLE);
        alert.setHeaderText(headline);
        alert.setContentText(e.getMessage());
        alert.showAndWait();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package com.finsense.controller;

import com.finsense.decision.DecisionResult;
import com.finsense.decision.Reason;
import com.finsense.decision.Recommendation;
import com.finsense.model.Money;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * Small, reusable JavaFX helpers shared by the controllers (a utility class:
 * private constructor, only static methods). Keeping them here avoids copying
 * the same table and styling code into every screen.
 */
public final class UiKit {

    public static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");
    public static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("d MMM yy");
    public static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");
    public static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy");
    public static final DateTimeFormatter SHORT_MONTH = DateTimeFormatter.ofPattern("MMM yy");

    private static final List<String> VERDICT_CLASSES = List.of("v-buy", "v-consider", "v-wait", "v-avoid");

    private UiKit() {
    }

    /** Text column: the lambda turns a row object into the cell text. */
    public static <T> void text(TableColumn<T, String> column, Function<T, String> value) {
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
    }

    /** Right-aligned money column. */
    public static <T> void money(TableColumn<T, String> column, Function<T, Money> value) {
        column.setCellValueFactory(c -> {
            Money m = value.apply(c.getValue());
            return new SimpleStringProperty(m == null ? "—" : m.format());
        });
        column.getStyleClass().add("money");
    }

    /** Shows or hides a node and removes it from the layout while hidden. */
    public static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** Colours a label/region with the verdict's style class (v-buy, v-wait, ...). */
    public static void applyVerdict(Node node, Recommendation recommendation) {
        node.getStyleClass().removeAll(VERDICT_CLASSES);
        node.getStyleClass().add("v-" + recommendation.name().toLowerCase());
    }

    /** Small coloured badge, e.g. for table cells or headers. */
    public static Label badge(Recommendation recommendation) {
        Label badge = new Label(recommendation.name());
        badge.getStyleClass().addAll("badge", "badge-" + recommendation.name().toLowerCase());
        return badge;
    }

    /** Builds the "why" list: one row per reason with a coloured marker. */
    public static void fillReasons(VBox box, DecisionResult result) {
        box.getChildren().clear();
        for (Reason reason : result.reasons()) {
            Region dot = new Region();
            dot.getStyleClass().addAll("reason-dot", dotClass(reason));
            Label text = new Label(reason.message());
            text.setWrapText(true);
            text.getStyleClass().add("body-text");
            HBox row = new HBox(12, dot, text);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("reason-row");
            box.getChildren().add(row);
        }
    }

    private static String dotClass(Reason reason) {
        if (reason.learned()) {
            return "learn";
        }
        return switch (reason.severity()) {
            case INFO -> "good";
            case LOW -> "neutral";
            case CAUTION -> "warn";
            case HIGH, CRITICAL -> "bad";
        };
    }

    /** Centered grey message for an empty panel. */
    public static VBox emptyState(String message) {
        Label label = new Label(message);
        label.setWrapText(true);
        VBox box = new VBox(label);
        box.getStyleClass().add("empty-state");
        return box;
    }

    /** Rupees as a chart value (Money stores paise). */
    public static double rupees(Money money) {
        return money.getPaise() / 100.0;
    }
}

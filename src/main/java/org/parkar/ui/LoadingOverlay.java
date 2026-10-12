package org.parkar.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * A translucent overlay that blocks the UI while a background operation runs and
 * reports its completion as a percentage.
 */
public class LoadingOverlay extends StackPane {

    private final ProgressBar bar = new ProgressBar(0);
    private final Label percent = new Label("0%");
    private final Label message = new Label();

    public LoadingOverlay() {
        Rectangle dim = new Rectangle();
        dim.widthProperty().bind(widthProperty());
        dim.heightProperty().bind(heightProperty());
        dim.setFill(Color.rgb(0, 0, 0, 0.35));

        message.setWrapText(true);
        message.setMaxWidth(300);
        message.setStyle("-fx-font-size: 13px; -fx-text-fill: #333333;");

        bar.setPrefWidth(300);

        percent.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1565c0;");

        VBox card = new VBox(12, message, bar, percent);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(26, 30, 26, 30));
        card.setMaxSize(360, 170);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 20, 0, 0, 6);");

        getChildren().addAll(dim, card);
        StackPane.setAlignment(card, Pos.CENTER);

        setVisible(false);
        setManaged(false);
    }

    /** Shows the overlay and resets the progress to 0%. */
    public void show(String text) {
        setProgress(0);
        setMessage(text);
        setVisible(true);
        setManaged(true);
    }

    /** Updates the progress bar and the percentage label. */
    public void setProgress(double fraction) {
        double value = Math.max(0, Math.min(1, fraction));
        bar.setProgress(value);
        percent.setText(Math.round(value * 100) + "%");
    }

    /** Updates the descriptive message shown above the bar. */
    public void setMessage(String text) {
        message.setText(text == null ? "" : text);
    }

    /** Hides the overlay and releases it from layout. */
    public void hide() {
        setVisible(false);
        setManaged(false);
    }
}

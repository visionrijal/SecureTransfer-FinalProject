package com.securetransferfrontend.util;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class ToastUtil {
    public static void showToast(Node parent, String message, boolean success) {
        Platform.runLater(() -> {
            StackPane root = getStackPane(parent);
            if (root == null) return;
            Text toastText = new Text(message);
            toastText.getStyleClass().add("toast-text");
            StackPane toast = new StackPane(toastText);
            toast.getStyleClass().add("toast-pane");
            if (success) {
                toast.getStyleClass().add("toast-success");
            } else {
                toast.getStyleClass().add("toast-error");
            }
            toast.setMouseTransparent(true);
            toast.setOpacity(0);
            StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(toast, new Insets(0, 30, 30, 0));
            root.getChildren().add(toast);
            root.layout();
            toast.toFront();
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
            FadeTransition fadeOut = new FadeTransition(Duration.millis(600), toast);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setDelay(Duration.seconds(2));
            fadeOut.setOnFinished(e -> root.getChildren().remove(toast));
            fadeOut.play();
        });
    }

    private static StackPane getStackPane(Node node) {
        if (node == null) return null;
        if (node instanceof StackPane) return (StackPane) node;
        if (node.getParent() != null) return getStackPane(node.getParent());
        if (node instanceof Pane && ((Pane) node).getChildrenUnmodifiable().size() > 0) {
            for (Node child : ((Pane) node).getChildrenUnmodifiable()) {
                StackPane found = getStackPane(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}

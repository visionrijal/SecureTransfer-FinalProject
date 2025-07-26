package com.securetransferfrontend;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SecureTransferApp extends Application {
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        showLoginScene();
    }

    public static void showLoginScene() throws Exception {
        Parent root = FXMLLoader.load(SecureTransferApp.class.getResource("/fxml/login.fxml"));
        primaryStage.setTitle("SecureTransfer - Login");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void showRegistrationScene() throws Exception {
        Parent root = FXMLLoader.load(SecureTransferApp.class.getResource("/fxml/registration.fxml"));
        primaryStage.setTitle("SecureTransfer - Register");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void showMainScene() throws Exception {
        Parent root = FXMLLoader.load(SecureTransferApp.class.getResource("/fxml/main.fxml"));
        primaryStage.setTitle("SecureTransfer - Dashboard");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}


package com.securetransferfrontend.controller;

import com.securetransferfrontend.SecureTransferApp;
import com.securetransferfrontend.util.ToastUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ClassicHttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MainController {
    // Static flag/message for post-login toast
    public static boolean showWelcomeToast = false;
    public static String welcomeToastMessage = null;

    @FXML private Label welcomeLabel;
    @FXML private StackPane root;
    @FXML private VBox contentArea;

    @FXML
    private void initialize() {
        if (showWelcomeToast && welcomeToastMessage != null) {
            Platform.runLater(() -> {
                ToastUtil.showToast(root, welcomeToastMessage, true);
                showWelcomeToast = false;
                welcomeToastMessage = null;
            });
        }
    }

    // Track current username for session
    public static String currentUsername = null;

    /**
     * Set the current username after successful login
     */
    public static void setCurrentUsername(String username) {
        currentUsername = username;
    }

    @FXML
    private void handleLogout() {
        if (currentUsername != null && !currentUsername.isBlank()) {
            try {
                RegistrationController.logoutAndRedirect(currentUsername);
            } catch (Exception e) {
                ToastUtil.showToast(root, "Logout failed: " + e.getMessage(), false);
            }
        } else {
            ToastUtil.showToast(root, "Logout failed: No username found for session.", false);
            try {
                RegistrationController.logoutAndRedirect(""); // Attempt token cleanup with blank username
                SecureTransferApp.showLoginScene();
            } catch (Exception e) {
                ToastUtil.showToast(root, "Logout failed: " + e.getMessage(), false);
            }
        }
    }

    @FXML
    private void showSendFiles() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/send-files.fxml"));
            javafx.scene.Node sendFilesNode = loader.load();
            contentArea.getChildren().setAll(sendFilesNode);
        } catch (Exception e) {
            ToastUtil.showToast(root, "Failed to load Send Files view: " + e.getMessage(), false);
            e.printStackTrace();
        }
    }

    @FXML
    private void showReceiveFiles() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/receive-files.fxml"));
            javafx.scene.Node receiveFilesNode = loader.load();
            contentArea.getChildren().setAll(receiveFilesNode);
        } catch (Exception e) {
            ToastUtil.showToast(root, "Failed to load Receive Files view: " + e.getMessage(), false);
            e.printStackTrace();
        }
    }

    @FXML
    private void showUSBWizard() {
        // TODO: Implement showUSBWizard logic
    }

    @FXML
    private void showTransferHistory() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/history-view.fxml"));
            javafx.scene.Node historyNode = loader.load();
            contentArea.getChildren().setAll(historyNode);
        } catch (Exception e) {
            ToastUtil.showToast(root, "Failed to load History view: " + e.getMessage(), false);
            e.printStackTrace();
        }
    }

    @FXML
    private void scrollToFeatures() {
        // TODO: Implement scrollToFeatures logic
    }

    @FXML
    private void scrollToSecurity() {
        // TODO: Implement scrollToSecurity logic
    }

    @FXML
    private void showProfile() {
        // TODO: Implement showProfile logic
    }

    @FXML
    private void logout() {
        ToastUtil.showToast(root, "Logged out successfully.", true);
        // TODO: Add actual logout logic (clear session, redirect to login, etc.)
    }

    @FXML
    private void showAbout() {
        ToastUtil.showToast(root, "SecureTransfer v1.0 — Secure file transfer with end-to-end encryption.", true);
        // TODO: Show about dialog/modal
    }
}

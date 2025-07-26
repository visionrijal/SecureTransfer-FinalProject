package com.securetransferfrontend.controller;


import com.securetransferfrontend.SecureTransferApp;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import com.securetransferfrontend.util.ToastUtil;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;

public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    // Static flag/message for post-registration toast
    public static boolean showRegistrationToast = false;
    public static String registrationToastMessage = null;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;

    private static final String KEY_DIR = System.getProperty("user.home") + "/.securetransfer/";
    private String jwtToken;

    /**
     * Save JWT token to user's securetransfer directory
     */
    private void saveJwtToken(String username, String token) {
        try {
            java.nio.file.Path userDir = java.nio.file.Paths.get(KEY_DIR, username);
            java.nio.file.Files.createDirectories(userDir);
            java.nio.file.Files.writeString(userDir.resolve("jwt.token"), token);
        } catch (Exception e) {
            logger.warn("Failed to save JWT token: {}", e.getMessage());
        }
    }

    /**
     * Load JWT token for a user if it exists
     */
    public static String loadJwtToken(String username) {
        try {
            java.nio.file.Path jwtPath = java.nio.file.Paths.get(KEY_DIR, username, "jwt.token");
            if (java.nio.file.Files.exists(jwtPath)) {
                return java.nio.file.Files.readString(jwtPath);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @FXML
    private void initialize() {
        // Show registration toast if set
        if (showRegistrationToast && registrationToastMessage != null) {
            Platform.runLater(() -> {
                ToastUtil.showToast(loginButton.getScene().getRoot(), registrationToastMessage, true);
                showRegistrationToast = false;
                registrationToastMessage = null;
            });
        }
        // Auto-login if JWT exists for entered username
        if (usernameField != null) {
            usernameField.textProperty().addListener((obs, oldVal, newVal) -> {
                logger.info("Checking for saved JWT for user: {}", newVal);
                String token = loadJwtToken(newVal);
                if (token != null && !token.isBlank()) {
                    logger.info("Found saved JWT for user: {}. Logging in automatically.", newVal);
                    jwtToken = token;
                    com.securetransferfrontend.controller.MainController.setCurrentUsername(newVal);
                    showSuccess("Welcome back, " + newVal + "!");
                    // TODO: Proceed to main app/dashboard
                } else {
                    logger.info("No saved JWT found for user: {}", newVal);
                }
            });
        }
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        if (errorLabel != null) {
            errorLabel.setVisible(false);
        }
        if (username.isEmpty() || password.isEmpty()) {
            showError("Username and password are required.");
            return;
        }
        try {
            HttpClient client = HttpClients.createDefault();
            HttpPost post = new HttpPost("http://localhost:8080/api/auth/login");
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(java.util.Map.of(
                "username", username,
                "password", password
            ));
            post.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
            post.setHeader("Content-Type", "application/json");
            ClassicHttpResponse response = (ClassicHttpResponse) client.executeOpen(null, post, null);
            int status = response.getCode();
            logger.info("Backend login response code: {}", status);
            String resp = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
            if (status == HttpStatus.SC_OK) {
                JsonNode node = mapper.readTree(resp);
                if (node.has("token")) {
                    jwtToken = node.get("token").asText();
                    saveJwtToken(username, jwtToken);
                    logger.info("Login successful for user: {}", username);
                    // Set current username for session
                    com.securetransferfrontend.controller.MainController.setCurrentUsername(username);
                    showSuccess("Login successful!");
                    // TODO: Proceed to main app/dashboard
                } else {
                    showError("Login failed: No token received.");
                }
            } else {
                String errorMsg = parseErrorMessage(resp);
                logger.error("Login failed for user {}: {}", username, errorMsg);
                showError(errorMsg);
            }
        } catch (Exception e) {
            logger.error("Login error for user {}: {}", username, e.getMessage(), e);
            showError("Login error: " + e.getMessage());
        }
    }

    private String parseErrorMessage(String resp) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(resp);
            if (node.has("message")) {
                return node.get("message").asText();
            } else if (node.has("error")) {
                return node.get("error").asText();
            } else if (node.isTextual()) {
                return node.asText();
            } else {
                return "Login failed. Please try again.";
            }
        } catch (Exception e) {
            return resp != null && !resp.isBlank() ? resp : "Login failed. Please try again.";
        }
    }

    private void showToast(String message, boolean success) {
        Platform.runLater(() -> {
            if (loginButton != null && loginButton.getScene() != null) {
                ToastUtil.showToast(loginButton.getScene().getRoot(), message, success);
            }
        });
    }

    private void showError(String message) {
        logger.error("Error: {}", message);
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setVisible(true);
        }
        showToast(message, false);
    }

    private void showSuccess(String message) {
        logger.info("Success: {}", message);
        // Set flag/message for MainController to show toast after scene loads
        com.securetransferfrontend.controller.MainController.showWelcomeToast = true;
        com.securetransferfrontend.controller.MainController.welcomeToastMessage = message;
        try {
            SecureTransferApp.showMainScene();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleRegister() {
        try {
            SecureTransferApp.showRegistrationScene();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

package com.securetransferfrontend.controller;

import com.securetransferfrontend.util.ToastUtil;
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
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import com.securetransferfrontend.SecureTransferApp;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class RegistrationController {
    // Static flag/message for post-login toast
    public static boolean showLoginToast = false;
    public static String loginToastMessage = null;
    private static final Logger logger = LoggerFactory.getLogger(RegistrationController.class);
    @FXML private StackPane root;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button registerButton;
    @FXML private Label passwordStrengthLabel;
    @FXML private VBox strengthContainer;
    @FXML private Region strengthBar1;
    @FXML private Region strengthBar2;
    @FXML private Region strengthBar3;
    @FXML private Region strengthBar4;
    @FXML private Label passwordMatchLabel;

    private static final String KEY_DIR = System.getProperty("user.home") + "/.securetransfer/";

    @FXML
    private void handleRegister() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        logger.info("Attempting registration for user: {}", username);

        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            logger.warn("Registration failed: missing fields");
            showError("All fields are required.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            logger.warn("Registration failed: passwords do not match");
            showError("Passwords do not match.");
            return;
        }
        int strengthScore = getPasswordStrengthScore(password);
        if (strengthScore < 1) {
            logger.warn("Registration failed: password too weak");
            showError("Password is too weak. Please choose a stronger password (8+ characters, mix of letters, numbers, and symbols).");
            return;
        }
        try {
            // Generate and save key pair
            KeyPair keyPair = generateAndSaveKeyPair(username);
            String publicKeyPem = encodePublicKeyToPEM(keyPair.getPublic());
            // Backend call
            HttpClient client = HttpClients.createDefault();
            HttpPost post = new HttpPost("http://localhost:8080/api/auth/register");
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(java.util.Map.of(
                "username", username,
                "password", password,
                "publicKey", publicKeyPem
            ));
            post.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
            post.setHeader("Content-Type", "application/json");
            ClassicHttpResponse response = (ClassicHttpResponse) client.executeOpen(null, post, null);
            int status = response.getCode();
            logger.info("Backend registration response code: {}", status);
            if (status == HttpStatus.SC_OK) {
                logger.info("Registration successful for user: {}", username);
                showSuccess("Registration successful!");
            } else {
                String resp = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                String errorMsg = parseErrorMessage(resp);
                logger.error("Registration failed for user {}: {}", username, errorMsg);
                showError(errorMsg);
            }
        } catch (Exception e) {
            logger.error("Registration error for user {}: {}", username, e.getMessage(), e);
            showError("Registration error: " + e.getMessage());
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
                return "Registration failed. Please try again.";
            }
        } catch (Exception e) {
            return resp != null && !resp.isBlank() ? resp : "Registration failed. Please try again.";
        }
    }

    private KeyPair generateAndSaveKeyPair(String username) throws NoSuchAlgorithmException, IOException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        Path userDir = Paths.get(KEY_DIR, username);
        Files.createDirectories(userDir);
        // Save private key (PKCS8, PEM)
        String privateKeyPem = encodePrivateKeyToPEM(keyPair.getPrivate());
        Files.writeString(userDir.resolve("private_key.pem"), privateKeyPem);
        // Save public key (X.509, PEM)
        String publicKeyPem = encodePublicKeyToPEM(keyPair.getPublic());
        Files.writeString(userDir.resolve("public_key.pem"), publicKeyPem);
        return keyPair;
    }

    private String encodePrivateKeyToPEM(PrivateKey privateKey) {
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64.replaceAll("(.{64})", "$1\n") + "\n-----END PRIVATE KEY-----\n";
    }

    private String encodePublicKeyToPEM(PublicKey publicKey) {
        String base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64.replaceAll("(.{64})", "$1\n") + "\n-----END PUBLIC KEY-----\n";
    }

    private void showToast(String message, boolean success) {
        if (root != null) {
            ToastUtil.showToast(root, message, success);
        }
    }

    private void showError(String message) {
        logger.error("Error: {}", message);
        showToast(message, false);
    }

    private void showSuccess(String message) {
        logger.info("Success: {}", message);
        // Only set flag/message for LoginController and redirect
        com.securetransferfrontend.controller.LoginController.showRegistrationToast = true;
        com.securetransferfrontend.controller.LoginController.registrationToastMessage = message;
        try {
            SecureTransferApp.showLoginScene();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBack() {
        try {
            SecureTransferApp.showLoginScene();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void checkPasswordStrength() {
        String password = passwordField.getText();
        int score = getPasswordStrengthScore(password);
        String[] strengthTexts = {"Very Weak", "Weak", "Medium", "Strong", "Very Strong"};
        String[] strengthColors = {"#e53e3e", "#ed8936", "#ecc94b", "#38a169", "#3182ce"};
        passwordStrengthLabel.setText(strengthTexts[score]);
        passwordStrengthLabel.setStyle("-fx-text-fill: " + strengthColors[score] + ";");
        passwordStrengthLabel.setVisible(true);
        passwordStrengthLabel.setManaged(true);
        // Show/hide strength bars
        strengthContainer.setVisible(true);
        strengthContainer.setManaged(true);
        Region[] bars = {strengthBar1, strengthBar2, strengthBar3, strengthBar4};
        for (int i = 0; i < bars.length; i++) {
            if (i < score) {
                bars[i].setStyle("-fx-background-color: " + strengthColors[score] + "; -fx-background-radius: 4; -fx-min-width: 24; -fx-min-height: 6;");
            } else {
                bars[i].setStyle("-fx-background-color: #e2e8f0; -fx-background-radius: 4; -fx-min-width: 24; -fx-min-height: 6;");
            }
        }
    }

    private int getPasswordStrengthScore(String password) {
        int score = 0;
        if (password.length() >= 8) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*[0-9].*")) score++;
        if (password.matches(".*[!@#$%^&*()_+\\-=[\\\\]{};':\"|,.<>/?].*")) score++;
        if (password.length() >= 12) score++;
        // Map score to 0-4
        if (score <= 1) return 0;
        if (score == 2) return 1;
        if (score == 3) return 2;
        if (score == 4) return 3;
        return 4;
    }

    @FXML
    private void validatePasswordMatch() {
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();
        if (confirm.isEmpty()) {
            passwordMatchLabel.setVisible(false);
            passwordMatchLabel.setManaged(false);
            return;
        }
        if (password.equals(confirm)) {
            passwordMatchLabel.setText("Passwords match");
            passwordMatchLabel.setStyle("-fx-text-fill: #38a169;");
            passwordMatchLabel.setVisible(true);
            passwordMatchLabel.setManaged(true);
        } else {
            passwordMatchLabel.setText("Passwords do not match");
            passwordMatchLabel.setStyle("-fx-text-fill: #e53e3e;");
            passwordMatchLabel.setVisible(true);
            passwordMatchLabel.setManaged(true);
        }
    }

    /**
     * Delete JWT token for a user (client-side logout)
     */
    public static boolean deleteJwtToken(String username) {
        Logger logger = LoggerFactory.getLogger(RegistrationController.class);
        try {
            Path jwtPath = Paths.get(KEY_DIR, username, "jwt.token");
            logger.info("Checking for JWT token file at: {}", jwtPath);
            // Show toast with file path and existence
            System.out.println("JWT token path: " + jwtPath + " Exists: " + Files.exists(jwtPath));
            Platform.runLater(() -> {
                ToastUtil.showToast(null, "JWT token path: " + jwtPath + "\nExists: " + Files.exists(jwtPath), true);
            });
            if (Files.exists(jwtPath)) {
                logger.info("JWT token file found for user: {}", username);
                boolean deletedResult = Files.deleteIfExists(jwtPath);
                System.out.println("Attempted to delete JWT token. Success: " + deletedResult);
                Platform.runLater(() -> {
                    ToastUtil.showToast(null, "Attempted to delete JWT token. Success: " + deletedResult, deletedResult);
                });
                if (deletedResult) {
                    logger.info("JWT token file deleted successfully for user: {}", username);
                } else {
                    logger.warn("JWT token file could not be deleted for user: {}", username);
                }
                return deletedResult;
            } else {
                logger.info("No JWT token file found for user: {}", username);
                return false;
            }
        } catch (Exception e) {
            logger.warn("Failed to delete JWT token for user {}: {}", username, e.getMessage());
            Platform.runLater(() -> {
                ToastUtil.showToast(null, "Exception during JWT delete: " + e.getMessage(), false);
            });
            return false;
        }
    }

    /**
     * Delete JWT token for a user (client-side logout) and redirect to login page
     */
    public static void logoutAndRedirect(String username) {
        Logger logger = LoggerFactory.getLogger(RegistrationController.class);
        boolean deleted = deleteJwtToken(username);
        Platform.runLater(() -> {
            if (!deleted) {
                ToastUtil.showToast(null, "Logout failed: Could not delete JWT token.", false);
            }
            try {
                logger.info("Calling SecureTransferApp.showLoginScene() for logout redirect");
                SecureTransferApp.showLoginScene();
            } catch (Exception e) {
                logger.error("Logout redirect to login failed: {}", e.getMessage());
                ToastUtil.showToast(null, "Logout failed: Could not redirect to login.", false);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Save JWT token for a user with owner-only permissions (rw-------)
     */
    public static void saveJwtToken(String username, String jwtToken) {
        Logger logger = LoggerFactory.getLogger(RegistrationController.class);
        try {
            Path jwtPath = Paths.get(KEY_DIR, username, "jwt.token");
            Files.createDirectories(jwtPath.getParent());
            Files.writeString(jwtPath, jwtToken, StandardCharsets.UTF_8);
            // Set permissions: rw------- (owner read/write only) on Linux/Unix
            try {
                java.util.Set<java.nio.file.attribute.PosixFilePermission> perms = java.nio.file.attribute.PosixFilePermissions.fromString("rw-------");
                java.nio.file.Files.setPosixFilePermissions(jwtPath, perms);
                logger.info("Set JWT token file permissions to rw------- for user: {}", username);
            } catch (UnsupportedOperationException e) {
                logger.warn("Posix file permissions not supported on this OS. Skipping permission set for JWT token file.");
            }
            logger.info("JWT token file saved for user: {} at {}", username, jwtPath);
        } catch (Exception e) {
            logger.error("Failed to save JWT token for user {}: {}", username, e.getMessage());
        }
    }
}

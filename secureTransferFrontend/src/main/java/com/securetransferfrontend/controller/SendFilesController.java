package com.securetransferfrontend.controller;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.Node;
import java.util.List;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ClassicHttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Random;
import javafx.application.Platform;
import java.util.Timer;
import java.util.TimerTask;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class SendFilesController {
public static final Logger log = LoggerFactory.getLogger(SendFilesController.class);

    @FXML private VBox selectedFilesContainer;
    @FXML private VBox selectedFilesList;
    @FXML private Text fileCountText;
    @FXML private Text totalSizeText;
    @FXML private Label sessionCodeLabel;
    @FXML private Button selectFilesBtn;
    @FXML private Button transferFilesBtn;
    private final Set<File> selectedFiles = new HashSet<>();
    private static final int MAX_FILES = 10;

    // Add @FXML fields and methods as needed for send-files.fxml
    @FXML
    private void initialize() {
        selectedFilesContainer.setVisible(false);
        selectedFilesContainer.setManaged(false);
        transferFilesBtn.setDisable(true);
    }
    // ...existing code...

    @FXML
    private void selectFiles(javafx.event.ActionEvent event) {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Files to Send");
        fileChooser.getExtensionFilters().addAll(
            new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
        );
        List<File> files = fileChooser.showOpenMultipleDialog(
            ((javafx.stage.Window) ((javafx.scene.Node) event.getSource()).getScene().getWindow())
        );
        if (files != null && !files.isEmpty()) {
            boolean added = false;
            for (File file : files) {
                if (selectedFiles.size() < MAX_FILES && selectedFiles.add(file)) {
                    added = true;
                }
            }
            if (added) {
                selectedFilesContainer.setVisible(true);
                selectedFilesContainer.setManaged(true);
                selectedFilesContainer.getStyleClass().add("fade-in");
                selectedFilesList.getChildren().clear();
                for (File file : selectedFiles) {
                    Text fileText = new Text(file.getName());
                    fileText.getStyleClass().setAll("file-list-item", "selected-file-theme");
                    selectedFilesList.getChildren().add(fileText);
                }
                fileCountText.setText("(" + selectedFiles.size() + "/" + MAX_FILES + ")");
                updateTotalSize();
                transferFilesBtn.setDisable(false);
            }
        }
    }

    @FXML
    private void sendFile(javafx.event.ActionEvent event) {
        // TODO: Implement file sending logic (AES encryption, backend call)
    }

    @FXML
    private void cancelSend(javafx.event.ActionEvent event) {
        // TODO: Handle cancel action (clear selection, reset UI)
    }

    @FXML
    private void handleDragOver(javafx.scene.input.DragEvent event) {
        if (event.getGestureSource() != selectedFilesList && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
        }
        event.consume();
    }

    @FXML
    private void handleDragDropped(javafx.scene.input.DragEvent event) {
        javafx.scene.input.Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            for (File file : db.getFiles()) {
                if (selectedFiles.size() < MAX_FILES && selectedFiles.add(file)) {
                    // Add file to UI
                    Text fileText = new Text(file.getName());
                    fileText.getStyleClass().add("file-list-item");
                    selectedFilesList.getChildren().add(fileText);
                }
            }
            fileCountText.setText("(" + selectedFiles.size() + "/" + MAX_FILES + ")");
            updateTotalSize();
            selectedFilesContainer.setVisible(true);
            selectedFilesContainer.setManaged(true);
            success = true;
        }
        event.setDropCompleted(success);
        event.consume();
    }

    @FXML
    private void handleDragExited(javafx.scene.input.DragEvent event) {
        event.consume();
    }

    @FXML
    private void clearAllFiles(javafx.event.ActionEvent event) {
        selectedFiles.clear();
        selectedFilesList.getChildren().clear();
        fileCountText.setText("(0/" + MAX_FILES + ")");
        totalSizeText.setText("Total: 0 B");
        selectedFilesContainer.setVisible(false);
        selectedFilesContainer.setManaged(false);
    }

    private byte[] createAtomicBlob(File file) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("filename", file.getName());
        metadata.put("size", file.length());
        String fileDataBase64 = Base64.getEncoder().encodeToString(java.nio.file.Files.readAllBytes(file.toPath()));
        ObjectNode atomicJson = mapper.createObjectNode();
        atomicJson.set("metadata", metadata);
        atomicJson.put("filedata", fileDataBase64);
        byte[] atomicBytes = mapper.writeValueAsBytes(atomicJson);
        return atomicBytes;
    }

    private String sessionCode;
    private boolean sessionInitiated = false;
    private Timer pollTimer;
    private boolean receiverJoined = false;

    // Make these methods static so they can be called from inner classes
    private static String getSenderToken() {
        try {
            String linuxUserHome = System.getProperty("user.home");
            String appUsername = getCurrentUsername();
            String tokenPath = linuxUserHome + "/.securetransfer/" + appUsername + "/jwt.token";
            java.nio.file.Path path = java.nio.file.Paths.get(tokenPath);
            if (java.nio.file.Files.exists(path)) {
                String token = java.nio.file.Files.readString(path).trim();
                if (token.isEmpty()) {
                    SendFilesController.log.error("JWT token file exists but is empty: {}", tokenPath);
                } else {
                    SendFilesController.log.info("JWT token loaded from {}: {}", tokenPath, token);
                }
                return token;
            } else {
                SendFilesController.log.error("JWT token file not found: {}", tokenPath);
            }
        } catch (Exception e) {
            SendFilesController.log.error("Error reading JWT token: {}", e.getMessage(), e);
        }
        return "";
    }

    private static String getCurrentUsername() {
        return com.securetransferfrontend.controller.MainController.currentUsername;
    }

    private static String getPublicKeyFingerprint(PublicKey key) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "ERROR";
        }
    }

    // Make transferStarted a field so it persists across timer runs
    private boolean transferStarted = false;
    private void pollReceiverJoined() {
        pollTimer = new Timer();
        pollTimer.schedule(new TimerTask() {
            private boolean errorLogged = false;
            @Override
            public void run() {
                try {
                    String url = "http://localhost:8080/api/transfer/status/" + sessionCode;
                    org.apache.hc.client5.http.classic.HttpClient client = org.apache.hc.client5.http.impl.classic.HttpClients.createDefault();
                    org.apache.hc.client5.http.classic.methods.HttpGet get = new org.apache.hc.client5.http.classic.methods.HttpGet(url);
                    get.setHeader("Authorization", "Bearer " + getSenderToken());
                    org.apache.hc.core5.http.ClassicHttpResponse resp = (org.apache.hc.core5.http.ClassicHttpResponse) client.execute(get);
                    int status = resp.getCode();
                    if (status == 200) {
                        ObjectMapper mapper = new ObjectMapper();
                        java.io.InputStream is = resp.getEntity().getContent();
                        String rawResponse = new String(is.readAllBytes());
                        logToFile("[SENDER] /verify raw response: " + rawResponse);
                        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(rawResponse);
                        // Use root-level 'status' field
                        String sessionStatus = node.has("status") ? node.get("status").asText() : "";
                        boolean receiverVerified = "VERIFIED".equals(sessionStatus);
                        boolean hasPublicKey = node.has("receiverPublicKey") && !node.get("receiverPublicKey").asText().isEmpty();
                        if (receiverVerified && hasPublicKey && !transferStarted) {
                            transferStarted = true;
                            receiverJoined = true;
                            pollTimer.cancel();
                            String receiverPublicKeyPem = node.get("receiverPublicKey").asText();
                            logToFile("[SENDER] receiverPublicKey PEM from /verify: " + receiverPublicKeyPem);
                            final String finalReceiverPublicKeyPem = receiverPublicKeyPem;
                            Platform.runLater(() -> {
                                sessionCodeLabel.setText("");
                                javafx.scene.layout.HBox otpBox = new javafx.scene.layout.HBox();
                                otpBox.setSpacing(8);
                                for (char digit : sessionCode.toCharArray()) {
                                    javafx.scene.text.Text digitText = new javafx.scene.text.Text(String.valueOf(digit));
                                    digitText.getStyleClass().setAll("otp-digit-box");
                                    otpBox.getChildren().add(digitText);
                                }
                                javafx.scene.text.Text msgText = new javafx.scene.text.Text("Receiver verified! Encrypting and sending files...");
                                msgText.getStyleClass().setAll("status-label-success");
                                javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(otpBox, msgText);
                                vbox.setSpacing(12);
                                vbox.setAlignment(javafx.geometry.Pos.CENTER);
                                sessionCodeLabel.setGraphic(vbox);
                                sessionCodeLabel.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
                                sessionCodeLabel.setVisible(true);
                                com.securetransferfrontend.util.ToastUtil.showToast(selectedFilesContainer, "Receiver verified. Encrypting and sending files...", true);
                                transferFilesBtn.setDisable(false);
                                // Log the files that will be sent
                                StringBuilder filesToSend = new StringBuilder();
                                for (File file : selectedFiles) {
                                    filesToSend.append(file.getName()).append(", ");
                                }
                                if (filesToSend.length() > 0) {
                                    filesToSend.setLength(filesToSend.length() - 2); // Remove trailing comma
                                }
                                logToFile("[SENDER] About to send files after verification: [" + filesToSend + "]");
                                // Start encryption and file transfer using the public key from file
                                startFileEncryptionAndTransfer(finalReceiverPublicKeyPem);
                            });
                        }
                        // Reset errorLogged on success
                        errorLogged = false;
                    }
                } catch (Exception e) {
                    if (!errorLogged) {
                        logToFile("[SENDER] Polling error: " + e.getMessage());
                        errorLogged = true;
                    }
                }
            }
        }, 0, 2000);
    }
    // REMOVE THE EXTRA CLOSING BRACE HERE
    

    /**
     * Encrypt files with AES, encrypt AES key with receiver's public key, and send to backend.
     */
    private javax.crypto.spec.IvParameterSpec generateIv() {
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);
        return new javax.crypto.spec.IvParameterSpec(iv);
    }
    private void startFileEncryptionAndTransfer(String receiverPublicKeyPem) {
        try {
            logToFile("[SENDER] Receiver public key PEM: " + receiverPublicKeyPem);
            PublicKey receiverPublicKey = getPublicKeyFromPem(receiverPublicKeyPem);
            String pubKeyBase64 = Base64.getEncoder().encodeToString(receiverPublicKey.getEncoded());
            String pubKeyFingerprint = Integer.toHexString(java.util.Arrays.hashCode(receiverPublicKey.getEncoded()));
            logToFile("[SENDER] Receiver public key fingerprint: " + pubKeyFingerprint);
            String senderToken = getSenderToken();
            String receiverUsername = getReceiverUsernameFromSession();
            org.apache.hc.client5.http.classic.HttpClient client = org.apache.hc.client5.http.impl.classic.HttpClients.createDefault();
            org.apache.hc.client5.http.classic.methods.HttpPost post = new org.apache.hc.client5.http.classic.methods.HttpPost("http://localhost:8080/api/transfer/send");
            post.setHeader("Authorization", "Bearer " + senderToken);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("sessionId", sessionCode);
            StringBuilder sentFilesLog = new StringBuilder();
            sentFilesLog.append("[CLIENT] Files sent: ");
            for (File file : selectedFiles) {
                sentFilesLog.append(file.getName()).append(", ");
                logToFile("[SENDER] Processing file: " + file.getName() + " | Size: " + file.length());
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                logToFile("[SENDER] Raw file bytes length: " + fileBytes.length);
                String fileDataBase64 = Base64.getEncoder().encodeToString(fileBytes);
                logToFile("[SENDER] fileDataBase64 length: " + fileDataBase64.length());
                byte[] atomicBlob = createAtomicBlob(file);
                logToFile("[SENDER] atomicBlob length: " + atomicBlob.length);
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256);
                SecretKey aesKey = keyGen.generateKey();
                String aesKeyBase64 = Base64.getEncoder().encodeToString(aesKey.getEncoded());
                logToFile("[SENDER] AES key (Base64): " + aesKeyBase64);
                // Log receiver public key fingerprint
                logToFile("[SENDER] Receiver public key fingerprint: " + getPublicKeyFingerprint(receiverPublicKey));
                // CBC mode with IV
                Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                javax.crypto.spec.IvParameterSpec ivSpec = generateIv();
                String ivBase64 = Base64.getEncoder().encodeToString(ivSpec.getIV());
                logToFile("[SENDER] IV (Base64): '" + ivBase64 + "' | Length: " + ivBase64.length());
                logToFile("[SENDER] IV bytes length: " + ivSpec.getIV().length);
                aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, ivSpec);
                byte[] encryptedFileBytes = aesCipher.doFinal(atomicBlob);
                logToFile("[SENDER] encryptedFileBytes length: " + encryptedFileBytes.length);
                Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                rsaCipher.init(Cipher.ENCRYPT_MODE, receiverPublicKey);
                byte[] encryptedAesKeyBytes = rsaCipher.doFinal(aesKey.getEncoded());
                logToFile("[SENDER] encryptedAesKeyBytes length: " + encryptedAesKeyBytes.length);
                String encryptedAesKeyBase64 = Base64.getEncoder().encodeToString(encryptedAesKeyBytes);
                logToFile("[SENDER] encryptedAesKeyBase64 length: " + encryptedAesKeyBase64.length());
                // Log encrypted AES key base64
                logToFile("[SENDER] encryptedAesKeyBase64: " + encryptedAesKeyBase64);
                builder.addTextBody("encryptedAesKey", encryptedAesKeyBase64);
                builder.addTextBody("filename", file.getName());
                builder.addTextBody("iv", ivBase64);
                builder.addBinaryBody("encryptedFile", encryptedFileBytes, org.apache.hc.core5.http.ContentType.DEFAULT_BINARY, file.getName());
            }
            if (sentFilesLog.length() > 0) {
                int len = sentFilesLog.length();
                sentFilesLog.delete(len - 2, len);
            }
            logToFile(sentFilesLog.toString() + " | Session: " + sessionCode + " | Receiver: " + receiverUsername);
            post.setEntity(builder.build());
            logToFile("[SENDER] Sending POST to backend /api/transfer/send");
            org.apache.hc.core5.http.ClassicHttpResponse resp = (org.apache.hc.core5.http.ClassicHttpResponse) client.execute(post);
            int status = resp.getCode();
            logToFile("[SENDER] Backend response status: " + status);
            if (status == 200) {
                log.info("Files sent successfully. Status: {}", status);
                logToFile("[SENDER] Files sent successfully. Status: " + status);
                eraseSessionOnBackend();
                // Reset all relevant state variables after successful transfer
                transferStarted = false;
                sessionInitiated = false;
                receiverJoined = false;
                sessionCode = null;
                cachedReceiverUsername = "";
                if (pollTimer != null) {
                    pollTimer.cancel();
                    pollTimer = null;
                }
                Platform.runLater(() -> {
                    com.securetransferfrontend.util.ToastUtil.showToast(selectedFilesContainer, "Files sent successfully.", true);
                    selectedFiles.clear();
                    selectedFilesList.getChildren().clear();
                    fileCountText.setText("(0/" + MAX_FILES + ")");
                    totalSizeText.setText("Total: 0 B");
                    selectedFilesContainer.setVisible(false);
                    selectedFilesContainer.setManaged(false);
                    transferFilesBtn.setDisable(true);
                    selectFilesBtn.setDisable(false);
                    sessionCodeLabel.setText("");
                    sessionCodeLabel.setGraphic(null);
                });
            } else {
                log.error("Failed to send files. Status: {}", status);
                logToFile("[SENDER] Failed to send files. Status: " + status);
                Platform.runLater(() -> {
                    com.securetransferfrontend.util.ToastUtil.showToast(selectedFilesContainer, "Failed to send files.", false);
                    transferFilesBtn.setDisable(false);
                    selectFilesBtn.setDisable(false);
                });
            }
        } catch (Exception e) {
            log.error("Error during file encryption/transfer: {}", e.getMessage(), e);
            logToFile("[SENDER] Exception during file encryption/transfer: " + e.getMessage());
            // Reset state variables on error as well
            transferStarted = false;
            sessionInitiated = false;
            receiverJoined = false;
            sessionCode = null;
            cachedReceiverUsername = "";
            if (pollTimer != null) {
                pollTimer.cancel();
                pollTimer = null;
            }
            Platform.runLater(() -> {
                com.securetransferfrontend.util.ToastUtil.showToast(selectedFilesContainer, "Error during file transfer: " + e.getMessage(), false);
                transferFilesBtn.setDisable(false);
                selectFilesBtn.setDisable(false);
            });
        }
    }

    /**
     * Erase session, status, and queue on backend after transfer.
     */
    private void eraseSessionOnBackend() {
        try {
            if (sessionCode == null || sessionCode.isEmpty()) return;
            String token = getSenderToken();
            org.apache.hc.client5.http.classic.HttpClient client = org.apache.hc.client5.http.impl.classic.HttpClients.createDefault();
            org.apache.hc.client5.http.classic.methods.HttpDelete delete = new org.apache.hc.client5.http.classic.methods.HttpDelete("http://localhost:8080/api/transfer/session/" + sessionCode);
            delete.setHeader("Authorization", "Bearer " + token);
            org.apache.hc.core5.http.ClassicHttpResponse resp = (org.apache.hc.core5.http.ClassicHttpResponse) client.execute(delete);
            int status = resp.getCode();
            if (status == 200) {
                log.info("Session {} erased on backend.", sessionCode);
            } else {
                log.warn("Failed to erase session {} on backend. Status: {}", sessionCode, status);
            }
        } catch (Exception e) {
            log.error("Error erasing session on backend: {}", e.getMessage(), e);
        }
    }

    /**
     * Helper to parse PEM and get PublicKey instance.
     */
    private PublicKey getPublicKeyFromPem(String pem) throws Exception {
        String publicKeyPEM = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                                .replace("-----END PUBLIC KEY-----", "")
                                .replaceAll("\\s+", "");
        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Helper to get receiver username from session (from backend status response or cached value).
     */
    private String getReceiverUsernameFromSession() {
        // Extract receiver username from backend session status response and cache it
        if (cachedReceiverUsername != null && !cachedReceiverUsername.isEmpty()) {
            return cachedReceiverUsername;
        }
        try {
            String token = getSenderToken();
            String url = "http://localhost:8080/api/transfer/status/" + sessionCode;
            org.apache.hc.client5.http.classic.HttpClient client = org.apache.hc.client5.http.impl.classic.HttpClients.createDefault();
            org.apache.hc.client5.http.classic.methods.HttpGet get = new org.apache.hc.client5.http.classic.methods.HttpGet(url);
            if (!token.isEmpty()) {
                get.setHeader("Authorization", "Bearer " + token);
            }
            org.apache.hc.core5.http.ClassicHttpResponse resp = (org.apache.hc.core5.http.ClassicHttpResponse) client.execute(get);
            int status = resp.getCode();
            if (status == 200) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.io.InputStream is = resp.getEntity().getContent();
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(is);
                if (node.has("receiverUsername")) {
                    cachedReceiverUsername = node.get("receiverUsername").asText();
                    return cachedReceiverUsername;
                }
            }
        } catch (Exception e) {
            // Log or handle error if needed
        }
        return "";
    }

    // Cache for receiver username
    private String cachedReceiverUsername = "";
    

    @FXML
    // Log file should be in the backend project root
    private static final String LOG_FILE_PATH = getCanonicalBackendLogPath();

    private static String getCanonicalBackendLogPath() {
        try {
            String backendDir = getBackendRootDir();
            java.io.File logFile = new java.io.File(backendDir, "securetransfer-frontend.log");
            return logFile.getCanonicalPath();
        } catch (Exception e) {
            // Fallback to absolute path if canonical fails
            return getBackendRootDir() + "/securetransfer-frontend.log";
        }
    }

    private static String getBackendRootDir() {
        // Assumes backend and frontend folders are siblings
        String frontendDir = System.getProperty("user.dir");
        java.io.File frontend = new java.io.File(frontendDir);
        java.io.File parent = frontend.getParentFile();
        java.io.File backend = new java.io.File(parent, "secureTransfer");
        return backend.getAbsolutePath();
    }
    private static void logToFile(String message) {
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get(LOG_FILE_PATH), (java.time.LocalDateTime.now() + " " + message + "\n").getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("Failed to write to frontend log file: {}", e.getMessage());
        }
    }

    @FXML
    private void startTransferFiles(javafx.event.ActionEvent event) {
        logToFile("[TRANSFER BUTTON] Sender: " + getCurrentUsername() + " clicked transfer");
        log.info("Transfer Files button clicked");
        if (selectedFiles.isEmpty()) {
            log.warn("No files selected. Transfer aborted.");
            logToFile("[TRANSFER BUTTON] No files selected. Transfer aborted.");
            com.securetransferfrontend.util.ToastUtil.showToast(selectedFilesContainer, "No files selected. Please choose files to transfer.", false);
            return;
        }
        try {
            log.info("Generating session code and initiating transfer session");
            logToFile("[TRANSFER BUTTON] Generating session code and initiating transfer session");
            Random rand = new Random();
            sessionCode = String.format("%06d", rand.nextInt(1000000));
            log.info("Generated session code: {}", sessionCode);
            logToFile("[TRANSFER BUTTON] Generated session code: " + sessionCode);
            // Update UI: show code and waiting message
            sessionCodeLabel.setText("");
            javafx.scene.layout.HBox otpBox = new javafx.scene.layout.HBox();
            otpBox.setSpacing(8);
            for (char digit : sessionCode.toCharArray()) {
                javafx.scene.text.Text digitText = new javafx.scene.text.Text(String.valueOf(digit));
                digitText.getStyleClass().setAll("otp-digit-box");
                otpBox.getChildren().add(digitText);
            }
            javafx.scene.text.Text msgText = new javafx.scene.text.Text("Waiting for receiver to join...");
            msgText.getStyleClass().setAll("status-label-waiting");
            javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(otpBox, msgText);
            vbox.setSpacing(12);
            vbox.setAlignment(javafx.geometry.Pos.CENTER);
            sessionCodeLabel.setGraphic(vbox);
            sessionCodeLabel.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
            sessionCodeLabel.setVisible(true);
            selectFilesBtn.setDisable(true);
            transferFilesBtn.setDisable(true);
            selectedFilesContainer.getStyleClass().add("fade-out");
            org.apache.hc.client5.http.classic.HttpClient client = org.apache.hc.client5.http.impl.classic.HttpClients.createDefault();
            org.apache.hc.client5.http.classic.methods.HttpPost post = new org.apache.hc.client5.http.classic.methods.HttpPost("http://localhost:8080/api/transfer/initiate");
            String token = getSenderToken();
            log.info("JWT token value: {}", token);
            logToFile("[TRANSFER BUTTON] JWT token requested");
            post.setHeader("Authorization", "Bearer " + token);
            post.setHeader("Content-Type", "application/json");
            String payload = "{\"code\":\"" + sessionCode + "\"}";
            log.info("POST payload: {}", payload);
            logToFile("[TRANSFER BUTTON] POST payload: " + payload);
            post.setEntity(new org.apache.hc.core5.http.io.entity.ByteArrayEntity(payload.getBytes(), org.apache.hc.core5.http.ContentType.APPLICATION_JSON));
            org.apache.hc.core5.http.ClassicHttpResponse resp = (org.apache.hc.core5.http.ClassicHttpResponse) client.execute(post);
            String respBody = "";
            if (resp.getEntity() != null) {
                respBody = new String(resp.getEntity().getContent().readAllBytes());
            }
            boolean initiated = false;
            String statusField = "";
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(respBody);
                statusField = node.has("status") ? node.get("status").asText() : "";
                if ("INITIATED".equalsIgnoreCase(statusField)) {
                    initiated = true;
                }
            } catch (Exception ex) {
                log.warn("Could not parse response body as JSON: {}", respBody);
            }
            if (initiated) {
                sessionInitiated = true;
                log.info("Session initiated successfully (status INITIATED). Starting receiver poll.");
                logToFile("[TRANSFER BUTTON] Session initiated successfully (status INITIATED). Starting receiver poll.");
                com.securetransferfrontend.util.ToastUtil.showToast(selectedFilesContainer, "Session initiated. Code: " + sessionCode + ". Waiting for receiver...", true);
                pollReceiverJoined();
            } else {
                log.error("Failed to initiate session. Status: {} Body: {}", statusField, respBody);
                logToFile("[TRANSFER BUTTON] Failed to initiate session. Status: " + statusField + " Body: " + respBody);
                com.securetransferfrontend.util.ToastUtil.showToast(selectedFilesContainer, "Failed to initiate session.", false);
                selectFilesBtn.setDisable(false);
                transferFilesBtn.setDisable(false);
            }
        } catch (Exception e) {
            log.error("Error initiating session: {}", e.getMessage(), e);
            logToFile("[TRANSFER BUTTON] Error initiating session: " + e.getMessage());
            com.securetransferfrontend.util.ToastUtil.showToast(selectedFilesContainer, "Error initiating session: " + e.getMessage(), false);
            selectFilesBtn.setDisable(false);
            transferFilesBtn.setDisable(false);
        }
    }

    // Helper class for transfer payload
    static class TransferPayload {
        public String receiverUsername;
        public String encryptedFile;
        public String encryptedAesKey;
        public String fileName;
        public TransferPayload(String receiverUsername, String encryptedFile, String encryptedAesKey, String fileName) {
            this.receiverUsername = receiverUsername;
            this.encryptedFile = encryptedFile;
            this.encryptedAesKey = encryptedAesKey;
            this.fileName = fileName;
        }
    }

    private void updateTotalSize() {
        long totalBytes = 0;
        for (File file : selectedFiles) {
            totalBytes += file.length();
        }
        String sizeStr = formatSize(totalBytes);
        totalSizeText.setText("Total: " + sizeStr);
    }

    /**
     * Format file size in human-readable form (B, KB, MB, GB, TB)
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }
}

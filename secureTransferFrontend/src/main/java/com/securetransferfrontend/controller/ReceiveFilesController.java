package com.securetransferfrontend.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.application.Platform;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.ContentType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.StreamReadConstraints;

import java.util.List;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.io.InputStream;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.KeyFactory;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;


public class ReceiveFilesController {
    // Ensure all FXML handlers are present as stubs
    @FXML
    private void handleDownloadFile(javafx.event.ActionEvent event) {
        // Stub for FXML linkage
    }

    @FXML
    private void handleSaveSelectedFile(javafx.event.ActionEvent event) {
        // Stub for FXML linkage
    }

    @FXML
    private void handleRequestFile(javafx.event.ActionEvent event) {
        // Stub for FXML linkage
    }

    @FXML
    private void handleNoFilesMessage(javafx.event.ActionEvent event) {
        // Stub for FXML linkage
    }

    @FXML
    private void handleReceivedFilesListView(javafx.event.ActionEvent event) {
        // Stub for FXML linkage
    }
    // Helper class for table rows
    public static class ReceivedFile {
        public Long id;
        public String filename;
        public String size;
        public String sender;
        public String receivedTime;
        public String status;
        public String encryptedAesKey;
        public String encryptedFileData;

        public byte[] decryptedBytes;

        public ReceivedFile(Long id, String filename, String size, String sender, String receivedTime, String status,
                String encryptedAesKey, String encryptedFileData) {
            this.id = id;
            this.filename = filename;
            this.size = size;
            this.sender = sender;
            this.receivedTime = receivedTime;
            this.status = status;
            this.encryptedAesKey = encryptedAesKey;
            this.encryptedFileData = encryptedFileData;
        }

        public Long getId() { return id; }
        public String getFilename() { return filename; }
        public String getSize() { return size; }
        public String getSender() { return sender; }
        public String getReceivedTime() { return receivedTime; }
        public String getStatus() { return status; }
        public String getEncryptedAesKey() { return encryptedAesKey; }
        public String getEncryptedFileData() { return encryptedFileData; }
    }

    // FXML UI fields (add stubs for compilation)
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReceiveFilesController.class);
    @FXML
    private ListView<String> receivedFilesListView;
    @FXML
    private TextField codeTextField;
    @FXML
    private Button saveAllButton; // Add this to your FXML with a save icon
    @FXML
    private VBox connectionStatusBox;

    // Other fields
    private boolean transferActive = false;
    private Timer pollTimer;

    private List<ReceivedFile> decryptedFiles = new ArrayList<>();

    // Helper methods (implemented logic)
    // Get current username from a config file or environment variable
    private String getCurrentUsername() {
        // Always use MainController.currentUsername
        return MainController.currentUsername;
    }

    // Get JWT token from a config file or environment variable
    private String getReceiverToken() {
        // Read JWT token from ~/.securetransfer/{username}/jwt.token
        try {
            String home = System.getProperty("user.home");
            String username = getCurrentUsername();
            Path tokenFile = Paths.get(home, ".securetransfer", username, "jwt.token");
            if (Files.exists(tokenFile)) {
                return Files.readString(tokenFile).trim();
            }
        } catch (Exception e) {
            // Ignore and fallback
        }
        return "";
    }

    // Refresh the received files list from backend and update the ListView
    private void fetchAndDecryptFiles() {
        log.info("[RECEIVE] fetchAndDecryptFiles called");
        try {
            String receiverUsername = getCurrentUsername();
            String url = "http://localhost:8080/api/transfer/inbox?receiver=" + receiverUsername;
            logToFrontendFile("[RECEIVER] Polling inbox: " + url);
            HttpClient client = HttpClients.createDefault();
            HttpGet get = new HttpGet(url);
            String token = getReceiverToken();
            log.info("[RECEIVE] Using JWT token: {}", token);
            if (!token.isEmpty()) {
                get.setHeader("Authorization", "Bearer " + token);
            }
            try (ClassicHttpResponse resp = (ClassicHttpResponse) client.execute(get)) {
                int status = resp.getCode();
                log.info("[RECEIVE] GET response status: {}", status);
                logToFrontendFile("[RECEIVER] Inbox response status: " + status);
                // Log raw response body
                String rawResponse = null;
                try (InputStream rawIs = resp.getEntity().getContent()) {
                    rawResponse = new String(rawIs.readAllBytes());
                }
                log.info("[RECEIVE] Raw server response: {}", rawResponse);
                logToFrontendFile("[RECEIVER] Raw server response: " + rawResponse);
                // Parse JSON from rawResponse
                ObjectMapper mapper = new ObjectMapper();
                mapper.getFactory().setStreamReadConstraints(
                    StreamReadConstraints.builder().maxStringLength(50_000_000).build()
                );
                JsonNode root = null;
                try {
                    if (rawResponse != null && !rawResponse.isEmpty()) {
                        root = mapper.readTree(rawResponse);
                    } else {
                        throw new IllegalArgumentException("Server response is empty or null");
                    }
                } catch (Exception jsonEx) {
                    log.error("[RECEIVE] Error parsing JSON: {}", jsonEx.getMessage(), jsonEx);
                    logToFrontendFile("[RECEIVER] Error parsing JSON: " + jsonEx.getMessage());
                    Platform.runLater(() -> {
                        com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Error parsing server response: " + jsonEx.getMessage(), false);
                    });
                    return;
                }
                JsonNode node = root.has("files") ? root.get("files") : null;
                List<String> filenames = new ArrayList<>();
                decryptedFiles.clear();
                if (status == 200) {
                    if (node != null && node.isArray()) {
                        String linuxUserHome = System.getProperty("user.home");
                        String appUsername = getCurrentUsername();
                        String keyPath = linuxUserHome + "/.securetransfer/" + appUsername + "/private_key.pem";
                        Path path = Paths.get(keyPath);
                        if (!Files.exists(path)) {
                            Platform.runLater(() -> {
                            com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Private key not found.", false);
                            });
                            return;
                        }
                        String pem = Files.readString(path);
                        PrivateKey privateKey = getPrivateKeyFromPem(pem);
                        for (JsonNode fileNode : node) {
                            try {
                                String encryptedAesKey = fileNode.has("encryptedAesKey") ? fileNode.get("encryptedAesKey").asText() : "";
                                String encryptedFileData = fileNode.has("encryptedFileData") ? fileNode.get("encryptedFileData").asText() : "";
                                logToFrontendFile("[RECEIVER] encryptedAesKey length: " + encryptedAesKey.length());
                                logToFrontendFile("[RECEIVER] encryptedFileData length: " + encryptedFileData.length());
                                byte[] encryptedAesKeyBytes = java.util.Base64.getDecoder().decode(encryptedAesKey);
                                logToFrontendFile("[RECEIVER] encryptedAesKeyBytes length: " + encryptedAesKeyBytes.length);
                               // Log encrypted AES key base64
                               logToFrontendFile("[RECEIVER] encryptedAesKeyBase64: " + encryptedAesKey);
                               // Log receiver private key fingerprint
                               logToFrontendFile("[RECEIVER] Private key fingerprint: " + getPrivateKeyFingerprint(privateKey));
                                javax.crypto.Cipher rsaCipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                                rsaCipher.init(javax.crypto.Cipher.DECRYPT_MODE, privateKey);
                                byte[] aesKeyBytes = rsaCipher.doFinal(encryptedAesKeyBytes);
                                logToFrontendFile("[RECEIVER] Decrypted AES key bytes length: " + aesKeyBytes.length);
                                javax.crypto.SecretKey aesKey = new javax.crypto.spec.SecretKeySpec(aesKeyBytes, "AES");
                                byte[] encryptedFileBytes = java.util.Base64.getDecoder().decode(encryptedFileData);
                                logToFrontendFile("[RECEIVER] encryptedFileBytes length: " + encryptedFileBytes.length);
                                String iv = fileNode.has("iv") ? fileNode.get("iv").asText() : "";
                                logToFrontendFile("[RECEIVER] IV (Base64): '" + iv + "' | Length: " + iv.length());
                                byte[] ivBytes = Base64.getDecoder().decode(iv);
                                logToFrontendFile("[RECEIVER] IV bytes length: " + ivBytes.length);
                                // Log AES key (Base64) for comparison
                                String aesKeyBase64 = Base64.getEncoder().encodeToString(aesKey.getEncoded());
                                logToFrontendFile("[RECEIVER] AES key (Base64): " + aesKeyBase64);
                                Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                                javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(ivBytes);
                                aesCipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);
                                byte[] atomicBlob = aesCipher.doFinal(encryptedFileBytes);
                                logToFrontendFile("[RECEIVER] atomicBlob length: " + atomicBlob.length);
                                com.fasterxml.jackson.databind.JsonNode blobNode = mapper.readTree(atomicBlob);
                                String filename = blobNode.get("metadata").get("filename").asText();
                                String fileDataBase64 = blobNode.get("filedata").asText();
                                logToFrontendFile("[RECEIVER] fileDataBase64 length: " + fileDataBase64.length());
                                byte[] fileBytes = java.util.Base64.getDecoder().decode(fileDataBase64);
                                logToFrontendFile("[RECEIVER] fileBytes length: " + fileBytes.length);
                                ReceivedFile rf = new ReceivedFile(
                                    fileNode.has("id") ? fileNode.get("id").asLong() : null,
                                    filename,
                                    fileNode.has("size") ? fileNode.get("size").asText() : "",
                                    fileNode.has("sender") ? fileNode.get("sender").asText() : "",
                                    fileNode.has("receivedTime") ? fileNode.get("receivedTime").asText() : "",
                                    fileNode.has("status") ? fileNode.get("status").asText() : "",
                                    encryptedAesKey,
                                    encryptedFileData
                                );
                                // Store decrypted bytes in a new field
                                rf.decryptedBytes = fileBytes;
                                decryptedFiles.add(rf);
                                filenames.add(filename);
                            } catch (Exception ex) {
                                log.error("[RECEIVE] Error decrypting file: {}", ex.getMessage(), ex);
                                logToFrontendFile("[RECEIVER] Error decrypting file: " + ex.getMessage());
                            }
                        }
                    }
                    log.info("[RECEIVE] Received files: {}", filenames);
                    logToFrontendFile("[RECEIVER] Inbox files: " + filenames);
                    try {
                        Platform.runLater(() -> {
                            try {
                                com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, filenames.isEmpty() ? "No files received." : "Files received.", true);
                                receivedFilesListView.getItems().setAll(filenames);
                            } catch (Exception uiEx) {
                                log.error("[RECEIVE] Error updating ListView: {}", uiEx.getMessage(), uiEx);
                                logToFrontendFile("[RECEIVER] Error updating ListView: " + uiEx.getMessage());
                                com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Error updating file list: " + uiEx.getMessage(), false);
                            }
                        });
                    } catch (Exception runEx) {
                        log.error("[RECEIVE] Error scheduling ListView update: {}", runEx.getMessage(), runEx);
                        logToFrontendFile("[RECEIVER] Error scheduling ListView update: " + runEx.getMessage());
                        Platform.runLater(() -> {
                            com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Error scheduling file list update: " + runEx.getMessage(), false);
                        });
                    }
                } else {
                    log.warn("[RECEIVE] Failed to fetch files. Status: {}", status);
                    Platform.runLater(() -> {
                        com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Failed to fetch files. Status: " + status, false);
                    });
                }
            }
        } catch (Exception e) {
            log.error("[RECEIVE] Exception fetching files: {}", e.getMessage(), e);
            logToFrontendFile("[RECEIVER] Exception fetching inbox: " + e.getMessage());
            Platform.runLater(() -> {
                com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Error fetching files: " + e.getMessage(), false);
            });
        }
    }

    // Update refreshReceivedFilesList to use fetchAndDecryptFiles
    private void refreshReceivedFilesList() {
        fetchAndDecryptFiles();
    }

    @FXML
    private void handleCancelTransfer(javafx.event.ActionEvent event) {
        log.info("[RECEIVE] handleCancelTransfer called");
        transferActive = false;
        if (pollTimer != null)
            pollTimer.cancel();
        com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Transfer cancelled.", false);
    }

    @FXML
    private void handleConnectButton(javafx.event.ActionEvent event) {
        log.info("[RECEIVE] handleConnectButton called");
        // 1. Get code from TextField
        String code = codeTextField.getText().trim();
        log.info("[CONNECT BUTTON] Entered code: {}", code);
        if (code.length() != 6 || !code.matches("\\d{6}")) {
            log.warn("[CONNECT BUTTON] Invalid code format: {}", code);
            com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Please enter a valid 6-digit code.", false);
            return;
        }

        // 2. Verify code with backend
        Platform.runLater(() -> com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Verifying code...", true));
        try {
            String token = getReceiverToken();
            log.info("[CONNECT BUTTON] Using JWT token: {}", token);
            String url = "http://localhost:8080/api/transfer/verify";
            HttpClient client = HttpClients.createDefault();
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            if (!token.isEmpty()) {
                post.setHeader("Authorization", "Bearer " + token);
            }
            String receiverUsername = getCurrentUsername();
            String body = String.format("{\"code\":\"%s\",\"receiverUsername\":\"%s\"}", code, receiverUsername);
            log.info("[CONNECT BUTTON] POST payload: {}", body);
            logToFrontendFile("[RECEIVER] Verifying code: " + body);
            post.setEntity(new ByteArrayEntity(body.getBytes(), ContentType.APPLICATION_JSON));
            try (ClassicHttpResponse resp = (ClassicHttpResponse) client.execute(post)) {
                int status = resp.getCode();
                log.info("[CONNECT BUTTON] Backend response status: {}", status);
                logToFrontendFile("[RECEIVER] Verify response status: " + status);
                if (status == 200) {
                    com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Code verified. Waiting for sender...", true);
                    log.info("[CONNECT BUTTON] Code verified successfully.");
                    logToFrontendFile("[RECEIVER] Code verified successfully.");
                } else {
                    com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Invalid code or already claimed.", false);
                    log.warn("[CONNECT BUTTON] Code verification failed. Status: {}", status);
                    logToFrontendFile("[RECEIVER] Code verification failed. Status: " + status);
                    return;
                }
            }
        } catch (Exception e) {
            com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Error verifying code: " + e.getMessage(), false);
            log.error("[CONNECT BUTTON] Exception during code verification: {}", e.getMessage(), e);
            logToFrontendFile("[RECEIVER] Exception verifying code: " + e.getMessage());
            return;
        }

        // 3. Poll for status and fetch files once available
        log.info("[CONNECT BUTTON] Starting poll for session status...");
        logToFrontendFile("[RECEIVER] Starting poll for session status...");
        pollTimer = new Timer();
        pollTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    String token = getReceiverToken();
                    String statusUrl = "http://localhost:8080/api/transfer/status/" + code;
                    logToFrontendFile("[RECEIVER] Polling status: " + statusUrl);
                    HttpClient client = HttpClients.createDefault();
                    HttpGet get = new HttpGet(statusUrl);
                    if (!token.isEmpty()) {
                        get.setHeader("Authorization", "Bearer " + token);
                    }
                    try (ClassicHttpResponse resp = (ClassicHttpResponse) client.execute(get)) {
                        int status = resp.getCode();
                        log.info("[CONNECT BUTTON] Polling status response: {}", status);
                        logToFrontendFile("[RECEIVER] Polling status response: " + status);
                        if (status == 200) {
                            ObjectMapper mapper = new ObjectMapper();
                            InputStream is = resp.getEntity().getContent();
                            JsonNode root = mapper.readTree(is);
                            String sessionStatus = root.has("status") ? root.get("status").asText() : "";
                            log.info("[CONNECT BUTTON] Session status: {}", sessionStatus);
                            logToFrontendFile("[RECEIVER] Session status: " + sessionStatus);
                            if ("FILE_SENT".equals(sessionStatus) || "COMPLETED".equals(sessionStatus)) {
                                pollTimer.cancel();
                                log.info("[CONNECT BUTTON] Files available, refreshing received files list.");
                                logToFrontendFile("[RECEIVER] Files available, refreshing received files list.");
                                Platform.runLater(() -> {
                                    com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Files available. Fetching...", true);
                                    refreshReceivedFilesList();
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[CONNECT BUTTON] Exception during polling: {}", e.getMessage(), e);
                    logToFrontendFile("[RECEIVER] Exception during polling: " + e.getMessage());
                    Platform.runLater(() -> {
                        com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Error polling status: " + e.getMessage(), false);
                    });
                }
            }
        }, 0, 3000);
     
    } // poll every 3 seconds
    // Helper to log to securetransfer-frontend.log
    private void logToFrontendFile(String message) {
        try {
            String logPath = System.getProperty("user.dir") + "/securetransfer-frontend.log";
            java.nio.file.Files.write(java.nio.file.Paths.get(logPath),
                (java.time.LocalDateTime.now() + " " + message + "\n").getBytes(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            // Ignore logging errors
        }
    }

    @FXML
    private void handleCancelCodePopup(javafx.event.ActionEvent event) {
        log.info("[RECEIVE] handleCancelCodePopup called");
        // Hide code popup (if used)
        // Example: codePopup.setVisible(false);
    }

    @FXML
    private void handleRefreshHistory(javafx.event.ActionEvent event) {
        log.info("[RECEIVE] handleRefreshHistory called");
        refreshReceivedFilesList();
    }

    @FXML
    private void requestFile(javafx.event.ActionEvent event) {
        log.info("[RECEIVE] requestFile called");
        // Optional: Implement logic to request file from backend (if needed)
    }

    @FXML
    private void downloadFile(javafx.event.ActionEvent event) {
        log.info("[RECEIVE] downloadFile called");
        int selectedIdx = receivedFilesListView.getSelectionModel().getSelectedIndex();
        if (selectedIdx < 0) {
            com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "No file selected.", false);
            return;
        }
        // Fetch file details from backend again to get encrypted data
        try {
            String receiverUsername = getCurrentUsername();
            String url = "http://localhost:8080/api/transfer/inbox?receiver=" + receiverUsername;
            HttpClient client = HttpClients.createDefault();
            HttpGet get = new HttpGet(url);
            String token = getReceiverToken();
            if (!token.isEmpty()) {
                get.setHeader("Authorization", "Bearer " + token);
            }
            try (ClassicHttpResponse resp = (ClassicHttpResponse) client.execute(get)) {
                int status = resp.getCode();
                if (status == 200) {
                    ObjectMapper mapper = new ObjectMapper();
                    InputStream is = resp.getEntity().getContent();
                    JsonNode root = mapper.readTree(is);
                    JsonNode node = root.has("files") ? root.get("files") : null;
                    if (node != null && node.isArray() && selectedIdx < node.size()) {
                        JsonNode fileNode = node.get(selectedIdx);
                        String encryptedAesKey = fileNode.has("encryptedAesKey")
                                ? fileNode.get("encryptedAesKey").asText()
                                : "";
                        String encryptedFileData = fileNode.has("encryptedFileData")
                                ? fileNode.get("encryptedFileData").asText()
                                : "";
                        Long fileId = fileNode.has("id") ? fileNode.get("id").asLong() : null;
                        // Decrypt
                        String linuxUserHome = System.getProperty("user.home");
                        String appUsername = getCurrentUsername();
                        String keyPath = linuxUserHome + "/.securetransfer/" + appUsername + "/private_key.pem";
                        Path path = Paths.get(keyPath);
                        if (!Files.exists(path)) {
                        com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Private key not found.", false);
                            return;
                        }
                        String pem = Files.readString(path);
                        PrivateKey privateKey = getPrivateKeyFromPem(pem);
                        byte[] encryptedAesKeyBytes = Base64.getDecoder().decode(encryptedAesKey);
                        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
                        byte[] aesKeyBytes = rsaCipher.doFinal(encryptedAesKeyBytes);
                        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");
                        byte[] encryptedFileBytes = Base64.getDecoder().decode(encryptedFileData);
                        Cipher aesCipher = Cipher.getInstance("AES");
                        aesCipher.init(Cipher.DECRYPT_MODE, aesKey);
                        byte[] atomicBlob = aesCipher.doFinal(encryptedFileBytes);
                        JsonNode blobNode = mapper.readTree(atomicBlob);
                        String filename = blobNode.get("metadata").get("filename").asText();
                        String fileDataBase64 = blobNode.get("filedata").asText();
                        byte[] fileBytes = Base64.getDecoder().decode(fileDataBase64);
                        // Prompt user for save location
                        FileChooser fileChooser = new FileChooser();
                        fileChooser.setTitle("Save Decrypted File");
                        fileChooser.setInitialFileName(filename);
                        Window window = connectionStatusBox.getScene().getWindow();
                        File saveFile = fileChooser.showSaveDialog(window);
                        if (saveFile != null) {
                            Files.write(saveFile.toPath(), fileBytes);
                            com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "File '" + filename + "' saved to " + saveFile.getAbsolutePath(), true);
                            // Delete from backend inbox
                            String deleteUrl = "http://localhost:8080/api/transfer/inbox/" + fileId;
                            try {
                                HttpClient delClient = HttpClients.createDefault();
                                HttpDelete delete = new HttpDelete(deleteUrl);
                                if (!token.isEmpty()) {
                                    delete.setHeader("Authorization", "Bearer " + token);
                                }
                                try (ClassicHttpResponse delResp = (ClassicHttpResponse) delClient.execute(delete)) {
                                    int delStatus = delResp.getCode();
                                    if (delStatus != 200) {
                                        com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "File saved, but failed to delete from inbox.", false);
                                    }
                                }
                            } catch (Exception ex) {
                                com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "File saved, but error deleting from inbox: " + ex.getMessage(), false);
                            }
                            refreshReceivedFilesList();
                        }
                    }
                }
            }
        } catch (Exception e) {
                        com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Error restoring file: " + e.getMessage(), false);
        }
    }

    @FXML
    private void saveSelectedFile(javafx.event.ActionEvent event) {
        log.info("[RECEIVE] saveSelectedFile called");
        int selectedIdx = receivedFilesListView.getSelectionModel().getSelectedIndex();
        if (selectedIdx < 0) {
            com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "No file selected.", false);
            return;
        }
        try {
            String receiverUsername = getCurrentUsername();
            String url = "http://localhost:8080/api/transfer/inbox?receiver=" + receiverUsername;
            HttpClient client = HttpClients.createDefault();
            HttpGet get = new HttpGet(url);
            String token = getReceiverToken();
            if (!token.isEmpty()) {
                get.setHeader("Authorization", "Bearer " + token);
            }
            try (ClassicHttpResponse resp = (ClassicHttpResponse) client.execute(get)) {
                int status = resp.getCode();
                if (status == 200) {
                    ObjectMapper mapper = new ObjectMapper();
                    InputStream is = resp.getEntity().getContent();
                    JsonNode root = mapper.readTree(is);
                    JsonNode node = root.has("files") ? root.get("files") : null;
                    if (node != null && node.isArray() && selectedIdx < node.size()) {
                        JsonNode fileNode = node.get(selectedIdx);
                        String encryptedAesKey = fileNode.has("encryptedAesKey")
                                ? fileNode.get("encryptedAesKey").asText()
                                : "";
                        String encryptedFileData = fileNode.has("encryptedFileData")
                                ? fileNode.get("encryptedFileData").asText()
                                : "";
                        // Decrypt
                        String linuxUserHome = System.getProperty("user.home");
                        String appUsername = getCurrentUsername();
            String keyPath = linuxUserHome + "/.securetransfer/" + appUsername + "/private_key.pem";
                        Path path = Paths.get(keyPath);
                        if (!Files.exists(path)) {
                        com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Private key not found.", false);
                            return;
                        }
                        String pem = Files.readString(path);
                        PrivateKey privateKey = getPrivateKeyFromPem(pem);
                        byte[] encryptedAesKeyBytes = Base64.getDecoder().decode(encryptedAesKey);
                        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
                        byte[] aesKeyBytes = rsaCipher.doFinal(encryptedAesKeyBytes);
                        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");
                        byte[] encryptedFileBytes = Base64.getDecoder().decode(encryptedFileData);
                        Cipher aesCipher = Cipher.getInstance("AES");
                        aesCipher.init(Cipher.DECRYPT_MODE, aesKey);
                        byte[] atomicBlob = aesCipher.doFinal(encryptedFileBytes);
                        JsonNode blobNode = mapper.readTree(atomicBlob);
                        String filename = blobNode.get("metadata").get("filename").asText();
                        String fileDataBase64 = blobNode.get("filedata").asText();
                        byte[] fileBytes = Base64.getDecoder().decode(fileDataBase64);
                        // Save file implicitly to frontend received/ directory
                        String frontendDir = System.getProperty("user.dir") + "/received";
                        java.nio.file.Path receivedDirPath = java.nio.file.Paths.get(frontendDir);
                        if (!java.nio.file.Files.exists(receivedDirPath)) {
                            java.nio.file.Files.createDirectories(receivedDirPath);
                        }
                        java.nio.file.Path savePath = receivedDirPath.resolve(filename);
                        java.nio.file.Files.write(savePath, fileBytes);
                        com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "File '" + filename + "' saved to " + savePath.toAbsolutePath(), true);
                    }
                }
            }
        } catch (Exception e) {
            com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "Error saving file: " + e.getMessage(), false);
        }
    }

    @FXML
    private void handleSaveAllFiles(javafx.event.ActionEvent event) {
        log.info("[RECEIVE] handleSaveAllFiles called");
        javafx.stage.DirectoryChooser dirChooser = new javafx.stage.DirectoryChooser();
        dirChooser.setTitle("Select Directory to Save All Files");
        Window window = connectionStatusBox.getScene().getWindow();
        File selectedDir = dirChooser.showDialog(window);
        if (selectedDir == null) {
            com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, "No directory selected.", false);
            return;
        }
        int savedCount = 0;
        for (ReceivedFile rf : decryptedFiles) {
            try {
                File saveFile = new File(selectedDir, rf.filename);
                Files.write(saveFile.toPath(), rf.decryptedBytes);
                savedCount++;
            } catch (Exception ex) {
                log.error("[RECEIVE] Error saving file: {}", ex.getMessage(), ex);
            }
        }
        com.securetransferfrontend.util.ToastUtil.showToast(connectionStatusBox, savedCount + " files saved to " + selectedDir.getAbsolutePath(), true);
    }

    // Helper to parse PEM and get PrivateKey instance
    private PrivateKey getPrivateKeyFromPem(String pem) throws Exception {
        // Try decoding the full PEM string, including BEGIN/END lines
        String cleanedPem = pem.replaceAll("\r", "").replaceAll("\n", "");
        byte[] encoded;
        try {
            encoded = Base64.getDecoder().decode(cleanedPem);
        } catch (IllegalArgumentException e) {
            // Fallback: extract base64 between BEGIN/END if decoding fails
            String privateKeyPEM = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("[\r\n]+", "")
                    .replaceAll("\s+", "");
            encoded = Base64.getDecoder().decode(privateKeyPEM);
        }
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        // Log the receiver's public key PEM after it is sent to the sender
        java.security.PublicKey publicKey = keyFactory.generatePublic(new java.security.spec.RSAPublicKeySpec(
            ((java.security.interfaces.RSAPrivateCrtKey) privateKey).getModulus(),
            ((java.security.interfaces.RSAPrivateCrtKey) privateKey).getPublicExponent()
        ));
        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(publicKey.getEncoded()) + "\n-----END PUBLIC KEY-----";
        logToFrontendFile("[RECEIVER] Public key PEM sent to sender: " + publicKeyPem);
        return privateKey;
    }

    /**
     * Utility to get SHA-256 fingerprint of a private key
     */
    private String getPrivateKeyFingerprint(java.security.PrivateKey key) {
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
}

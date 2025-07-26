package com.securetransfer.controller;

import com.securetransfer.security.JwtUtil;
import com.securetransfer.service.UserService;
import com.securetransfer.model.TransferSession;
import com.securetransfer.model.User;
import com.securetransfer.service.TransferSessionService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
@Slf4j
public class FileTransferController {
    private static final String BACKEND_LOG_PATH = System.getProperty("user.dir") + "/securetransfer-backend.log";

    private static void logToBackendFile(String message) {
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get(BACKEND_LOG_PATH), (java.time.LocalDateTime.now() + " " + message + "\n").getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("Failed to write to backend log file: {}", e.getMessage());
        }
    }
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> transferFile(@RequestHeader("Authorization") String authHeader,
                                          @RequestBody Map<String, String> req) {
        // Validate JWT
        String token = authHeader.replace("Bearer ", "");
        String sender = jwtUtil.extractUsername(token);
        String receiver = req.get("receiver");
        String encryptedFile = req.get("encryptedFile"); // base64 string
        String encryptedAesKey = req.get("encryptedAesKey"); // base64 string
        String filename = req.get("filename");

        // Log file transfer on backend
        log.info("[SERVER] File sent: {} | From: {} | To: {}", filename, sender, receiver);
        logToBackendFile("[SERVER] File sent: " + filename + " | From: " + sender + " | To: " + receiver);

        // Check receiver exists
        if (userService.findByUsername(receiver).isEmpty()) {
            logToBackendFile("[SERVER] Receiver not found: " + receiver + ". File: " + filename + " from sender: " + sender);
            log.warn("[SERVER] Receiver not found: {}. File: {} from sender: {}", receiver, filename, sender);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Receiver not found",
                "code", "RECEIVER_NOT_FOUND"
            ));
        }

        // Log file receipt for receiver
        logToBackendFile("[RECEIVER] File received: " + filename + " | From: " + sender + " | To: " + receiver);
        log.info("[RECEIVER] File received: {} | From: {} | To: {}", filename, sender, receiver);

        // In a real app, you would relay this to the receiver (e.g., via websocket, notification, etc.)
        // For now, just return the payload as a simulation
        return ResponseEntity.ok(Map.of(
                "from", sender,
                "to", receiver,
                "filename", filename,
                "encryptedFile", encryptedFile,
                "encryptedAesKey", encryptedAesKey
        ));
    }
    private final TransferSessionService transferSessionService;

    @PostMapping("/initiate")
    public ResponseEntity<?> initiateTransfer(@RequestHeader("Authorization") String authHeader, @RequestBody InitiateRequest request) {
        String token = authHeader.replace("Bearer ", "");
        String sender = jwtUtil.extractUsername(token);
        log.info("Initiate transfer: sender={}, code={}", sender, request.getCode());
        TransferSession session = transferSessionService.initiateSession(sender, request.getCode());
        log.info("Transfer session initiated: sessionId={}, sender={}, code={}", session.getId(), sender, session.getCode());
        return ResponseEntity.ok(session);
    }

    @GetMapping("/status/{code}")
    public ResponseEntity<?> getStatus(@RequestHeader("Authorization") String authHeader, @PathVariable String code) {
        String token = authHeader.replace("Bearer ", "");
        String authenticatedUsername = jwtUtil.extractUsername(token);
        Optional<TransferSession> sessionOpt = transferSessionService.getSessionByCode(code);
        if (sessionOpt.isPresent()) {
            TransferSession session = sessionOpt.get();
            // Allow sender or receiver to view status
            boolean isSender = authenticatedUsername.equals(session.getSenderUsername());
            boolean isReceiver = authenticatedUsername.equals(session.getReceiverUsername());
            if (!isSender && !isReceiver) {
                log.warn("Unauthorized status access: code={}, requestedBy={}, senderUsername={}, receiverUsername={}", code, authenticatedUsername, session.getSenderUsername(), session.getReceiverUsername());
                return ResponseEntity.status(403).body(Map.of(
                    "error", "Forbidden: Only the sender or receiver can view this session status.",
                    "code", "FORBIDDEN"
                ));
            }
            return ResponseEntity.ok(session);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyCode(@RequestBody VerifyRequest request) {
        String authenticatedUsername = null;
        if (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null) {
            authenticatedUsername = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        }
        String receiverUsername = request.getReceiverUsername(); // get the receiver username
        
        log.info("/verify called: code={}, senderUsername(from JWT)={}, receiverUsername={}", request.getCode(), authenticatedUsername, receiverUsername);
        // Fetch receiver's public key from file: ~/.securetransfer/{username}/public_key.pem
        String receiverPublicKey = null;
        String linuxUserHome = System.getProperty("user.home");
        String pubKeyPath = linuxUserHome + "/.securetransfer/" + receiverUsername + "/public_key.pem";
        try {
            receiverPublicKey = java.nio.file.Files.readString(java.nio.file.Paths.get(pubKeyPath));
            log.info("/verify loaded receiverPublicKey from file {}: {}", pubKeyPath, receiverPublicKey);
        } catch (Exception ex) {
            log.warn("Receiver public key file not found or unreadable for username={} at {}: {}", receiverUsername, pubKeyPath, ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Receiver public key file not found or unreadable",
                "code", "RECEIVER_KEY_FILE_NOT_FOUND"
            ));
        }
        TransferSession session = transferSessionService.verifySession(request.getCode(), authenticatedUsername, receiverPublicKey);
        if (session != null) {
            log.info("Session claimed successfully: {}", session);
            // Add receiverPublicKey to response for debugging
            return ResponseEntity.ok(Map.of(
                "session", session,
                "receiverPublicKey", receiverPublicKey
            ));
        }
        log.warn("Session claim failed for code={}, senderUsername={}, receiverUsername={}", request.getCode(), authenticatedUsername, receiverUsername);
        return ResponseEntity.badRequest().body(Map.of(
            "error", "Invalid code or code already claimed",
            "code", "INVALID_CODE_OR_ALREADY_CLAIMED"
        ));
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendFiles(
        @RequestHeader("Authorization") String authHeader,
        @RequestParam String sessionId,
        @RequestParam("encryptedAesKey") List<String> encryptedAesKeys,
        @RequestParam("encryptedFile") List<MultipartFile> encryptedFiles,
        @RequestParam("filename") List<String> filenames,
        @RequestParam("iv") List<String> ivs
    ) {
        String token = authHeader.replace("Bearer ", "");
        String senderUsername = jwtUtil.extractUsername(token);
        TransferSession session = transferSessionService.getSessionByCode(sessionId).orElse(null);
        if (session == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Session not found",
                "code", "SESSION_NOT_FOUND"
            ));
        }
        if (session.getStatus() == TransferSession.Status.EXPIRED || session.getStatus() == TransferSession.Status.COMPLETED) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Session expired or completed",
                "code", "SESSION_EXPIRED_OR_COMPLETED"
            ));
        }
        try {
            List<Object> savedFiles = transferSessionService.addFilesToSession(session, senderUsername, encryptedAesKeys, encryptedFiles, filenames, ivs);
            return ResponseEntity.ok(Map.of("files", savedFiles));
        } catch (Exception e) {
            log.error("Error processing file upload", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "File upload failed",
                "code", "FILE_UPLOAD_FAILED"
            ));
        }
    }

    @GetMapping("/inbox")
    public ResponseEntity<?> getInbox(@RequestParam String receiver) {
        List<Object> inbox = transferSessionService.getReceiverInbox(receiver);
        // Log what is being sent to the frontend (console and backend file)
        if (inbox.isEmpty()) {
            System.out.println("[FileTransferController] Inbox for receiver '" + receiver + "' is empty.");
            logToBackendFile("[FileTransferController] Inbox for receiver '" + receiver + "' is empty.");
        } else {
            System.out.println("[FileTransferController] Sending inbox to receiver '" + receiver + "':");
            logToBackendFile("[FileTransferController] Sending inbox to receiver '" + receiver + "':");
            for (Object fileObj : inbox) {
                System.out.println("  " + fileObj);
                logToBackendFile("  " + fileObj);
            }
        }

        // Only delete files if session status is FILE_SENT or COMPLETED
        TransferSession session = transferSessionService.getSessionByReceiver(receiver);
        if (session != null && (session.getStatus() == TransferSession.Status.FILE_SENT || session.getStatus() == TransferSession.Status.COMPLETED)) {
            for (Object fileObj : inbox) {
                if (fileObj instanceof java.util.Map) {
                    Object idObj = ((java.util.Map<?,?>)fileObj).get("id");
                    if (idObj instanceof Long) {
                        transferSessionService.deleteFileById((Long)idObj);
                    } else if (idObj != null) {
                        try {
                            Long idLong = Long.valueOf(idObj.toString());
                            transferSessionService.deleteFileById(idLong);
                        } catch (Exception ignore) {}
                    }
                }
            }
        }
        return ResponseEntity.ok(Map.of("files", inbox));
    }

    @DeleteMapping("/inbox/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable Long id) {
        boolean deleted = transferSessionService.deleteFileById(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "File deleted"));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "File not found or already deleted",
                "code", "FILE_NOT_FOUND_OR_DELETED"
            ));
        }
    }

    // DTOs for requests
    public static class InitiateRequest {
        private String code;
        // getters and setters
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }
    public static class VerifyRequest {
        private String code;
        private String receiverUsername;
        // getters and setters
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getReceiverUsername() { return receiverUsername; }
        public void setReceiverUsername(String receiverUsername) { this.receiverUsername = receiverUsername; }
    }

}

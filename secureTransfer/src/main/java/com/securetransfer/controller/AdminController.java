package com.securetransfer.controller;

import com.securetransfer.model.User;
import com.securetransfer.model.TransferSession;
import com.securetransfer.model.Notification;
import com.securetransfer.model.AuditLog;
import com.securetransfer.repository.UserRepository;
import com.securetransfer.repository.TransferSessionRepository;
import com.securetransfer.repository.NotificationRepository;
import com.securetransfer.repository.AuditLogRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminController.class);
    private final UserRepository userRepository;
    private final TransferSessionRepository sessionRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private static final String LOG_FILE_PATH = System.getProperty("user.dir") + "/securetransfer-backend.log";

    public AdminController(UserRepository userRepository, TransferSessionRepository sessionRepository, NotificationRepository notificationRepository, AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogRepository = auditLogRepository;
    }

    private static void logToFile(String message) {
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get(LOG_FILE_PATH), (java.time.LocalDateTime.now() + " " + message + "\n").getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("Failed to write to backend log file: {}", e.getMessage());
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<TransferSession>> getAllSessions() {
        return ResponseEntity.ok(sessionRepository.findAll());
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> getAllNotifications() {
        return ResponseEntity.ok(notificationRepository.findAll());
    }

    @GetMapping("/auditlogs")
    public ResponseEntity<List<AuditLog>> getAllAuditLogs() {
        return ResponseEntity.ok(auditLogRepository.findAll());
    }

    @DeleteMapping("/user/{username}")
    public ResponseEntity<Void> deleteUser(@PathVariable String username) {
        userRepository.deleteByUsername(username);
        logToFile("[DELETE USER] Username: " + username + ", Endpoint: /api/admin/user/" + username);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/session/{code}")
    public ResponseEntity<Void> deleteSession(@PathVariable String code) {
        sessionRepository.deleteByCode(code);
        logToFile("[DELETE SESSION] Code: " + code + ", Endpoint: /api/admin/session/" + code);
        log.info("Session deleted via DELETE /api/admin/session/{} (service)", code);
        return ResponseEntity.ok().build();
    }
}

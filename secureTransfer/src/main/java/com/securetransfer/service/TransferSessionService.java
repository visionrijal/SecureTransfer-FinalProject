package com.securetransfer.service;

import com.securetransfer.model.TransferSession;
import com.securetransfer.repository.TransferSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Map;
import com.securetransfer.model.TransferFile;
import org.springframework.web.multipart.MultipartFile;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import org.springframework.scheduling.annotation.Scheduled;

@Service
@Slf4j
public class TransferSessionService {
    @Autowired
    private TransferSessionRepository transferSessionRepository;
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private NotificationService notificationService;

    public TransferSession initiateSession(String sender, String code) {
        log.info("Initiating transfer session: sender={}, code={}", sender, code);
        // Clear any existing queue for this code to avoid old files persisting
        sessionFileQueue.remove(code);
        TransferSession session = new TransferSession();
        session.setSenderUsername(sender);
        session.setCode(code);
        session.setStatus(TransferSession.Status.INITIATED);
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        TransferSession saved = transferSessionRepository.save(session);
        log.info("Transfer session created: {}", saved);
        auditLogService.log(sender, code, "SESSION_INITIATED", null, "Session initiated");
        notificationService.createNotification(sender, "Transfer session initiated. Code: " + code);
        return saved;
    }

    public Optional<TransferSession> getSessionByCode(String code) {
        log.info("Fetching transfer session by code: {}", code);
        Optional<TransferSession> session = transferSessionRepository.findByCode(code);
        log.info("Session found: {}", session);
        return session;
    }
    public TransferSession getSessionByReceiver(String receiver) {
        return transferSessionRepository.findAll().stream()
            .filter(s -> receiver.equals(s.getReceiverUsername()) && s.getStatus() != TransferSession.Status.EXPIRED && s.getStatus() != TransferSession.Status.COMPLETED)
            .findFirst()
            .orElse(null);
    }

    public TransferSession verifySession(String code, String receiverUsername, String receiverPublicKey) {
        log.info("Verifying session: code={}, receiverUsername={}, receiverPublicKey={}", code, receiverUsername, receiverPublicKey);
        Optional<TransferSession> sessionOpt = transferSessionRepository.findByCode(code);
        if (sessionOpt.isPresent()) {
            TransferSession session = sessionOpt.get();
            if (session.getReceiverUsername() == null) {
                session.setReceiverUsername(receiverUsername);
                session.setReceiverPublicKey(receiverPublicKey);
                session.setStatus(TransferSession.Status.VERIFIED);
                TransferSession saved = transferSessionRepository.save(session);
                log.info("Session claimed and verified: {}", saved);
                auditLogService.log(receiverUsername, code, "SESSION_CLAIMED", null, "Session claimed by receiver");
                notificationService.createNotification(receiverUsername, "You claimed transfer session: " + code);
                notificationService.createNotification(session.getSenderUsername(), "Your session was claimed by receiver: " + receiverUsername);
                return saved;
            } else {
                log.warn("Session code already claimed by receiver: {}", session.getReceiverUsername());
                return null;
            }
        }
        log.warn("Session not found for code: {}", code);
        return null;
    }

    // Additional methods for status polling, expiration, etc.

    // In-memory queue for file relay
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<TransferFile>> sessionFileQueue = new ConcurrentHashMap<>();

    // Add multiple files to session queue
    public List<Object> addFilesToSession(TransferSession session, String senderUsername, List<String> encryptedAesKeys, List<MultipartFile> encryptedFiles, List<String> filenames, List<String> ivs) throws Exception {
        if (session.getStatus() == TransferSession.Status.EXPIRED || session.getStatus() == TransferSession.Status.COMPLETED) {
            throw new IllegalStateException("Session expired or completed");
        }
        ConcurrentLinkedQueue<TransferFile> queue = sessionFileQueue.computeIfAbsent(session.getCode(), k -> new ConcurrentLinkedQueue<>());
        List<TransferFile> newFiles = new ArrayList<>();
        for (int i = 0; i < encryptedFiles.size(); i++) {
            TransferFile file = new TransferFile();
            file.setSessionCode(session.getCode());
            file.setFilename(filenames.get(i));
            file.setEncryptedAesKey(encryptedAesKeys.get(i));
            file.setEncryptedFileData(java.util.Base64.getEncoder().encodeToString(encryptedFiles.get(i).getBytes()));
            file.setIv(ivs.get(i));
            file.setUploadedAt(LocalDateTime.now());
            file.setId(System.currentTimeMillis() + i); // simple unique id
            newFiles.add(file);
            auditLogService.log(senderUsername, session.getCode(), "FILE_UPLOADED", file.getId(), "File uploaded: " + file.getFilename());
            notificationService.createNotification(session.getReceiverUsername(), "New file received: " + file.getFilename() + " in session: " + session.getCode());
        }
        queue.addAll(newFiles);
        log.info("Queue status for session {}: size={}, files=[{}]", session.getCode(), queue.size(),
            queue.stream().map(TransferFile::getFilename).reduce((a, b) -> a + ", " + b).orElse("<empty>"));
        // Update session status to FILE_SENT if files are present
        if (queue.size() > 0 && session.getStatus() != TransferSession.Status.FILE_SENT) {
            session.setStatus(TransferSession.Status.FILE_SENT);
            transferSessionRepository.save(session);
            auditLogService.log(senderUsername, session.getCode(), "SESSION_FILE_SENT", null, "Session status updated to FILE_SENT");
        }
        return newFiles.stream().map(f -> Map.of(
            "id", f.getId(),
            "filename", f.getFilename(),
            "encryptedAesKey", f.getEncryptedAesKey(),
            "encryptedFileData", f.getEncryptedFileData()
        )).collect(java.util.stream.Collectors.toList());
    }

    // Get receiver's inbox (all files in session queue)
    public List<Object> getReceiverInbox(String receiver) {
        List<Object> files = new ArrayList<>();
        List<String> logFilenames = new ArrayList<>();
        transferSessionRepository.findAll().stream()
            .filter(s -> receiver.equals(s.getReceiverUsername()) && s.getStatus() != TransferSession.Status.EXPIRED && s.getStatus() != TransferSession.Status.COMPLETED)
            .forEach(session -> {
                Queue<TransferFile> queue = sessionFileQueue.get(session.getCode());
                if (queue != null) {
                    queue.forEach(f -> {
                        if (f.getDeletedAt() == null) {
                            files.add(Map.of(
                                "id", f.getId(),
                                "filename", f.getFilename(),
                                "encryptedAesKey", f.getEncryptedAesKey(),
                                "encryptedFileData", f.getEncryptedFileData(),
                                "iv", f.getIv()
                            ));
                            logFilenames.add(f.getFilename());
                            auditLogService.log(receiver, session.getCode(), "INBOX_VIEWED", f.getId(), "Inbox viewed for file: " + f.getFilename());
                        }
                    });
                    if (!logFilenames.isEmpty()) {
                        log.info("Receiver '{}' got files in session '{}': {}", receiver, session.getCode(), logFilenames);
                    }
                }
            });
        return files;
    }

    // Delete file by id from queue and update session status if all files deleted
    @org.springframework.transaction.annotation.Transactional
    public boolean deleteFileById(Long id) {
        for (Map.Entry<String, ConcurrentLinkedQueue<TransferFile>> entry : sessionFileQueue.entrySet()) {
            ConcurrentLinkedQueue<TransferFile> queue = entry.getValue();
            for (TransferFile file : queue) {
                if (file.getId() != null && file.getId().equals(id) && file.getDeletedAt() == null) {
                    file.setDeletedAt(LocalDateTime.now());
                    auditLogService.log(null, entry.getKey(), "FILE_DELETED", file.getId(), "File deleted: " + file.getFilename());
                    TransferSession sessionForNotification = transferSessionRepository.findByCode(entry.getKey()).orElse(null);
                    if (sessionForNotification != null) {
                        notificationService.createNotification(sessionForNotification.getReceiverUsername(), "File deleted: " + file.getFilename() + " from session: " + entry.getKey());
                    }
                    // If all files deleted, mark session completed
                    TransferSession session = transferSessionRepository.findByCode(entry.getKey()).orElse(null);
                    if (session != null) {
                        boolean allDeleted = queue.stream().allMatch(f -> f.getDeletedAt() != null);
                        if (allDeleted && session.getStatus() != TransferSession.Status.COMPLETED) {
                            session.setStatus(TransferSession.Status.COMPLETED);
                            session.setCompletedAt(LocalDateTime.now());
                            transferSessionRepository.save(session);
                            auditLogService.log(session.getSenderUsername(), entry.getKey(), "SESSION_COMPLETED", null, "Session completed after all files deleted");
                            // Immediately clean up session and queue
                            sessionFileQueue.remove(entry.getKey());
                            transferSessionRepository.deleteByCode(entry.getKey());
                            log.info("Session {} and its queue deleted after completion", entry.getKey());
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Delete session and all associated files and queue by code.
     */
    public void deleteSessionByCode(String code) {
        // Remove from in-memory queue
        sessionFileQueue.remove(code);
        // Remove from database
        transferSessionRepository.deleteByCode(code);
        log.info("Session {} and all associated files/queue deleted by explicit request", code);
        auditLogService.log(null, code, "SESSION_DELETED", null, "Session and all files deleted by explicit request");
    }

    // Scheduled cleanup for expired/unclaimed sessions and completed transfers
    @Scheduled(fixedRate = 300000) // every 5 minutes
    public void cleanupQueues() {
        LocalDateTime now = LocalDateTime.now();
        for (TransferSession session : transferSessionRepository.findAll()) {
            // Expire unclaimed sessions
            if (session.getStatus() == TransferSession.Status.INITIATED && session.getCreatedAt().plusMinutes(30).isBefore(now)) {
                session.setStatus(TransferSession.Status.EXPIRED);
                session.setExpiresAt(now);
                transferSessionRepository.save(session);
                sessionFileQueue.remove(session.getCode());
                auditLogService.log(session.getSenderUsername(), session.getCode(), "SESSION_EXPIRED", null, "Session expired and files deleted");
                log.info("Session {} expired and queue removed", session.getCode());
            }
            // Cleanup completed sessions
            if (session.getStatus() == TransferSession.Status.COMPLETED || session.getStatus() == TransferSession.Status.EXPIRED) {
                sessionFileQueue.remove(session.getCode());
                log.info("Session {} cleaned up from queue", session.getCode());
            }
        }
    }
}

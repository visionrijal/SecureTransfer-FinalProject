package com.securetransfer.service;

import com.securetransfer.model.TransferSession;
import com.securetransfer.repository.TransferSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class SessionCleanupService {
    @Autowired
    private TransferSessionRepository transferSessionRepository;

    // Run every 5 minutes
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredSessions() {
        List<TransferSession> sessions = transferSessionRepository.findAll();
        for (TransferSession session : sessions) {
            if (session.getStatus() != null && session.getStatus().equals(TransferSession.Status.EXPIRED)) continue;
            LocalDateTime now = LocalDateTime.now();
            // Expire if not claimed within 30 min
            if (session.getStatus() == TransferSession.Status.INITIATED && session.getCreatedAt().plusMinutes(30).isBefore(now)) {
                session.setStatus(TransferSession.Status.EXPIRED);
                session.setExpiresAt(now);
                transferSessionRepository.save(session);
                log.info("Session expired due to unclaimed timeout: {}", session.getCode());
            }
            // Expire if all files are deleted/claimed (COMPLETED)
            if (session.getFiles() != null && !session.getFiles().isEmpty()) {
                boolean allDeleted = session.getFiles().stream().allMatch(f -> f.getDeletedAt() != null);
                if (allDeleted && session.getStatus() != TransferSession.Status.COMPLETED) {
                    session.setStatus(TransferSession.Status.COMPLETED);
                    session.setCompletedAt(now);
                    transferSessionRepository.save(session);
                    log.info("Session completed and cleaned up: {}", session.getCode());
                }
            }
        }
    }
}

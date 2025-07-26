package com.securetransfer.service;

import com.securetransfer.model.AuditLog;
import com.securetransfer.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    public void log(String username, String sessionCode, String action, Long fileId, String details) {
        AuditLog log = new AuditLog(Instant.now(), username, sessionCode, action, fileId, details);
        auditLogRepository.save(log);
    }

    public List<AuditLog> getLogsBySessionCode(String sessionCode) {
        return auditLogRepository.findBySessionCode(sessionCode);
    }

    public List<AuditLog> getLogsByUsername(String username) {
        return auditLogRepository.findByUsername(username);
    }
}

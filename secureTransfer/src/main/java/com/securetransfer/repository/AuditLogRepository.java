package com.securetransfer.repository;

import com.securetransfer.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findBySessionCode(String sessionCode);
    List<AuditLog> findByUsername(String username);
}

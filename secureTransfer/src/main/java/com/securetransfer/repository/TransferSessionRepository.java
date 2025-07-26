package com.securetransfer.repository;

import com.securetransfer.model.TransferSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TransferSessionRepository extends JpaRepository<TransferSession, Long> {
    void deleteByCode(String code);
    Optional<TransferSession> findByCode(String code);
}

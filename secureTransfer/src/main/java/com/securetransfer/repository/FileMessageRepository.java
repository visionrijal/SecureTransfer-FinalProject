package com.securetransfer.repository;

import com.securetransfer.model.FileMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FileMessageRepository extends JpaRepository<FileMessage, Long> {
    List<FileMessage> findByReceiverUsername(String receiverUsername);
}

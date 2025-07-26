package com.securetransfer.service;

import com.securetransfer.model.FileMessage;
import com.securetransfer.repository.FileMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class FileMessageService {
    @Autowired
    private FileMessageRepository fileMessageRepository;

    public FileMessage sendFile(FileMessage fileMessage) {
        log.info("Sending file: sender={}, receiver={}, sessionId={}", fileMessage.getSenderUsername(), fileMessage.getReceiverUsername(), fileMessage.getTransferSession() != null ? fileMessage.getTransferSession().getId() : null);
        fileMessage.setSentAt(LocalDateTime.now());
        FileMessage saved = fileMessageRepository.save(fileMessage);
        log.info("File sent and saved: {}", saved);
        return saved;
    }

    public List<FileMessage> getInbox(String receiverUsername) {
        log.info("Fetching inbox for receiver: {}", receiverUsername);
        List<FileMessage> inbox = fileMessageRepository.findByReceiverUsername(receiverUsername);
        log.info("Inbox size: {}", inbox.size());
        return inbox;
    }

    public void deleteFile(Long id) {
        log.info("Deleting file with id: {}", id);
        fileMessageRepository.deleteById(id);
    }
}

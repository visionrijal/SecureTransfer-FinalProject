package com.securetransfer.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class FileMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "transfer_session_id")
    private TransferSession transferSession;

    @Column(nullable = false)
    private String senderUsername;

    @Column(nullable = false)
    private String receiverUsername;

    @Column(columnDefinition = "TEXT")
    private String encryptedFile;

    @Column(columnDefinition = "TEXT")
    private String encryptedAesKey;

    private LocalDateTime sentAt;
}

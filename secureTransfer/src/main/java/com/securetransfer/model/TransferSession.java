package com.securetransfer.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class TransferSession {
    @Column(nullable = false)
    private String senderUsername;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 6, unique = true)
    private String code;

    @Column(nullable = true)
    private String receiverUsername;

    @Enumerated(EnumType.STRING)
    private Status status;

    private LocalDateTime createdAt;
    private LocalDateTime claimedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String receiverPublicKey;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "sessionCode", referencedColumnName = "code")
    private List<TransferFile> files;

    @PrePersist
    private void validateFields() {
        if (code == null || code.length() != 6) {
            throw new IllegalArgumentException("TransferSession code must be exactly 6 characters and not null");
        }
        if (senderUsername == null || senderUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("TransferSession senderUsername must not be null or empty");
        }
        this.createdAt = LocalDateTime.now();
        this.status = Status.INITIATED;
    }

    public enum Status {
        INITIATED, VERIFIED, FILE_SENT, COMPLETED, EXPIRED
    }
}

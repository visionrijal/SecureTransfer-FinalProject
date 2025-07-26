package com.securetransfer.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class TransferFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sessionCode;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedAesKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedFileData;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String iv;

    private LocalDateTime uploadedAt;
    private LocalDateTime claimedAt;
    private LocalDateTime deletedAt;

    @PrePersist
    private void setUploadTime() {
        this.uploadedAt = LocalDateTime.now();
    }
}

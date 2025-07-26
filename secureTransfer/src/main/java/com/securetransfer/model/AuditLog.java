package com.securetransfer.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant timestamp;
    private String username;
    private String sessionCode;
    private String action;
    private Long fileId;
    private String details;

    public AuditLog() {}
    public AuditLog(Instant timestamp, String username, String sessionCode, String action, Long fileId, String details) {
        this.timestamp = timestamp;
        this.username = username;
        this.sessionCode = sessionCode;
        this.action = action;
        this.fileId = fileId;
        this.details = details;
    }
    // Getters and setters
    public Long getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getSessionCode() { return sessionCode; }
    public void setSessionCode(String sessionCode) { this.sessionCode = sessionCode; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}

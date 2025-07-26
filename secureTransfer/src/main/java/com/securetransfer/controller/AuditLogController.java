package com.securetransfer.controller;

import com.securetransfer.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditLogController {
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<?> getAuditLogs(@RequestParam(required = false) String sessionCode,
                                          @RequestParam(required = false) String username) {
        if (sessionCode != null) {
            return ResponseEntity.ok(auditLogService.getLogsBySessionCode(sessionCode));
        } else if (username != null) {
            return ResponseEntity.ok(auditLogService.getLogsByUsername(username));
        } else {
            return ResponseEntity.badRequest().body("Provide sessionCode or username");
        }
    }
}

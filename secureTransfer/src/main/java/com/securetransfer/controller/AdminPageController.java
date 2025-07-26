package com.securetransfer.controller;

import com.securetransfer.repository.UserRepository;
import com.securetransfer.repository.TransferSessionRepository;
import com.securetransfer.repository.NotificationRepository;
import com.securetransfer.repository.AuditLogRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {
    private final UserRepository userRepository;
    private final TransferSessionRepository sessionRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminPageController(UserRepository userRepository, TransferSessionRepository sessionRepository, NotificationRepository notificationRepository, AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/admin")
    public String adminDashboard(Model model) {
        var users = userRepository.findAll();
        var transferSessions = sessionRepository.findAll();
        var notifications = notificationRepository.findAll();
        var auditlogs = auditLogRepository.findAll();
        System.out.println("[AdminPageController] Rendering admin dashboard: "
            + users.size() + " users, "
            + transferSessions.size() + " transferSessions, "
            + notifications.size() + " notifications, "
            + auditlogs.size() + " audit logs");
        model.addAttribute("users", users != null ? users : java.util.Collections.emptyList());
        model.addAttribute("transferSessions", transferSessions != null ? transferSessions : java.util.Collections.emptyList());
        model.addAttribute("notifications", notifications != null ? notifications : java.util.Collections.emptyList());
        model.addAttribute("auditlogs", auditlogs != null ? auditlogs : java.util.Collections.emptyList());
        return "admin";
    }
}

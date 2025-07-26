package com.securetransfer.controller;

import com.securetransfer.model.Notification;
import com.securetransfer.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails.getUsername();
        return ResponseEntity.ok(notificationService.getNotifications(username));
    }

    @PostMapping("/read/{id}")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        Notification notification = notificationService.getNotificationById(id);
        if (notification == null || !notification.getUsername().equals(userDetails.getUsername())) {
            return ResponseEntity.status(403).build();
        }
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }
}

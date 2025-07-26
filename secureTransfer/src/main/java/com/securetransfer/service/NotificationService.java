package com.securetransfer.service;

import com.securetransfer.model.Notification;
import com.securetransfer.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NotificationService {
    public Notification getNotificationById(Long id) {
        return notificationRepository.findById(id).orElse(null);
    }
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification createNotification(String username, String message) {
        Notification notification = new Notification(username, message);
        return notificationRepository.save(notification);
    }

    public List<Notification> getNotifications(String username) {
        return notificationRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public void markAsRead(Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
            System.out.println("Notification " + id + " marked as read for user: " + n.getUsername());
        });
    }
}

package com.securetransferfrontend.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

public class HistoryController {
    @FXML private TableView<Notification> notificationTable;
    @FXML private TableColumn<Notification, Long> idColumn;
    @FXML private TableColumn<Notification, String> messageColumn;
    @FXML private TableColumn<Notification, String> createdAtColumn;
    @FXML private TableColumn<Notification, Boolean> readColumn;

    private final ObservableList<Notification> notifications = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        messageColumn.setCellValueFactory(new PropertyValueFactory<>("message"));
        createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        readColumn.setCellValueFactory(new PropertyValueFactory<>("read"));
        notificationTable.setItems(notifications);
        fetchNotifications();
    }

    private void fetchNotifications() {
        Task<List<Notification>> task = new Task<>() {
            @Override
            protected List<Notification> call() throws Exception {
                try (CloseableHttpClient client = HttpClients.createDefault()) {
                    String username = com.securetransferfrontend.controller.MainController.currentUsername;
                    String tokenPath = System.getProperty("user.home") + "/.securetransfer/" + username + "/jwt.token";
                    String jwt = null;
                    try {
                        jwt = Files.readString(Paths.get(tokenPath)).trim();
                    } catch (IOException e) {
                        System.err.println("Could not read JWT token: " + e.getMessage());
                    }
                    HttpGet request = new HttpGet("http://localhost:8080/api/notifications");
                    if (jwt != null && !jwt.isEmpty()) {
                        request.addHeader("Authorization", "Bearer " + jwt);
                        System.out.println("Added JWT Authorization header for user: " + username);
                    } else {
                        System.err.println("JWT token is missing, request will likely fail.");
                    }
                    System.out.println("Sending GET request to: " + request.getUri());
                    ClassicHttpResponse response = (ClassicHttpResponse) client.execute(request);
                    int status = response.getCode();
                    System.out.println("Response status: " + status);
                    String body = new String(response.getEntity().getContent().readAllBytes());
                    System.out.println("Response body: " + body);
                    ObjectMapper mapper = new ObjectMapper();
                    return mapper.readValue(body, new TypeReference<List<Notification>>(){});
                }
            }
        };
        task.setOnSucceeded(e -> notifications.setAll(task.getValue()));
        task.setOnFailed(e -> {
            System.err.println("Failed to fetch notifications: " + task.getException());
        });
        new Thread(task).start();
    }

    public static class Notification {
        private Long id;
        private String username;
        private String message;
        private boolean read;
        private String createdAt;

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getMessage() { return message; }
        public boolean getRead() { return read; }
        public String getCreatedAt() { return createdAt; }
    }
}

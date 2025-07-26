package com.securetransfer.controller;

import com.securetransfer.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
@Slf4j
public class KeyController {
    private final UserService userService;

    @GetMapping("/{username}")
    public ResponseEntity<?> getPublicKey(@PathVariable String username) {
        log.info("Fetching public key for username: {}", username);
        return userService.findByUsername(username)
                .<ResponseEntity<?>>map(user -> {
                    log.info("User found: {}", user.getUsername());
                    return ResponseEntity.ok(user.getPublicKey());
                })
                .orElseGet(() -> {
                    log.warn("User not found: {}", username);
                    return ResponseEntity.status(404).body(Map.of(
                        "error", "User not found",
                        "code", "USER_NOT_FOUND"
                    ));
                });
    }
}

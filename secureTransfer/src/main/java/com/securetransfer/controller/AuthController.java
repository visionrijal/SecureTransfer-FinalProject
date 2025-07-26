package com.securetransfer.controller;

import com.securetransfer.model.User;
import com.securetransfer.security.JwtUtil;
import com.securetransfer.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");
        String publicKey = req.get("publicKey");
        log.info("Register attempt: username={}, publicKey_present={}", username, publicKey != null);
        if (userService.findByUsername(username).isPresent()) {
            log.warn("Registration failed: username {} already exists", username);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Username already exists",
                "code", "USERNAME_EXISTS"
            ));
        }
        User user = userService.register(username, password, publicKey);
        log.info("Registration successful: username={}", username);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");
        log.info("Login attempt: username={}", username);
        var userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty() || !passwordEncoder.matches(password, userOpt.get().getPassword())) {
            log.warn("Login failed: invalid credentials for username={}", username);
            return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid credentials",
                "code", "INVALID_CREDENTIALS"
            ));
        }
        String token = jwtUtil.generateToken(username);
        log.info("Login successful: username={}", username);
        return ResponseEntity.ok(Map.of("token", token));
    }
}

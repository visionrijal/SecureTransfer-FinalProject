package com.securetransfer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminLoginController {
    @GetMapping("/admin-login")
    public String adminLogin() {
        System.out.println("AdminLoginController: /admin-login GET hit");
        return "admin-login";
    }
}


package com.securetransfer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AdminWebSecurityConfig {
    @Value("${admin.username}")
    private String adminUsername;
    @Value("${admin.password}")
    private String adminPassword;

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        System.out.println(
                "AdminWebSecurityConfig: adminFilterChain created with matcher /admin-login**, /admin/**, /api/admin/**");
        http
                .securityMatcher("/admin-login**", "/admin/**", "/api/admin/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin-login", "/error", "/favicon.ico", "/css/**", "/js/**", "/images/**")
                        .permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage("/admin-login")
                        .defaultSuccessUrl("/admin", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .permitAll())
                .userDetailsService(adminUserDetailsService());
        return http.build();
    }

    @Bean
    public UserDetailsService adminUserDetailsService() {
        UserDetails admin = User.withUsername(adminUsername)
                .password(adminPassword)
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }
    // Removed defaultFilterChain bean to avoid filter chain conflict. Only
    // admin-specific filter chain remains.
}

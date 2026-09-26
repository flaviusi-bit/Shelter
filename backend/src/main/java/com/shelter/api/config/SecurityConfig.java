package com.shelter.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/users/me/password").authenticated()
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/animals").hasAnyRole("ADMIN", "VETERINARIAN", "COORDINATOR")
                .requestMatchers(HttpMethod.PUT, "/api/animals/**").hasAnyRole("ADMIN", "VETERINARIAN", "COORDINATOR")
                .requestMatchers(HttpMethod.POST, "/api/animals/*/vaccinations").hasAnyRole("ADMIN", "VETERINARIAN")
                .requestMatchers(HttpMethod.POST, "/api/animals/*/dewormings").hasAnyRole("ADMIN", "VETERINARIAN")
                .requestMatchers(HttpMethod.POST, "/api/animals/*/medical-events").hasAnyRole("ADMIN", "VETERINARIAN")
                .requestMatchers(HttpMethod.POST, "/api/animals/*/treatments").hasAnyRole("ADMIN", "VETERINARIAN")
                .requestMatchers(HttpMethod.POST, "/api/animals/*/treatments/*/administrations/generate").hasAnyRole("ADMIN", "VETERINARIAN")
                .requestMatchers(HttpMethod.POST, "/api/animals/*/treatments/*/administrations/*/administer").hasAnyRole("ADMIN", "VETERINARIAN")
                .requestMatchers(HttpMethod.POST, "/api/animals/*/treatments/*/administrations/*/status").hasAnyRole("ADMIN", "VETERINARIAN")
                .requestMatchers(HttpMethod.POST, "/api/animals/*/documents").hasAnyRole("ADMIN", "VETERINARIAN")
                .requestMatchers(HttpMethod.POST, "/api/animals/*/documents/upload").hasAnyRole("ADMIN", "VETERINARIAN")
                .requestMatchers(HttpMethod.POST, "/api/tasks").hasAnyRole("ADMIN", "VETERINARIAN", "COORDINATOR", "VOLUNTEER")
                .requestMatchers(HttpMethod.POST, "/api/tasks/sync-medical-reminders").hasAnyRole("ADMIN", "VETERINARIAN", "COORDINATOR")
                .requestMatchers(HttpMethod.POST, "/api/tasks/*/complete").hasAnyRole("ADMIN", "VETERINARIAN", "COORDINATOR", "VOLUNTEER")
                .requestMatchers(HttpMethod.POST, "/api/tasks/*/skip").hasAnyRole("ADMIN", "VETERINARIAN", "COORDINATOR", "VOLUNTEER")
                .requestMatchers("/api/**").denyAll()
                .anyRequest().authenticated())
            .httpBasic(basic -> {})
            .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

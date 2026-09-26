package com.shelter.api.user;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrentUserController {
    @GetMapping("/api/me")
    public CurrentUserView current(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring("ROLE_".length()))
            .findFirst()
            .orElse("VIEWER");
        return new CurrentUserView(authentication.getName(), role);
    }

    public record CurrentUserView(String username, String role) {}
}

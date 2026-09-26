package com.shelter.api.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private static final Set<String> ALLOWED_ROLES = Set.of(
        "ADMIN", "VETERINARIAN", "COORDINATOR", "VOLUNTEER", "VIEWER"
    );

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserController(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<UserView> list() {
        return users.findAll().stream()
            .map(u -> new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isActive()))
            .toList();
    }

    @PostMapping
    public UserView create(@RequestBody CreateUserRequest r) {
        String username = r.username() == null ? "" : r.username().trim().toLowerCase(Locale.ROOT);
        String displayName = r.displayName() == null ? "" : r.displayName().trim();
        String password = r.password() == null ? "" : r.password();
        String role = r.role() == null ? "" : r.role().trim().toUpperCase(Locale.ROOT);

        if (!username.matches("[a-z0-9][a-z0-9._-]{2,79}")) {
            throw new IllegalArgumentException("Username must be 3-80 characters and contain only letters, numbers, dot, underscore or hyphen");
        }
        if (displayName.isBlank() || displayName.length() > 120) {
            throw new IllegalArgumentException("Display name is required and must be at most 120 characters");
        }
        if (password.length() < 12) {
            throw new IllegalArgumentException("Password must be at least 12 characters");
        }
        if (!ALLOWED_ROLES.contains(role)) {
            throw new IllegalArgumentException("Invalid role");
        }
        if (users.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        AppUser u = new AppUser();
        u.setUsername(username);
        u.setDisplayName(displayName);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRole(role);
        u.setActive(true);
        u = users.save(u);
        return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isActive());
    }

    public record CreateUserRequest(String username, String displayName, String password, String role) {}
    public record UserView(java.util.UUID id, String username, String displayName, String role, boolean active) {}
}

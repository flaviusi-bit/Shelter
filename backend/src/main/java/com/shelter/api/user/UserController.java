package com.shelter.api.user;

import com.shelter.api.audit.AuditLogService;
import org.springframework.security.core.Authentication;
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
    private final AuditLogService audit;

    public UserController(AppUserRepository users, PasswordEncoder passwordEncoder, AuditLogService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @GetMapping
    public List<UserView> list() {
        return users.findAll().stream()
            .map(u -> new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isActive()))
            .toList();
    }

    @PostMapping
    public UserView create(@RequestBody CreateUserRequest r, Authentication auth) {
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
        audit.record(auth.getName(), "CREATE_USER", "USER", u.getId(), "username=" + u.getUsername() + ", role=" + u.getRole());
        return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isActive());
    }

    @PostMapping("/{id}/deactivate")
    public UserView deactivate(@PathVariable java.util.UUID id, Authentication auth) {
        AppUser u = users.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("User not found"));
        if (!u.isActive()) throw new IllegalStateException("User is already inactive");
        if ("ADMIN".equals(u.getRole()) && users.countByRoleAndActiveTrue("ADMIN") <= 1) {
            throw new IllegalStateException("Cannot deactivate the last active administrator");
        }
        u.setActive(false);
        u = users.save(u);
        audit.record(auth.getName(), "DEACTIVATE_USER", "USER", u.getId(), "username=" + u.getUsername() + ", role=" + u.getRole());
        return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isActive());
    }

    @PostMapping("/{id}/reactivate")
    public UserView reactivate(@PathVariable java.util.UUID id, Authentication auth) {
        AppUser u = users.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("User not found"));
        if (u.isActive()) throw new IllegalStateException("User is already active");
        u.setActive(true);
        u = users.save(u);
        audit.record(auth.getName(), "REACTIVATE_USER", "USER", u.getId(), "username=" + u.getUsername() + ", role=" + u.getRole());
        return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isActive());
    }

    @PostMapping("/me/password")
    public void changeMyPassword(@RequestBody ChangePasswordRequest r, Authentication auth) {
        String current = r.currentPassword() == null ? "" : r.currentPassword();
        String next = r.newPassword() == null ? "" : r.newPassword();
        if (next.length() < 12) throw new IllegalArgumentException("Password must be at least 12 characters");

        AppUser u = users.findByUsername(auth.getName())
            .filter(AppUser::isActive)
            .orElseThrow(() -> new java.util.NoSuchElementException("User not found"));

        if (!passwordEncoder.matches(current, u.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passwordEncoder.matches(next, u.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        u.setPasswordHash(passwordEncoder.encode(next));
        users.save(u);
        audit.record(auth.getName(), "CHANGE_PASSWORD", "USER", u.getId(), "username=" + u.getUsername());
    }

    @PostMapping("/{id}/password")
    public void resetPassword(@PathVariable java.util.UUID id, @RequestBody ResetPasswordRequest r, Authentication auth) {
        String next = r.newPassword() == null ? "" : r.newPassword();
        if (next.length() < 12) throw new IllegalArgumentException("Password must be at least 12 characters");
        AppUser u = users.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("User not found"));
        if (!u.isActive()) throw new IllegalStateException("Cannot reset password for an inactive user");
        u.setPasswordHash(passwordEncoder.encode(next));
        users.save(u);
        audit.record(auth.getName(), "RESET_PASSWORD", "USER", u.getId(), "username=" + u.getUsername());
    }

    public record ResetPasswordRequest(String newPassword) {}

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    public record CreateUserRequest(String username, String displayName, String password, String role) {}
    public record UserView(java.util.UUID id, String username, String displayName, String role, boolean active) {}
}

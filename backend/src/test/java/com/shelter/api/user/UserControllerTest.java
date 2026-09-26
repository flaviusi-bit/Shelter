package com.shelter.api.user;

import com.shelter.api.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserControllerTest {
    private AppUserRepository users;
    private PasswordEncoder passwordEncoder;
    private AuditLogService audit;
    private Authentication auth;
    private UserController controller;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        audit = mock(AuditLogService.class);
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin");
        controller = new UserController(users, passwordEncoder, audit);
    }

    @Test
    void deactivateActiveUser() {
        AppUser user = user("volunteer", "VOLUNTEER", true);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);

        UserController.UserView result = controller.deactivate(user.getId(), auth);

        assertFalse(result.active());
        assertFalse(user.isActive());
        verify(audit).record("admin", "DEACTIVATE_USER", "USER", user.getId(), "username=volunteer, role=VOLUNTEER");
    }

    @Test
    void cannotDeactivateAlreadyInactiveUser() {
        AppUser user = user("volunteer", "VOLUNTEER", false);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> controller.deactivate(user.getId(), auth));

        assertEquals("User is already inactive", ex.getMessage());
        verify(users, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void cannotDeactivateLastActiveAdministrator() {
        AppUser user = user("admin2", "ADMIN", true);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.countByRoleAndActiveTrue("ADMIN")).thenReturn(1L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> controller.deactivate(user.getId(), auth));

        assertEquals("Cannot deactivate the last active administrator", ex.getMessage());
        verify(users, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void reactivateInactiveUser() {
        AppUser user = user("volunteer", "VOLUNTEER", false);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);

        UserController.UserView result = controller.reactivate(user.getId(), auth);

        assertTrue(result.active());
        assertTrue(user.isActive());
        verify(audit).record("admin", "REACTIVATE_USER", "USER", user.getId(), "username=volunteer, role=VOLUNTEER");
    }

    @Test
    void cannotReactivateAlreadyActiveUser() {
        AppUser user = user("volunteer", "VOLUNTEER", true);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> controller.reactivate(user.getId(), auth));

        assertEquals("User is already active", ex.getMessage());
        verify(users, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void changeMyPasswordSuccessfully() {
        AppUser user = user("admin", "ADMIN", true);
        user.setPasswordHash("old-hash");
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("new-password-123", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("new-password-123")).thenReturn("new-hash");

        controller.changeMyPassword(new UserController.ChangePasswordRequest("old-password", "new-password-123"), auth);

        assertEquals("new-hash", user.getPasswordHash());
        verify(users).save(user);
        verify(audit).record("admin", "CHANGE_PASSWORD", "USER", user.getId(), "username=admin");
    }

    @Test
    void changeMyPasswordRejectsWrongCurrentPassword() {
        AppUser user = user("volunteer", "VOLUNTEER", true);
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> controller.changeMyPassword(new UserController.ChangePasswordRequest("wrong-password", "new-password-123"), auth));

        assertEquals("Current password is incorrect", ex.getMessage());
        verify(users, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void changeMyPasswordRejectsShortPassword() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> controller.changeMyPassword(new UserController.ChangePasswordRequest("old-password", "short"), auth));

        assertEquals("Password must be at least 12 characters", ex.getMessage());
        verifyNoInteractions(users, passwordEncoder, audit);
    }

    @Test
    void changeMyPasswordRejectsSamePassword() {
        AppUser user = user("admin", "ADMIN", true);
        user.setPasswordHash("old-hash");
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> controller.changeMyPassword(new UserController.ChangePasswordRequest("old-password", "old-password"), auth));

        assertEquals("New password must be different from the current password", ex.getMessage());
        verify(users, never()).save(any());
        verifyNoInteractions(audit);
    }

    private static AppUser user(String username, String role, boolean active) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(username);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setActive(active);
        return user;
    }
}

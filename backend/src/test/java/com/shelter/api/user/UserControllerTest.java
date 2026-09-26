package com.shelter.api.user;

import com.shelter.api.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

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

package com.shelter.api.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShelterUserDetailsServiceTest {
    @Test
    void activeUserIsLoadedWithRole() {
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser user = user("vet", "VETERINARIAN", true);
        when(users.findByUsername("vet")).thenReturn(Optional.of(user));

        UserDetails details = new ShelterUserDetailsService(users).loadUserByUsername("vet");

        assertEquals("vet", details.getUsername());
        assertEquals("hash", details.getPassword());
        assertTrue(details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_VETERINARIAN")));
    }

    @Test
    void inactiveUserCannotBeLoaded() {
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser user = user("volunteer", "VOLUNTEER", false);
        when(users.findByUsername("volunteer")).thenReturn(Optional.of(user));

        assertThrows(UsernameNotFoundException.class,
            () -> new ShelterUserDetailsService(users).loadUserByUsername("volunteer"));
    }

    @Test
    void unknownUserCannotBeLoaded() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
            () -> new ShelterUserDetailsService(users).loadUserByUsername("missing"));
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

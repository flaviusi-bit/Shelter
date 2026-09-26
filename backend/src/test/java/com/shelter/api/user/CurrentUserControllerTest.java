package com.shelter.api.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserControllerTest {

    @Test
    void returnsAuthenticatedUsernameAndRole() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("vet");
        when(authentication.getAuthorities()).thenReturn(List.of(
            new SimpleGrantedAuthority("ROLE_VETERINARIAN")
        ));

        CurrentUserController.CurrentUserView result =
            new CurrentUserController().current(authentication);

        assertEquals("vet", result.username());
        assertEquals("VETERINARIAN", result.role());
    }

    @Test
    void fallsBackToViewerWhenNoRoleAuthorityExists() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("viewer");
        GrantedAuthority authority = new SimpleGrantedAuthority("SOME_AUTHORITY");
        when(authentication.getAuthorities()).thenReturn(List.of(authority));

        CurrentUserController.CurrentUserView result =
            new CurrentUserController().current(authentication);

        assertEquals("viewer", result.username());
        assertEquals("VIEWER", result.role());
    }
}

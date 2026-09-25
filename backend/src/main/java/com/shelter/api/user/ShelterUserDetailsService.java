package com.shelter.api.user;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class ShelterUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;
    public ShelterUserDetailsService(AppUserRepository users){this.users=users;}

    @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser u=users.findByUsername(username)
            .filter(AppUser::isActive)
            .orElseThrow(()->new UsernameNotFoundException(username));
        return User.withUsername(u.getUsername())
            .password(u.getPasswordHash())
            .roles(u.getRole())
            .build();
    }
}

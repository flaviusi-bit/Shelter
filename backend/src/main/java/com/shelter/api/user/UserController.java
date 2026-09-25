package com.shelter.api.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    public UserController(AppUserRepository users, PasswordEncoder passwordEncoder){this.users=users;this.passwordEncoder=passwordEncoder;}

    @GetMapping
    public List<UserView> list(){
        return users.findAll().stream().map(u->new UserView(u.getId(),u.getUsername(),u.getDisplayName(),u.getRole(),u.isActive())).toList();
    }

    @PostMapping
    public UserView create(@RequestBody CreateUserRequest r){
        if(users.findByUsername(r.username()).isPresent()) throw new IllegalArgumentException("Username already exists");
        AppUser u=new AppUser(); u.setUsername(r.username()); u.setDisplayName(r.displayName());
        u.setPasswordHash(passwordEncoder.encode(r.password())); u.setRole(r.role()); u.setActive(true);
        u=users.save(u);
        return new UserView(u.getId(),u.getUsername(),u.getDisplayName(),u.getRole(),u.isActive());
    }

    public record CreateUserRequest(String username,String displayName,String password,String role){}
    public record UserView(java.util.UUID id,String username,String displayName,String role,boolean active){}
}

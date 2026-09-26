package com.shelter.api;

import com.shelter.api.user.AppUser;
import com.shelter.api.user.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableScheduling
public class ShelterApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShelterApplication.class, args);
    }

    @Bean
    CommandLineRunner bootstrapAdmin(AppUserRepository users, PasswordEncoder encoder) {
        return args -> {
            if (users.count() == 0) {
                AppUser admin = new AppUser();
                admin.setUsername(System.getenv().getOrDefault("SHELTER_ADMIN_USERNAME", "admin"));
                admin.setDisplayName(System.getenv().getOrDefault("SHELTER_ADMIN_DISPLAY_NAME", "Shelter Administrator"));
                admin.setPasswordHash(encoder.encode(System.getenv().getOrDefault("SHELTER_ADMIN_PASSWORD", "change-me-now")));
                admin.setRole("ADMIN");
                admin.setActive(true);
                users.save(admin);
            }
        };
    }
}

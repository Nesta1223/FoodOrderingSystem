package com.pbgold.orderpayment.config;

import com.pbgold.orderpayment.model.User;
import com.pbgold.orderpayment.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The API has no user endpoints, so a few demo users are inserted on startup
 * to make orders testable from Swagger. Existing users (matched by email) are left alone.
 */
@Slf4j
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    public DataSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        List<User> demoUsers = List.of(
                User.builder().name("Somchai Jaidee").email("somchai@example.com").phone("0812345678").build(),
                User.builder().name("Malee Srisuk").email("malee@example.com").phone("0898765432").build()
        );
        for (User user : demoUsers) {
            if (!userRepository.existsByEmail(user.getEmail())) {
                User saved = userRepository.save(user);
                log.info("Seeded demo user id={} email={}", saved.getId(), saved.getEmail());
            }
        }
    }
}

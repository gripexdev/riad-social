package com.instagramclone.backend.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminUserInitializer implements ApplicationRunner {

    private final UserService userService;
    private final UserRepository userRepository;

    @Value("${admin.user.username:admin@admin.com}")
    private String adminUsername;

    @Value("${admin.user.email:admin@admin.com}")
    private String adminEmail;

    @Value("${admin.user.password:admin123456}")
    private String adminPassword;

    @Value("${admin.user.full-name:Admin}")
    private String adminFullName;

    public AdminUserInitializer(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean usernameExists = userRepository.findByUsername(adminUsername).isPresent();
        boolean emailExists = userRepository.findByEmail(adminEmail).isPresent();
        if (usernameExists || emailExists) {
            return;
        }

        User adminUser = new User(adminUsername, adminPassword, adminEmail, adminFullName, null, null);
        userService.registerUser(adminUser);
    }
}

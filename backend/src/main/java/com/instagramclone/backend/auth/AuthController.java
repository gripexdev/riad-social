package com.instagramclone.backend.auth;

import com.instagramclone.backend.jwt.JwtUtil;
import com.instagramclone.backend.user.User;
import com.instagramclone.backend.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordResetService passwordResetService;
    private final PasswordResetEmailService passwordResetEmailService;
    private final PasswordResetRateLimiter passwordResetRateLimiter;
    private final boolean returnResetToken;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserService userService,
            JwtUtil jwtUtil,
            PasswordResetService passwordResetService,
            PasswordResetEmailService passwordResetEmailService,
            PasswordResetRateLimiter passwordResetRateLimiter,
            @Value("${password.reset.return-token:false}") boolean returnResetToken) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.passwordResetService = passwordResetService;
        this.passwordResetEmailService = passwordResetEmailService;
        this.passwordResetRateLimiter = passwordResetRateLimiter;
        this.returnResetToken = returnResetToken;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest) {
        User newUser = new User(
            registerRequest.getUsername(),
            registerRequest.getPassword(),
            registerRequest.getEmail(),
            registerRequest.getFullName(),
            null, // bio
            null  // profilePictureUrl
        );
        try {
            User registeredUser = userService.registerUser(newUser);
            final UserDetails userDetails = userService.loadUserByUsername(registeredUser.getUsername());
            final String token = jwtUtil.generateToken(userDetails);
            return ResponseEntity.ok(new AuthResponse(token));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest loginRequest) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            );
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Incorrect username or password");
        }

        final UserDetails userDetails = userService.loadUserByUsername(loginRequest.getUsername());
        final String token = jwtUtil.generateToken(userDetails);

        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpServletRequest) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim();
        String normalizedEmail = email.toLowerCase();
        String message = "If the email exists, a reset link has been sent.";

        String clientIp = passwordResetRateLimiter.resolveClientIp(httpServletRequest);
        if (!passwordResetRateLimiter.isAllowed(clientIp, normalizedEmail)) {
            return ResponseEntity.ok(new ForgotPasswordResponse(message, null));
        }

        if (!normalizedEmail.isEmpty()) {
            Optional<String> resetToken = passwordResetService.createResetToken(normalizedEmail);
            resetToken.ifPresent(token -> {
                try {
                    passwordResetEmailService.sendResetEmail(normalizedEmail, token);
                } catch (Exception e) {
                    log.warn("Failed to send password reset email for {}", normalizedEmail, e);
                }
            });

            if (returnResetToken && resetToken.isPresent()) {
                return ResponseEntity.ok(new ForgotPasswordResponse(message, resetToken.get()));
            }
        }

        return ResponseEntity.ok(new ForgotPasswordResponse(message, null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        String token = request.getToken() == null ? "" : request.getToken().trim();
        if (token.isEmpty()) {
            return ResponseEntity.badRequest().body("Reset token is required");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest().body("Password must be at least 6 characters");
        }

        try {
            passwordResetService.resetPassword(token, request.getNewPassword());
            return ResponseEntity.ok(new ResetPasswordResponse("Password updated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}

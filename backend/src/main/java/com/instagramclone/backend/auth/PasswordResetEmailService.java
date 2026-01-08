package com.instagramclone.backend.auth;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailService.class);

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String frontendBaseUrl;
    private final Duration tokenTtl;

    public PasswordResetEmailService(
            JavaMailSender mailSender,
            @Value("${mail.from:}") String mailFrom,
            @Value("${frontend.base-url:http://localhost:4200}") String frontendBaseUrl,
            @Value("${password.reset.token-ttl-minutes:60}") long tokenTtlMinutes) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.frontendBaseUrl = frontendBaseUrl;
        this.tokenTtl = Duration.ofMinutes(tokenTtlMinutes);
    }

    public void sendResetEmail(String recipientEmail, String token) {
        if (mailFrom == null || mailFrom.isBlank()) {
            log.warn("MAIL_FROM is not configured; skipping password reset email.");
            return;
        }

        String resetLink = buildResetLink(token);
        if (resetLink.isBlank()) {
            log.warn("FRONTEND_BASE_URL is not configured; skipping password reset email.");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipientEmail);
        message.setFrom(mailFrom);
        message.setSubject("Reset your password");
        message.setText(
                "We received a request to reset your password.\n\n"
                        + "Reset link: " + resetLink + "\n\n"
                        + "This link expires in " + tokenTtl.toMinutes() + " minutes.\n"
                        + "If you did not request this, you can ignore this email.\n"
        );

        mailSender.send(message);
    }

    private String buildResetLink(String token) {
        String baseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.isBlank()) {
            return "";
        }
        return baseUrl + "/reset-password?token=" + token;
    }
}

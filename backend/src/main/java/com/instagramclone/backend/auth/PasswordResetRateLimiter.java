package com.instagramclone.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetRateLimiter {

    private final Duration windowDuration;
    private final int maxPerIp;
    private final int maxPerEmail;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public PasswordResetRateLimiter(
            @Value("${password.reset.rate-limit.window-minutes:15}") long windowMinutes,
            @Value("${password.reset.rate-limit.per-ip:5}") int maxPerIp,
            @Value("${password.reset.rate-limit.per-email:5}") int maxPerEmail) {
        this.windowDuration = Duration.ofMinutes(windowMinutes);
        this.maxPerIp = maxPerIp;
        this.maxPerEmail = maxPerEmail;
    }

    public boolean isAllowed(String ipAddress, String email) {
        boolean ipAllowed = allowKey("ip:" + ipAddress, maxPerIp);
        boolean emailAllowed = true;
        if (email != null && !email.isBlank()) {
            emailAllowed = allowKey("email:" + email, maxPerEmail);
        }
        return ipAllowed && emailAllowed;
    }

    public String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr;
    }

    private boolean allowKey(String key, int limit) {
        if (limit <= 0) {
            return true;
        }
        Instant now = Instant.now();
        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.isExpired(now, windowDuration)) {
                return new WindowCounter(now);
            }
            existing.increment();
            return existing;
        });
        return counter.getCount() <= limit;
    }

    private static final class WindowCounter {
        private Instant windowStart;
        private final AtomicInteger count;

        private WindowCounter(Instant windowStart) {
            this.windowStart = windowStart;
            this.count = new AtomicInteger(1);
        }

        private void increment() {
            count.incrementAndGet();
        }

        private int getCount() {
            return count.get();
        }

        private boolean isExpired(Instant now, Duration window) {
            return now.isAfter(windowStart.plus(window));
        }
    }
}

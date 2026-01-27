package com.instagramclone.backend.config;

import com.instagramclone.backend.jwt.JwtAuthenticationFilter;
import com.instagramclone.backend.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void buildsCoreSecurityBeans() {
        SecurityConfig config = new SecurityConfig(userService, jwtAuthenticationFilter, "http://example.com");

        PasswordEncoder encoder = config.passwordEncoder();
        assertNotNull(encoder);

        AuthenticationProvider provider = config.authenticationProvider();
        assertTrue(provider instanceof DaoAuthenticationProvider);

        UrlBasedCorsConfigurationSource source = config.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/"));
        assertNotNull(cors);
        assertTrue(cors.getAllowedOrigins().contains("http://example.com"));
        assertTrue(cors.getAllowedOriginPatterns().contains("http://localhost:*"));

        WebSecurityCustomizer customizer = config.webSecurityCustomizer();
        assertNotNull(customizer);
    }
}

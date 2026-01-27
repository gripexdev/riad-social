package com.instagramclone.backend.config;

import com.instagramclone.backend.jwt.JwtUtil;
import com.instagramclone.backend.user.UserService;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.net.URI;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.WebSocketHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketHandshakeHandlerTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserService userService;

    private WebSocketHandshakeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WebSocketHandshakeHandler(jwtUtil, userService);
    }

    @Test
    void determineUserReturnsAuthenticatedPrincipal() {
        UserDetails userDetails = new User("alice", "pass", List.of());
        when(jwtUtil.extractUsername("token")).thenReturn("alice");
        when(userService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(jwtUtil.validateToken("token", userDetails)).thenReturn(true);

        ServerHttpRequest request = buildRequest("token=token");
        Principal principal = handler.determineUser(request, mock(WebSocketHandler.class), new HashMap<>());

        assertNotNull(principal);
        assertEquals("alice", principal.getName());
    }

    @Test
    void determineUserFallsBackWhenTokenInvalid() {
        when(jwtUtil.extractUsername("bad")).thenThrow(new RuntimeException("bad"));

        ServerHttpRequest request = buildRequest("token=bad");
        Principal principal = handler.determineUser(request, mock(WebSocketHandler.class), new HashMap<>());

        assertNull(principal);
    }

    @Test
    void determineUserStripsBearerToken() {
        ServerHttpRequest request = buildRequest("token=Bearer+token");
        String token = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                handler,
                "resolveTokenFromQuery",
                request
        );

        assertEquals("token", token);
    }

    private ServerHttpRequest buildRequest(String query) {
        URI uri = URI.create("http://localhost/ws?" + query);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(uri);
        return request;
    }
}

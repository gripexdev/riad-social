package com.instagramclone.backend.config;

import com.instagramclone.backend.jwt.JwtUtil;
import com.instagramclone.backend.user.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserService userService;

    @Mock
    private MessageChannel channel;

    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(jwtUtil, userService);
    }

    @Test
    void preSendAuthenticatesWithBearerToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        UserDetails userDetails = new User("alice", "pass", List.of());
        when(jwtUtil.extractUsername("token")).thenReturn("alice");
        when(userService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(jwtUtil.validateToken("token", userDetails)).thenReturn(true);

        interceptor.preSend(message, channel);

        org.mockito.Mockito.verify(jwtUtil).extractUsername("token");
        org.mockito.Mockito.verify(userService).loadUserByUsername("alice");
        org.mockito.Mockito.verify(jwtUtil).validateToken("token", userDetails);
    }

    @Test
    void preSendThrowsWhenTokenMissing() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void preSendIgnoresWhenUserAlreadySet() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("alice", null));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, channel);

        verifyNoInteractions(jwtUtil, userService);
    }
}

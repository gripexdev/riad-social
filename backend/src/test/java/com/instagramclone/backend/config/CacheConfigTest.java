package com.instagramclone.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class CacheConfigTest {

    @Test
    void redisCacheManagerBuildsCaches() {
        CacheConfig config = new CacheConfig();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        ObjectMapper mapper = new ObjectMapper();

        RedisCacheManager cacheManager = config.redisCacheManager(connectionFactory, mapper);

        assertNotNull(cacheManager);
        assertNotNull(cacheManager.getCache("notificationUnreadCount"));
        assertNotNull(cacheManager.getCache("generic"));
    }
}

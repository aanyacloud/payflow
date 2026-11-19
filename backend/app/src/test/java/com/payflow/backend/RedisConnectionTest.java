
package com.payflow.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void shouldConnectToRedis() {
        try (RedisConnection connection =
                     redisConnectionFactory.getConnection()) {

            String response = connection.ping();

            assertEquals("PONG", response);
        }
    }
}

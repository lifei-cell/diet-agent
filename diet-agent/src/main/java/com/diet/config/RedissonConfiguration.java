package com.diet.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures Redisson independently of Spring Data Redis so RLock Watchdog is explicit. */
@Configuration
public class RedissonConfiguration {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${diet.session.distributed-lock.watchdog-timeout-ms:45000}") long watchdogTimeoutMs,
            @Value("${diet.session.distributed-lock.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${diet.session.distributed-lock.command-timeout-ms:2000}") int commandTimeoutMs
    ) {
        Config config = new Config();
        config.setLockWatchdogTimeout((int) Math.max(3_000, watchdogTimeoutMs));

        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + host + ':' + port)
                .setConnectTimeout(Math.max(500, connectTimeoutMs))
                .setTimeout(Math.max(500, commandTimeoutMs))
                .setRetryAttempts(1)
                .setRetryInterval(250)
                // This client only coordinates session locks; avoid Redisson's larger cache-oriented defaults per replica.
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2);
        if (password != null && !password.isBlank()) {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }
}

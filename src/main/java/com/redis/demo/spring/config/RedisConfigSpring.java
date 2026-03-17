package com.redis.demo.spring.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import redis.clients.jedis.JedisPoolConfig;

/**
 * Spring Redis configuration for OSS demo.
 * Mirrors customer's exact configuration pattern with JedisConnectionFactory and RedisStandaloneConfiguration.
 */
@Configuration
public class RedisConfigSpring {

    @Value("${redis.endpoint.east1.host}")
    private String writerHost;

    @Value("${redis.endpoint.east1.port}")
    private int writerPort;

    @Value("${redis.endpoint.east1.username}")
    private String writerUsername;

    @Value("${redis.endpoint.east1.password}")
    private String writerPassword;

    @Value("${redis.endpoint.east2.host}")
    private String readerHost;

    @Value("${redis.endpoint.east2.port}")
    private int readerPort;

    @Value("${redis.endpoint.east2.username}")
    private String readerUsername;

    @Value("${redis.endpoint.east2.password}")
    private String readerPassword;

    /**
     * Writer Redis Template - connects to East-1 (Writer region)
     * Exact replica of customer's configuration pattern
     */
    @Bean(name = "writerRedisTemplate")
    @Primary
    @ConditionalOnMissingBean(name = "writerRedisTemplate")
    public StringRedisTemplate writerRedisTemplate() {
        return new StringRedisTemplate(writerJedisConnectionFactory());
    }

    /**
     * Writer JedisConnectionFactory - optimized pool configuration
     */
    @Bean(name = "writerJedisConnectionFactory")
    @Primary
    @ConditionalOnMissingBean(name = "writerJedisConnectionFactory")
    public JedisConnectionFactory writerJedisConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName(writerHost);
        redisStandaloneConfiguration.setPort(writerPort);
        redisStandaloneConfiguration.setPassword(writerPassword);
        redisStandaloneConfiguration.setUsername(writerUsername);

        // Optimized pool configuration for connection reuse
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(500);
        poolConfig.setMaxIdle(100);
        poolConfig.setMinIdle(20);
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setTestWhileIdle(true);

        JedisClientConfiguration clientConfig = JedisClientConfiguration.builder()
            .usePooling()
            .poolConfig(poolConfig)
            .build();

        JedisConnectionFactory factory = new JedisConnectionFactory(redisStandaloneConfiguration, clientConfig);
        factory.afterPropertiesSet();

        return factory;
    }

    /**
     * Reader Redis Template - connects to East-2 (Reader region)
     * Exact replica of customer's configuration pattern
     */
    @Bean(name = "readerRedisTemplate")
    @ConditionalOnMissingBean(name = "readerRedisTemplate")
    public StringRedisTemplate readerRedisTemplate() {
        return new StringRedisTemplate(readerJedisConnectionFactory());
    }

    /**
     * Reader JedisConnectionFactory - optimized pool configuration
     */
    @Bean(name = "readerJedisConnectionFactory")
    @ConditionalOnMissingBean(name = "readerJedisConnectionFactory")
    public JedisConnectionFactory readerJedisConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName(readerHost);
        redisStandaloneConfiguration.setPort(readerPort);
        redisStandaloneConfiguration.setPassword(readerPassword);
        redisStandaloneConfiguration.setUsername(readerUsername);

        // Optimized pool configuration for connection reuse
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(500);
        poolConfig.setMaxIdle(100);
        poolConfig.setMinIdle(20);
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setTestWhileIdle(true);

        JedisClientConfiguration clientConfig = JedisClientConfiguration.builder()
            .usePooling()
            .poolConfig(poolConfig)
            .build();

        JedisConnectionFactory factory = new JedisConnectionFactory(redisStandaloneConfiguration, clientConfig);
        factory.afterPropertiesSet();

        return factory;
    }
}


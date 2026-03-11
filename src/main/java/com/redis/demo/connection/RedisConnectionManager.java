package com.redis.demo.connection;

import com.redis.demo.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.JedisPoolConfig;

import java.util.List;

/**
 * Manages Redis connections using JedisPooled.
 * Designed to be easily migrated to JedisCluster for Redis Cloud Active-Active.
 */
public class RedisConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(RedisConnectionManager.class);
    
    private final ConfigManager config;
    private final JedisPooled writerClient;
    private final JedisPooled readerClient;
    private final String writerRegion;
    private final String readerRegion;

    public RedisConnectionManager(ConfigManager config) {
        this.config = config;

        List<String> endpoints = config.getRedisEndpoints();
        logger.info("Initializing Redis connections to endpoints: {}", endpoints);

        // For now, both clients connect to the same endpoints
        // In production with Redis Cloud Active-Active, these would be different regional endpoints
        String writerEndpoint = endpoints.get(0);
        String readerEndpoint = endpoints.size() > 1 ? endpoints.get(1) : endpoints.get(0);

        this.writerClient = createJedisPooled(writerEndpoint);
        this.readerClient = createJedisPooled(readerEndpoint);
        this.writerRegion = extractRegion(writerEndpoint);
        this.readerRegion = extractRegion(readerEndpoint);

        testConnections();
    }
    
    private JedisPooled createJedisPooled(String endpoint) {
        String username = null;
        String password = null;
        String host;
        int port = 6379;
        boolean useSsl = false;

        endpoint = endpoint.trim();

        // Parse format: [username:password@]host:port
        if (endpoint.contains("@")) {
            String[] authParts = endpoint.split("@", 2);
            String credentials = authParts[0];
            String hostPort = authParts[1];

            // Parse credentials (username:password)
            if (credentials.contains(":")) {
                String[] credParts = credentials.split(":", 2);
                username = credParts[0];
                password = credParts[1];
            } else {
                password = credentials; // Only password provided
            }

            // Parse host:port
            String[] hostParts = hostPort.split(":");
            host = hostParts[0];
            if (hostParts.length > 1) {
                port = Integer.parseInt(hostParts[1]);
            }

            // Redis Cloud uses SSL
            useSsl = true;
        } else {
            // Simple format: host:port (localhost)
            String[] parts = endpoint.split(":");
            host = parts[0];
            if (parts.length > 1) {
                port = Integer.parseInt(parts[1]);
            }
        }

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getPoolMaxTotal());
        poolConfig.setMaxIdle(config.getPoolMaxIdle());
        poolConfig.setMinIdle(config.getPoolMinIdle());
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);

        logger.info("Creating JedisPooled connection to {}:{} (SSL: {}, Auth: {})",
                   host, port, useSsl, password != null ? "yes" : "no");

        // Build client configuration
        DefaultJedisClientConfig.Builder configBuilder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(10000)
                .socketTimeoutMillis(10000);

        // Add SSL if needed
        if (useSsl) {
            configBuilder.ssl(true);
        }

        // Add authentication if provided
        if (password != null) {
            if (username != null) {
                configBuilder.user(username).password(password);
            } else {
                configBuilder.password(password);
            }
        }

        DefaultJedisClientConfig clientConfig = configBuilder.build();
        HostAndPort hostAndPort = new HostAndPort(host, port);

        // JedisPooled constructor: (hostAndPort, clientConfig)
        return new JedisPooled(hostAndPort, clientConfig);
    }
    
    private void testConnections() {
        try {
            String writerPing = writerClient.ping();
            String readerPing = readerClient.ping();
            logger.info("Redis connections established successfully - Writer: {}, Reader: {}", writerPing, readerPing);
        } catch (Exception e) {
            logger.error("Failed to establish Redis connections", e);
            throw new RuntimeException("Redis connection test failed", e);
        }
    }
    
    public JedisPooled getWriterClient() {
        return writerClient;
    }

    public JedisPooled getReaderClient() {
        return readerClient;
    }

    public String getWriterRegion() {
        return writerRegion;
    }

    public String getReaderRegion() {
        return readerRegion;
    }

    /**
     * Measure network latency to writer endpoint using PING command.
     * @return latency in milliseconds
     */
    public double measureWriterLatency() {
        return measureLatency(writerClient);
    }

    /**
     * Measure network latency to reader endpoint using PING command.
     * @return latency in milliseconds
     */
    public double measureReaderLatency() {
        return measureLatency(readerClient);
    }

    private double measureLatency(JedisPooled client) {
        try {
            // Measure round-trip time for PING command (average of 3 samples)
            long totalNanos = 0;
            int samples = 3;

            for (int i = 0; i < samples; i++) {
                long startNanos = System.nanoTime();
                client.ping();
                long endNanos = System.nanoTime();
                totalNanos += (endNanos - startNanos);
            }

            // Convert to milliseconds and return average
            return (totalNanos / samples) / 1_000_000.0;
        } catch (Exception e) {
            logger.warn("Failed to measure latency", e);
            return -1.0;
        }
    }

    private String extractRegion(String endpoint) {
        // Extract region from endpoint like: default:pass@redis-11036.mc2103-0.us-east-1-mz.ec2.cloud.rlrcp.com:11036
        // Return simplified format like: us-east-1-mz
        try {
            // Remove auth part if present
            String hostPart = endpoint.contains("@") ? endpoint.split("@")[1] : endpoint;

            // Extract hostname (before port)
            String hostname = hostPart.split(":")[0];

            // Look for pattern like "us-east-1-mz" or "us-east-2-mz"
            if (hostname.contains("us-east-1-mz")) {
                return "us-east-1-mz";
            } else if (hostname.contains("us-east-2-mz")) {
                return "us-east-2-mz";
            } else if (hostname.contains("localhost")) {
                return "localhost";
            } else {
                // Generic extraction: find region pattern
                String[] parts = hostname.split("\\.");
                for (String part : parts) {
                    if (part.contains("-mz") || part.contains("east") || part.contains("west")) {
                        return part;
                    }
                }
                return hostname; // Fallback to full hostname
            }
        } catch (Exception e) {
            logger.warn("Could not extract region from endpoint: {}", endpoint);
            return "unknown";
        }
    }

    public void close() {
        logger.info("Closing Redis connections");
        if (writerClient != null) {
            writerClient.close();
        }
        if (readerClient != null) {
            readerClient.close();
        }
    }
}


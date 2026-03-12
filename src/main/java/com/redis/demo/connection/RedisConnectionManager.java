package com.redis.demo.connection;

import com.redis.demo.config.ConfigManager;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;
import redis.clients.jedis.MultiDbClient;
import redis.clients.jedis.MultiDbConfig;

import java.time.Duration;
import java.util.List;

/**
 * Manages Redis connections using MultiDbClient for automatic failover.
 * Supports Active-Active replication with circuit breaker and health checks.
 */
public class RedisConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(RedisConnectionManager.class);

    private final ConfigManager config;
    private final MultiDbClient writerClient;
    private final MultiDbClient readerClient;
    private final String writerRegion;
    private final String readerRegion;
    private final List<String> allEndpoints;
    private final List<String> allRegions;

    // Track active regions after failover
    private volatile String activeWriterRegion;
    private volatile String activeReaderRegion;

    public RedisConnectionManager(ConfigManager config) {
        this.config = config;

        List<String> endpoints = config.getRedisEndpoints();
        this.allEndpoints = endpoints;

        // For Active-Active, create separate clients for writer and reader regions
        // Writer prefers first endpoint, Reader prefers second endpoint
        String endpoint1 = endpoints.get(0);
        String endpoint2 = endpoints.size() > 1 ? endpoints.get(1) : endpoints.get(0);

        this.writerClient = createMultiDbClient(endpoint1, endpoint2, true);  // Writer prefers endpoint1
        this.readerClient = createMultiDbClient(endpoint2, endpoint1, false); // Reader prefers endpoint2
        this.writerRegion = extractRegion(endpoint1);
        this.readerRegion = extractRegion(endpoint2);

        // Initialize active regions to configured regions
        this.activeWriterRegion = this.writerRegion;
        this.activeReaderRegion = this.readerRegion;

        // Extract all regions
        this.allRegions = endpoints.stream()
                .map(this::extractRegion)
                .distinct()
                .toList();

        testConnections();
    }

    /**
     * Creates a MultiDbClient with failover support.
     * @param primaryEndpoint The preferred endpoint (higher weight)
     * @param secondaryEndpoint The backup endpoint (lower weight)
     * @param isWriter Whether this is for the writer client (affects logging)
     * @return Configured MultiDbClient
     */
    private MultiDbClient createMultiDbClient(String primaryEndpoint, String secondaryEndpoint, boolean isWriter) {
        String clientType = isWriter ? "Writer" : "Reader";

        // Parse both endpoints
        EndpointInfo primary = parseEndpoint(primaryEndpoint);
        EndpointInfo secondary = parseEndpoint(secondaryEndpoint);

        // Build client configuration
        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(10000)
                .socketTimeoutMillis(10000)
                .ssl(primary.useSsl)
                .user(primary.username)
                .password(primary.password)
                .build();

        // Build connection pool configuration
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(config.getPoolMaxTotal());
        poolConfig.setMaxIdle(config.getPoolMaxIdle());
        poolConfig.setMinIdle(config.getPoolMinIdle());
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWait(Duration.ofSeconds(1));
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(1));

        // Build MultiDbConfig with failover settings
        MultiDbConfig.Builder multiConfigBuilder = MultiDbConfig.builder()
                // Primary endpoint (higher weight = preferred)
                .database(MultiDbConfig.DatabaseConfig.builder(primary.hostAndPort, clientConfig)
                        .connectionPoolConfig(poolConfig)
                        .weight(1.0f)
                        .build())
                // Secondary endpoint (lower weight = backup)
                .database(MultiDbConfig.DatabaseConfig.builder(secondary.hostAndPort, clientConfig)
                        .connectionPoolConfig(poolConfig)
                        .weight(0.5f)
                        .build())
                // Circuit breaker configuration
                .failureDetector(MultiDbConfig.CircuitBreakerConfig.builder()
                        .slidingWindowSize(config.getCircuitBreakerSlidingWindowSize())
                        .minNumOfFailures(config.getCircuitBreakerMinNumFailures())
                        .failureRateThreshold(config.getCircuitBreakerFailureRateThreshold())
                        .build())
                // Retry configuration
                .commandRetry(MultiDbConfig.RetryConfig.builder()
                        .maxAttempts(config.getRetryMaxAttempts())
                        .waitDuration(config.getRetryWaitDuration())
                        .exponentialBackoffMultiplier(config.getRetryExponentialBackoffMultiplier())
                        .build())
                // General failover configuration
                .maxNumFailoverAttempts(config.getMaxNumFailoverAttempts())
                .delayInBetweenFailoverAttempts(config.getDelayBetweenFailoverAttempts())
                .gracePeriod(config.getGracePeriod())
                .fastFailover(config.isFastFailover())
                .retryOnFailover(config.isRetryOnFailover())
                // Failback configuration
                .failbackSupported(config.isFailbackSupported())
                .failbackCheckInterval(config.getFailbackCheckInterval());

        // Build and return MultiDbClient with failover callback
        return MultiDbClient.builder()
                .multiDbConfig(multiConfigBuilder.build())
                .databaseSwitchListener(event -> {
                    String newEndpoint = event.getEndpoint().toString();

                    // ANSI color codes: RED for failover warning
                    String RED = "\u001B[31m";
                    String GREEN = "\u001B[32m";
                    String RESET = "\u001B[0m";

                    logger.warn(RED + "⚠️  FAILOVER EVENT - {} switched to: {} - Reason: {}" + RESET,
                               clientType,
                               newEndpoint,
                               event.getReason());

                    // Update active region tracking
                    // Extract region from the endpoint string (format: host:port)
                    String newRegion = extractRegionFromEndpointString(newEndpoint);
                    if (isWriter) {
                        activeWriterRegion = newRegion;
                        logger.info(GREEN + "✓ Active writer region updated to: {}" + RESET, newRegion);
                    } else {
                        activeReaderRegion = newRegion;
                        logger.info(GREEN + "✓ Active reader region updated to: {}" + RESET, newRegion);
                    }
                })
                .build();
    }

    /**
     * Helper class to hold parsed endpoint information
     */
    private static class EndpointInfo {
        final HostAndPort hostAndPort;
        final String username;
        final String password;
        final boolean useSsl;

        EndpointInfo(HostAndPort hostAndPort, String username, String password, boolean useSsl) {
            this.hostAndPort = hostAndPort;
            this.username = username;
            this.password = password;
            this.useSsl = useSsl;
        }
    }

    /**
     * Extract region from endpoint string by matching the host against configured endpoints.
     * @param endpointString The endpoint string from failover event (format: "host:port")
     * @return The region name, or "unknown" if not found
     */
    private String extractRegionFromEndpointString(String endpointString) {
        // Extract just the host from "host:port"
        String host = endpointString.contains(":") ? endpointString.split(":")[0] : endpointString;

        // Match against configured endpoints
        for (String endpoint : allEndpoints) {
            if (endpoint.contains(host)) {
                return extractRegion(endpoint);
            }
        }
        return "unknown";
    }

    /**
     * Parse endpoint string into EndpointInfo
     * Format: [username:password@]host:port
     */
    private EndpointInfo parseEndpoint(String endpoint) {
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

        return new EndpointInfo(new HostAndPort(host, port), username, password, useSsl);
    }
    
    private void testConnections() {
        try {
            writerClient.ping();
            readerClient.ping();
            logger.info("✅ Connected to Redis Active-Active endpoints");
            logger.info("   Writer region: {} | Reader region: {}", writerRegion, readerRegion);
        } catch (Exception e) {
            logger.error("Failed to establish Redis connections", e);
            throw new RuntimeException("Redis connection test failed", e);
        }
    }
    
    public MultiDbClient getWriterClient() {
        return writerClient;
    }

    public MultiDbClient getReaderClient() {
        return readerClient;
    }

    public String getWriterRegion() {
        return writerRegion;
    }

    public String getReaderRegion() {
        return readerRegion;
    }

    /**
     * Get the currently active writer region (may differ from configured writer after failover).
     * @return the region name of the currently active writer endpoint
     */
    public String getActiveWriterRegion() {
        return activeWriterRegion;
    }

    /**
     * Get the currently active reader region (may differ from configured reader after failover).
     * @return the region name of the currently active reader endpoint
     */
    public String getActiveReaderRegion() {
        return activeReaderRegion;
    }

    public List<String> getAllRegions() {
        return allRegions;
    }

    /**
     * Check if an endpoint is healthy by attempting a PING.
     * @param region The region name to check
     * @return true if healthy, false otherwise
     */
    public boolean isEndpointHealthy(String region) {
        // Find the endpoint for this region
        String endpoint = allEndpoints.stream()
                .filter(ep -> extractRegion(ep).equals(region))
                .findFirst()
                .orElse(null);

        if (endpoint == null) {
            return false;
        }

        // Try to ping the endpoint directly
        try {
            EndpointInfo info = parseEndpoint(endpoint);
            DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                    .user(info.username)
                    .password(info.password)
                    .ssl(info.useSsl)
                    .connectionTimeoutMillis(1000)
                    .socketTimeoutMillis(1000)
                    .build();

            try (Jedis jedis = new Jedis(info.hostAndPort, clientConfig)) {
                jedis.ping();
                return true;
            }
        } catch (Exception e) {
            return false;
        }
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

    private double measureLatency(MultiDbClient client) {
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

        // Wait for any in-flight health checks to complete
        // Health check interval is typically 1000ms, so wait slightly longer
        try {
            Thread.sleep(config.getHealthCheckInterval() + 500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Close clients - suppress any remaining exceptions from health check threads
        if (writerClient != null) {
            try {
                writerClient.close();
            } catch (Exception e) {
                // Suppress exceptions during shutdown
            }
        }
        if (readerClient != null) {
            try {
                readerClient.close();
            } catch (Exception e) {
                // Suppress exceptions during shutdown
            }
        }

        logger.info("Redis connections closed");
    }
}


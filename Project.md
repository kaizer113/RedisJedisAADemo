# Redis Active-Active Replication Demo

## Project Goal
Demonstrate Redis Active-Active Replication across multiple AWS regions with real-time replication lag measurement.
This demo measures cross-region replication performance and helps understand the impact of network latency on perceived lag.

**Target Audience**: DevOps Engineers, Application Developers, and Technical Decision Makers

## Technical Stack
- **Language**: Java 21 (OpenJDK LTS)
- **Build System**: Maven
- **Redis Client**: Jedis 5.1.0 (JedisPooled for connection management)
- **Redis**: Redis Cloud Active-Active (Multi-region deployment)
- **Current Deployment**: AWS us-east-1 (Writer) ↔ AWS us-east-2 (Reader)
- **Development Environment**: macOS
- **Security**: SSL/TLS enabled, username/password authentication

## Architecture Overview

### Current Implementation (Production-Ready)
- **Writer Endpoint**: Redis Cloud Active-Active in AWS us-east-1
- **Reader Endpoint**: Redis Cloud Active-Active in AWS us-east-2
- **External Configuration**: `redis.properties` file (no rebuild required for config changes)
- **Connection Management**: JedisPooled with SSL/TLS support
- **Metrics Display**: Console output every 15 seconds with network latency breakdown

## Core Functionality

### 1. Latency Key Writer
- **Thread**: Dedicated writer thread
- **Frequency**: Write one key per second (configurable)
- **Key Pattern**: `latency:<counter>` (e.g., latency:1, latency:2, ...)
- **Value Format**: `<millis>.<nanos>_<padding>` (high-precision timestamp + padding)
  - Example: `1773253063880.123456_AAAA...` (500 bytes total)
- **TTL**: 30 seconds (configurable)
- **Optimization**: Pre-generated static padding string (no random generation overhead)

### 2. Latency Key Reader
- **Thread**: Dedicated reader thread with producer-consumer pattern
- **Polling Interval**: Every 0.5ms for low-latency measurement
- **Operation**: MGET batch retrieval from shared queue
- **Calculation**: Replication lag = (current_timestamp - write_timestamp)
- **Queue Management**: Re-queues keys not yet replicated for retry

### 3. Metrics Aggregation & Display
- **Interval**: Every 15 seconds
- **Metrics**:
  - Average replication lag
  - P95 replication lag (95th percentile)
  - Maximum replication lag
  - Minimum replication lag
  - Sample count
- **Network Latency Measurement**:
  - Writer client latency (PING to us-east-1)
  - Reader client latency (PING to us-east-2)
  - Network overhead calculation
  - Estimated true replication lag (measured lag - network overhead)
- **Display**: Console output with region information and latency breakdown

### 4. Background Load Generator
- **Thread**: Separate thread for realistic load simulation
- **Target**: ~10,000 operations/second
- **Operations**: Random GET/SET on random keys
- **Ratio**: 10:1 (reads:writes)
- **Value Size**: 500 bytes (same as latency keys)
- **TTL**: 30 seconds on all keys
- **Statistics**: Total reads/writes, ops/sec displayed in metrics

## Failover Testing

### DNS Failure Simulation
- **Method**: Manipulate `/etc/hosts` file to simulate DNS failure
- **Trigger**: Manual terminal command
- **Expected Behavior**: JedisCluster automatically detects failure and routes to healthy endpoint
- **Note**: We will NOT fail both Redis instances simultaneously

### Failback Testing
- **Method**: Restore `/etc/hosts` file to original state
- **Expected Behavior**: Application continues operating, may rebalance connections

## Performance Targets

### Progressive Scaling
1. **Phase 1**: Baseline - Establish stable operation with minimal load
2. **Phase 2**: 1K operations/second (900 reads, 100 writes)
3. **Phase 3**: 5K operations/second (4.5K reads, 500 writes)
4. **Phase 4**: 10K operations/second (9K reads, 1K writes)

**Note**: Hardware requirements will be evaluated at each phase. May require larger EC2 instances or optimized Redis configuration.

## Implementation Phases

### Phase 1: Project Setup & Basic Connection
- [ ] Create Maven project structure
- [ ] Add Jedis dependency
- [ ] Create configuration management (properties file)
- [ ] Implement basic Redis connection using JedisCluster
- [ ] Test connection to localhost:6379

### Phase 2: Core Latency Measurement
- [ ] Implement latency key writer thread
- [ ] Implement latency key reader thread
- [ ] Calculate and display replication lag
- [ ] Add proper error handling and logging

### Phase 3: Metrics & Monitoring
- [ ] Implement 10-second metrics aggregation
- [ ] Calculate avg, p95, max, min statistics
- [ ] Create formatted console output
- [ ] Add metrics history tracking

### Phase 4: Background Load Generation
- [ ] Implement background load generator thread
- [ ] Random key generation
- [ ] 10:1 read/write ratio
- [ ] TTL management on all keys

### Phase 5: Failover Testing
- [ ] Create scripts to manipulate /etc/hosts
- [ ] Test DNS failure scenarios
- [ ] Verify JedisCluster failover behavior
- [ ] Document failover timing and behavior

### Phase 6: Failback & Optimization
- [ ] Test failback scenarios
- [ ] Performance tuning
- [ ] Scale to target throughput
- [ ] Documentation for DevOps/Developers

## Configuration

### External Configuration (redis.properties)
The application supports external configuration files that can be modified without rebuilding:

```properties
# Redis Endpoints (comma-separated)
# Format: [username:password@]host:port
redis.endpoints=default:password@redis-11036.mc2103-0.us-east-1-mz.ec2.cloud.rlrcp.com:11036,default:password@redis-11036.mc2103-1.us-east-2-mz.ec2.cloud.rlrcp.com:11036

# Writer Configuration
writer.interval.ms=1000
writer.key.prefix=latency

# Value size (total size including timestamp)
# Timestamp format: "millis.nanos_" = ~21 bytes
# Actual padding size = value.size - 21
value.size=500

# Metrics Configuration
metrics.interval.seconds=15

# Background Load Configuration
background.load.enabled=true
background.load.read.write.ratio=10

# TTL Configuration
key.ttl.seconds=30

# Connection Pool Configuration
redis.pool.max.total=50
redis.pool.max.idle=20
redis.pool.min.idle=5
```

### Configuration Loading Priority
1. **External file** (current directory): `./redis.properties` - **No rebuild required!**
2. **Packaged file** (classpath): `src/main/resources/redis.properties` - Requires rebuild

## Current Status

### Completed Features ✅
1. ✅ Multi-region Redis Cloud Active-Active deployment (us-east-1 ↔ us-east-2)
2. ✅ SSL/TLS connection support with authentication
3. ✅ High-precision timestamp tracking (millisecond + nanosecond)
4. ✅ Producer-consumer pattern with shared queue (efficient key tracking)
5. ✅ MGET batching for efficient reads
6. ✅ Background load generator (~10K ops/sec)
7. ✅ Network latency measurement (PING-based RTT)
8. ✅ Estimated true replication lag calculation (measured - network overhead)
9. ✅ External configuration support (no rebuild required)
10. ✅ Region-aware metrics display
11. ✅ Unified value size configuration

### Key Optimizations
- **Static padding strings**: Eliminated random string generation overhead
- **MGET batching**: Reduced network round-trips
- **Producer-consumer queue**: Efficient key tracking between writer and reader
- **High-precision timestamps**: Sub-millisecond accuracy for low-latency scenarios
- **Fast polling**: 0.5ms reader interval for responsive lag measurement

## Success Criteria
1. ✅ Stable operation with measurable replication lag across AWS regions
2. ✅ Clear metrics display with network latency breakdown
3. ✅ Scalable to 10K ops/second background load
4. ✅ Easy configuration for different environments (external config file)
5. ✅ Production-ready SSL/TLS connections
6. ✅ Accurate replication lag measurement accounting for network overhead
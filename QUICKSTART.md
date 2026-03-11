# Quick Start Guide

Get the Redis Active-Active demo running in 5 minutes!

## Prerequisites Check

```bash
# Check Java version (need Java 21)
java -version

# Check Maven
mvn -version

# Check Redis
redis-cli ping
```

## Step 1: Build the Project

```bash
mvn clean package
```

This will:
- Download all dependencies
- Compile the Java code
- Create an executable JAR file in `target/`

## Step 2: Start Redis (if not running)

### macOS
```bash
# Using Homebrew
brew services start redis

# Or run directly
redis-server
```

### Linux
```bash
sudo systemctl start redis
# or
redis-server
```

### Verify Redis is running
```bash
redis-cli ping
# Should return: PONG
```

## Step 3: Run the Demo

### Option A: Using the run script (recommended)
```bash
./run.sh
```

### Option B: Using Java directly
```bash
java -jar target/jedis-active-active-1.0.0.jar
```

### Option C: Using Maven
```bash
mvn exec:java -Dexec.mainClass="com.redis.demo.RedisActiveActiveDemo"
```

## What You'll See

The application will start and display:

```
================================================================================
  REDIS ACTIVE-ACTIVE REPLICATION DEMO
================================================================================
Configuration:
  Redis Endpoints:     [redis-xxxxx.us-east-1.ec2.redns.redis-cloud.com:12000, redis-xxxxx.us-east-2.ec2.redns.redis-cloud.com:12000]
  Writer Interval:     1000 ms
  Metrics Interval:    10 seconds
  Background Load:     ENABLED
  Read/Write Ratio:    10:1
  Key TTL:             300 seconds
================================================================================
Press Ctrl+C to stop the demo
```

After 10 seconds, you'll see metrics:

```
================================================================================
REPLICATION LAG METRICS - 2024-03-11T10:30:45
================================================================================
Sample Count:    10 measurements
Average Lag:     2.50 ms
P95 Lag:         5 ms
Max Lag:         8 ms
Min Lag:         1 ms
================================================================================
```

## Monitoring Redis

In another terminal, you can monitor what's happening in Redis:

```bash
# Watch keys being created
redis-cli --scan --pattern "latency:*"

# Monitor all commands
redis-cli monitor

# Check key count
redis-cli dbsize

# Get a specific latency key
redis-cli get latency:1
```

## Stopping the Demo

Press `Ctrl+C` in the terminal where the demo is running. The application will gracefully shutdown.

## Troubleshooting

### "Connection refused"
- Make sure Redis is running: `redis-cli ping`
- Check if Redis is on port 6379: `netstat -an | grep 6379`

### "Build failed"
- Ensure Java 21 is installed: `java -version`
- Ensure Maven is installed: `mvn -version`

### "No metrics displayed"
- Wait at least 10 seconds for the first metrics report
- Check logs for any errors

## Next Steps

1. **Customize Configuration**: Edit `src/main/resources/redis.properties`
2. **Test Failover**: See README.md for DNS failure simulation
3. **Scale Performance**: Adjust intervals to increase throughput
4. **Connect to Redis Cloud**: Update endpoints in properties file

## Configuration Quick Reference

Edit `src/main/resources/redis.properties`:

```properties
# Change write frequency (default: 1 key per second)
writer.interval.ms=500

# Change metrics reporting interval (default: 10 seconds)
metrics.interval.seconds=5

# Disable background load
background.load.enabled=false

# Change read/write ratio (default: 10:1)
background.load.read.write.ratio=20
```

After changing configuration, rebuild:
```bash
mvn clean package
```

## Support

For issues, check the full README.md or contact your Redis support team.


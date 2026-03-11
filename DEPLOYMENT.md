# Deployment Guide

This guide covers deploying the Redis Active-Active demo to production environments.

## Prerequisites

- Redis Cloud Active-Active database configured
- EC2 Ubuntu instance (or similar) with Java 21
- Network connectivity to Redis Cloud endpoints
- DNS configured for Redis endpoints

## Step 1: Prepare Redis Cloud Active-Active

### Create Active-Active Database

1. Log into Redis Cloud console
2. Create a new Active-Active database
3. Configure at least 2 regions (e.g., us-east-1, eu-west-1)
4. Note the endpoints for each region:
   ```
   Region 1: redis-12345-us-east.cloud.redislabs.com:12000
   Region 2: redis-12345-eu-west.cloud.redislabs.com:12000
   ```

### Configure Authentication (if required)

If your Redis Cloud database requires authentication:

1. Note the username and password
2. You'll need to update the code to include credentials

## Step 2: Update Configuration

Edit `src/main/resources/redis.properties`:

```properties
# Update with your Redis Cloud endpoints
redis.endpoints=redis-12345-us-east.cloud.redislabs.com:12000,redis-12345-eu-west.cloud.redislabs.com:12000

# Adjust intervals for production load
writer.interval.ms=1000
metrics.interval.seconds=10

# Enable background load
background.load.enabled=true
background.load.read.write.ratio=10

# TTL configuration
key.ttl.seconds=300

# Connection pool for production
redis.pool.max.total=100
redis.pool.max.idle=50
redis.pool.min.idle=10
```

## Step 3: Build for Production

```bash
# Clean build
mvn clean package

# Verify JAR was created
ls -lh target/jedis-active-active-1.0.0.jar
```

## Step 4: Deploy to EC2 Ubuntu

### Transfer Files

```bash
# From your local machine
scp target/jedis-active-active-1.0.0.jar ubuntu@<ec2-ip>:~/
scp src/main/resources/redis.properties ubuntu@<ec2-ip>:~/
```

### Install Java on EC2

```bash
# SSH to EC2
ssh ubuntu@<ec2-ip>

# Install Java 21
sudo apt update
sudo apt install -y openjdk-21-jdk

# Verify installation
java -version
```

### Run the Application

```bash
# Run in foreground (for testing)
java -jar jedis-active-active-1.0.0.jar

# Run in background with nohup
nohup java -jar jedis-active-active-1.0.0.jar > app.log 2>&1 &

# Check it's running
ps aux | grep jedis-active-active

# View logs
tail -f app.log
```

## Step 5: Create Systemd Service (Recommended)

Create `/etc/systemd/system/redis-demo.service`:

```ini
[Unit]
Description=Redis Active-Active Replication Demo
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu
ExecStart=/usr/bin/java -jar /home/ubuntu/jedis-active-active-1.0.0.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

Enable and start the service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable redis-demo
sudo systemctl start redis-demo

# Check status
sudo systemctl status redis-demo

# View logs
sudo journalctl -u redis-demo -f
```

## Step 6: DNS Failover Testing

### Prepare DNS Entries

Ensure you have DNS entries for your Redis endpoints:
```
redis-primary.example.com -> redis-12345-us-east.cloud.redislabs.com
redis-secondary.example.com -> redis-12345-eu-west.cloud.redislabs.com
```

### Simulate DNS Failure

```bash
# Backup /etc/hosts
sudo cp /etc/hosts /etc/hosts.backup

# Add entry to simulate DNS failure
sudo bash -c 'echo "127.0.0.1 redis-primary.example.com # REDIS_DEMO_TEST" >> /etc/hosts'

# Monitor application logs
sudo journalctl -u redis-demo -f
```

### Restore DNS

```bash
# Remove test entry
sudo sed -i '/# REDIS_DEMO_TEST/d' /etc/hosts

# Verify restoration
cat /etc/hosts
```

## Step 7: Monitoring

### Application Metrics

The application outputs metrics every 10 seconds to stdout/logs:

```bash
# View real-time metrics
sudo journalctl -u redis-demo -f | grep "REPLICATION LAG METRICS"
```

### Redis Monitoring

```bash
# Install redis-cli on EC2
sudo apt install -y redis-tools

# Monitor keys
redis-cli -h redis-12345-us-east.cloud.redislabs.com -p 12000 --scan --pattern "latency:*"

# Check database size
redis-cli -h redis-12345-us-east.cloud.redislabs.com -p 12000 dbsize
```

## Performance Tuning

### Scaling to 1K ops/sec

```properties
writer.interval.ms=100
background.load.enabled=true
```

### Scaling to 5K ops/sec

```properties
writer.interval.ms=20
redis.pool.max.total=200
redis.pool.max.idle=100
```

### Scaling to 10K ops/sec

```properties
writer.interval.ms=10
redis.pool.max.total=500
redis.pool.max.idle=200
```

Consider larger EC2 instance (e.g., c5.2xlarge) for high throughput.

## Troubleshooting

### Connection Issues

```bash
# Test connectivity to Redis Cloud
telnet redis-12345-us-east.cloud.redislabs.com 12000

# Check DNS resolution
nslookup redis-12345-us-east.cloud.redislabs.com

# Check security groups allow outbound to Redis Cloud
```

### High Latency

- Check network latency to Redis Cloud regions
- Verify EC2 instance is in same region as one Redis endpoint
- Monitor Redis Cloud metrics in console
- Check EC2 instance CPU/memory usage

### Application Crashes

```bash
# Check logs
sudo journalctl -u redis-demo -n 100

# Check Java heap size
java -XX:+PrintFlagsFinal -version | grep HeapSize

# Increase heap if needed
ExecStart=/usr/bin/java -Xmx2g -jar /home/ubuntu/jedis-active-active-1.0.0.jar
```

## Security Considerations

1. **Network Security**: Ensure EC2 security groups allow outbound to Redis Cloud
2. **Credentials**: Store Redis credentials securely (AWS Secrets Manager, etc.)
3. **Monitoring**: Set up CloudWatch alarms for application health
4. **Updates**: Keep Java and dependencies updated

## Cleanup

```bash
# Stop service
sudo systemctl stop redis-demo

# Disable service
sudo systemctl disable redis-demo

# Remove files
rm ~/jedis-active-active-1.0.0.jar
rm ~/redis.properties
```

## Support

For production issues:
- Check Redis Cloud console for database health
- Review application logs
- Contact Redis support team


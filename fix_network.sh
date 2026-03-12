#!/bin/bash

# Script to restore network traffic to the Redis East endpoint
# This removes the iptables rule that was blocking traffic

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if running as root
if [ "$EUID" -ne 0 ]; then 
    echo -e "${RED}Error: This script must be run as root (use sudo)${NC}"
    exit 1
fi

# Parse the east endpoint from redis.properties
if [ ! -f "redis.properties" ]; then
    echo -e "${RED}Error: redis.properties file not found${NC}"
    exit 1
fi

# Extract the east endpoint and parse the hostname
EAST_ENDPOINT=$(grep "^redis.endpoint.east=" redis.properties | cut -d'=' -f2)

if [ -z "$EAST_ENDPOINT" ]; then
    echo -e "${RED}Error: redis.endpoint.east not found in redis.properties${NC}"
    exit 1
fi

# Extract hostname from the endpoint
# Format: [username]:[password]@hostname:port
# We need to extract just the hostname part
HOSTNAME=$(echo "$EAST_ENDPOINT" | sed 's/.*@\([^:]*\):.*/\1/')

if [ -z "$HOSTNAME" ]; then
    echo -e "${RED}Error: Could not parse hostname from endpoint: $EAST_ENDPOINT${NC}"
    exit 1
fi

echo -e "${YELLOW}Restoring network traffic to Redis East endpoint...${NC}"
echo ""
echo "Endpoint: $EAST_ENDPOINT"
echo "Hostname: $HOSTNAME"
echo ""
echo "Command to execute:"
echo -e "${GREEN}sudo iptables -D OUTPUT -d $HOSTNAME -j DROP${NC}"
echo ""

# Execute the iptables command
iptables -D OUTPUT -d "$HOSTNAME" -j DROP

echo -e "${GREEN}✓ Network traffic to $HOSTNAME is now RESTORED${NC}"
echo ""
echo "This will trigger failback to the East endpoint (if failback is enabled)."


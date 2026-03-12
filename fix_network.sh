#!/bin/bash

# Script to restore network traffic to the Redis East endpoint
# This removes the iptables rule that was blocking traffic

set -e

# Colors for output
GREEN='\033[0;92m'
RED='\033[1;31m'  # Bright/Bold Red
YELLOW='\033[1;93m'  # Bright Yellow
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

# Get timestamp
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

# Display command with timestamp
echo -e "${YELLOW}[$TIMESTAMP] ${GREEN}sudo iptables -D OUTPUT -d $HOSTNAME -j DROP${NC}"

# Execute the iptables command
iptables -D OUTPUT -d "$HOSTNAME" -j DROP


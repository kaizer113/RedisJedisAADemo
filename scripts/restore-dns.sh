#!/bin/bash

# Script to restore DNS by removing entries from /etc/hosts
# Usage: sudo ./restore-dns.sh

if [ "$EUID" -ne 0 ]; then 
    echo "Please run as root (use sudo)"
    exit 1
fi

HOSTS_FILE="/etc/hosts"

# Remove lines added by simulate-dns-failure.sh
sed -i.bak '/# REDIS_DEMO_DNS_FAILURE/d' $HOSTS_FILE

echo "✅ Removed DNS failure entries from $HOSTS_FILE"
echo "✅ Backup saved to ${HOSTS_FILE}.bak"
echo ""
echo "DNS restored to normal operation"


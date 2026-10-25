#!/bin/sh
set -u

rm -f /tmp/backup-service-ready
while true; do
  if ! /bin/sh /scripts/backup-now.sh; then
    rm -f /tmp/backup-service-ready
    echo "Database backup failed; the service will retry in 60 seconds." >&2
    sleep 60
    continue
  fi
  sleep "$BACKUP_INTERVAL_SECONDS"
done

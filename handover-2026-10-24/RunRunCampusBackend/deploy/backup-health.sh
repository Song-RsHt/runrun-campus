#!/bin/sh
set -eu

marker=/tmp/backup-service-ready
test -f "$marker"
age=$(( $(date +%s) - $(stat -c %Y "$marker") ))
test "$age" -ge 0
test "$age" -le "$((BACKUP_INTERVAL_SECONDS + 7200))"

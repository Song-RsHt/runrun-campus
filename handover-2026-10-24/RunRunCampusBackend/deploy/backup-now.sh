#!/bin/sh
set -eu

backup_dir=/backups
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
temporary_file="$backup_dir/runrun-campus-$timestamp.dump.tmp"
final_file="$backup_dir/runrun-campus-$timestamp.dump"

mkdir -p "$backup_dir"
trap 'rm -f "$temporary_file"' EXIT

pg_dump \
  --host=postgres \
  --username="$POSTGRES_USER" \
  --dbname="$POSTGRES_DB" \
  --format=custom \
  --compress=6 \
  --file="$temporary_file"

mv "$temporary_file" "$final_file"
find "$backup_dir" -type f -name 'runrun-campus-*.dump' -mtime +"$BACKUP_RETENTION_DAYS" -delete
touch /tmp/backup-service-ready
echo "$final_file"

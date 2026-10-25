#!/bin/sh
set -eu

# Run only in a disposable container: fixtures use its /scripts and /tmp.
mkdir -p /scripts /test-bin
cp /source/backup-loop.sh /scripts/backup-loop.sh
export BACKUP_INTERVAL_SECONDS=86400
export PATH="/test-bin:$PATH"
failures=0
check() {
    if eval "$2"; then echo "PASS: $1"; else echo "FAIL: $1"; failures=$((failures + 1)); fi
}

cat > /scripts/backup-now.sh <<'EOF'
#!/bin/sh
count=$(cat /tmp/attempts 2>/dev/null || echo 0)
count=$((count + 1))
echo "$count" > /tmp/attempts
if [ "$count" -eq 1 ]; then
    test ! -f /tmp/backup-service-ready && touch /tmp/start-cleared
    exit 1
fi
touch /tmp/backup-service-ready
EOF
cat > /test-bin/sleep <<'EOF'
#!/bin/sh
echo "$1" >> /tmp/delays
if [ "$1" = 60 ]; then exit 0; fi
kill -TERM "$PPID"
EOF
chmod +x /test-bin/sleep
touch /tmp/backup-service-ready
sh /scripts/backup-loop.sh > /tmp/loop.log 2>&1 || :
check 'startup clears an old success marker' 'test -f /tmp/start-cleared'
check 'failed backup retries before daily interval' 'test "$(cat /tmp/attempts)" = 2'
check 'retry then success uses 60 and 86400 seconds' 'test "$(tr "\n" " " < /tmp/delays)" = "60 86400 "'
check 'successful backup leaves a success marker' 'test -f /tmp/backup-service-ready'

cat > /scripts/backup-now.sh <<'EOF'
#!/bin/sh
touch /tmp/backup-service-ready
exit 1
EOF
cat > /test-bin/sleep <<'EOF'
#!/bin/sh
test ! -f /tmp/backup-service-ready && touch /tmp/failure-cleared
kill -TERM "$PPID"
EOF
sh /scripts/backup-loop.sh > /tmp/loop.log 2>&1 || :
check 'failure clears the success marker' 'test -f /tmp/failure-cleared'

if [ -f /source/backup-health.sh ]; then
    rm -f /tmp/backup-service-ready
    check 'missing success marker is unhealthy' '! sh /source/backup-health.sh'
    touch /tmp/backup-service-ready
    check 'recent successful backup is healthy' 'sh /source/backup-health.sh'
    touch -t 202001010000 /tmp/backup-service-ready
    check 'expired successful backup is unhealthy' '! sh /source/backup-health.sh'
    touch -t 209901010000 /tmp/backup-service-ready
    check 'future success marker is unhealthy' '! sh /source/backup-health.sh'
else
    echo 'FAIL: freshness health check is missing'
    failures=$((failures + 1))
fi
echo "Failures: $failures"
test "$failures" -eq 0

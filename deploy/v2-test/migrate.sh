#!/bin/sh
set -eu
trap 'echo "ERROR: isolated migration failed. Inspect migrate logs; never enable legacy Flyway or baseline. See docs/甲方本地v2测试环境.md." >&2' EXIT
[ "$PGHOST" = postgres ] && [ "$PGDATABASE" = rrc_v2_test ]
# Receipts live outside business schemas so original migration/rollback ownership stays intact.
# A crash after SQL commit and before receipt creation fails closed on the next run.
while IFS= read -r file || [ -n "$file" ]; do
  [ -n "$file" ] || continue
  digest=$(sha256sum "/migrations/$file" | cut -d ' ' -f 1)
  if [ -f "/state/$file" ]; then
    [ "$(cat "/state/$file")" = "$digest" ] || { echo "ERROR: changed migration $file" >&2; exit 1; }
    echo "Verified receipt: $file"
  else
    echo "Applying: $file"
    psql -X -q -v ON_ERROR_STOP=1 -f "/migrations/$file"
    printf '%s' "$digest" > "/state/$file"
  fi
done < /scripts/migrations.txt
# A separate synthetic legacy database proves public objects are never read or changed by v2.
if [ "$(psql -X -Atqc "SELECT count(*) FROM pg_database WHERE datname='rrc_legacy_sentinel'")" = 0 ]; then
  createdb rrc_legacy_sentinel
  psql -X -q -v ON_ERROR_STOP=1 -d rrc_legacy_sentinel -c "CREATE TABLE public.v2_test_sentinel(id integer PRIMARY KEY, value text); INSERT INTO public.v2_test_sentinel VALUES(1,'synthetic-untouched');"
fi
psql -X -Atq -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM runrun_v2.app_users" >/dev/null
trap - EXIT
echo 'PASS: M00-M08 isolated migrations complete; legacy Flyway disabled.'

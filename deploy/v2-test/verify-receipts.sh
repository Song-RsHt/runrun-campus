#!/bin/sh
set -eu
count=0
while IFS= read -r file || [ -n "$file" ]; do
  [ -n "$file" ] || continue
  digest=$(sha256sum "/migrations/$file" | cut -d ' ' -f 1)
  [ "$(cat "/state/$file")" = "$digest" ]
  count=$((count+1))
done < /scripts/migrations.txt
[ "$count" = 12 ]
echo PASS

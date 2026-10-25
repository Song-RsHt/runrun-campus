#!/bin/sh
set -eu
database=$1
token=$(cat /proc/sys/kernel/random/uuid)
cleanup() {
    psql -X -q -v ON_ERROR_STOP=1 -U runrun -d "$database" -c "DELETE FROM user_sessions WHERE id = -1 AND token = '$token'" >/dev/null
}
trap cleanup EXIT
# A temporary session in the restored copy exercises actual admin reads without
# retrieving a user's password or token, and without advancing its sequence.
psql -X -q -v ON_ERROR_STOP=1 -U runrun -d "$database" -c "
INSERT INTO user_sessions(id, expires_at, token, user_id)
SELECT -1, now() + interval '5 minutes', '$token', id FROM app_users
WHERE role = 'ADMIN' AND admin_role = 'SUPER_ADMIN' AND enabled = true ORDER BY id LIMIT 1" >/dev/null
for path in /api/v1/admin/dashboard '/api/v1/admin/orders?size=1' '/api/v1/admin/couriers?size=1' '/api/v1/admin/users?size=1'; do
    wget -q -T 5 -O /dev/null --header="Authorization: Bearer $token" "http://api:8080$path"
done
wget -q -T 5 -O - http://api:8080/ | grep -q '<title>跑跑代取运营后台</title>'

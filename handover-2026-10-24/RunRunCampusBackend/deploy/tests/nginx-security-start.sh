#!/bin/sh
set -eu
# Only run in the disposable QA container: no production files are mounted writable.
cp /source/nginx-campus.conf.example /etc/nginx/conf.d/default.conf
mkdir -p /etc/letsencrypt/live/api.runruncampus.cn /var/www/letsencrypt/.well-known/acme-challenge
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=api.runruncampus.cn \
    -addext subjectAltName=DNS:api.runruncampus.cn,IP:127.0.0.1 \
    -keyout /etc/letsencrypt/live/api.runruncampus.cn/privkey.pem \
    -out /etc/letsencrypt/live/api.runruncampus.cn/fullchain.pem 2>/dev/null
echo qa-challenge > /var/www/letsencrypt/.well-known/acme-challenge/qa
cat > /tmp/qa-probe.js <<'EOF'
const script = document.createElement('script');
script.textContent = 'window.qaInlineExecuted = true';
document.head.appendChild(script);
try { window.eval('window.qaEvalExecuted = true'); } catch (_) {}
fetch('https://csp-qa.invalid/blocked').catch(() => {});
EOF
cat > /etc/nginx/conf.d/qa-upstream.conf <<'EOF'
server {
    listen 127.0.0.1:8080;
    root /admin;
    location = /qa-probe.js { default_type application/javascript; alias /tmp/qa-probe.js; }
    location = /api/qa-unauthorized { return 401; }
    location = /api/qa-forbidden { return 403; }
    location = /api/qa-error { return 500; }
    location = /actuator/health { return 200 '{"status":"UP"}'; }
}
# Browser traffic enters the production template from its allowed loopback source.
# This gateway exists only in the test container and is published on host loopback.
server {
    listen 8443 ssl;
    ssl_certificate /etc/letsencrypt/live/api.runruncampus.cn/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.runruncampus.cn/privkey.pem;
    location / {
        proxy_set_header Host api.runruncampus.cn;
        proxy_pass https://127.0.0.1:443;
    }
}
EOF
nginx -t
exec nginx -g 'daemon off;'

#!/bin/sh
set -eu
passed=0
failed=0
assert_contains() {
    if grep -Eiq -- "$2" "$1"; then
        echo "PASS $3"; passed=$((passed + 1))
    else
        echo "FAIL $3"; failed=$((failed + 1))
    fi
}
headers() {
    path=$1; status=$2; source=$3
    curl -ksS --max-time 5 --interface "$source" --resolve api.runruncampus.cn:443:127.0.0.1 \
        -D /tmp/qa-headers -o /tmp/qa-body "https://api.runruncampus.cn$path"
    assert_contains /tmp/qa-headers "^HTTP/1.1 $status" "$source $path status $status"
    assert_contains /tmp/qa-headers '^Strict-Transport-Security: max-age=31536000' "$source $path HSTS"
    assert_contains /tmp/qa-headers '^X-Content-Type-Options: nosniff' "$source $path nosniff"
    assert_contains /tmp/qa-headers '^X-Frame-Options: DENY' "$source $path no framing"
    assert_contains /tmp/qa-headers '^Referrer-Policy: no-referrer' "$source $path referrer"
    assert_contains /tmp/qa-headers "^Content-Security-Policy:.*default-src 'self'" "$source $path CSP"
}
headers / 200 127.0.0.1
headers /api/qa-unauthorized 401 127.0.0.1
headers /api/qa-forbidden 403 127.0.0.1
headers /missing-qa-path 404 127.0.0.1
headers /api/qa-error 500 127.0.0.1
headers / 200 127.0.0.2
headers /actuator/health 200 127.0.0.1
headers /actuator/health 403 127.0.0.2
curl -sS --max-time 5 -D /tmp/qa-headers -o /tmp/qa-body -H 'Host: api.runruncampus.cn' http://127.0.0.1/path?q=qa
assert_contains /tmp/qa-headers '^HTTP/1.1 301' 'HTTP redirect status'
assert_contains /tmp/qa-headers '^Location: https://api.runruncampus.cn/path\?q=qa' 'HTTP redirect preserves path and query'
curl -sS --max-time 5 --interface 127.0.0.2 -D /tmp/qa-headers -o /tmp/qa-body \
    -H 'Host: api.runruncampus.cn' http://127.0.0.1/.well-known/acme-challenge/qa
assert_contains /tmp/qa-headers '^HTTP/1.1 200' 'ACME reachable from external source'
assert_contains /tmp/qa-body '^qa-challenge' 'ACME challenge body'
for version in tls1 tls1_1; do
    timeout 5 openssl s_client -connect 127.0.0.1:443 -servername api.runruncampus.cn \
        -"$version" -cipher 'ALL:@SECLEVEL=0' </dev/null > /tmp/qa-tls 2>&1 || true
    assert_contains /tmp/qa-tls 'alert protocol version' "$version rejected by server"
done
timeout 5 openssl s_client -connect 127.0.0.1:443 -servername api.runruncampus.cn \
    -tls1_2 -cipher ECDHE-RSA-AES128-GCM-SHA256 </dev/null > /tmp/qa-tls 2>&1
assert_contains /tmp/qa-tls 'Cipher is ECDHE-RSA-AES128-GCM-SHA256' 'TLS 1.2 AEAD handshake'
timeout 5 openssl s_client -connect 127.0.0.1:443 -servername api.runruncampus.cn \
    -tls1_3 </dev/null > /tmp/qa-tls 2>&1
assert_contains /tmp/qa-tls 'TLSv1.3, Cipher is TLS_' 'TLS 1.3 handshake'
for cipher in AES128-GCM-SHA256 ECDHE-RSA-AES128-SHA256; do
    # Verify local support first, so an unsupported client cannot give a false pass.
    openssl ciphers "$cipher" > /tmp/qa-ciphers
    assert_contains /tmp/qa-ciphers "$cipher" "client supports forbidden cipher $cipher"
    timeout 5 openssl s_client -connect 127.0.0.1:443 -servername api.runruncampus.cn \
        -tls1_2 -cipher "$cipher" </dev/null > /tmp/qa-tls 2>&1 || true
    assert_contains /tmp/qa-tls 'alert handshake failure' "server rejects $cipher"
done
echo "{\"passed\":$passed,\"failed\":$failed}"
test "$failed" -eq 0

#!/bin/sh
set -eu
fail() { echo "ERROR: $1. See docs/甲方本地v2测试环境.md; credentials are not printed." >&2; exit 1; }
[ "$RRC_V2_JDBC_URL" = 'jdbc:postgresql://postgres:5432/rrc_v2_test' ] || fail 'Only the isolated Compose database is allowed'
[ "$RRC_V2_ENVIRONMENT" = test ] || fail 'RRC_V2_ENVIRONMENT must be test'
[ "$RRC_PAYMENT_MOCK_ENABLED" = true ] || fail 'Mock payment must be enabled'
[ "$RRC_V2_ADMIN_PHONE" = test-admin ] || fail 'Only synthetic test-admin is allowed'
[ "$RRC_V2_BOOTSTRAP_CONFIRM" = CREATE_ISOLATED_TEST_ADMIN ] || fail 'Explicit local fixture confirmation is required'
case "$RRC_V2_DB_PASSWORD:$RRC_IDENTITY_ENCRYPTION_KEY:$RRC_IDENTITY_HASH_KEY:$RRC_V2_ADMIN_PASSWORD" in
  *REPLACE*) fail 'Generate local values with tools/initialize-v2-environment.ps1' ;;
esac
exec java -jar /app/app.jar

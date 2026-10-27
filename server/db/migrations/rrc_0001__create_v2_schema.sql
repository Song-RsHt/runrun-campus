-- RunRunCampus v2 independent database initialization
-- Migration: rrc_0001__create_v2_schema.sql
-- Scope: create only the namespace for the new system. No business tables are created in M00.
-- This migration must run against an isolated empty database. It must never be applied to the legacy V1-V43 database.

CREATE SCHEMA IF NOT EXISTS runrun_v2;

COMMENT ON SCHEMA runrun_v2 IS
  'RunRunCampus stage-one v2 namespace; created from an empty isolated database. Legacy V1-V43 objects are out of scope.';

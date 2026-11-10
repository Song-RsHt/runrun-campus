-- M06: settlement entries, allocations, freezes and per-order mock payouts.
-- All write functions lock the order first so after-sales and payout races have
-- one deterministic winner.  Amounts are integer cents and entries are append-only.
BEGIN;
DO $$ BEGIN
  IF to_regclass('runrun_v2.v2_orders') IS NULL
     OR to_regclass('runrun_v2.fulfillment_tasks') IS NULL
     OR to_regclass('runrun_v2.after_sales_cases') IS NULL
     OR to_regclass('runrun_v2.app_users') IS NULL
  THEN RAISE EXCEPTION 'M06 requires M01-M04 tables'; END IF;
END $$;

CREATE TABLE runrun_v2.settlement_entries (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  order_id BIGINT NOT NULL REFERENCES runrun_v2.v2_orders(id),
  beneficiary_type TEXT NOT NULL CHECK (beneficiary_type IN ('PLATFORM','COURIER')),
  beneficiary_id BIGINT REFERENCES runrun_v2.app_users(id),
  task_id BIGINT REFERENCES runrun_v2.fulfillment_tasks(id),
  source_type TEXT NOT NULL CHECK (source_type IN ('ORIGINAL_PAYMENT','SUPPLEMENT','DOWNGRADE','REFUND_REVERSAL','LIABILITY','ARRIVAL_SUBSIDY','COMPENSATION')),
  source_id TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('EARNED','REVERSAL','ADJUSTMENT')),
  delta_cents BIGINT NOT NULL CHECK (delta_cents <> 0),
  reason TEXT NOT NULL CHECK (length(btrim(reason)) > 0),
  CONSTRAINT settlement_beneficiary_shape CHECK ((beneficiary_type='PLATFORM' AND beneficiary_id IS NULL) OR (beneficiary_type='COURIER' AND beneficiary_id IS NOT NULL)),
  UNIQUE NULLS NOT DISTINCT (source_type, source_id, beneficiary_type, beneficiary_id, kind)
);
CREATE INDEX settlement_entries_order_beneficiary ON runrun_v2.settlement_entries(order_id, beneficiary_type, beneficiary_id);

CREATE TABLE runrun_v2.rider_allocations (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  order_id BIGINT NOT NULL REFERENCES runrun_v2.v2_orders(id), allocation_version INTEGER NOT NULL CHECK (allocation_version > 0),
  status TEXT NOT NULL CHECK (status IN ('DRAFT','APPROVED','SUPERSEDED')), automatic BOOLEAN NOT NULL DEFAULT FALSE,
  approved_by BIGINT REFERENCES runrun_v2.app_users(id), reason TEXT NOT NULL CHECK (length(btrim(reason)) > 0),
  UNIQUE(order_id, allocation_version)
);
CREATE UNIQUE INDEX rider_allocations_current ON runrun_v2.rider_allocations(order_id) WHERE status='APPROVED';
CREATE TABLE runrun_v2.rider_allocation_lines (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  allocation_id BIGINT NOT NULL REFERENCES runrun_v2.rider_allocations(id) ON DELETE RESTRICT,
  task_id BIGINT NOT NULL REFERENCES runrun_v2.fulfillment_tasks(id),
  courier_id BIGINT NOT NULL REFERENCES runrun_v2.app_users(id),
  amount_cents BIGINT NOT NULL CHECK (amount_cents >= 0),
  UNIQUE(allocation_id, task_id, courier_id)
);

CREATE TABLE runrun_v2.settlement_freezes (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), released_at TIMESTAMPTZ,
  order_id BIGINT NOT NULL REFERENCES runrun_v2.v2_orders(id), beneficiary_type TEXT NOT NULL CHECK (beneficiary_type IN ('PLATFORM','COURIER')),
  beneficiary_id BIGINT REFERENCES runrun_v2.app_users(id), source_type TEXT NOT NULL, source_id TEXT NOT NULL,
  frozen_cents BIGINT NOT NULL CHECK (frozen_cents >= 0), reason TEXT NOT NULL CHECK (length(btrim(reason)) > 0),
  CHECK ((beneficiary_type='PLATFORM' AND beneficiary_id IS NULL) OR (beneficiary_type='COURIER' AND beneficiary_id IS NOT NULL))
);
CREATE UNIQUE INDEX settlement_freezes_active_source ON runrun_v2.settlement_freezes(order_id, source_type, source_id, beneficiary_type, beneficiary_id) WHERE released_at IS NULL;
CREATE INDEX settlement_freezes_order_active ON runrun_v2.settlement_freezes(order_id, released_at);
CREATE TABLE runrun_v2.freeze_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, freeze_id BIGINT NOT NULL REFERENCES runrun_v2.settlement_freezes(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), event_type TEXT NOT NULL CHECK (event_type IN ('CREATED','RESIZED','RELEASED')),
  previous_cents BIGINT NOT NULL CHECK (previous_cents >= 0), new_cents BIGINT NOT NULL CHECK (new_cents >= 0),
  actor_id BIGINT REFERENCES runrun_v2.app_users(id), reason TEXT NOT NULL CHECK (length(btrim(reason)) > 0)
);
CREATE INDEX freeze_events_lookup ON runrun_v2.freeze_events(freeze_id, created_at);

CREATE TABLE runrun_v2.payout_registrations (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES runrun_v2.v2_orders(id),
  beneficiary_type TEXT NOT NULL CHECK (beneficiary_type IN ('PLATFORM','COURIER')), beneficiary_id BIGINT REFERENCES runrun_v2.app_users(id),
  amount_cents BIGINT NOT NULL CHECK (amount_cents > 0), mode TEXT NOT NULL DEFAULT 'MOCK' CHECK (mode='MOCK'),
  reference TEXT NOT NULL UNIQUE, reason TEXT NOT NULL CHECK (length(btrim(reason)) > 0), registered_by BIGINT NOT NULL REFERENCES runrun_v2.app_users(id), registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((beneficiary_type='PLATFORM' AND beneficiary_id IS NULL) OR (beneficiary_type='COURIER' AND beneficiary_id IS NOT NULL))
);
CREATE INDEX payout_registrations_order_beneficiary ON runrun_v2.payout_registrations(order_id, beneficiary_type, beneficiary_id);

CREATE OR REPLACE FUNCTION runrun_v2.record_split(p_order BIGINT, p_source_type TEXT, p_source_id TEXT, p_total BIGINT, p_task BIGINT, p_courier BIGINT, p_reason TEXT)
RETURNS VOID LANGUAGE plpgsql AS $$ DECLARE platform BIGINT; BEGIN
  IF p_total <= 0 OR p_reason IS NULL OR btrim(p_reason)='' THEN RAISE EXCEPTION 'VALIDATION_ERROR'; END IF;
  PERFORM 1 FROM runrun_v2.v2_orders o WHERE o.id=p_order FOR UPDATE; IF NOT FOUND THEN RAISE EXCEPTION 'RESOURCE_NOT_FOUND'; END IF;
  platform := floor(p_total * 30 / 100.0)::BIGINT;
  IF platform > 0 THEN
    INSERT INTO runrun_v2.settlement_entries(order_id,beneficiary_type,source_type,source_id,kind,delta_cents,reason) VALUES(p_order,'PLATFORM',p_source_type,p_source_id,'EARNED',platform,p_reason) ON CONFLICT DO NOTHING;
  END IF;
  IF p_courier IS NOT NULL THEN
    INSERT INTO runrun_v2.settlement_entries(order_id,beneficiary_type,beneficiary_id,task_id,source_type,source_id,kind,delta_cents,reason) VALUES(p_order,'COURIER',p_courier,p_task,p_source_type,p_source_id,'EARNED',p_total-platform,p_reason) ON CONFLICT DO NOTHING;
  END IF;
END $$;

CREATE OR REPLACE FUNCTION runrun_v2.record_arrival_subsidy(p_order BIGINT,p_courier BIGINT,p_task BIGINT,p_reason TEXT,p_confirmed BOOLEAN)
RETURNS VOID LANGUAGE plpgsql AS $$ BEGIN
  IF NOT p_confirmed OR p_reason IS NULL OR btrim(p_reason)='' THEN RAISE EXCEPTION 'VALIDATION_ERROR'; END IF;
  PERFORM 1 FROM runrun_v2.v2_orders o WHERE o.id=p_order FOR UPDATE; IF NOT FOUND THEN RAISE EXCEPTION 'RESOURCE_NOT_FOUND'; END IF;
  INSERT INTO runrun_v2.settlement_entries(order_id,beneficiary_type,task_id,source_type,source_id,kind,delta_cents,reason)
    VALUES(p_order,'PLATFORM',p_task,'ARRIVAL_SUBSIDY','ORDER:'||p_order::text,'ADJUSTMENT',100,p_reason) ON CONFLICT DO NOTHING;
END $$;

CREATE OR REPLACE FUNCTION runrun_v2.record_settlement_adjustment(p_order BIGINT,p_source_type TEXT,p_source_id TEXT,p_platform_cents BIGINT,p_courier_cents BIGINT,p_courier BIGINT,p_task BIGINT,p_reason TEXT,p_confirmed BOOLEAN)
RETURNS VOID LANGUAGE plpgsql AS $$ BEGIN
  IF NOT p_confirmed OR p_source_type NOT IN ('REFUND_REVERSAL','DOWNGRADE','LIABILITY','COMPENSATION') OR p_platform_cents < 0 OR p_courier_cents < 0 OR (p_platform_cents=0 AND p_courier_cents=0) OR p_reason IS NULL OR btrim(p_reason)='' THEN RAISE EXCEPTION 'VALIDATION_ERROR'; END IF;
  PERFORM 1 FROM runrun_v2.v2_orders o WHERE o.id=p_order FOR UPDATE; IF NOT FOUND THEN RAISE EXCEPTION 'RESOURCE_NOT_FOUND'; END IF;
  IF p_platform_cents > 0 THEN INSERT INTO runrun_v2.settlement_entries(order_id,beneficiary_type,source_type,source_id,kind,delta_cents,reason) VALUES(p_order,'PLATFORM',p_source_type,p_source_id,'REVERSAL',-p_platform_cents,p_reason) ON CONFLICT DO NOTHING; END IF;
  IF p_courier_cents > 0 THEN
    IF p_courier IS NULL THEN RAISE EXCEPTION 'VALIDATION_ERROR'; END IF;
    INSERT INTO runrun_v2.settlement_entries(order_id,beneficiary_type,beneficiary_id,task_id,source_type,source_id,kind,delta_cents,reason) VALUES(p_order,'COURIER',p_courier,p_task,p_source_type,p_source_id,'REVERSAL',-p_courier_cents,p_reason) ON CONFLICT DO NOTHING;
  END IF;
END $$;

CREATE OR REPLACE FUNCTION runrun_v2.approve_rider_allocation(p_order BIGINT,p_lines JSONB,p_admin BIGINT,p_reason TEXT,p_confirmed BOOLEAN)
RETURNS BIGINT LANGUAGE plpgsql AS $$ DECLARE aid BIGINT; next_version INTEGER; pool BIGINT; assigned BIGINT; line JSONB; BEGIN
  IF NOT p_confirmed OR p_reason IS NULL OR btrim(p_reason)='' OR jsonb_typeof(p_lines)<>'array' OR jsonb_array_length(p_lines)=0 THEN RAISE EXCEPTION 'VALIDATION_ERROR'; END IF;
  PERFORM 1 FROM runrun_v2.v2_orders o WHERE o.id=p_order FOR UPDATE; IF NOT FOUND THEN RAISE EXCEPTION 'RESOURCE_NOT_FOUND'; END IF;
  -- Allocation is based on the current effective rider pool.  Reversals and
  -- adjustments are part of that pool; using only EARNED would re-create
  -- rights that a refund or liability entry already removed.
  SELECT COALESCE(SUM(delta_cents),0) INTO pool FROM runrun_v2.settlement_entries WHERE order_id=p_order AND beneficiary_type='COURIER';
  SELECT COALESCE(MAX(allocation_version),0)+1 INTO next_version FROM runrun_v2.rider_allocations WHERE order_id=p_order;
  INSERT INTO runrun_v2.rider_allocations(order_id,allocation_version,status,automatic,approved_by,reason) VALUES(p_order,next_version,'DRAFT',FALSE,p_admin,p_reason) RETURNING id INTO aid;
  FOR line IN SELECT * FROM jsonb_array_elements(p_lines) LOOP
    IF (line->>'taskId') IS NULL OR (line->>'courierId') IS NULL OR (line->>'amountCents')::BIGINT < 0 THEN RAISE EXCEPTION 'VALIDATION_ERROR'; END IF;
    IF NOT EXISTS (SELECT 1 FROM runrun_v2.fulfillment_tasks t WHERE t.id=(line->>'taskId')::BIGINT AND t.order_id=p_order AND t.courier_id=(line->>'courierId')::BIGINT) THEN RAISE EXCEPTION 'RESOURCE_NOT_FOUND'; END IF;
    INSERT INTO runrun_v2.rider_allocation_lines(allocation_id,task_id,courier_id,amount_cents) VALUES(aid,(line->>'taskId')::BIGINT,(line->>'courierId')::BIGINT,(line->>'amountCents')::BIGINT);
  END LOOP;
  SELECT COALESCE(SUM(amount_cents),0) INTO assigned FROM runrun_v2.rider_allocation_lines WHERE allocation_id=aid;
  IF assigned <> pool THEN RAISE EXCEPTION 'AMOUNT_EXCEEDS_AVAILABLE'; END IF;
  UPDATE runrun_v2.rider_allocations SET status='SUPERSEDED' WHERE order_id=p_order AND status='APPROVED';
  UPDATE runrun_v2.rider_allocations SET status='APPROVED',updated_at=now() WHERE id=aid;
  RETURN aid;
END $$;

CREATE OR REPLACE FUNCTION runrun_v2.freeze_after_sales(p_order BIGINT,p_case_id BIGINT,p_actor BIGINT,p_reason TEXT,p_confirmed BOOLEAN)
RETURNS VOID LANGUAGE plpgsql AS $$ DECLARE e RECORD; available BIGINT; paid BIGINT; amount BIGINT; BEGIN
  IF NOT p_confirmed OR p_reason IS NULL OR btrim(p_reason)='' THEN RAISE EXCEPTION 'VALIDATION_ERROR'; END IF;
  PERFORM 1 FROM runrun_v2.v2_orders o WHERE o.id=p_order FOR UPDATE; IF NOT FOUND THEN RAISE EXCEPTION 'RESOURCE_NOT_FOUND'; END IF;
  FOR e IN SELECT 'PLATFORM'::TEXT AS typ,NULL::BIGINT AS bid UNION ALL SELECT 'COURIER',x.beneficiary_id FROM (SELECT DISTINCT beneficiary_id FROM runrun_v2.settlement_entries WHERE order_id=p_order AND beneficiary_type='COURIER' AND beneficiary_id IS NOT NULL) x LOOP
    SELECT COALESCE(SUM(delta_cents),0) INTO available FROM runrun_v2.settlement_entries WHERE order_id=p_order AND beneficiary_type=e.typ AND beneficiary_id IS NOT DISTINCT FROM e.bid;
    SELECT COALESCE(SUM(amount_cents),0) INTO paid FROM runrun_v2.payout_registrations WHERE order_id=p_order AND beneficiary_type=e.typ AND beneficiary_id IS NOT DISTINCT FROM e.bid;
    amount := GREATEST(available-paid,0);
    IF amount > 0 THEN PERFORM runrun_v2.freeze_settlement(p_order,e.typ,e.bid,'AFTER_SALES',p_case_id::TEXT,amount,p_reason,p_actor,p_confirmed); END IF;
  END LOOP;
END $$;

CREATE OR REPLACE FUNCTION runrun_v2.m06_after_sales_freeze_trigger() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
  PERFORM runrun_v2.freeze_after_sales(NEW.order_id,NEW.id,NEW.opened_by,NEW.reason,TRUE);
  RETURN NEW;
END $$;
CREATE TRIGGER after_sales_default_settlement_freeze
  AFTER INSERT ON runrun_v2.after_sales_cases FOR EACH ROW EXECUTE FUNCTION runrun_v2.m06_after_sales_freeze_trigger();

CREATE OR REPLACE FUNCTION runrun_v2.register_payout(p_order BIGINT,p_type TEXT,p_beneficiary BIGINT,p_amount BIGINT,p_reference TEXT,p_reason TEXT,p_admin BIGINT,p_confirmed BOOLEAN)
RETURNS BIGINT LANGUAGE plpgsql AS $$ DECLARE available BIGINT; frozen BIGINT; paid BIGINT; v_id BIGINT; BEGIN
  IF NOT p_confirmed OR p_amount<=0 OR p_reference IS NULL OR btrim(p_reference)='' OR p_reason IS NULL OR btrim(p_reason)='' THEN RAISE EXCEPTION 'VALIDATION_ERROR'; END IF;
  PERFORM 1 FROM runrun_v2.v2_orders o WHERE o.id=p_order FOR UPDATE; IF NOT FOUND THEN RAISE EXCEPTION 'RESOURCE_NOT_FOUND'; END IF;
  IF p_type NOT IN ('PLATFORM','COURIER') OR (p_type='PLATFORM' AND p_beneficiary IS NOT NULL) OR (p_type='COURIER' AND p_beneficiary IS NULL) THEN RAISE EXCEPTION 'VALIDATION_ERROR'; END IF;
  SELECT COALESCE(SUM(delta_cents),0) INTO available FROM runrun_v2.settlement_entries WHERE order_id=p_order AND beneficiary_type=p_type AND beneficiary_id IS NOT DISTINCT FROM p_beneficiary;
  SELECT COALESCE(SUM(amount_cents),0) INTO paid FROM runrun_v2.payout_registrations WHERE order_id=p_order AND beneficiary_type=p_type AND beneficiary_id IS NOT DISTINCT FROM p_beneficiary;
  SELECT COALESCE(SUM(frozen_cents),0) INTO frozen FROM runrun_v2.settlement_freezes WHERE order_id=p_order AND beneficiary_type=p_type AND beneficiary_id IS NOT DISTINCT FROM p_beneficiary AND released_at IS NULL;
  IF frozen > 0 THEN RAISE EXCEPTION 'SETTLEMENT_FROZEN'; END IF;
  IF p_amount > available-paid THEN RAISE EXCEPTION 'AMOUNT_EXCEEDS_AVAILABLE'; END IF;
  INSERT INTO runrun_v2.payout_registrations(order_id,beneficiary_type,beneficiary_id,amount_cents,reference,reason,registered_by) VALUES(p_order,p_type,p_beneficiary,p_amount,p_reference,p_reason,p_admin) RETURNING id INTO v_id;
  RETURN v_id;
EXCEPTION WHEN unique_violation THEN RAISE EXCEPTION 'IDEMPOTENCY_KEY_CONFLICT'; END $$;

CREATE OR REPLACE FUNCTION runrun_v2.freeze_settlement(p_order BIGINT,p_type TEXT,p_beneficiary BIGINT,p_source TEXT,p_source_id TEXT,p_amount BIGINT,p_reason TEXT,p_actor BIGINT,p_confirmed BOOLEAN)
RETURNS BIGINT LANGUAGE plpgsql AS $$ DECLARE v_id BIGINT; BEGIN
  IF NOT p_confirmed OR p_amount<0 OR p_reason IS NULL OR btrim(p_reason)='' THEN RAISE EXCEPTION 'VALIDATION_ERROR'; END IF;
  PERFORM 1 FROM runrun_v2.v2_orders o WHERE o.id=p_order FOR UPDATE; IF NOT FOUND THEN RAISE EXCEPTION 'RESOURCE_NOT_FOUND'; END IF;
  INSERT INTO runrun_v2.settlement_freezes(order_id,beneficiary_type,beneficiary_id,source_type,source_id,frozen_cents,reason) VALUES(p_order,p_type,p_beneficiary,p_source,p_source_id,p_amount,p_reason) RETURNING id INTO v_id;
  INSERT INTO runrun_v2.freeze_events(freeze_id,event_type,previous_cents,new_cents,actor_id,reason) VALUES(v_id,'CREATED',0,p_amount,p_actor,p_reason); RETURN v_id;
EXCEPTION WHEN unique_violation THEN RAISE EXCEPTION 'IDEMPOTENCY_KEY_CONFLICT'; END $$;

CREATE OR REPLACE FUNCTION runrun_v2.release_settlement_freeze(p_freeze BIGINT,p_reason TEXT,p_actor BIGINT,p_confirmed BOOLEAN) RETURNS VOID LANGUAGE plpgsql AS $$ DECLARE f runrun_v2.settlement_freezes; BEGIN
  IF NOT p_confirmed OR p_reason IS NULL OR btrim(p_reason)='' THEN RAISE EXCEPTION 'VALIDATION_ERROR'; END IF;
  SELECT * INTO f FROM runrun_v2.settlement_freezes WHERE id=p_freeze FOR UPDATE; IF NOT FOUND OR f.released_at IS NOT NULL THEN RETURN; END IF;
  UPDATE runrun_v2.settlement_freezes SET released_at=now() WHERE id=p_freeze;
  INSERT INTO runrun_v2.freeze_events(freeze_id,event_type,previous_cents,new_cents,actor_id,reason) VALUES(p_freeze,'RELEASED',f.frozen_cents,0,p_actor,p_reason);
END $$;
COMMIT;



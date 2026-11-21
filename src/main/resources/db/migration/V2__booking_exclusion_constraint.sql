-- =============================================================================
-- VoltGrid Database Migration: V2__booking_exclusion_constraint.sql
-- Enables btree_gist extension and enforces exclusion constraint on active bookings.
-- Guarantees at the database level that no charger can have overlapping
-- active time slots (PENDING_PAYMENT, CONFIRMED, ACTIVE).
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Clean up any historical test bookings that might conflict (mark duplicate ones as CANCELLED)
-- to ensure clean migration on pre-existing databases
WITH duplicates AS (
    SELECT b1.id
    FROM bookings b1
    JOIN bookings b2 ON b1.charger_id = b2.charger_id
      AND b1.id > b2.id
      AND tstzrange(b1.start_time, b1.end_time) && tstzrange(b2.start_time, b2.end_time)
      AND b1.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ACTIVE')
      AND b2.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ACTIVE')
)
UPDATE bookings
SET status = 'CANCELLED', updated_at = NOW()
WHERE id IN (SELECT id FROM duplicates);

ALTER TABLE bookings
ADD CONSTRAINT no_overlapping_active_bookings
EXCLUDE USING gist (
    charger_id WITH =,
    tstzrange(start_time, end_time) WITH &&
)
WHERE (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ACTIVE'));

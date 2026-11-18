-- =============================================================================
-- VoltGrid Database Migration: V1__init_schema.sql
-- Baseline schema definition matching all JPA entities, constraints, and indexes.
-- =============================================================================

-- 1. Users
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- 2. Stations
CREATE TABLE IF NOT EXISTS stations (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(300) NOT NULL,
    city VARCHAR(100) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_stations_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_stations_owner ON stations (owner_id);
CREATE INDEX IF NOT EXISTS idx_stations_status_city ON stations (status, city);
CREATE INDEX IF NOT EXISTS idx_stations_lat_lng ON stations (latitude, longitude);

-- 3. Chargers
CREATE TABLE IF NOT EXISTS chargers (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    power_kw NUMERIC(5, 2) NOT NULL,
    connector_type VARCHAR(20) NOT NULL,
    price_per_kwh NUMERIC(5, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_chargers_station FOREIGN KEY (station_id) REFERENCES stations (id) ON DELETE CASCADE,
    CONSTRAINT uk_chargers_station_name UNIQUE (station_id, name)
);

CREATE INDEX IF NOT EXISTS idx_chargers_station ON chargers (station_id);

-- 4. Vehicles
CREATE TABLE IF NOT EXISTS vehicles (
    id BIGSERIAL PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    model VARCHAR(100) NOT NULL,
    usable_battery_kwh NUMERIC(6, 2) NOT NULL,
    base_consumption_kwh_per_km NUMERIC(5, 3) NOT NULL,
    max_charge_kw NUMERIC(6, 2) NOT NULL,
    connector_type VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_vehicles_driver FOREIGN KEY (driver_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_vehicles_driver ON vehicles (driver_id);

-- 5. Bookings
CREATE TABLE IF NOT EXISTS bookings (
    id BIGSERIAL PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    charger_id BIGINT NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ,
    base_amount NUMERIC(8, 2) DEFAULT 0.00,
    overstay_penalty NUMERIC(8, 2) DEFAULT 0.00,
    total_amount NUMERIC(8, 2) DEFAULT 0.00,
    session_start_time TIMESTAMPTZ,
    charge_completed_at TIMESTAMPTZ,
    plug_out_at TIMESTAMPTZ,
    payment_intent_id VARCHAR(150),
    version BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_bookings_driver FOREIGN KEY (driver_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_charger FOREIGN KEY (charger_id) REFERENCES chargers (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_bookings_charger_time ON bookings (charger_id, start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_bookings_vehicle_time ON bookings (vehicle_id, start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_bookings_driver ON bookings (driver_id);

-- 6. Payments
CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    amount NUMERIC(8, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    transaction_id VARCHAR(150),
    idempotency_key VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_payments_booking ON payments (booking_id);
CREATE INDEX IF NOT EXISTS idx_payments_transaction ON payments (transaction_id);

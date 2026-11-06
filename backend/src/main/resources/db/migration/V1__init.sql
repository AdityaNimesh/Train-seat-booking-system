-- Train seat booking schema (PostgreSQL)

CREATE TABLE reservations (
    id           UUID PRIMARY KEY,
    user_id      VARCHAR(64)  NOT NULL,
    status       VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE','CONFIRMED','CANCELLED','EXPIRED')),
    created_at   TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    confirmed_at TIMESTAMPTZ
);
CREATE INDEX idx_reservations_status_expiry ON reservations (status, expires_at);

-- Exactly 20 rows, one per seat. This row is the single source of truth for a seat and the target of row locks.
CREATE TABLE seats (
    seat_number    INTEGER PRIMARY KEY CHECK (seat_number BETWEEN 1 AND 20),
    status         VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','LOCKED','BOOKED')),
    reservation_id UUID REFERENCES reservations (id),
    locked_until   TIMESTAMPTZ,
    CONSTRAINT seat_state_consistent CHECK (
        (status = 'AVAILABLE' AND reservation_id IS NULL AND locked_until IS NULL) OR
        (status = 'LOCKED'    AND reservation_id IS NOT NULL AND locked_until IS NOT NULL) OR
        (status = 'BOOKED'    AND reservation_id IS NOT NULL)
    )
);

CREATE TABLE reservation_seats (
    reservation_id UUID    NOT NULL REFERENCES reservations (id) ON DELETE CASCADE,
    seat_number    INTEGER NOT NULL REFERENCES seats (seat_number),
    PRIMARY KEY (reservation_id, seat_number)
);

-- Confirmed bookings. UNIQUE(seat_number) is the last line of defence: the database itself
-- refuses a second booking for the same seat, and the FK to seats caps it at 20 seats.
CREATE TABLE bookings (
    id             BIGSERIAL PRIMARY KEY,
    seat_number    INTEGER     NOT NULL UNIQUE REFERENCES seats (seat_number),
    reservation_id UUID        NOT NULL REFERENCES reservations (id),
    user_id        VARCHAR(64) NOT NULL,
    booked_at      TIMESTAMPTZ NOT NULL
);

INSERT INTO seats (seat_number) SELECT generate_series(1, 20);

CREATE TABLE events (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    venue       VARCHAR(200) NOT NULL,
    starts_at   TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE seats (
    id           BIGSERIAL PRIMARY KEY,
    event_id     BIGINT      NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    seat_row     VARCHAR(5)  NOT NULL,
    seat_number  INT         NOT NULL,
    price_cents  INT         NOT NULL CHECK (price_cents >= 0),
    status       VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                 CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_seat_per_event UNIQUE (event_id, seat_row, seat_number)
);

CREATE INDEX idx_seats_event_id ON seats(event_id);

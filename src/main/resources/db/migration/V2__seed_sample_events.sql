-- Sample data: 3 events, each with 50 seats (rows A-E, seats 1-10).
-- Rows A and B are premium (7500 cents), rows C to E are standard (4500 cents).

WITH new_events AS (
    INSERT INTO events (name, venue, starts_at) VALUES
        ('Rock Night Live',        'City Arena',         '2026-12-05 19:30:00+00'),
        ('Jazz in the Park',       'Riverside Pavilion', '2026-11-20 18:00:00+00'),
        ('Comedy Special Tonight', 'Downtown Theatre',   '2027-01-15 20:00:00+00')
    RETURNING id
)
INSERT INTO seats (event_id, seat_row, seat_number, price_cents)
SELECT e.id,
       r.seat_row,
       n.seat_number,
       CASE WHEN r.seat_row IN ('A', 'B') THEN 7500 ELSE 4500 END
FROM new_events e
CROSS JOIN (VALUES ('A'), ('B'), ('C'), ('D'), ('E')) AS r(seat_row)
CROSS JOIN generate_series(1, 10) AS n(seat_number);

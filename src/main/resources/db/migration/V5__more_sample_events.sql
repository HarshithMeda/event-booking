-- 5 more sample events (made-up names and venues), each with 50 seats (rows A-E, seats 1-10).
-- Rows A and B are premium (7500 cents), rows C to E are standard (4500 cents), like V2.

WITH new_events AS (
    INSERT INTO events (name, venue, starts_at) VALUES
        ('Winter Lights Symphony',    'Maple Grove Hall',       '2026-12-18 19:00:00+00'),
        ('Harbor Nights Jazz Club',   'Old Mill Theatre',       '2027-01-30 20:00:00+00'),
        ('Neon Skyline Dance Party',  'Riverbend Arena',        '2027-03-13 21:00:00+00'),
        ('Spring Stories Poetry Slam','Cedar Park Pavilion',    '2027-04-24 18:30:00+00'),
        ('Summer Folk Gathering',     'Lakeside Amphitheater',  '2027-06-12 17:00:00+00')
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

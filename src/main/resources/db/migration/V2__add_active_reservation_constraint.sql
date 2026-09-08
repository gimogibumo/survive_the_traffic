CREATE UNIQUE INDEX uq_active_reservation_per_seat
    ON reservation (seat_id)
    WHERE status IN ('PENDING', 'CONFIRMED');

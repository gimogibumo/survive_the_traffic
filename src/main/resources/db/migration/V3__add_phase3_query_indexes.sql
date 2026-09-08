CREATE INDEX idx_seat_concert_id
    ON seat (concert_id);

CREATE INDEX idx_reservation_user_id
    ON reservation (user_id);

CREATE INDEX idx_reservation_seat_id
    ON reservation (seat_id);

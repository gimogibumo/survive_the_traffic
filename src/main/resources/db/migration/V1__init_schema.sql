CREATE TABLE concert (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    reservation_open_at TIMESTAMP(6) NOT NULL,
    concert_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE seat (
    id BIGSERIAL PRIMARY KEY,
    concert_id BIGINT NOT NULL,
    section VARCHAR(255) NOT NULL,
    row_number INTEGER NOT NULL,
    seat_number INTEGER NOT NULL,
    price BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL,
    version BIGINT,
    CONSTRAINT fk_seat_concert
        FOREIGN KEY (concert_id)
        REFERENCES concert (id),
    CONSTRAINT uk_seat_concert_position
        UNIQUE (concert_id, section, row_number, seat_number)
);

CREATE TABLE reservation (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_reservation_user
        FOREIGN KEY (user_id)
        REFERENCES users (id),
    CONSTRAINT fk_reservation_seat
        FOREIGN KEY (seat_id)
        REFERENCES seat (id)
);

CREATE TABLE payment (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL UNIQUE,
    amount BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL,
    payment_key VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_payment_reservation
        FOREIGN KEY (reservation_id)
        REFERENCES reservation (id)
);

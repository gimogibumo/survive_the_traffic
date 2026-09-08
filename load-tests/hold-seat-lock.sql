\set ON_ERROR_STOP on
BEGIN;
SET LOCAL application_name = 'phase3-lock-holder';
SELECT id FROM seat WHERE id = :seat_id FOR UPDATE;
SELECT pg_sleep(:hold_seconds);
ROLLBACK;

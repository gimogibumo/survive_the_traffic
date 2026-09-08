\set ON_ERROR_STOP on
-- Supply the shared seed's ID with psql -v concert_id=... .
-- A session-local copy keeps the application's data and indexes untouched.
BEGIN;
CREATE TEMP TABLE phase3_seat AS TABLE seat;
ANALYZE phase3_seat;
SELECT count(*) AS total_seats FROM phase3_seat;
\echo No index (warm-up)
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM phase3_seat WHERE concert_id = :concert_id;
\echo No index (measured)
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM phase3_seat WHERE concert_id = :concert_id;
CREATE UNIQUE INDEX phase3_position ON phase3_seat (concert_id, section, row_number, seat_number);
\echo Phase 2 composite index (warm-up)
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM phase3_seat WHERE concert_id = :concert_id;
\echo Phase 2 composite index (measured)
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM phase3_seat WHERE concert_id = :concert_id;
CREATE INDEX phase3_concert ON phase3_seat (concert_id);
\echo Phase 3 additional index (warm-up)
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM phase3_seat WHERE concert_id = :concert_id;
\echo Phase 3 additional index (measured)
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM phase3_seat WHERE concert_id = :concert_id;
ROLLBACK;

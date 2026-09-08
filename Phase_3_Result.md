# Ticketing: Survive the Traffic — Phase 3 Results

## Run Server

```bash
SPRING_PROFILES_ACTIVE=performance ./gradlew --no-daemon bootRun
```

Pool and thread settings can be changed one at a time.

```bash
TOMCAT_MAX_THREADS=200 HIKARI_MAX_POOL_SIZE=5 SPRING_PROFILES_ACTIVE=performance ./gradlew --no-daemon bootRun
```

## k6 Commands

Seat read baseline:

```bash
VUS=10 DURATION=30s SEAT_COUNT=10000 k6 run load-tests/k6/seat-get.js
VUS=50 DURATION=30s SEAT_COUNT=10000 k6 run load-tests/k6/seat-get.js
VUS=100 DURATION=30s SEAT_COUNT=10000 k6 run load-tests/k6/seat-get.js
VUS=200 DURATION=30s SEAT_COUNT=10000 k6 run load-tests/k6/seat-get.js
```

Hikari comparison:

```bash
HIKARI_MAX_POOL_SIZE=5 SPRING_PROFILES_ACTIVE=performance ./gradlew --no-daemon bootRun
VUS=100 DURATION=30s k6 run load-tests/k6/seat-get.js
```

Hot seat reservation:

```bash
VUS=100 ITERATIONS=100 USER_COUNT=100 k6 run load-tests/k6/hot-seat-reservation.js
```

Distributed seat reservation:

```bash
VUS=100 ITERATIONS=100 COUNT=100 k6 run load-tests/k6/distributed-seat-reservation.js
```

User reservation history:

```bash
VUS=100 DURATION=30s RESERVATION_COUNT=100 k6 run load-tests/k6/user-reservations.js
```

EXPLAIN:

```bash
curl -s "http://localhost:8080/api/performance/explain/concerts/{concertId}/seats?analyze=true"
```

## Result Table

| Experiment | Setting | VUs | RPS | Avg | p95 | p99 | Error |
|---|---|---:|---:|---:|---:|---:|---:|
| Seat GET | Baseline | 10 |  |  |  |  |  |
| Seat GET | Baseline | 50 |  |  |  |  |  |
| Seat GET | Baseline | 100 |  |  |  |  |  |
| Seat GET | Baseline | 200 |  |  |  |  |  |
| Seat GET | Hikari 5 | 100 |  |  |  |  |  |
| Seat GET | Hikari 10 | 100 |  |  |  |  |  |
| Seat GET | Hikari 20 | 100 |  |  |  |  |  |
| Same Seat Reserve | Pessimistic | 100 |  |  |  |  |  |
| Different Seat Reserve | Pessimistic | 100 |  |  |  |  |  |
| User Reservations | Fetch Join | 100 |  |  |  |  |  |

## Observations

- Seat read uses `idx_seat_concert_id`.
- User reservation history uses fetch join to load `Reservation -> Seat -> Concert` in one query path after the user existence check.
- Hot seat reservation should show higher tail latency and conflict responses because all requests contend for one row lock.
- Distributed seat reservation should avoid row-level lock contention because each request uses a different seat row.

# Phase 5.1 — Queue OFF Baseline

## 1. 목적

Phase 5에서는 Redis 기반 Waiting Queue / Admission Control을 도입하기 전에,  
**대기열이 없는 현재 예약 시스템이 지속적인 write traffic을 어디까지 감당할 수 있는지** 먼저 측정한다.

핵심 질문은 다음과 같다.

- 예약 요청이 증가할 때 HTTP latency가 어떻게 변하는가?
- HikariCP connection pool은 언제부터 포화되는가?
- DB connection을 기다리는 pending request가 발생하는가?
- 어느 구간부터 target arrival rate를 유지하지 못하는가?
- Waiting Queue를 도입해야 할 실험적 근거가 존재하는가?

이번 단계에서는 **Queue를 아직 도입하지 않는다.**

---

## 2. 현재 예약 요청 처리 경로

`POST /api/reservations`

현재 예약 생성 로직은 하나의 transaction 안에서 다음 작업을 수행한다.

```text
Client
  ↓
Tomcat
  ↓
Spring MVC
  ↓
ReservationService.createReservation()
  ↓
User SELECT
  ↓
Seat SELECT ... FOR UPDATE
  ↓
Seat 상태 변경
  ↓
Reservation INSERT
  ↓
COMMIT
```

핵심 서비스 구조:

```java
@Transactional
public ReservationResponse createReservation(ReservationRequest request) {

    User user = userRepository.findById(request.userId())
        .orElseThrow(UserNotFoundException::new);

    Seat seat = seatRepository.findByIdWithPessimisticLock(request.seatId())
        .orElseThrow(SeatNotFoundException::new);

    seat.hold();
    seatRepository.save(seat);

    LocalDateTime now = LocalDateTime.now();

    Reservation reservation = Reservation.builder()
        .seat(seat)
        .user(user)
        .createdAt(now)
        .expiresAt(now.plusMinutes(5))
        .status(ReservationStatus.PENDING)
        .build();

    Reservation saved = reservationRepository.save(reservation);

    return new ReservationResponse(
        saved.getId(),
        saved.getSeat().getId(),
        saved.getStatus(),
        saved.getExpiresAt()
    );
}
```

---

## 3. 실험 환경

### Application

```text
Spring Boot
Java 21
PostgreSQL 17
HikariCP
```

### HikariCP

Actuator로 확인한 현재 connection pool 최대값:

```text
hikaricp.connections.max = 10
```

즉 동시에 DB connection을 빌릴 수 있는 요청은 최대 10개이다.

### Tomcat

실험 중 명시한 설정:

```yaml
server:
  tomcat:
    mbeanregistry:
      enabled: true

    threads:
      max: 200

    accept-count: 300
```

따라서 주요 서버 설정은:

```text
Tomcat max threads = 200
Tomcat accept queue = 300
Hikari max connections = 10
```

### Load Generator

k6를 사용했다.

최종 sustained load test에서는 load-generator 자체의 VU allocation 영향을 제거하기 위해 다음과 같이 고정하였다.

```javascript
preAllocatedVUs: 300,
maxVUs: 300,
```

---

# 4. 테스트 데이터 정합성 이슈

초기 실험에서 일부 Seat가 다음과 같은 비정상 상태임을 발견했다.

```text
seat.status = AVAILABLE

하지만

active reservation(PENDING / CONFIRMED) 존재
```

이는 다음 invariant를 위반한다.

```text
AVAILABLE ↔ active reservation 없음
```

DB에는 Phase 2에서 생성한 partial unique index가 존재한다.

```sql
CREATE UNIQUE INDEX uq_active_reservation_per_seat
ON reservation(seat_id)
WHERE status IN ('PENDING', 'CONFIRMED');
```

따라서 비정상 테스트 데이터에서는 DB unique constraint가 정상적으로 예약 생성 요청을 차단했다.

이 실험은 성능 baseline에서 제외하였다.

이후 모든 부하 테스트는 다음 조건을 만족하는 clean seat만 사용했다.

```text
seat.status = AVAILABLE
AND
active PENDING / CONFIRMED reservation 없음
```

---

# 5. Burst Test

## 5.1 100 concurrent requests

100개의 서로 다른 clean seat를 준비하고,

```text
100 VUs
1 iteration / VU
총 100 reservation requests
```

를 거의 동시에 전송했다.

결과:

| Metric | Result |
|---|---:|
| Requests | 100 |
| Success | 100 |
| Error | 0% |
| Avg | 85.27 ms |
| p95 | 114.97 ms |
| p99 | 115.40 ms |
| Max | 115.72 ms |

따라서 100 concurrent burst는 정상 처리되었다.

---

## 5.2 200 concurrent requests

200 VU burst에서는 반복적으로 `connection reset by peer`가 발생했다.

예:

```text
status = 0
body = null
connection reset by peer
```

한 실험에서는:

```text
200 requests
106 success
94 TCP reset
```

이 발생했다.

동일 시점의 metric을 분석한 결과 실제 DB connection acquisition 횟수는 성공 요청 수와 일치했다.

```text
Hikari acquire count delta = 106
```

따라서 reset된 요청은 DB transaction까지 도달하지 못한 것으로 판단했다.

다만 200 VU burst 테스트는 약 0.1~0.3초 내에 종료되는 매우 짧은 traffic spike였고,  
OS socket / Tomcat connector / local load generator 상태에 따른 run-to-run variance가 컸다.

따라서 **시스템의 지속 처리량(capacity)을 판단하는 주 실험으로는 사용하지 않고**,  
순간 burst에 대한 별도 관찰 결과로만 기록한다.

---

# 6. Sustained Load Test 설계

Burst test 대신 일정한 arrival rate를 유지하는 open workload를 사용했다.

k6:

```javascript
executor: 'constant-arrival-rate'
```

예:

```javascript
queue_off_sustained: {
    executor: 'constant-arrival-rate',

    rate: RATE,
    timeUnit: '1s',
    duration: '10s',

    preAllocatedVUs: 300,
    maxVUs: 300,

    gracefulStop: '5s',
}
```

`__VU`를 seat index로 사용하면 하나의 VU가 여러 iteration을 수행하면서 같은 seat를 중복 예약할 수 있다.

따라서 각 request에는:

```javascript
exec.scenario.iterationInTest
```

를 사용하여 서로 다른 seat ID를 할당했다.

---

# 7. Hikari metric 측정 방법

테스트 전후로 다음 metric을 기록하였다.

```bash
curl -s \
  http://localhost:8080/actuator/metrics/hikaricp.connections.acquire \
  | jq
```

실험 구간의 connection acquisition count:

```text
COUNT_after - COUNT_before
```

총 acquisition time:

```text
TOTAL_TIME_after - TOTAL_TIME_before
```

평균 acquisition time:

```text
(TOTAL_TIME_after - TOTAL_TIME_before)
/
(COUNT_after - COUNT_before)
```

또한 테스트 중 다음 metric을 polling하였다.

```text
hikaricp.connections.active
hikaricp.connections.pending
```

---

# 8. Sustained Load Test 결과

## 8.1 50 RPS

```text
50 requests/sec
10 sec
```

결과:

| Metric | Result |
|---|---:|
| HTTP Requests | 500 |
| Success | 100% |
| Error | 0% |
| Avg | 7.77 ms |
| p95 | 11.56 ms |
| p99 | 19.02 ms |
| Max | 60.02 ms |
| Hikari acquire avg | 0.046 ms |
| VU max | 1 |
| Dropped iterations | 0 |

### 해석

50 RPS에서는 connection pool contention이 사실상 존재하지 않았다.

---

## 8.2 100 RPS

```text
100 requests/sec
10 sec
```

결과:

| Metric | Result |
|---|---:|
| HTTP Requests | 1001 |
| Success | 100% |
| Error | 0% |
| Avg | 4.90 ms |
| p95 | 8.15 ms |
| p99 | 16.93 ms |
| Max | 35.73 ms |
| Hikari acquire avg | 0.014 ms |
| VU max | 1 |
| Dropped iterations | 0 |

### 해석

100 RPS에서도 시스템은 충분한 여유를 보였다.

---

## 8.3 200 RPS

```text
200 requests/sec
10 sec
```

결과:

| Metric | Result |
|---|---:|
| HTTP Requests | 2000 |
| Success | 100% |
| Error | 0% |
| Avg | 2.92 ms |
| p95 | 4.00 ms |
| p99 | 7.97 ms |
| Max | 26.88 ms |
| Hikari acquire avg | 0.008 ms |
| VU max | 1 |
| Dropped iterations | 0 |

### 해석

200 RPS에서도 connection pool saturation은 관찰되지 않았다.

---

## 8.4 400 RPS

```text
400 requests/sec
10 sec
```

대표 실행 결과:

| Metric | Result |
|---|---:|
| HTTP Requests | 4001 |
| Success | 100% |
| Error | 0% |
| Avg | 5.16 ms |
| p95 | 23.58 ms |
| p99 | 74.55 ms |
| Max | 148.20 ms |
| Hikari acquire avg | 1.23 ms |
| VU max | 34 |
| Hikari active observed | 2 |
| Hikari pending observed | 0 |
| Dropped iterations | 0 |

### 해석

Tail latency가 증가한 실행이 관찰되었지만:

```text
Hikari active ≈ 2
pending = 0
```

이었으므로 Hikari pool saturation으로 판단하지 않았다.

또한 local benchmark의 run-to-run variance가 존재했기 때문에  
400 RPS 결과 하나만으로 saturation onset을 확정하지 않았다.

---

# 9. 600 RPS — Load Generator 설정 검증

초기 600 RPS 테스트는 다음 설정을 사용했다.

```javascript
preAllocatedVUs: 100,
maxVUs: 300,
```

이 실행에서는:

```text
p95 = 152.59 ms
p99 = 257.55 ms

dropped_iterations = 56
VU max = 91
```

등 비정상적으로 큰 latency가 관찰되었다.

하지만 테스트 중 k6가 VU를 동적으로 추가해야 했고,  
k6와 Spring Boot가 동일 머신에서 실행되고 있었기 때문에 load generator 자체의 자원 사용이 결과에 영향을 줄 가능성이 있었다.

따라서 이 결과를 capacity baseline에서 제외하였다.

---

# 10. 600 RPS — 재검증

Load generator 변수를 줄이기 위해:

```javascript
preAllocatedVUs: 300,
maxVUs: 300,
```

으로 변경하였다.

## Run 1

| Metric | Result |
|---|---:|
| Requests | 6001 |
| Success | 100% |
| Error | 0% |
| Avg | 2.06 ms |
| p95 | 5.17 ms |
| p99 | 18.11 ms |
| Max | 32.41 ms |
| Hikari acquire avg | 0.044 ms |
| VU max | 2 |
| Hikari pending | 0 |
| Dropped iterations | 0 |

## Run 2

| Metric | Result |
|---|---:|
| Requests | 6001 |
| Success | 100% |
| Error | 0% |
| Avg | 2.05 ms |
| p95 | 4.81 ms |
| p99 | 16.89 ms |
| Max | 31.89 ms |
| Hikari acquire avg | 0.058 ms |
| VU max | 3 |
| Hikari active peak observed | 7 |
| Hikari pending | 0 |
| Dropped iterations | 0 |

### 결론

두 번의 반복 실험에서 거의 동일한 결과가 재현되었다.

따라서 현재 환경에서:

```text
600 RPS = 안정 처리 구간
```

으로 판단하였다.

---

# 11. 800 RPS — Hikari Contention Onset

```text
800 requests/sec
10 sec
```

결과:

| Metric | Result |
|---|---:|
| Requests | 8000 |
| Success | 100% |
| Error | 0% |
| Avg | 3.71 ms |
| p95 | 14.96 ms |
| p99 | 54.65 ms |
| Max | 124.95 ms |
| Hikari acquire avg | 1.04 ms |
| VU max | 52 |
| Hikari active peak | **10** |
| Hikari pending peak | **1** |
| Dropped iterations | 0 |

Hikari 설정:

```text
maximumPoolSize = 10
```

실제로:

```text
active = 10
pending = 1
```

이 관측되었다.

이는 어느 순간:

```text
DB connection 10개 모두 사용
+
추가 thread가 connection 반환을 기다림
```

상태가 발생했다는 의미이다.

### 결론

800 RPS에서는 모든 요청을 성공적으로 처리했지만  
**Hikari connection pool contention이 처음 직접 관찰되었다.**

따라서 이 구간을:

```text
Contention Onset
```

으로 정의하였다.

---

# 12. 1000 RPS — Saturation

```text
1000 requests/sec
10 sec
```

결과:

| Metric | Result |
|---|---:|
| Target rate | 1000 RPS |
| Actual HTTP rate | 984.86 RPS |
| HTTP Requests | 9858 |
| HTTP Success | 100% |
| HTTP Error | 0% |
| Avg | 45.11 ms |
| p95 | 254.67 ms |
| p99 | 530.12 ms |
| Max | 1.29 s |
| Hikari acquire avg | **32.4 ms** |
| Hikari acquire max observed | 1.288 s |
| Hikari active peak | **10** |
| Hikari pending peak | **189** |
| VU max | 260 |
| VU limit | 300 |
| Dropped iterations | **145** |

k6 warning:

```text
Insufficient VUs, reached 300 active VUs and cannot initialize more
```

---

## 12.1 Connection Pool Saturation

Hikari는:

```text
active = 10
pending = 189
```

까지 관찰되었다.

현재:

```text
maximumPoolSize = 10
```

이므로 connection 10개가 모두 사용 중인 상태에서  
최대 189개의 request thread가 connection 반환을 기다리고 있었던 것이다.

구조적으로:

```text
Incoming Requests
       ↓
Tomcat Workers
       ↓
@Transactional
       ↓
HikariCP

10 threads
→ DB connection 사용

189 threads
→ connection 대기
```

상태가 발생한 것으로 해석할 수 있다.

---

## 12.2 Tail Latency Explosion

800 RPS:

```text
p95 = 14.96 ms
p99 = 54.65 ms
```

1000 RPS:

```text
p95 = 254.67 ms
p99 = 530.12 ms
```

즉:

```text
p95 약 17배 증가
p99 약 9.7배 증가
```

하였다.

또한:

```text
HTTP max = 1.29 s
Hikari acquire max ≈ 1.288 s
```

가 관찰되었다.

Request-level로 두 metric이 정확히 동일 요청을 의미한다고 단정할 수는 없지만,  
connection acquisition delay가 tail latency 증가에 크게 기여했다는 강한 근거이다.

---

## 12.3 Dropped Iterations

1000 RPS에서는:

```text
executed iterations = 9858
dropped iterations  = 145
```

였다.

대략:

```text
9858 + 145 = 10003 scheduled iterations
```

중 일부 iteration을 k6가 시작하지 못했다.

여기서:

```text
checks_succeeded = 100%
```

는 **실제로 전송된 HTTP 요청이 모두 성공했다는 의미**이지,

```text
1000 RPS workload 전체를 정상 처리했다
```

는 의미가 아니다.

이번에는:

```text
preAllocatedVUs = 300
maxVUs = 300
```

으로 load-generator 동적 VU 생성 변수를 제거한 상태였기 때문에,  
SUT latency 증가로 VU가 장시간 점유되면서 arrival schedule을 완전히 유지하지 못한 것으로 판단하였다.

---

# 13. 최종 결과 요약

| Rate | Success | Avg | p95 | p99 | Hikari Acquire Avg | Active Peak | Pending Peak | Dropped |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 50 | 100% | 7.77 ms | 11.56 ms | 19.02 ms | 0.046 ms | - | - | 0 |
| 100 | 100% | 4.90 ms | 8.15 ms | 16.93 ms | 0.014 ms | - | - | 0 |
| 200 | 100% | 2.92 ms | 4.00 ms | 7.97 ms | 0.008 ms | - | - | 0 |
| 600 | 100% | 2.05 ms | 4.81 ms | 16.89 ms | 0.058 ms | 7 | 0 | 0 |
| **800** | **100%** | **3.71 ms** | **14.96 ms** | **54.65 ms** | **1.04 ms** | **10** | **1** | **0** |
| **1000** | **100%*** | **45.11 ms** | **254.67 ms** | **530.12 ms** | **32.4 ms** | **10** | **189** | **145** |

\* HTTP success 100%는 실제로 전송된 9858건 기준이다.  
145개의 scheduled iteration은 HTTP request가 되기 전에 dropped되었다.

400 RPS는 run-to-run variance가 존재하여 최종 capacity 판단 표에서는 제외하였다.

---

# 14. Saturation 구간 해석

실험 결과 시스템은 다음 세 구간으로 나눌 수 있었다.

```text
≤ 600 RPS
│
├─ Stable Region
│  - Error 0
│  - Dropped 0
│  - Hikari pending 0
│  - Low latency
│
800 RPS
│
├─ Contention Onset
│  - Hikari active = 10
│  - pending = 1
│  - tail latency 증가
│  - 전체 request 처리 성공
│
1000 RPS
│
└─ Saturation Region
   - Hikari active = 10
   - pending = 189
   - Hikari acquire latency 급증
   - p95 / p99 폭증
   - VU 장시간 점유
   - dropped iteration 발생
```

따라서 현재 환경에서는:

```text
Saturation knee ≈ 800 ~ 1000 RPS 사이
```

에 존재한다고 판단하였다.

정확한 maximum throughput을 산출하려면 850 / 900 / 950 RPS 등의 추가 실험이 필요하지만,  
Phase 5의 목적은 최대 TPS benchmark 자체가 아니라 **Waiting Queue / Admission Control 도입 필요성 검증**이므로 여기서 Queue OFF baseline을 종료한다.

---

# 15. Waiting Queue 도입 근거

현재 Queue OFF 구조에서는 모든 예약 요청이 즉시 Reservation Service와 DB transaction으로 진입한다.

```text
Traffic
  ↓
Tomcat
  ↓
Reservation Transaction
  ↓
HikariCP
  ↓
PostgreSQL
```

트래픽이 안정 구간을 넘어가면:

```text
Traffic 증가
  ↓
DB connection 10개 전부 사용
  ↓
Hikari pending 증가
  ↓
request thread 대기
  ↓
tail latency 증가
  ↓
더 많은 동시 request
  ↓
resource contention 심화
```

의 형태로 queueing이 발생한다.

특히 1000 RPS에서:

```text
pending peak = 189
p95 = 254.67 ms
p99 = 530.12 ms
dropped iterations = 145
```

가 관찰되었다.

따라서 다음 단계에서는 모든 요청을 DB로 직접 전달하는 대신,

```text
Client
  ↓
Waiting Queue
  ↓
Admission Control
  ↓
허용된 traffic만
  ↓
Reservation Service
  ↓
PostgreSQL
```

구조를 도입하여 **DB가 안정적으로 처리할 수 있는 수준으로 write admission을 제한**한다.

---

# 16. Phase 5.1 결론

Phase 5.1에서 Queue OFF 상태의 예약 write path를 부하 테스트한 결과:

1. 600 RPS까지는 안정적으로 처리하였다.
2. 800 RPS에서 Hikari pool의 모든 10개 connection이 사용되고 pending request가 처음 관측되었다.
3. 1000 RPS에서는 pending이 최대 189까지 증가하였다.
4. Hikari average acquire time은 약 32.4ms까지 증가하였다.
5. HTTP p95는 254.67ms, p99는 530.12ms까지 상승하였다.
6. k6는 300 VU를 사용하면서도 145개의 iteration을 시작하지 못했다.
7. 따라서 **800~1000 RPS 사이에서 현재 Queue OFF 구조의 saturation knee가 존재함을 확인했다.**

이를 Phase 5 Waiting Queue / Admission Control 도입의 실험적 근거로 사용한다.

다음 단계:

```text
Phase 5.2
Redis Waiting Queue 구조 설계
```

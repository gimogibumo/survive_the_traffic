# Ticketing: Survive the Traffic — Phase 5
## Waiting Queue / Traffic Admission Control

---

## 0. Phase 5의 목적

Phase 5의 목표는 단순히 Redis를 이용해 대기열을 구현하는 것이 아니다.

현재 시스템은 사용자의 예약 요청을 별도의 admission control 없이 즉시 Spring Boot와 PostgreSQL까지 전달한다.

```text
Users
  ↓
Spring Boot
  ↓
Reservation Service
  ↓
HikariCP
  ↓
PostgreSQL
  ↓
Transaction / Lock
```

이 구조에서는 짧은 시간에 많은 예약 요청이 몰릴 경우 다음과 같은 문제가 발생할 수 있다.

- 애플리케이션 Thread 증가 및 대기
- HikariCP Connection Pool 경쟁
- PostgreSQL Connection 사용량 증가
- Transaction 증가
- Row Lock 경쟁
- Lock Wait 증가
- 응답 지연 증가
- Timeout 가능성 증가
- 전체 서비스의 성능 저하

따라서 Phase 5에서는 먼저 **대기열이 없는 현재 시스템의 한계**를 재현하고 측정한다.

그 다음 Redis 기반 Waiting Queue를 도입하여,

> 모든 사용자를 동시에 DB까지 진입시키는 방식과  
> 제한된 수의 사용자만 예약 시스템으로 진입시키는 방식

을 비교한다.

Phase 5의 핵심 질문은 다음과 같다.

> **대량 사용자가 동시에 예약을 시도할 때, Redis 기반 Waiting Queue가 DB로 유입되는 순간 트래픽을 제한하여 시스템 부하와 latency를 실제로 줄일 수 있는가?**

---

# 1. Phase 5의 위치

현재 프로젝트의 전체 흐름은 다음과 같다.

```text
Phase 1
Single Request Correctness
        ↓
Phase 2
Concurrent Reservation Control
        ↓
Phase 3
Load Test & Bottleneck Analysis
        ↓
Phase 4
Redis Cache / Read Load Reduction
        ↓
Phase 5
Waiting Queue / Traffic Admission Control
        ↓
Phase 6
Kafka / Async Event
```

Phase 4에서는 반복적인 Read 요청을 Redis로 offload하여 PostgreSQL의 Read 부하를 줄였다.

Phase 5에서는 Read가 아니라 **Write 요청의 유입량 자체**를 제어한다.

```text
Phase 4

많은 Read
→ Redis Cache
→ DB Read 감소
```

```text
Phase 5

많은 Write 진입 요청
→ Waiting Queue
→ DB에 진입하는 사용자 수 제한
```

---

# 2. Phase 5에서 지켜야 할 원칙

## 2.1 Redis Queue부터 만들지 않는다

대기열은 반드시 문제를 관찰한 뒤 도입한다.

먼저 다음 구조에서 실제 병목을 확인한다.

```text
Queue OFF

Users
  ↓
Spring Boot
  ↓
Reservation API
  ↓
DB
```

그 다음 Queue를 추가한다.

```text
Queue ON

Users
  ↓
Waiting Queue
  ↓
Admission Control
  ↓
Reservation API
  ↓
DB
```

## 2.2 동일 좌석 경쟁과 시스템 부하를 분리한다

Phase 2에서는 동일 좌석에 대한 동시 요청을 이용해 Race Condition과 Lock을 실험했다.

Phase 5에서는 목적이 다르다.

동일 좌석 하나에 1000명이 몰리게 하면 대부분의 요청이 Seat Lock과 Business Conflict에서 종료된다.

그러면 측정되는 값이 다음 문제에 오염된다.

```text
Queue 효과
+
Seat Lock 경쟁
+
중복 예약 실패
```

따라서 Phase 5의 Queue OFF baseline에서는 가능한 한 **서로 다른 좌석을 대상으로 예약 요청을 분산**시킨다.

목표는 다음 경로 자체의 부하를 확인하는 것이다.

```text
Reservation Request
→ Spring Thread
→ Hikari Connection
→ PostgreSQL Transaction
```

## 2.3 PostgreSQL은 여전히 Source of Truth다

Redis Waiting Queue가 다음을 결정하면 안 된다.

```text
좌석 최종 예약 성공 여부
결제 성공 여부
좌석 정합성
```

최종 예약 상태와 좌석 정합성은 기존과 동일하게 PostgreSQL Transaction이 책임진다.

Redis의 역할은 다음뿐이다.

```text
누가
언제
예약 시스템 안으로 진입할 수 있는가
```

즉 Redis는 **Traffic Admission Layer**다.

---

# 3. Phase 5 Architecture

## Queue OFF

```text
                    ┌─────────────────────┐
Users ────────────▶ │ Spring Boot         │
                    │ Reservation API     │
                    └─────────┬───────────┘
                              │
                              ▼
                    ┌─────────────────────┐
                    │ HikariCP            │
                    │ Connection Pool     │
                    └─────────┬───────────┘
                              │
                              ▼
                    ┌─────────────────────┐
                    │ PostgreSQL          │
                    │ Transaction / Lock  │
                    └─────────────────────┘
```

## Queue ON

```text
                    ┌─────────────────────┐
Users ────────────▶ │ Waiting API         │
                    └─────────┬───────────┘
                              │
                              ▼
                    ┌─────────────────────┐
                    │ Redis               │
                    │ Waiting Queue       │
                    └─────────┬───────────┘
                              │
                       Admission Control
                              │
                              ▼
                    ┌─────────────────────┐
                    │ Reservation API     │
                    └─────────┬───────────┘
                              │
                              ▼
                    ┌─────────────────────┐
                    │ PostgreSQL          │
                    └─────────────────────┘
```

---

# 4. Step 5.1 — Queue OFF Baseline

## 목표

대기열이 없는 현재 구조에서 많은 예약 요청이 동시에 들어왔을 때 시스템이 어떻게 변하는지 측정한다.

## 4.1 테스트 대상

```http
POST /api/reservations
```

예시 Request:

```json
{
  "userId": 1,
  "seatId": 101
}
```

실제 DTO 필드명은 현재 프로젝트 구현에 맞춘다.

## 4.2 좌석 준비

Queue 효과와 동일 좌석 Lock contention을 구분하기 위해 충분한 수의 AVAILABLE 좌석을 준비한다.

예:

```text
Concert 1

Seat 1
Seat 2
Seat 3
...
Seat 1000
```

각 VU 또는 iteration마다 다른 seatId를 사용하도록 한다.

필요하면 테스트 전용 seed data를 만든다.

## 4.3 k6 테스트

예시 조건:

```text
100 VU
30 sec
```

이후:

```text
200 VU
500 VU
```

로 단계적으로 증가한다.

처음부터 지나치게 큰 부하를 넣지 않는다.

## 4.4 측정 항목

### HTTP

- total requests
- RPS
- avg latency
- p95
- p99
- max
- error rate

### Application

- Tomcat active thread
- request queue
- JVM CPU
- JVM memory

### HikariCP

- active connections
- idle connections
- pending threads
- connection acquisition time

### PostgreSQL

- active sessions
- transaction 수
- query latency
- lock wait
- connection 사용량

## 4.5 Queue OFF Baseline 기록

| VU | RPS | Avg | p95 | p99 | Error | Hikari Active | Hikari Pending |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | | | | | | | |
| 200 | | | | | | | |
| 500 | | | | | | | |

결과는 실제 측정 후 입력한다.

---

# 5. Step 5.2 — Queue 필요성 판단

Queue를 도입하기 전에 Queue OFF 실험 결과를 해석한다.

다음과 같은 현상이 관찰되는지 확인한다.

```text
VU 증가
↓
RPS 증가폭 둔화
↓
p95 / p99 급증
```

또는:

```text
Hikari Active
→ maxPoolSize 도달

Hikari Pending
→ 증가
```

또는:

```text
PostgreSQL Connection 증가
Transaction 증가
Lock Wait 증가
```

Queue 도입의 논리는 다음과 같이 만들어야 한다.

> 예약 트래픽 증가 시 모든 요청을 즉시 DB Transaction으로 진입시키면서 Hikari Connection과 PostgreSQL Transaction 수가 동시에 증가했다. 특정 동시성 이후 처리량 증가 없이 p95/p99와 connection waiting이 증가하는 포화 구간을 확인했다. 따라서 DB가 감당 가능한 수준으로 예약 시스템 진입량을 제한하기 위해 Waiting Queue를 도입한다.

실험 결과가 Queue 필요성을 뒷받침하지 않는다면 억지로 결론을 만들지 않는다.

---

# 6. Step 5.3 — Waiting Queue 데이터 구조 선정

Waiting Queue는 Redis를 사용한다.

후보:

```text
Redis List
Redis Sorted Set
Redis Stream
```

Phase 5에서는 우선 **Redis Sorted Set**을 후보로 한다.

## 6.1 Sorted Set

예:

```redis
ZADD waiting:concert:1 1729000000000 user:100
ZADD waiting:concert:1 1729000000100 user:101
```

구조:

```text
KEY
waiting:concert:{concertId}

MEMBER
userId 또는 queueToken

SCORE
입장 요청 시점 / sequence
```

Sorted Set을 사용하면:

```redis
ZRANK waiting:concert:1 user:100
```

으로 대기 순서를 얻을 수 있다.

## 6.2 Queue Key

예:

```text
waiting:concert:1
```

공연별 Queue를 분리한다.

```text
waiting:concert:1
waiting:concert:2
waiting:concert:3
```

이를 통해 한 공연의 폭발적인 트래픽이 다른 공연 Queue와 논리적으로 분리된다.

---

# 7. Step 5.4 — Queue Join

새 API 후보:

```http
POST /api/queue/concerts/{concertId}
```

사용자가 Queue에 들어가면 서버는 queue token을 발급한다.

예:

```json
{
  "queueToken": "..."
}
```

Redis:

```text
waiting:concert:{concertId}
```

에 해당 사용자를 추가한다.

## 중복 진입

같은 사용자가 refresh 또는 재시도를 반복할 수 있다.

```text
User A

Join
Join
Join

→ Queue에 3번 들어감
```

따라서 중복 Queue entry 방지가 필요하다.

Phase 5에서는 동일 user/token 중복 여부를 확인한다.

---

# 8. Step 5.5 — Queue Position 조회

API 후보:

```http
GET /api/queue/concerts/{concertId}/status
```

Response 예:

```json
{
  "position": 1542,
  "status": "WAITING"
}
```

현재 Phase 5에서는 사용자 UI나 SSE까지 구현하지 않아도 된다.

Phase 5의 목적은 **Waiting Queue 기술 검증**이다.

실시간 사용자 경험은 이후 Phase 8에서 확장한다.

---

# 9. Step 5.6 — Admission Control

Queue의 핵심은 사용자를 줄 세우는 것이 아니라:

> **동시에 몇 명을 실제 예약 시스템 안으로 진입시키는가**

이다.

예:

```text
Waiting Users = 10,000

Admission Rate
= 100 users / second
```

그러면 DB는 한 순간에 10,000개의 Transaction을 받는 대신 제한된 속도로 요청을 받는다.

## Admission 구조

```text
Waiting Queue
       ↓
Admission
       ↓
Active Users
       ↓
Reservation API
```

Redis Key 예:

```text
waiting:concert:1
active:concert:1
```

---

# 10. Step 5.7 — Admission Token

Queue를 통과한 사용자는 예약 API를 호출할 수 있는 token을 받는다.

예:

```text
admission:{token}
```

TTL:

```text
30 sec
60 sec
```

등으로 제한한다.

흐름:

```text
User
↓
Queue Join
↓
WAITING
↓
Admission
↓
Token 발급
↓
Reservation API
↓
Token 검증
↓
DB Transaction
```

# 11. Admission Token이 필요한 이유

Queue에서 순번만 통과시킨 뒤 아무 검증 없이 Reservation API를 열어두면 사용자는 Queue를 우회할 수 있다.

```text
Client
→ POST /api/reservations 직접 호출
```

따라서 Reservation API에서:

```text
유효한 Admission Token이 있는가?
```

를 확인해야 한다.

---

# 12. Step 5.8 — Queue ON Load Test

Queue OFF와 동일한 요청량을 발생시킨다.

단 차이는:

```text
Queue OFF
모든 요청 → Reservation API

Queue ON
모든 요청 → Waiting Queue
일부 요청만 → Reservation API
```

## 비교 지표

| Metric | Queue OFF | Queue ON |
|---|---:|---:|
| Incoming Users | | |
| Reservation API RPS | | |
| Avg Latency | | |
| p95 | | |
| p99 | | |
| Error Rate | | |
| Hikari Active | | |
| Hikari Pending | | |
| PostgreSQL Active Connection | | |
| Lock Wait | | |

---

# 13. 핵심 실험

예:

```text
1000 concurrent users
```

## Queue OFF

```text
1000
↓
Reservation API
↓
DB
```

## Queue ON

Admission limit:

```text
100 concurrent users
```

```text
1000
↓
Redis Queue
↓
100
↓
Reservation API
↓
DB
```

목표는 반드시:

```text
Queue ON이 더 빠르다
```

가 아니다.

오히려 일부 사용자는 Queue에서 기다리므로 전체 사용자 완료시간은 길어질 수 있다.

중요한 목표는:

```text
DB peak load 제한
connection contention 감소
tail latency 안정화
error 감소
```

이다.

---

# 14. Queue의 핵심 Trade-off

Waiting Queue는 성능을 공짜로 높이는 기술이 아니다.

본질은:

> **즉시 처리할 수 없는 요청을 기다리게 하거나 일부 요청을 제한해서 downstream 시스템을 보호하는 backpressure / load shedding 계층**

에 가깝다.

## Queue OFF

장점:

```text
바로 예약 시도
```

단점:

```text
Traffic Spike가 DB까지 그대로 전달
```

## Queue ON

장점:

```text
DB Load 제한
Latency 안정화
Connection Pool 보호
```

단점:

```text
사용자 대기시간 발생
Redis 의존성 증가
Queue 상태 관리 필요
복구 로직 필요
```

---

# 15. Redis 장애

Phase 4에서 Cache는 Redis가 죽어도 PostgreSQL fallback이 가능했다.

하지만 Waiting Queue는 성격이 다르다.

Cache:

```text
Redis 장애
→ DB fallback 가능
```

Queue:

```text
Redis 장애
→ Admission 상태 자체를 판단하기 어려움
```

따라서 Queue Redis 장애 시 어떤 정책을 사용할지 결정해야 한다.

후보:

```text
Fail Open
Queue 우회 후 예약 API 허용
```

```text
Fail Closed
예약 시스템 진입 차단
```

티켓팅처럼 Traffic Spike 보호가 Queue의 핵심 목적이라면 무조건 Fail Open이 안전하다고 볼 수 없다.

실험과 시스템 요구사항을 바탕으로 결정한다.

---

# 16. 중복 요청

Queue 시스템에서는 다음 문제를 확인한다.

```text
동일 사용자 Queue Join 반복
```

```text
Admission Token 중복 사용
```

```text
Reservation API 재시도
```

Phase 5에서는 최소한 다음을 확인한다.

- Queue 중복 진입 여부
- Admission Token 재사용 여부
- Admission Token TTL
- 만료 Token 처리

---

# 17. Queue Fairness

기본 정책:

```text
FIFO
```

즉 먼저 Queue에 들어온 사용자에게 먼저 Admission 기회를 준다.

Sorted Set에서는 Score를 이용해 순서를 유지한다.

단, 동일 timestamp 충돌 가능성도 고려한다.

필요하면:

```text
timestamp + sequence
```

또는 별도의 monotonic sequence를 검토한다.

---

# 18. 테스트해야 할 Failure Scenario

## Scenario 1

```text
Queue Join 성공
→ 사용자가 브라우저 종료
```

stale waiting entry 처리 필요.

## Scenario 2

```text
Admission 성공
→ 사용자가 Reservation API 호출하지 않음
```

Admission Token TTL로 회수.

## Scenario 3

```text
Reservation API 호출 중 실패
```

재시도 정책 검토.

## Scenario 4

```text
Redis Restart
```

Queue 상태가 어떻게 되는지 확인.

## Scenario 5

```text
동일 User가 여러 Client에서 Join
```

중복 정책 확인.

---

# 19. Phase 5와 Phase 8의 차이

Phase 5에서는 Waiting Queue의 **backend 기술 효과**를 검증한다.

즉:

```text
Queue가 실제 DB Load를 줄이는가?
Admission Rate를 제어할 수 있는가?
```

가 핵심이다.

Phase 8에서는 이를 실제 사용자 서비스로 확장한다.

Phase 8:

```text
대기 순번
실시간 SSE
Admission Token
Reconnect
Recovery
UX
운영 정책
```

따라서 Phase 5에서 지나치게 완성된 Queue Platform을 만들 필요는 없다.

---

# 20. Phase 5 실험 순서

```text
Step 5.1
Queue OFF Reservation Load Test
        ↓
Step 5.2
System Saturation / DB Load 분석
        ↓
Step 5.3
Redis Waiting Queue 구조 선정
        ↓
Step 5.4
Queue Join
        ↓
Step 5.5
Queue Position
        ↓
Step 5.6
Admission Control
        ↓
Step 5.7
Admission Token
        ↓
Step 5.8
Queue ON Load Test
        ↓
Step 5.9
Queue OFF vs ON 비교
        ↓
Step 5.10
Failure / Duplicate / Expiration 실험
```

---

# 21. Phase 5 핵심 Before / After

## Before

```text
Traffic Spike
      ↓
Spring Boot
      ↓
HikariCP
      ↓
PostgreSQL
```

모든 Traffic이 downstream으로 그대로 전달된다.

## After

```text
Traffic Spike
      ↓
Redis Waiting Queue
      ↓
Admission Control
      ↓
Controlled Traffic
      ↓
Spring Boot
      ↓
PostgreSQL
```

---

# 22. Phase 5 결과 기록 Template

## Queue OFF

```text
Concurrent Users:

Reservation RPS:

Average Latency:

p95:

p99:

Error Rate:

Hikari Active:

Hikari Pending:

PostgreSQL Active Connections:

Lock Wait:
```

## Queue ON

```text
Concurrent Users:

Admission Limit:

Reservation RPS:

Average Latency:

p95:

p99:

Error Rate:

Hikari Active:

Hikari Pending:

PostgreSQL Active Connections:

Lock Wait:
```

## Comparison

```text
DB Peak Load:

Hikari Pending:

p95:

p99:

Error Rate:

Admission Stability:
```

---

# 23. Phase 5 완료 조건

```text
[ ] Queue OFF 예약 부하 Baseline 측정

[ ] 여러 좌석으로 요청을 분산하여
    동일 좌석 Lock contention과
    시스템 부하를 분리

[ ] VU 증가에 따른
    throughput / latency / error 분석

[ ] HikariCP 상태 관찰

[ ] PostgreSQL connection / transaction 관찰

[ ] Waiting Queue 도입 근거 확보

[ ] Redis Waiting Queue 구현

[ ] Queue Join 구현

[ ] 중복 Queue Entry 정책 구현

[ ] Queue Position 조회

[ ] Admission Control 구현

[ ] Admission Token 구현

[ ] Token TTL 적용

[ ] Queue 우회 방지

[ ] Queue ON 부하 테스트

[ ] Queue OFF vs Queue ON 비교

[ ] DB Peak Load 감소 여부 확인

[ ] tail latency 안정화 여부 확인

[ ] Redis 장애 시 Queue 정책 검토

[ ] Queue의 Trade-off 설명 가능

[ ] Phase 8에서 production-grade queue로
    확장해야 할 항목 정리
```

---

# 24. Phase 5에서 반드시 설명할 수 있어야 하는 개념

- Waiting Queue
- Admission Control
- Backpressure
- Load Shedding
- Redis Sorted Set
- FIFO
- Queue Position
- Admission Token
- TTL
- Duplicate Request
- Idempotency
- HikariCP
- Connection Pool Saturation
- PostgreSQL Transaction
- Lock Contention
- Fail Open
- Fail Closed
- Traffic Spike
- Tail Latency
- Queue Fairness
- Single Instance / Multi Instance 차이

---

# 25. 면접에서 설명할 수 있는 최종 흐름

Phase 5가 완료되면 다음 수준으로 설명할 수 있어야 한다.

> 예약 트래픽이 순간적으로 증가할 때 모든 요청을 즉시 DB Transaction으로 진입시키면 Connection Pool과 PostgreSQL이 동시에 부하를 받는 구조였습니다. 먼저 Queue가 없는 상태에서 여러 좌석에 예약 요청을 분산해 부하 테스트를 수행하고, 동시성 증가에 따른 처리량, p95/p99, Hikari Connection 경쟁과 DB 부하를 측정했습니다. 이후 Redis 기반 Waiting Queue와 Admission Control을 적용해 실제 예약 시스템으로 진입하는 요청량을 제한했습니다. Queue 적용 전후를 동일한 부하 조건에서 비교하여 DB peak load와 tail latency가 어떻게 변하는지 검증했습니다. Redis Queue는 예약 정합성을 담당하지 않고 트래픽 진입량만 제어하며, 최종 좌석 상태와 예약 정합성은 PostgreSQL Transaction이 계속 담당하도록 역할을 분리했습니다.

---

# 26. 다음 시작 지점

Phase 5의 첫 실제 작업은 다음이다.

```text
Step 5.1
Queue OFF Reservation Load Test
```

바로 Redis Queue를 구현하지 않는다.

먼저:

```text
POST /api/reservations
```

를 대상으로 충분한 AVAILABLE 좌석을 준비하고,

```text
100 VU
200 VU
500 VU
```

처럼 부하를 단계적으로 증가시키면서 현재 시스템이 어느 지점에서 포화되는지 측정한다.

그 결과가 Phase 5에서 Waiting Queue를 도입하는 실제 근거가 된다.

# Ticketing: Survive the Traffic — Phase 2

## 1. Phase 2 개요

### 1.1 목표

Phase 2의 목표는 **동일한 좌석에 여러 사용자가 동시에 예약 요청을 보냈을 때 발생하는 동시성 문제를 직접 재현하고 해결하는 것**이다.

Phase 1에서는 다음과 같은 단순한 구조를 구현했다.

```text
Client
  │
  ▼
Spring Boot
  │
  ▼
PostgreSQL
```

단일 요청 환경에서는 정상적으로 동작한다.

```text
Seat

AVAILABLE
   ↓
 HELD
   ↓
 SOLD
```

하지만 실제 티켓팅 서비스에서는 하나의 좌석에 여러 사용자가 동시에 접근할 수 있다.

예:

```text
Seat A-1

status = AVAILABLE
```

예매가 오픈되는 순간:

```text
User A ─┐
User B ─┼── 동시에 Seat A-1 예약 요청
User C ─┤
User D ─┤
 ...    │
User Z ─┘
```

정상적인 시스템에서는:

```text
1명 성공

나머지 사용자 실패
```

해야 한다.

Phase 2에서는 이 조건을 보장하도록 시스템을 개선한다.

---

## 1.2 Phase 2 핵심 질문

Phase 2에서는 다음 질문에 답할 수 있어야 한다.

```text
@Transactional을 붙였는데
왜 중복 예약이 발생할 수 있는가?
```

```text
두 Transaction이 동시에
같은 데이터를 읽으면 어떤 일이 발생하는가?
```

```text
Database Lock은 언제 획득되고
언제 해제되는가?
```

```text
Pessimistic Lock과
Optimistic Lock의 차이는 무엇인가?
```

```text
티켓팅처럼 충돌이 많은 상황에서는
어떤 Lock 전략이 적절한가?
```

```text
Application 코드뿐 아니라
Database Constraint도 필요한 이유는 무엇인가?
```

---

# 2. Phase 2 시스템 구조

Phase 2에서도 아직 시스템 구조는 단순하게 유지한다.

```text
Concurrent Clients
       │
       │
       ▼
┌─────────────────┐
│   Spring Boot   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   PostgreSQL    │
└─────────────────┘
```

아직 다음 기술은 사용하지 않는다.

```text
Redis

Kafka

Distributed Lock

Waiting Queue

Load Balancer

Scale-out
```

먼저 **하나의 Spring Boot 서버 + 하나의 PostgreSQL DB에서도 발생하는 동시성 문제**를 이해하는 것이 목적이다.

---

# 3. Phase 2 범위

## 구현 및 학습 대상

```text
동시성 테스트

Race Condition 재현

Check-Then-Act 문제 분석

Database Transaction Isolation

PostgreSQL READ COMMITTED

Row Lock

Pessimistic Lock

Optimistic Lock

JPA @Version

Lock Timeout

Deadlock 기본 개념

Database Constraint

동시성 제어 전략 비교
```

---

## Phase 2에서 아직 구현하지 않는 기능

```text
Redis Distributed Lock

Redis Cache

Redis Waiting Queue

Kafka

Event Driven Architecture

Server Scale-out

Load Balancer

실제 PG 결제

Saga

Outbox Pattern

Idempotency Key

Monitoring

Distributed Tracing

Chaos Engineering
```

이 기술들은 이후 Phase에서 현재 구조의 한계가 드러난 뒤 도입한다.

---

# 4. Phase 1의 현재 예약 로직

현재 좌석 예약 로직은 개념적으로 다음과 같다.

```java
@Transactional
public ReservationResponse createReservation(
        ReservationRequest request
) {

    User user = userRepository.findById(request.userId())
        .orElseThrow(...);

    Seat seat = seatRepository.findById(request.seatId())
        .orElseThrow(...);

    seat.hold();

    Reservation reservation = ...

    reservationRepository.save(reservation);

    return ...;
}
```

`Seat.hold()`는 다음과 같은 역할을 한다.

```java
public void hold() {

    if (status != SeatStatus.AVAILABLE) {
        throw new SeatNotAvailableException();
    }

    status = SeatStatus.HELD;
}
```

논리적으로 보면:

```text
Seat 읽기

↓

AVAILABLE인지 확인

↓

HELD로 변경
```

즉:

```text
READ

↓

CHECK

↓

UPDATE
```

구조다.

---

# 5. Check-Then-Act Race Condition

현재 코드는 전형적인 **Check-Then-Act** 구조다.

```text
Check

Seat가 AVAILABLE인가?

↓

Act

AVAILABLE이면 HELD로 변경
```

단일 요청에서는 문제가 없다.

하지만 두 요청이 동시에 들어오면 문제가 생긴다.

---

# 6. Race Condition 예시

초기 상태:

```text
Seat #1

status = AVAILABLE
```

두 사용자가 거의 동시에 예약 요청을 보낸다.

```text
Transaction A                Transaction B

Seat 조회
AVAILABLE

                             Seat 조회
                             AVAILABLE

AVAILABLE 확인
성공

                             AVAILABLE 확인
                             성공

Seat → HELD

                             Seat → HELD

Reservation A 생성

                             Reservation B 생성

COMMIT                       COMMIT
```

최종 DB:

```text
Seat #1

status = HELD
```

그런데 Reservation은:

```text
Reservation #1
seat_id = 1
status = PENDING
```

```text
Reservation #2
seat_id = 1
status = PENDING
```

처럼 여러 개 생성될 수 있다.

---

# 7. 핵심 문제

Seat의 최종 상태만 보면:

```text
HELD
```

이므로 정상처럼 보일 수 있다.

하지만 실제 invariant는:

> 하나의 Seat에는 동시에 하나의 활성 Reservation만 존재해야 한다.

이다.

즉 다음 상태는 잘못된 상태다.

```text
Seat #1

HELD
```

```text
Reservation A
PENDING

Reservation B
PENDING
```

좌석 하나가 두 사용자에게 동시에 예약된 것이다.

---

# 8. `@Transactional`은 왜 해결하지 못하는가

Phase 1에서 다음과 같이 Transaction을 적용했다.

```java
@Transactional
public ReservationResponse createReservation(...) {
    ...
}
```

하지만:

```text
@Transactional
```

의 의미는:

> 해당 메서드 내부의 DB 작업을 하나의 Transaction으로 묶는다.

이다.

다음을 의미하는 것은 아니다.

> 다른 Transaction은 이 데이터에 접근할 수 없다.

---

# 9. 두 Transaction은 동시에 존재할 수 있다

예:

```text
Transaction A
BEGIN

Transaction B
BEGIN
```

둘 다 동시에 실행될 수 있다.

```text
Transaction A          Transaction B

SELECT Seat            SELECT Seat

CHECK                   CHECK

UPDATE                  UPDATE

COMMIT                  COMMIT
```

따라서:

```text
@Transactional
```

만으로는 동시성 제어가 되지 않는다.

---

# 10. Transaction과 Lock의 차이

Transaction은:

```text
여러 DB 작업을
하나의 논리적인 작업 단위로 묶는다.
```

예:

```text
Seat HELD 변경

+

Reservation 생성
```

둘 중 하나가 실패하면:

```text
ROLLBACK
```

한다.

반면 Lock은:

```text
동시에 같은 데이터에 접근하는
Transaction 사이의 순서를 제어한다.
```

즉:

```text
Transaction
=
작업의 원자성

Lock
=
동시 접근 제어
```

로 우선 이해한다.

---

# 11. ACID에서 Isolation

Transaction의 ACID:

```text
Atomicity

Consistency

Isolation

Durability
```

Phase 2에서는 특히:

```text
Isolation
```

을 집중적으로 다룬다.

Isolation은 여러 Transaction이 동시에 실행될 때 서로에게 어떤 영향을 미칠 수 있는지를 결정한다.

---

# 12. PostgreSQL Isolation Level

PostgreSQL의 기본 Transaction Isolation Level은:

```text
READ COMMITTED
```

이다.

READ COMMITTED에서는 일반적으로 하나의 SQL statement가 실행되는 시점에 이미 Commit된 데이터를 읽는다.

예:

```text
Seat = AVAILABLE
```

상태에서 A와 B가 거의 동시에 SELECT하면:

```text
Transaction A

SELECT seat
→ AVAILABLE
```

```text
Transaction B

SELECT seat
→ AVAILABLE
```

이 모두 가능하다.

---

# 13. Isolation Level 종류

주요 Isolation Level은 다음과 같다.

```text
READ UNCOMMITTED

READ COMMITTED

REPEATABLE READ

SERIALIZABLE
```

다만 PostgreSQL은 `READ UNCOMMITTED`를 실질적으로 `READ COMMITTED`처럼 처리한다.

Phase 2에서 중심적으로 볼 것은:

```text
READ COMMITTED

+

Explicit Lock
```

이다.

단순히 Isolation Level을 무조건 가장 높은 수준으로 올리는 방식으로 해결하지 않는다.

---

# 14. Phase 2 첫 번째 작업

**Lock을 적용하지 않는다.**

먼저 현재 코드가 실제로 깨지는지 확인한다.

```text
Baseline 구현

↓

동일 Seat에 동시 요청

↓

Race Condition 재현

↓

DB 상태 확인
```

이 테스트가 Phase 2의 기준점이 된다.

---

# 15. 동시성 테스트 구조

일반적인 Mockito Unit Test로는 이 문제를 테스트할 수 없다.

Mockito에서는 실제:

```text
Transaction

Database Lock

PostgreSQL MVCC

Concurrent UPDATE
```

가 존재하지 않기 때문이다.

따라서 실제 Spring과 PostgreSQL을 사용하는 Integration Test가 필요하다.

```text
JUnit

↓

Spring Boot

↓

ReservationService

↓

JPA

↓

PostgreSQL
```

---

# 16. 동시성 테스트 기본 환경

예:

```java
@SpringBootTest
class ReservationConcurrencyTest {
}
```

실제 PostgreSQL에 연결한다.

향후 테스트 재현성을 높이기 위해:

```text
Testcontainers
```

를 도입할 수 있다.

Phase 2 초기에는 기존 Docker PostgreSQL을 사용해도 된다.

---

# 17. ExecutorService

여러 Thread에서 동시에 예약 메서드를 실행하기 위해:

```java
ExecutorService
```

를 사용한다.

예:

```java
ExecutorService executorService =
    Executors.newFixedThreadPool(100);
```

의미:

```text
최대 100개의 Worker Thread를 생성하여
여러 작업을 병렬 실행한다.
```

---

# 18. CountDownLatch

단순히 100개의 Task를 등록한다고 해서 정말 같은 순간에 실행되는 것은 아니다.

```text
Thread 1 실행
Thread 1 거의 완료

Thread 2 실행

Thread 3 실행
```

처럼 실행되면 Race Condition이 잘 재현되지 않을 수 있다.

따라서:

```java
CountDownLatch
```

를 사용한다.

---

# 19. Start Latch

여러 Thread를 대기시킨다.

```text
Thread 1 ─┐
Thread 2 ─┤
Thread 3 ─┤
...       ├── WAIT
Thread100─┘
```

준비가 끝나면:

```text
START
```

신호를 보낸다.

```text
Thread 1 ─┐
Thread 2 ─┤
Thread 3 ─┼── reserve()
...       │
Thread100─┘
```

이를 통해 최대한 비슷한 시점에 요청을 발생시킨다.

---

# 20. Baseline 동시성 테스트 개념

예:

```text
Seat #1

status = AVAILABLE
```

100개의 사용자가 동일한 Seat에 예약을 요청한다.

```text
100 Threads

↓

createReservation()

↓

Seat #1
```

정상적인 기대값은:

```text
Success = 1

Failure = 99
```

이다.

Phase 1 구현에서는 이를 만족하지 못하는지 확인한다.

---

# 21. Baseline Test 개념 코드

개념적으로 다음과 같다.

```java
int threadCount = 100;

ExecutorService executor =
    Executors.newFixedThreadPool(threadCount);

CountDownLatch startLatch =
    new CountDownLatch(1);

CountDownLatch doneLatch =
    new CountDownLatch(threadCount);

for (int i = 0; i < threadCount; i++) {

    executor.submit(() -> {

        try {

            startLatch.await();

            reservationService.createReservation(...);

        } catch (Exception e) {

            // 실패 기록

        } finally {

            doneLatch.countDown();
        }
    });
}

startLatch.countDown();

doneLatch.await();
```

그 후 DB를 검사한다.

---

# 22. 동시성 테스트 검증 대상

단순히 HTTP Response만 보는 것이 아니다.

최종 DB 상태까지 확인한다.

예:

```text
Seat #1
```

에 대해:

```text
활성 Reservation 개수
```

를 확인한다.

정상:

```text
1
```

비정상:

```text
2 이상
```

---

# 23. 테스트 시 주의할 점

동시성 테스트 자체를 다음처럼 하나의 Test Transaction으로 묶지 않는 것이 좋다.

```java
@Test
@Transactional
void concurrencyTest() {
}
```

Spring Transaction은 기본적으로 **현재 Thread에 바인딩**된다.

하지만 ExecutorService에서 생성된 Worker Thread는 서로 다른 Thread다.

따라서:

```text
Test Thread Transaction

≠

Worker Thread Transaction
```

이다.

동시성 테스트에서는 각 Worker가 호출하는:

```java
reservationService.createReservation()
```

의 `@Transactional`을 통해 각각 독립적인 Transaction이 생성되도록 해야 한다.

---

# 24. Phase 2 Baseline 결과

예상 가능한 결과:

```text
Thread 1
Success

Thread 2
Success

Thread 3
SeatNotAvailable

Thread 4
Success

...
```

최종:

```text
Success = 3
Failure = 97
```

예:

```text
Reservation

#1 seat=1 PENDING
#2 seat=1 PENDING
#3 seat=1 PENDING
```

이 결과가 나오면 Race Condition 재현에 성공한 것이다.

정확한 성공 개수는 실행마다 달라질 수 있다.

---

# 25. Non-deterministic Test

Race Condition은 Timing에 따라 발생하기 때문에:

```text
매 실행마다 정확히 같은 결과
```

가 나오지 않을 수 있다.

예:

```text
실행 1 → 4명 성공

실행 2 → 2명 성공

실행 3 → 7명 성공
```

이런 특성을:

```text
Non-deterministic
```

이라고 한다.

따라서 동시성 테스트에서는 여러 번 실행하면서 문제를 관찰하는 것이 중요하다.

---

# 26. Pessimistic Lock

Race Condition을 확인한 뒤 첫 번째 해결 방법으로:

```text
Pessimistic Lock
```

을 적용한다.

비관적 락의 기본 가정:

> 다른 Transaction과 충돌할 가능성이 높다고 가정하고 데이터를 먼저 Lock한다.

---

# 27. PostgreSQL Row Lock

일반 조회:

```sql
SELECT *
FROM seat
WHERE id = 1;
```

비관적 Lock을 사용하는 조회는 개념적으로:

```sql
SELECT *
FROM seat
WHERE id = 1
FOR UPDATE;
```

와 같은 방식으로 동작한다.

---

# 28. `SELECT ... FOR UPDATE`

Transaction A:

```sql
SELECT *
FROM seat
WHERE id = 1
FOR UPDATE;
```

Seat #1의 Row Lock을 획득한다.

Transaction B가 같은 Row를:

```sql
SELECT *
FROM seat
WHERE id = 1
FOR UPDATE;
```

하려고 하면:

```text
WAIT
```

상태가 된다.

---

# 29. Pessimistic Lock 동작

초기:

```text
Seat = AVAILABLE
```

실행:

```text
Transaction A                  Transaction B

SELECT FOR UPDATE

Lock 획득

                               SELECT FOR UPDATE

                               WAIT

AVAILABLE 확인

Seat → HELD

Reservation 생성

COMMIT

Lock 해제

                               SELECT 완료

                               Seat = HELD

                               hold()

                               SeatNotAvailableException
```

최종:

```text
Reservation = 1개
```

가 된다.

---

# 30. Spring Data JPA Pessimistic Lock

Repository에 별도의 메서드를 생성한다.

예:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select s
    from Seat s
    where s.id = :seatId
""")
Optional<Seat> findByIdWithPessimisticLock(
    @Param("seatId") Long seatId
);
```

Service:

```java
Seat seat =
    seatRepository
        .findByIdWithPessimisticLock(request.seatId())
        .orElseThrow(SeatNotFoundException::new);
```

---

# 31. Lock은 Transaction 안에서 사용해야 한다

Pessimistic Lock은:

```java
@Transactional
```

범위 안에서 사용해야 한다.

```text
BEGIN

↓

SELECT ... FOR UPDATE

↓

Lock 획득

↓

Business Logic 실행

↓

COMMIT

↓

Lock 해제
```

Transaction이 종료되면 Lock도 해제된다.

---

# 32. Pessimistic Lock 테스트

Baseline에서 사용했던 동일한 동시성 테스트를 다시 실행한다.

```text
100 Threads

↓

Seat #1 예약 요청
```

기대 결과:

```text
Success = 1

SeatNotAvailable = 99
```

DB:

```text
Seat #1
HELD
```

```text
PENDING Reservation
= 1개
```

---

# 33. 중요한 테스트 원칙

Lock 적용 전과 후에 **동일한 테스트**를 사용한다.

```text
Before

동일 좌석 동시 예약
→ 여러 Reservation
```

```text
After

동일 좌석 동시 예약
→ 하나의 Reservation
```

그래야 Lock을 적용한 효과를 명확하게 비교할 수 있다.

---

# 34. Pessimistic Lock의 비용

Lock을 걸면 모든 문제가 공짜로 해결되는 것은 아니다.

예:

```text
User A
Lock 획득

↓

Business Logic 1초 수행

↓

User B
1초 대기

↓

User C
더 대기
```

즉 인기 좌석 하나에 요청이 몰리면:

```text
Lock Wait
```

가 발생한다.

---

# 35. Lock Contention

여러 Transaction이 하나의 Lock을 얻기 위해 경쟁하는 상황을:

```text
Lock Contention
```

이라고 한다.

예:

```text
100 users
   │
   │
   ▼
Seat #1 Row Lock
```

동시에 모두 처리되는 것이 아니라:

```text
Transaction A
   ↓
Transaction B
   ↓
Transaction C
   ↓
...
```

처럼 순차적으로 처리될 수 있다.

따라서:

```text
정합성 ↑

동시 처리량 ↓
```

이라는 Trade-off가 존재한다.

---

# 36. Lock Timeout

Lock을 무한정 기다리게 하면 문제가 될 수 있다.

예:

```text
Transaction A

Seat Lock 획득

↓

매우 오래 수행
```

```text
Transaction B

Seat Lock 요청

↓

WAIT

WAIT

WAIT
```

따라서 실제 시스템에서는:

```text
Lock Timeout

Transaction Timeout
```

등을 고려해야 한다.

Phase 2에서는 개념과 간단한 동작을 확인한다.

---

# 37. Deadlock

여러 Lock을 사용하는 경우 Deadlock이 발생할 수 있다.

예:

```text
Transaction A

Seat 1 Lock 획득

↓

Seat 2 Lock 요청
```

```text
Transaction B

Seat 2 Lock 획득

↓

Seat 1 Lock 요청
```

결과:

```text
A waits B

B waits A
```

서로 영원히 기다리는 구조가 된다.

이를:

```text
Deadlock
```

이라고 한다.

---

# 38. Deadlock 해결

PostgreSQL은 Deadlock을 탐지하면 일반적으로 Transaction 중 하나를 실패시켜 교착 상태를 해소한다.

따라서 Application에서는:

```text
Deadlock Exception 처리

Retry 여부 판단
```

이 필요할 수 있다.

또한 여러 Resource를 Lock할 때:

```text
항상 동일한 순서로 Lock 획득
```

하는 것이 중요한 설계 원칙이다.

예:

```text
잘못된 방식

Transaction A
Seat 1 → Seat 2

Transaction B
Seat 2 → Seat 1
```

대신:

```text
항상 ID 오름차순

Seat 1 → Seat 2
```

같은 규칙을 사용한다.

---

# 39. Optimistic Lock

두 번째 동시성 제어 방법으로:

```text
Optimistic Lock
```

을 학습한다.

낙관적 락의 기본 가정:

> 대부분의 Transaction은 충돌하지 않을 것이므로 먼저 실행하고, 실제 충돌이 발생했을 때 이를 감지한다.

---

# 40. JPA `@Version`

Seat Entity에 Version 필드를 추가한다.

```java
@Version
private Long version;
```

초기 데이터:

```text
Seat #1

status = AVAILABLE

version = 1
```

---

# 41. Optimistic Lock 동작

Transaction A와 B가 동시에 읽는다.

```text
Transaction A

Seat
AVAILABLE
version = 1
```

```text
Transaction B

Seat
AVAILABLE
version = 1
```

둘 다:

```text
HELD
```

로 변경하려고 한다.

---

# 42. Version 기반 UPDATE

Transaction A:

```sql
UPDATE seat
SET
    status = 'HELD',
    version = 2
WHERE
    id = 1
    AND version = 1;
```

성공한다.

DB:

```text
status = HELD

version = 2
```

Transaction B도:

```sql
UPDATE seat
SET
    status = 'HELD',
    version = 2
WHERE
    id = 1
    AND version = 1;
```

을 시도한다.

하지만 현재 DB의 version은:

```text
2
```

이다.

따라서:

```text
UPDATE row count = 0
```

이 된다.

JPA/Hibernate는 이를 Optimistic Lock 충돌로 판단한다.

---

# 43. Optimistic Lock 실패

Spring/JPA에서는 상황에 따라 다음 계열 예외를 만나게 된다.

```text
OptimisticLockException

ObjectOptimisticLockingFailureException
```

핵심 의미는:

> 내가 읽었던 이후 누군가 이 데이터를 먼저 수정했다.

이다.

---

# 44. Optimistic Lock과 Transaction Rollback

예약 과정:

```text
Seat 변경

+

Reservation INSERT
```

가 하나의 Transaction이라면 Optimistic Lock 충돌이 발생할 경우 해당 Transaction 전체가 Rollback되어야 한다.

즉 실패한 요청이:

```text
Reservation만 남는 상태
```

가 되면 안 된다.

최종적으로:

```text
승리 Transaction

Seat HELD
Reservation PENDING
```

만 남아야 한다.

---

# 45. Optimistic Lock에서 Retry

Optimistic Lock 실패가 발생했다고 해서 모든 상황에서 무조건 Retry하는 것은 아니다.

이번 티켓팅 예제에서 동일 좌석 예약 경쟁이라면:

```text
충돌 발생

↓

다른 사용자가 이미 Seat 선점

↓

다시 읽으면 Seat = HELD
```

일 가능성이 높다.

따라서 단순히 무한 Retry하는 것보다 사용자에게:

```text
SeatNotAvailable
```

을 반환하는 것이 자연스럽다.

Retry는:

```text
충돌 원인

Business Semantics

Retry Cost
```

를 고려해 결정해야 한다.

---

# 46. Pessimistic vs Optimistic Lock

| 항목 | Pessimistic Lock | Optimistic Lock |
|---|---|---|
| 기본 가정 | 충돌이 많다 | 충돌이 적다 |
| 동작 | 먼저 Lock | 충돌을 나중에 감지 |
| DB Row Lock | 적극 사용 | Version 기반 |
| 충돌 시 | 대기 | 실패 |
| 구현 | 비교적 직관적 | Exception 처리 필요 |
| 높은 경쟁 | 적합할 수 있음 | 충돌 실패가 많이 발생 |
| 낮은 경쟁 | Lock 비용 발생 가능 | 효율적 |
| 대표 JPA | `PESSIMISTIC_WRITE` | `@Version` |

---

# 47. 티켓팅에서는 어떤 방식이 적절한가

인기 공연의 특정 좌석:

```text
Seat A-1

↓

수십~수백 명 요청
```

처럼 충돌 가능성이 높은 Resource에서는 Pessimistic Lock이 직관적인 선택일 수 있다.

반면:

```text
100,000개의 좌석

↓

각 사용자가 서로 다른 좌석 접근
```

처럼 실제 동일 Row 충돌 비율이 낮다면 Optimistic Lock도 고려할 수 있다.

즉:

```text
Pessimistic이 무조건 좋다

또는

Optimistic이 무조건 좋다
```

가 아니다.

트래픽 패턴과 충돌 확률에 따라 선택한다.

---

# 48. Database Constraint

Application 코드에서 Lock을 적용했더라도 Database에도 가능한 범위에서 **최후의 정합성 방어선**을 두는 것을 고려한다.

우리가 보장해야 하는 invariant:

```text
한 좌석에는 동시에
하나의 활성 Reservation만 존재한다.
```

---

# 49. 단순 UNIQUE(seat_id)는 사용할 수 없다

Reservation은 History를 가진다.

예:

```text
Reservation #1

seat = A-1

status = EXPIRED
```

이후:

```text
Reservation #2

seat = A-1

status = PENDING
```

이 가능해야 한다.

따라서:

```sql
UNIQUE(seat_id)
```

를 사용하면 안 된다.

---

# 50. Partial Unique Index

PostgreSQL에서는 조건부 Unique Index를 만들 수 있다.

개념적으로:

```sql
CREATE UNIQUE INDEX
unique_active_reservation_per_seat
ON reservation(seat_id)
WHERE status IN ('PENDING', 'CONFIRMED');
```

이를 사용하면:

```text
Seat #1

PENDING Reservation #1 존재
```

상태에서 또 다른:

```text
PENDING Reservation #2
```

생성을 DB가 차단할 수 있다.

---

# 51. Partial Unique Index 주의사항

이 전략이 제대로 동작하려면 Reservation 만료 시:

```text
PENDING → EXPIRED
```

상태 변경이 실제 DB에도 반영되어야 한다.

그렇지 않고 만료된 Reservation이 계속:

```text
PENDING
```

으로 남아 있다면 Index 입장에서는 여전히 활성 Reservation으로 판단한다.

따라서 만료 Reservation 정리 문제는 향후:

```text
Scheduler

Redis TTL

Expiration Event
```

등과 연결될 수 있다.

Phase 2에서는 Constraint의 역할과 한계를 이해하는 수준으로 다룬다.

---

# 52. Application Lock + DB Constraint

이상적인 방향은:

```text
Application

Lock으로 정상적인 동시성 제어
```

그리고:

```text
Database

Constraint로 최종 정합성 보호
```

이다.

즉:

```text
Lock

+

Constraint
```

는 반드시 둘 중 하나만 선택하는 관계가 아니다.

서로 다른 역할을 한다.

---

# 53. 왜 DB Constraint가 필요한가

미래에 다음과 같은 일이 생길 수 있다.

```text
새로운 코드 경로 추가

↓

Lock을 사용하지 않는 Reservation 저장 로직 생성

↓

동시 예약 발생
```

Application 코드만 믿고 있다면 데이터가 깨질 수 있다.

DB Constraint는:

> 어떤 Application 코드가 접근하더라도 이 데이터 상태는 허용하지 않는다.

는 최종 방어선 역할을 한다.

---

# 54. 동시성 테스트용 Query

동일 Seat에 생성된 Reservation을 확인하기 위해 Repository에 테스트용 조회가 필요할 수 있다.

예:

```java
List<Reservation> findAllBySeatId(Long seatId);
```

또는:

```java
long countBySeatIdAndStatus(
    Long seatId,
    ReservationStatus status
);
```

검증:

```text
PENDING Reservation Count
= 1
```

---

# 55. 동시성 테스트 Success Count

DB 상태뿐 아니라 요청 결과도 확인한다.

예:

```text
Request = 100

Success = 1

SeatNotAvailable = 99
```

이를 위해:

```java
AtomicInteger successCount =
    new AtomicInteger();
```

등을 사용할 수 있다.

여러 Thread가 동시에 값을 변경하기 때문에 일반:

```java
int successCount
```

보다 Thread-safe한 자료형이 필요하다.

---

# 56. AtomicInteger

잘못된 방식:

```java
int successCount = 0;

successCount++;
```

`++` 연산 역시 내부적으로:

```text
READ

↓

ADD

↓

WRITE
```

이므로 Thread-safe하지 않다.

따라서:

```java
AtomicInteger successCount =
    new AtomicInteger();
```

```java
successCount.incrementAndGet();
```

을 사용한다.

재미있게도 **동시성 테스트 코드 자체에도 동시성 문제가 발생할 수 있다.**

---

# 57. Concurrency Test 예시 구조

개념적으로:

```java
@SpringBootTest
class ReservationConcurrencyTest {

    @Test
    void concurrentReservation() throws Exception {

        int threadCount = 100;

        ExecutorService executor =
            Executors.newFixedThreadPool(threadCount);

        CountDownLatch start =
            new CountDownLatch(1);

        CountDownLatch done =
            new CountDownLatch(threadCount);

        AtomicInteger success =
            new AtomicInteger();

        AtomicInteger fail =
            new AtomicInteger();

        for (...) {

            executor.submit(() -> {

                try {

                    start.await();

                    reservationService
                        .createReservation(...);

                    success.incrementAndGet();

                } catch (
                    SeatNotAvailableException e
                ) {

                    fail.incrementAndGet();

                } finally {

                    done.countDown();
                }
            });
        }

        start.countDown();

        done.await();

        assertEquals(1, success.get());

        assertEquals(
            threadCount - 1,
            fail.get()
        );
    }
}
```

실제 테스트에서는 User/Seat 데이터 생성 및 DB 조회 검증도 추가한다.

---

# 58. 테스트 데이터 초기화

동시성 테스트는 테스트마다 동일한 초기 상태에서 시작해야 한다.

예:

```text
Seat #1

AVAILABLE
```

```text
Reservation

0개
```

각 테스트 후 데이터를 정리하지 않으면:

```text
첫 테스트
Seat HELD

↓

두 번째 테스트
시작부터 HELD
```

상태가 되어 결과가 왜곡된다.

따라서:

```text
BeforeEach

Data Initialize
```

또는 테스트 데이터 정리 전략이 필요하다.

---

# 59. 실제 PostgreSQL을 사용하는 이유

H2와 PostgreSQL은:

```text
Lock 동작

Isolation 구현

SQL

Transaction 처리
```

가 완전히 동일하지 않다.

Phase 2의 목적 자체가 PostgreSQL의 동시성 동작을 이해하는 것이므로 가능하면:

```text
실제 PostgreSQL
```

로 테스트한다.

향후:

```text
Testcontainers PostgreSQL
```

을 사용하는 것이 이상적이다.

---

# 60. Testcontainers 도입 후보

Phase 2 후반에는 테스트 환경을:

```text
JUnit 실행

↓

PostgreSQL Docker Container 자동 시작

↓

Test 실행

↓

Container 종료
```

방식으로 만들 수 있다.

장점:

```text
개발자 DB 상태에 의존하지 않음

테스트마다 동일한 DB 환경

CI에서도 재현 가능
```

Testcontainers 자체가 Phase 2의 핵심 목표는 아니므로 Lock 문제를 재현한 이후 도입해도 된다.

---

# 61. SQL 로그 확인

Phase 2에서는 Hibernate SQL 로그를 적극적으로 보는 것이 좋다.

Pessimistic Lock 적용 전:

```sql
select ...
from seat
where id = ?
```

적용 후 PostgreSQL/JPA가 생성하는 Lock Query를 확인한다.

개념적으로:

```sql
SELECT ...
FROM seat
WHERE id = ?
FOR UPDATE;
```

와 같은 형태가 나타나는지 확인한다.

---

# 62. PostgreSQL에서 직접 Transaction 실험

코드 외에도 두 개의 psql Terminal을 띄우면 Lock을 직접 관찰할 수 있다.

Terminal A:

```sql
BEGIN;

SELECT *
FROM seat
WHERE id = 1
FOR UPDATE;
```

아직 Commit하지 않는다.

Terminal B:

```sql
BEGIN;

SELECT *
FROM seat
WHERE id = 1
FOR UPDATE;
```

그러면 B가 대기하는 것을 확인할 수 있다.

Terminal A:

```sql
COMMIT;
```

하면 B의 SELECT가 진행된다.

이 실험을 통해 Row Lock을 직접 확인한다.

---

# 63. HTTP 수준 동시 요청

Service Integration Test가 정상적으로 동작한 이후에는 실제 API에도 여러 요청을 동시에 전달해볼 수 있다.

```text
POST /api/reservations
```

동일:

```json
{
  "seatId": 1
}
```

에 대해 여러 요청을 보낸다.

단 Phase 2 핵심 검증은 우선 Service + 실제 DB 수준의 Integration Test로 수행한다.

본격적인 HTTP 부하 테스트 도구는 이후 Phase에서 사용한다.

---

# 64. Phase 2 예외 처리

동시성 제어를 적용하면서 새로운 기술 예외가 나타날 수 있다.

예:

```text
OptimisticLockException

LockTimeoutException

PessimisticLockException

Deadlock 관련 DB Exception
```

이러한 Infrastructure Exception을 사용자에게 그대로 노출하지 않는다.

예:

```json
{
  "message":
  "ObjectOptimisticLockingFailureException..."
}
```

처럼 내부 기술 정보를 반환하면 안 된다.

---

# 65. Domain Exception 변환

외부 API에서는 기존 도메인 의미를 유지한다.

예:

```text
다른 사용자가 좌석을 먼저 획득

↓

SeatNotAvailableException
```

응답:

```http
409 Conflict
```

```json
{
  "code": "SEAT_NOT_AVAILABLE",
  "message": "이미 선택된 좌석입니다."
}
```

Infrastructure Detail은 서버 내부에서 처리한다.

---

# 66. Phase 2 성능 관찰

Pessimistic과 Optimistic을 비교할 때 단순히:

```text
정답인가?
```

만 보는 것이 아니라 간단한 성능도 관찰한다.

예:

```text
100 Concurrent Requests

Success Count

Failure Count

Total Execution Time

Average Latency

Maximum Latency
```

이 단계에서는 정교한 Benchmark가 아니라 차이를 체감하는 정도면 충분하다.

---

# 67. 예상 비교

높은 충돌:

```text
같은 Seat에 100 요청
```

Pessimistic:

```text
Lock 대기 발생

1명 성공

나머지는 순차적으로 HELD 확인 후 실패
```

Optimistic:

```text
여러 Transaction 동시 실행

Version Conflict 다수 발생
```

낮은 충돌:

```text
100명이 서로 다른 Seat 예약
```

Pessimistic:

```text
서로 다른 Row라면
큰 충돌 없이 실행 가능
```

Optimistic:

```text
Version Conflict 거의 없음

병렬 처리 가능
```

두 시나리오를 비교하면 Lock의 특성을 훨씬 쉽게 이해할 수 있다.

---

# 68. Phase 2 테스트 시나리오 1

## Baseline Race Condition

### Given

```text
Seat #1

status = AVAILABLE
```

### When

```text
100 Threads

동시에
createReservation(seatId = 1)
```

### Then

Lock 적용 전 시스템에서 중복 Reservation이 발생할 수 있음을 확인한다.

---

# 69. Phase 2 테스트 시나리오 2

## Pessimistic Lock

### Given

```text
Seat = AVAILABLE
```

### When

```text
동일 Seat
100 Concurrent Reservations
```

### Then

```text
Success = 1
```

```text
Active Reservation = 1
```

나머지 요청:

```text
SeatNotAvailableException
```

---

# 70. Phase 2 테스트 시나리오 3

## Optimistic Lock

### Given

```text
Seat

AVAILABLE

version = 0
```

### When

```text
동일 Seat에 동시 예약
```

### Then

하나의 Transaction만 Seat 변경에 성공하고 나머지 Transaction에서 Version 충돌이 감지된다.

DB에는:

```text
Active Reservation = 1
```

만 남아야 한다.

---

# 71. Phase 2 테스트 시나리오 4

## 서로 다른 Seat 동시 예약

### Given

```text
Seat 1
AVAILABLE

Seat 2
AVAILABLE

...

Seat 100
AVAILABLE
```

### When

각 Thread가 서로 다른 Seat를 예약한다.

### Then

```text
Success = 100
```

이어야 한다.

Lock을 적용했다고 전체 예약 시스템이 직렬화되어서는 안 된다.

즉 **Row 단위 Lock이 정상적으로 동작하는지 확인하는 테스트**다.

---

# 72. Phase 2 테스트 시나리오 5

## Transaction Rollback

동시성 충돌로 Transaction이 실패한 경우:

```text
Reservation INSERT만 남거나

Seat 변경만 남으면 안 된다.
```

최종 DB가 일관된 상태인지 검증한다.

---

# 73. Phase 2 테스트 시나리오 6

## Lock Wait

Transaction A가 Seat Lock을 잡은 상태에서 Transaction B가 같은 Seat Lock을 요청한다.

검증:

```text
Transaction B가
A Commit 전에는 진행되지 않음
```

을 확인한다.

---

# 74. Phase 2 테스트 시나리오 7

## Deadlock 기본 실험

가능하면 두 Resource에 대해 반대 순서 Lock을 걸어 Deadlock 상황을 간단하게 관찰한다.

목적은 Production 코드에 Deadlock을 만드는 것이 아니라:

```text
왜 Lock 순서가 중요한가
```

를 이해하는 것이다.

---

# 75. Package 변경 예상

Phase 2에서는 기존 Package 구조를 크게 변경하지 않는다.

```text
seat
├── domain
│   ├── Seat.java
│   └── SeatStatus.java
└── repository
    └── SeatRepository.java
```

Pessimistic Lock Query는:

```text
SeatRepository
```

에 추가한다.

Optimistic Lock에서는:

```text
Seat
└── @Version
```

을 추가한다.

테스트:

```text
src/test/java/...

reservation
├── service
│   └── ReservationServiceTest.java
│
└── integration
    └── ReservationConcurrencyTest.java
```

처럼 분리할 수 있다.

---

# 76. Phase 2 구현 순서

```text
1. 현재 Phase 1 코드 그대로 유지

↓

2. Concurrency Integration Test 작성

↓

3. 동일 Seat 동시 예약 요청

↓

4. 중복 Reservation 발생 재현

↓

5. SQL 및 Transaction 실행 순서 분석

↓

6. READ COMMITTED 이해

↓

7. Pessimistic Lock Repository 구현

↓

8. ReservationService에 적용

↓

9. 동일 Concurrency Test 재실행

↓

10. Success = 1 검증

↓

11. Lock Wait 관찰

↓

12. Pessimistic Lock의 비용 분석

↓

13. 별도 실험으로 Optimistic Lock 적용

↓

14. Seat에 @Version 추가

↓

15. 동일 Concurrency Test 실행

↓

16. Optimistic Lock Exception 확인

↓

17. Pessimistic vs Optimistic 비교

↓

18. DB Constraint 검토

↓

19. Partial Unique Index 실험

↓

20. Deadlock / Lock Timeout 기본 실험

↓

21. Phase 2 완료
```

---

# 77. Phase 2 완료 조건

다음 항목을 만족하면 Phase 2를 완료한 것으로 본다.

```text
[ ] 동일 Seat 동시 예약 테스트 작성

[ ] Phase 1 코드에서 Race Condition 실제 재현

[ ] Check-Then-Act Race Condition 설명 가능

[ ] @Transactional만으로 해결되지 않는 이유 설명 가능

[ ] Transaction Isolation 개념 이해

[ ] PostgreSQL READ COMMITTED 이해

[ ] Row Lock 개념 이해

[ ] SELECT FOR UPDATE 동작 이해

[ ] JPA Pessimistic Lock 적용

[ ] Pessimistic Lock 적용 후
    동일 Seat Reservation 1개만 생성

[ ] 서로 다른 Seat는 병렬 예약 가능

[ ] Lock Wait 확인

[ ] Optimistic Lock 원리 이해

[ ] Seat에 @Version 적용

[ ] Version Conflict 재현

[ ] Optimistic Lock 충돌 시
    데이터 정합성 유지

[ ] Pessimistic / Optimistic
    Trade-off 설명 가능

[ ] DB Constraint가 필요한 이유 이해

[ ] UNIQUE(seat_id)를 사용할 수 없는 이유 설명 가능

[ ] Partial Unique Index 개념 이해

[ ] Lock Timeout 개념 이해

[ ] Deadlock 발생 원리 이해

[ ] 실제 PostgreSQL 기반
    Concurrency Integration Test 통과
```

---

# 78. Phase 2에서 반드시 이해해야 할 개념

Phase 2의 목적은 단순히:

```java
@Lock(...)
```

하나 붙이는 것이 아니다.

다음 개념을 이해해야 한다.

## Concurrency

```text
Concurrent Request

Shared Resource

Race Condition

Critical Section

Check-Then-Act
```

---

## Transaction

```text
Transaction Boundary

Commit

Rollback

Isolation

Concurrent Transaction
```

---

## PostgreSQL

```text
MVCC

READ COMMITTED

Row Lock

SELECT FOR UPDATE

Lock Wait

Deadlock
```

MVCC는 Phase 2에서 특히 중요한 키워드다.

단순히:

```text
DB에 Lock이 있다
```

수준이 아니라:

> PostgreSQL이 여러 Transaction의 읽기와 쓰기를 어떻게 동시에 처리하는가

라는 관점으로 이해한다.

---

## JPA

```text
@Transactional

@Lock

LockModeType.PESSIMISTIC_WRITE

@Version

Optimistic Lock

Persistence Context

Flush
```

특히 Optimistic Lock에서는 실제 SQL UPDATE 및 Flush 시점에 충돌이 드러날 수 있다는 점도 확인한다.

---

## Database Constraint

```text
Unique Constraint

Unique Index

Partial Index

Application Validation

Database Invariant
```

---

# 79. Phase 2에서 가장 중요한 사고방식

다음 코드는 얼핏 보면 안전해 보인다.

```java
if (seat.isAvailable()) {
    seat.hold();
}
```

하지만 동시성 환경에서는:

```text
코드 한 줄 한 줄이 맞는가?
```

만으로 충분하지 않다.

반드시:

```text
두 Request가 동시에 이 코드를 실행하면?
```

을 생각해야 한다.

---

# 80. Phase 2의 핵심 변화

Phase 1에서의 사고:

```text
Request 하나가 들어왔을 때
정상적으로 처리되는가?
```

Phase 2에서의 사고:

```text
같은 데이터를 대상으로
여러 Request가 동시에 들어왔을 때도
정상적으로 처리되는가?
```

즉:

```text
Single Request Correctness

↓

Concurrent Request Correctness
```

로 관점이 확장된다.

---

# 81. Phase 2의 최종 시스템

Phase 2가 끝나면 시스템은:

```text
여러 사용자가 동시에
같은 좌석에 예약을 요청하더라도

↓

하나의 사용자만 좌석 선점 성공

↓

나머지 사용자는 실패

↓

DB에는 하나의 활성 Reservation만 존재
```

하는 시스템이 된다.

즉:

```text
Phase 1

기능적으로 정상적인
단일 서버 티켓팅 시스템
```

에서:

```text
Phase 2

동시 요청에서도
좌석 정합성을 보장하는
티켓팅 시스템
```

으로 발전한다.

---

# 82. 하지만 Phase 2에도 한계가 있다

좌석의 정합성 문제는 해결했지만 여전히:

```text
Spring Boot 1대

PostgreSQL 1대
```

구조다.

또한 인기 공연 예매 오픈 시:

```text
100 users

↓

1,000 users

↓

10,000 users
```

가 몰려오면:

```text
DB Connection 부족

Lock Wait 증가

Request Latency 증가

DB 부하 증가
```

같은 문제가 발생할 수 있다.

즉 이제 다음 질문이 생긴다.

```text
좌석 정합성은 지켰는데

수만 명이 동시에 들어오면
서버와 DB가 버틸 수 있는가?
```

이 문제는 이후 Phase에서:

```text
Load Test

Connection Pool

Caching

Redis

Waiting Queue

Rate Limiting
```

등으로 발전시킨다.

---

# 83. Phase 2 한 문장 정의

> **Phase 2 — Concurrent Reservation Control**  
> 동일 좌석에 대한 다수의 동시 예약 요청으로 Check-Then-Act Race Condition을 실제 PostgreSQL 환경에서 재현하고, Transaction Isolation과 Pessimistic/Optimistic Lock을 이용해 좌석 중복 예약을 방지한다. 이후 Lock contention, timeout, deadlock 및 Database Constraint를 학습하고 각 동시성 제어 방식의 정합성과 성능 Trade-off를 비교한다.

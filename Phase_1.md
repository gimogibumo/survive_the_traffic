# Ticketing: Survive the Traffic — Phase 1

## 1. Phase 1 개요

### 1.1 목표

Phase 1의 목표는 **가장 단순한 형태의 티켓팅 서버를 구현하는 것**이다.

현재 단계에서는 고성능·분산 처리를 고려하지 않는다.

```text
Client
  │
  ▼
Spring Boot
  │
  ▼
PostgreSQL
```

사용자가 공연의 좌석을 조회하고, 좌석을 일정 시간 동안 선점한 뒤 Mock 결제를 통해 예매를 확정할 수 있는 최소 기능을 구현한다.

Phase 1에서는 의도적으로 Redis, Kafka, 분산 락 등의 기술을 사용하지 않는다.

향후 Phase에서 현재 구조의 문제점을 실제로 재현한 뒤 필요한 기술을 하나씩 도입한다.

---

## 1.2 핵심 사용자 흐름

```text
공연 조회
   ↓
좌석 조회
   ↓
좌석 선택
   ↓
좌석 임시 선점
   ↓
Reservation 생성
   ↓
Mock 결제
   ↓
예매 확정
```

좌석의 상태는 다음과 같이 변경된다.

```text
AVAILABLE
    │
    │ 좌석 선점
    ▼
  HELD
    │
    │ 결제 성공
    ▼
  SOLD
```

결제하지 않고 선점 시간이 만료되면 다음과 같이 변경된다.

```text
HELD
 │
 │ Timeout
 ▼
AVAILABLE
```

---

# 2. 기술 스택

## Backend

- Java
- Spring Boot
- Spring Web
- Spring Data JPA
- Hibernate

## Database

- PostgreSQL

## Build

- Gradle

## Infrastructure

- Docker
- Docker Compose

## Test

- JUnit 5
- Spring Boot Test

향후 필요하면 Testcontainers를 도입한다.

---

# 3. Phase 1 범위

## 구현 대상

- 사용자 데이터
- 공연 데이터
- 좌석 데이터
- 공연 조회
- 좌석 조회
- 좌석 임시 선점
- Reservation 생성
- Mock 결제
- 예매 확정
- 사용자 예매 내역 조회
- 기본적인 예외 처리
- DB Transaction 처리

## 구현하지 않는 기능

Phase 1에서는 다음 기능을 구현하지 않는다.

- 회원가입
- 로그인
- Spring Security
- JWT
- 실제 PG 결제
- 환불
- 예매 취소
- Redis
- 대기열
- Kafka
- 분산 락
- 서버 Scale-out
- Load Balancer
- Monitoring
- Distributed Tracing
- Chaos Engineering

사용자는 임시로 `userId`를 직접 전달하는 방식으로 처리한다.

---

# 4. Domain Model

Phase 1에서는 다음 5개의 주요 Domain을 사용한다.

```text
User

Concert

Seat

Reservation

Payment
```

전체 관계는 다음과 같다.

```text
User
 │
 │ 1:N
 ▼
Reservation
 │
 │ N:1
 ▼
Seat
 │
 │ N:1
 ▼
Concert

Reservation
 │
 │ 1:1
 ▼
Payment
```

---

# 5. User

사용자를 나타낸다.

## Fields

| Field | Type | 설명 |
|---|---|---|
| id | BIGINT | 사용자 PK |
| email | VARCHAR | 이메일 |
| name | VARCHAR | 사용자 이름 |
| createdAt | TIMESTAMP | 생성 시각 |

## Example

```text
id = 1
email = kim@example.com
name = 기범
```

Phase 1에서는 인증 기능이 없기 때문에 User는 예약의 소유자를 식별하는 용도로만 사용한다.

---

# 6. Concert

예매 대상 공연을 나타낸다.

## Fields

| Field | Type | 설명 |
|---|---|---|
| id | BIGINT | 공연 PK |
| title | VARCHAR | 공연 이름 |
| reservationOpenAt | TIMESTAMP | 예매 오픈 시각 |
| concertAt | TIMESTAMP | 공연 시작 시각 |
| createdAt | TIMESTAMP | 생성 시각 |

## Example

```text
id = 1

title = 2026 Backend Festival

reservationOpenAt
= 2026-09-01 20:00

concertAt
= 2026-09-30 19:00
```

---

# 7. Seat

공연에 존재하는 개별 좌석을 나타낸다.

## Fields

| Field | Type | 설명 |
|---|---|---|
| id | BIGINT | 좌석 PK |
| concertId | BIGINT | 공연 FK |
| section | VARCHAR | 좌석 구역 |
| rowNumber | INT | 열 |
| seatNumber | INT | 좌석 번호 |
| price | BIGINT | 가격 |
| status | ENUM | 현재 좌석 상태 |

## Example

```text
Concert
2026 Backend Festival

Seat
A구역 3열 17번

Price
150,000원

Status
AVAILABLE
```

---

# 8. SeatStatus

좌석 상태는 세 종류로 정의한다.

```text
AVAILABLE

HELD

SOLD
```

## AVAILABLE

현재 아무도 선점하지 않은 좌석.

새로운 Reservation을 생성할 수 있다.

## HELD

특정 사용자가 결제를 위해 임시 선점한 좌석.

다른 사용자는 해당 좌석을 예약할 수 없다.

## SOLD

결제가 완료되어 판매된 좌석.

다시 예약할 수 없다.

---

# 9. 좌석 상태 전이

정상적인 상태 전이는 다음과 같다.

```text
AVAILABLE
    │
    │ reserve()
    ▼
  HELD
    │
    │ payment success
    ▼
  SOLD
```

선점 시간이 만료된 경우:

```text
HELD
 │
 │ expiresAt 경과
 ▼
AVAILABLE
```

Phase 1에서는 좌석 선점 시간을 기본적으로 **5분**으로 설정한다.

---

# 10. Reservation

사용자가 특정 좌석을 선점하고 결제하는 예매 프로세스를 나타낸다.

## Fields

| Field | Type | 설명 |
|---|---|---|
| id | BIGINT | Reservation PK |
| userId | BIGINT | 사용자 FK |
| seatId | BIGINT | 좌석 FK |
| status | ENUM | 예약 상태 |
| expiresAt | TIMESTAMP | 좌석 선점 만료 시각 |
| createdAt | TIMESTAMP | 생성 시각 |

---

# 11. ReservationStatus

```text
PENDING

CONFIRMED

CANCELLED

EXPIRED
```

## PENDING

좌석을 선점했지만 결제가 완료되지 않은 상태.

이 상태에서는 Seat가 `HELD` 상태다.

## CONFIRMED

결제가 정상적으로 완료된 상태.

이 상태에서는 Seat가 `SOLD` 상태다.

## CANCELLED

사용자가 Reservation을 취소한 상태.

Phase 1에서는 취소 기능을 실제 API로 제공하지 않는다.

## EXPIRED

결제 가능 시간이 지나 Reservation이 만료된 상태.

---

# 12. Seat Status와 Reservation Status의 차이

Seat의 상태와 Reservation의 상태는 서로 다른 의미를 가진다.

SeatStatus:

> 현재 이 좌석을 새로운 사용자가 구매할 수 있는가?

ReservationStatus:

> 특정 사용자의 예매 프로세스가 현재 어느 단계인가?

예:

```text
Seat A-17

status = HELD
```

```text
Reservation #1001

user = User 1
seat = A-17
status = PENDING
expiresAt = 20:05
```

결제가 성공하면:

```text
Seat

HELD
 ↓
SOLD
```

```text
Reservation

PENDING
 ↓
CONFIRMED
```

---

# 13. Payment

결제 기록을 나타낸다.

Phase 1에서는 실제 PG사를 연동하지 않고 Mock Payment를 사용한다.

## Fields

| Field | Type | 설명 |
|---|---|---|
| id | BIGINT | Payment PK |
| reservationId | BIGINT | Reservation FK |
| amount | BIGINT | 결제 금액 |
| status | ENUM | 결제 상태 |
| paymentKey | VARCHAR | 결제 식별값 |
| createdAt | TIMESTAMP | 생성 시각 |

---

# 14. PaymentStatus

```text
READY

SUCCESS

FAILED

CANCELLED
```

Phase 1의 MockPaymentService는 기본적으로 성공 결과를 반환한다.

```text
Payment

READY
 ↓
SUCCESS
```

결제 장애 및 실패율 조작은 이후 Phase에서 구현한다.

---

# 15. ERD

```text
┌────────────────────┐
│        User        │
├────────────────────┤
│ id PK              │
│ email              │
│ name               │
│ created_at         │
└─────────┬──────────┘
          │
          │ 1:N
          ▼
┌────────────────────┐
│    Reservation     │
├────────────────────┤
│ id PK              │
│ user_id FK         │
│ seat_id FK         │
│ status             │
│ expires_at         │
│ created_at         │
└────────┬──────┬────┘
         │      │
         │      │ 1:1
         │      ▼
         │ ┌────────────────────┐
         │ │      Payment       │
         │ ├────────────────────┤
         │ │ id PK              │
         │ │ reservation_id FK  │
         │ │ amount             │
         │ │ status             │
         │ │ payment_key        │
         │ │ created_at         │
         │ └────────────────────┘
         │
         │ N:1
         ▼
┌────────────────────┐
│        Seat        │
├────────────────────┤
│ id PK              │
│ concert_id FK      │
│ section            │
│ row_number         │
│ seat_number        │
│ price              │
│ status             │
└─────────┬──────────┘
          │
          │ N:1
          ▼
┌────────────────────┐
│      Concert       │
├────────────────────┤
│ id PK              │
│ title              │
│ reservation_open   │
│ concert_at         │
│ created_at         │
└────────────────────┘
```

---

# 16. Database Constraint

## User

이메일은 중복될 수 없다.

```text
UNIQUE(email)
```

## Seat

하나의 공연에 동일한 실제 좌석이 두 번 생성될 수 없다.

따라서 다음 조합에 Unique Constraint를 둔다.

```text
concert_id
+
section
+
row_number
+
seat_number
```

즉:

```sql
UNIQUE (
    concert_id,
    section,
    row_number,
    seat_number
)
```

---

# 17. Reservation의 seat_id에 UNIQUE를 걸지 않는 이유

한 좌석은 시간에 따라 여러 Reservation history를 가질 수 있다.

예:

```text
User A

Seat A-17 선점
↓
Reservation #1
↓
결제하지 않음
↓
EXPIRED
```

이후:

```text
User B

Seat A-17 선점
↓
Reservation #2
```

가 가능해야 한다.

따라서:

```text
UNIQUE(seat_id)
```

를 걸면 안 된다.

Phase 1에서는 실제 좌석의 현재 가용 여부는 `Seat.status`를 기준으로 판단한다.

---

# 18. API 목록

Phase 1에서 구현할 API는 다음과 같다.

```text
GET  /api/concerts

GET  /api/concerts/{concertId}

GET  /api/concerts/{concertId}/seats

POST /api/reservations

POST /api/reservations/{reservationId}/payments

GET  /api/users/{userId}/reservations
```

---

# 19. 공연 목록 조회 API

## Request

```http
GET /api/concerts
```

## Response

```json
[
  {
    "id": 1,
    "title": "2026 Backend Festival",
    "reservationOpenAt": "2026-09-01T20:00:00",
    "concertAt": "2026-09-30T19:00:00"
  }
]
```

---

# 20. 공연 상세 조회 API

## Request

```http
GET /api/concerts/{concertId}
```

## Example

```http
GET /api/concerts/1
```

## Response

```json
{
  "id": 1,
  "title": "2026 Backend Festival",
  "reservationOpenAt": "2026-09-01T20:00:00",
  "concertAt": "2026-09-30T19:00:00"
}
```

---

# 21. 좌석 조회 API

## Request

```http
GET /api/concerts/{concertId}/seats
```

## Example

```http
GET /api/concerts/1/seats
```

## Response

```json
[
  {
    "seatId": 101,
    "section": "A",
    "row": 1,
    "number": 1,
    "price": 150000,
    "status": "AVAILABLE"
  },
  {
    "seatId": 102,
    "section": "A",
    "row": 1,
    "number": 2,
    "price": 150000,
    "status": "SOLD"
  }
]
```

---

# 22. 좌석 선점 API

## Request

```http
POST /api/reservations
```

```json
{
  "userId": 1,
  "seatId": 101
}
```

## 성공 조건

해당 좌석이:

```text
SeatStatus.AVAILABLE
```

이어야 한다.

## 처리 과정

```text
User 조회

↓

Seat 조회

↓

Seat.status == AVAILABLE 확인

↓

Seat
AVAILABLE → HELD

↓

Reservation 생성

↓

Reservation.status = PENDING

↓

expiresAt = 현재 시각 + 5분

↓

Transaction Commit
```

## Response

```json
{
  "reservationId": 1001,
  "seatId": 101,
  "status": "PENDING",
  "expiresAt": "2026-09-01T20:05:00"
}
```

---

# 23. 결제 API

## Request

```http
POST /api/reservations/{reservationId}/payments
```

## Example

```http
POST /api/reservations/1001/payments
```

## 처리 과정

```text
Reservation 조회

↓

Reservation.status == PENDING 확인

↓

expiresAt 확인

↓

Mock Payment 실행

↓

Payment 생성

↓

Payment.status = SUCCESS

↓

Reservation
PENDING → CONFIRMED

↓

Seat
HELD → SOLD

↓

Transaction Commit
```

## Response

```json
{
  "paymentId": 5001,
  "reservationId": 1001,
  "status": "SUCCESS",
  "amount": 150000
}
```

---

# 24. 사용자 예매 내역 조회 API

## Request

```http
GET /api/users/{userId}/reservations
```

## Example

```http
GET /api/users/1/reservations
```

## Response

```json
[
  {
    "reservationId": 1001,
    "concertTitle": "2026 Backend Festival",
    "section": "A",
    "row": 1,
    "seatNumber": 1,
    "price": 150000,
    "status": "CONFIRMED"
  }
]
```

---

# 25. Transaction 정책

Phase 1에서 가장 중요한 개념 중 하나다.

## 좌석 선점

다음 두 작업은 반드시 하나의 Transaction에서 실행되어야 한다.

```text
Seat
AVAILABLE → HELD

+

Reservation INSERT
```

잘못된 경우:

```text
Seat HELD 저장 성공

↓

Exception 발생

↓

Reservation 저장 실패
```

결과:

```text
Seat = HELD

Reservation = 없음
```

사용자는 없는데 좌석만 잠겨버리는 데이터 불일치가 발생한다.

따라서:

```text
BEGIN

Seat UPDATE

Reservation INSERT

COMMIT
```

형태로 처리한다.

중간에 실패하면:

```text
ROLLBACK
```

한다.

---

# 26. 결제 Transaction

결제 과정에서도 다음 데이터는 함께 변경되어야 한다.

```text
Payment 생성

+

Reservation
PENDING → CONFIRMED

+

Seat
HELD → SOLD
```

Phase 1의 Mock 결제는 동일 애플리케이션 내부에서 처리되므로 하나의 DB Transaction으로 묶는다.

실제 외부 PG사가 추가되면 단순 DB Transaction만으로 해결할 수 없으며 이는 이후 Phase에서 다룬다.

---

# 27. Reservation Service 기본 로직

개념적으로 다음 구조를 갖는다.

```java
@Transactional
public Long reserve(Long userId, Long seatId) {

    User user = userRepository.findById(userId)
        .orElseThrow(...);

    Seat seat = seatRepository.findById(seatId)
        .orElseThrow(...);

    if (seat.getStatus() != SeatStatus.AVAILABLE) {
        throw new SeatNotAvailableException();
    }

    seat.hold();

    Reservation reservation =
        Reservation.create(
            user,
            seat,
            LocalDateTime.now().plusMinutes(5)
        );

    reservationRepository.save(reservation);

    return reservation.getId();
}
```

Phase 1에서는 의도적으로 별도의 Lock을 사용하지 않는다.

---

# 28. Phase 1의 의도적인 동시성 취약점

현재 로직은 다음 형태다.

```text
Seat 조회

↓

AVAILABLE인지 확인

↓

HELD로 변경
```

즉:

```text
Read

↓

Check

↓

Update
```

구조다.

동시에 두 요청이 들어오면:

```text
Request A                   Request B

Seat 조회                   Seat 조회

AVAILABLE                   AVAILABLE

   │                           │
   ▼                           ▼

조건 통과                    조건 통과
```

가 가능하다.

즉 다음 코드 자체가 동시성 환경에서는 안전하지 않다.

```java
if (seat.getStatus() == AVAILABLE) {
    seat.hold();
}
```

이를 **Check-Then-Act Race Condition** 문제로 발전시킬 예정이다.

Phase 1에서는 해당 문제를 해결하지 않는다.

---

# 29. 예외 처리 정책

공통적으로 다음 HTTP Status를 사용한다.

## 400 Bad Request

잘못된 요청 값.

예:

```json
{
  "code": "INVALID_REQUEST",
  "message": "잘못된 요청입니다."
}
```

## 404 Not Found

존재하지 않는 Resource.

예:

```json
{
  "code": "SEAT_NOT_FOUND",
  "message": "좌석을 찾을 수 없습니다."
}
```

## 409 Conflict

현재 Resource 상태 때문에 요청을 수행할 수 없는 경우.

예:

```json
{
  "code": "SEAT_NOT_AVAILABLE",
  "message": "이미 선택된 좌석입니다."
}
```

## 500 Internal Server Error

예상하지 못한 서버 내부 오류.

---

# 30. 공통 Error Response

```json
{
  "code": "SEAT_NOT_AVAILABLE",
  "message": "이미 선택된 좌석입니다."
}
```

Spring에서는 다음 구조로 공통 처리한다.

```text
@RestControllerAdvice

+

@ExceptionHandler
```

---

# 31. Package Structure

도메인 기준 Package 구조를 사용한다.

```text
src/main/java/com/example/ticketing

├── TicketingApplication.java
│
├── user
│   ├── domain
│   │   └── User.java
│   ├── repository
│   │   └── UserRepository.java
│   └── service
│       └── UserService.java
│
├── concert
│   ├── controller
│   │   └── ConcertController.java
│   ├── domain
│   │   └── Concert.java
│   ├── repository
│   │   └── ConcertRepository.java
│   ├── service
│   │   └── ConcertService.java
│   └── dto
│
├── seat
│   ├── domain
│   │   ├── Seat.java
│   │   └── SeatStatus.java
│   ├── repository
│   │   └── SeatRepository.java
│   ├── service
│   │   └── SeatService.java
│   └── dto
│
├── reservation
│   ├── controller
│   │   └── ReservationController.java
│   ├── domain
│   │   ├── Reservation.java
│   │   └── ReservationStatus.java
│   ├── repository
│   │   └── ReservationRepository.java
│   ├── service
│   │   └── ReservationService.java
│   └── dto
│
├── payment
│   ├── controller
│   │   └── PaymentController.java
│   ├── domain
│   │   ├── Payment.java
│   │   └── PaymentStatus.java
│   ├── repository
│   │   └── PaymentRepository.java
│   ├── service
│   │   └── MockPaymentService.java
│   └── dto
│
└── common
    ├── exception
    └── config
```

---

# 32. Layer 기본 구조

각 도메인 내부에서는 기본적으로 다음 흐름을 사용한다.

```text
Controller
    │
    ▼
Service
    │
    ▼
Repository
    │
    ▼
PostgreSQL
```

## Controller

HTTP Request 및 Response를 담당한다.

비즈니스 로직을 직접 구현하지 않는다.

## Service

Application의 비즈니스 로직과 Transaction 경계를 담당한다.

## Repository

DB 조회 및 저장을 담당한다.

## Domain

실제 비즈니스 상태 및 상태 변경을 표현한다.

예:

```text
Seat.hold()

Seat.sell()

Reservation.confirm()
```

---

# 33. 시간 처리

서비스 내부에서 `LocalDateTime.now()`를 직접 여러 곳에서 호출하는 방식은 피한다.

시간 의존성을 테스트할 수 있도록 `Clock`을 주입한다.

```java
private final Clock clock;
```

현재 시간:

```java
LocalDateTime.now(clock);
```

테스트에서는:

```java
Clock.fixed(...)
```

를 사용할 수 있다.

예:

```text
현재 시간
20:00

좌석 선점

expiresAt
20:05
```

처럼 결정적인 테스트를 작성할 수 있다.

---

# 34. 초기 Seed Data

## Users

```text
User 1

name = 기범
email = kim@example.com
```

```text
User 2

name = 철수
email = chulsoo@example.com
```

```text
User 3

name = 영희
email = younghee@example.com
```

---

# 35. 초기 Concert

```text
id = 1

title
2026 Backend Festival

reservationOpenAt
2026-09-01 20:00

concertAt
2026-09-30 19:00
```

---

# 36. 초기 Seats

간단하게 100석을 생성한다.

```text
A구역

1열 1번 ~ 10번
2열 1번 ~ 10번
...
10열 1번 ~ 10번

총 100석
```

초기 상태:

```text
status = AVAILABLE
```

가격:

```text
150,000원
```

---

# 37. 테스트 요구사항

## Reservation Test 1

AVAILABLE 좌석을 정상적으로 선점할 수 있다.

### Given

```text
Seat.status = AVAILABLE
```

### When

```text
reserve(userId, seatId)
```

### Then

```text
Seat.status = HELD

Reservation.status = PENDING
```

---

# 38. Reservation Test 2

좌석을 선점하면 Reservation이 생성된다.

### Then

```text
Reservation.user = 요청 사용자

Reservation.seat = 요청 좌석

Reservation.status = PENDING

Reservation.expiresAt
= 현재시각 + 5분
```

---

# 39. Reservation Test 3

이미 HELD 상태인 좌석은 선점할 수 없다.

### Given

```text
Seat.status = HELD
```

### When

```text
reserve(...)
```

### Then

```text
SeatNotAvailableException
```

---

# 40. Reservation Test 4

이미 SOLD 상태인 좌석은 선점할 수 없다.

### Given

```text
Seat.status = SOLD
```

### Then

```text
SeatNotAvailableException
```

---

# 41. Payment Test 1

PENDING Reservation을 정상적으로 결제할 수 있다.

### Given

```text
Reservation.status = PENDING

Seat.status = HELD
```

### When

```text
pay(reservationId)
```

### Then

```text
Payment.status = SUCCESS

Reservation.status = CONFIRMED

Seat.status = SOLD
```

---

# 42. Payment Test 2

이미 만료된 Reservation은 결제할 수 없다.

### Given

```text
Reservation.expiresAt
<
현재 시간
```

### When

```text
pay(...)
```

### Then

결제가 수행되지 않는다.

---

# 43. Payment Test 3

이미 CONFIRMED된 Reservation을 다시 결제할 수 없다.

### Given

```text
Reservation.status = CONFIRMED
```

### Then

결제를 거부한다.

중복 결제에 대한 완전한 Idempotency 설계는 이후 Phase에서 별도로 다룬다.

---

# 44. Transaction Test

Reservation 저장 과정에서 예외가 발생하면 Seat 변경도 Rollback되어야 한다.

예:

```text
Seat

AVAILABLE → HELD

↓

Reservation 저장 중 Exception
```

최종 결과:

```text
Seat.status = AVAILABLE

Reservation = 없음
```

이어야 한다.

---

# 45. Phase 1 구현 순서

```text
1. Spring Boot 프로젝트 생성

↓

2. PostgreSQL Docker 환경 구성

↓

3. Spring Boot ↔ PostgreSQL 연결

↓

4. User Entity 구현

↓

5. Concert Entity 구현

↓

6. Seat / SeatStatus 구현

↓

7. Reservation / ReservationStatus 구현

↓

8. Payment / PaymentStatus 구현

↓

9. Repository 구현

↓

10. 공연 조회 API 구현

↓

11. 좌석 조회 API 구현

↓

12. ReservationService 구현

↓

13. 좌석 선점 API 구현

↓

14. MockPaymentService 구현

↓

15. 결제 API 구현

↓

16. 사용자 Reservation 조회 API 구현

↓

17. Exception Handling 구현

↓

18. Test 작성

↓

19. Seed Data 생성

↓

20. Phase 1 완료
```

---

# 46. Phase 1 완료 조건

다음 항목을 모두 만족하면 Phase 1을 완료한 것으로 본다.

```text
[ ] Spring Boot Application 실행 가능

[ ] PostgreSQL 연결 성공

[ ] User 데이터 저장 가능

[ ] Concert 데이터 저장 가능

[ ] Seat 데이터 저장 가능

[ ] 공연 목록 조회 가능

[ ] 공연 상세 조회 가능

[ ] 공연 좌석 조회 가능

[ ] AVAILABLE 좌석 선점 가능

[ ] 좌석 선점 시 HELD 상태 변경

[ ] Reservation PENDING 생성

[ ] Reservation expiresAt 설정

[ ] HELD/SOLD 좌석 재선점 차단

[ ] Mock 결제 가능

[ ] 결제 성공 시 Payment SUCCESS

[ ] 결제 성공 시 Reservation CONFIRMED

[ ] 결제 성공 시 Seat SOLD

[ ] 사용자 Reservation 조회 가능

[ ] 주요 상태 변경에 @Transactional 적용

[ ] 정상 시나리오 Integration Test 통과
```

---

# 47. Phase 1에서 반드시 이해해야 할 개념

Phase 1은 단순 CRUD 구현이 목적이 아니다.

다음 개념을 이해하고 넘어가야 한다.

## HTTP

```text
Request
Response
HTTP Method
Status Code
JSON
```

## Spring

```text
Dependency Injection

Controller

Service

Repository

Bean
```

## JPA

```text
Entity

Persistence Context

Dirty Checking

Entity Relationship

Lazy Loading
```

## Database

```text
Primary Key

Foreign Key

Unique Constraint

Index

Transaction
```

## Transaction

```text
Atomicity

Commit

Rollback

@Transactional
```

## Domain Modeling

```text
Entity

State

State Transition

Business Rule
```

---

# 48. Phase 1의 의도적인 한계

현재 구조는 동시 요청에 안전하다고 보장하지 않는다.

예를 들어 같은 좌석에 동시에 여러 요청이 들어오는 경우:

```text
Seat A-1

AVAILABLE
```

```text
User A ─┐
User B ─┼── 동시에 reserve()
User C ─┘
```

현재 구조에서는 여러 요청이 동시에 `AVAILABLE`을 읽을 가능성이 있다.

Phase 1에서는 이를 해결하지 않는다.

즉 Phase 1의 최종 시스템은:

```text
기능적으로는 정상 동작하지만

고동시성 환경에서는 안전하지 않은

단일 서버 티켓팅 시스템
```

이다.

이 한계가 이후 단계에서 본격적인 백엔드 시스템 문제를 학습하기 위한 출발점이 된다.
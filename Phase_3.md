# Ticketing: Survive the Traffic — Phase 3

## 1. Phase 3 개요

### 1.1 목표

Phase 3의 목표는 **동시성 정합성을 확보한 현재 시스템에 실제 HTTP 부하를 발생시키고, 서버와 데이터베이스의 병목 지점을 측정·분석·개선하는 것**이다.

Phase 1에서는 단일 요청 환경에서 기능적으로 정상 동작하는 티켓팅 시스템을 구현했다.

```text
Phase 1
Single Request Correctness

한 사용자가 요청했을 때
예약과 결제가 정상적으로 동작하는가?
```

Phase 2에서는 여러 사용자가 동일 좌석에 동시에 접근할 때 발생하는 Race Condition을 재현하고, Database Lock과 Constraint를 이용해 동시성 정합성을 개선했다.

```text
Phase 2
Concurrent Request Correctness

여러 사용자가 동시에
같은 좌석을 예약하더라도

하나의 Reservation만 생성되는가?
```

Phase 3에서는 다음 질문으로 확장한다.

```text
정합성은 지켰다.

그런데 요청이 많이 들어오면
서버와 DB가 실제로 버틸 수 있는가?
```

즉 Phase 3의 핵심은:

```text
Load
↓
Measure
↓
Find Bottleneck
↓
Analyze
↓
Improve
↓
Measure Again
```

이다.

---

## 1.2 Phase 3 핵심 질문

Phase 3에서는 다음 질문에 답할 수 있어야 한다.

```text
현재 서버는 초당 몇 개의 요청을 처리할 수 있는가?

동시 사용자가 증가하면
어느 시점부터 Latency가 급격히 증가하는가?

Tomcat Thread Pool은 어떤 역할을 하는가?

HikariCP Connection Pool은 어떤 역할을 하는가?

Thread가 부족한 것과
DB Connection이 부족한 것은 어떻게 다른가?

Lock Wait은 실제 API Latency에 어떤 영향을 주는가?

Slow Query는 어떻게 찾는가?

Index를 추가하면 언제 성능이 개선되는가?

JPA N+1 문제는 실제로 Query 수와 Latency를 얼마나 증가시키는가?

설정을 바꿀 때
무엇이 실제 개선 원인이었는지 어떻게 확인할 수 있는가?
```

---

# 2. Phase 3 시스템 구조

현재 시스템은 아직 다음과 같은 구조다.

```text
Clients
   │
   │ HTTP Requests
   ▼
┌─────────────────┐
│   Spring Boot   │
│                 │
│ Tomcat          │
│ Thread Pool     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│    HikariCP     │
│ Connection Pool │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   PostgreSQL    │
└─────────────────┘
```

Phase 3에서는 이 구조를 유지하면서 각 계층의 처리 능력과 병목을 확인한다.

아직 다음 기술은 적극적으로 도입하지 않는다.

```text
Redis Cache
Waiting Queue
Kafka
Distributed Lock
Scale-out
Load Balancer
CDN
Event Driven Architecture
```

먼저 현재 구조가 어디서 느려지는지 확인하는 것이 목적이다.

---

# 3. Phase 3 범위

## 구현 및 학습 대상

```text
HTTP Load Test
k6
Virtual User
RPS
Throughput
Latency
Average Latency
p95
p99
Error Rate
Tomcat Thread Pool
HikariCP
Connection Pool
Pool Exhaustion
Connection Timeout
Lock Contention
Lock Wait
Slow Query
PostgreSQL EXPLAIN
EXPLAIN ANALYZE
Sequential Scan
Index Scan
Database Index
JPA N+1
Fetch Join
EntityGraph
성능 Before / After 비교
```

## Phase 3에서 아직 구현하지 않는 기능

```text
Redis Cache
Redis Distributed Lock
Redis Waiting Queue
Kafka
Saga
Outbox Pattern
Server Scale-out
Load Balancer
Kubernetes
Autoscaling
Distributed Tracing
Chaos Engineering
```

이러한 기술은 Phase 3에서 병목을 직접 확인한 뒤 이후 단계에서 도입한다.

---

# 4. Phase 3의 핵심 원칙

Phase 3에서는 다음 방식으로 개발하지 않는다.

```text
느리다
↓
Redis 추가
↓
Thread 늘림
↓
Connection 늘림
↓
Index 여러 개 추가
↓
빨라졌는지 확인
```

이 방식의 문제는:

```text
무엇 때문에 빨라졌는지 알 수 없다.
```

는 것이다.

따라서 항상 다음 순서를 따른다.

```text
Baseline 측정
↓
병목 후보 확인
↓
하나의 변수 변경
↓
동일 조건으로 재측정
↓
결과 비교
```

---

# 5. Load Test

Phase 3에서는 실제 HTTP 요청을 대량으로 발생시킨다.

기존 테스트:

```text
JUnit
↓
Service / Controller
↓
기능과 정합성 검증
```

Phase 3:

```text
Load Generator
↓
HTTP
↓
Tomcat
↓
Controller
↓
Service
↓
JPA
↓
HikariCP
↓
PostgreSQL
```

전체 시스템의 성능을 측정한다.

---

# 6. Load Test Tool

Phase 3에서는 `k6`를 기본 부하 테스트 도구로 사용한다.

예:

```javascript
import http from 'k6/http';

export const options = {
    vus: 100,
    duration: '30s',
};

export default function () {
    http.get('http://localhost:8080/api/concerts/1/seats');
}
```

의미:

```text
Virtual Users = 100
Duration = 30초
GET /api/concerts/1/seats 반복 호출
```

---

# 7. Virtual User

k6에서 `VU`는 `Virtual User`를 의미한다.

예:

```text
vus = 100
```

이면 100명의 가상 사용자가 동시에 요청을 수행하는 것처럼 부하를 생성한다.

실제 사용자 수와 완전히 동일한 개념은 아니지만, 서버에 동시 요청을 발생시키는 기본 단위로 사용한다.

---

# 8. Phase 3 첫 번째 API

처음부터 예약 API에 부하를 주지 않는다.

우선 다음 조회 API를 사용한다.

```http
GET /api/concerts/{concertId}/seats
```

이유:

예약 API에는 이미 다음 요소가 존재한다.

```text
Transaction
UPDATE
INSERT
Pessimistic Lock
Lock Wait
```

따라서 처음부터 예약 API를 사용하면 병목 원인이 너무 많아진다.

먼저 단순 조회 API로:

```text
Spring Boot
+
JPA
+
HikariCP
+
PostgreSQL
```

의 기본 성능을 확인한다.

---

# 9. Baseline Performance

아무 설정도 변경하지 않은 상태에서 성능을 측정한다.

이 결과를 `Baseline`으로 사용한다.

예:

```text
Scenario
GET /api/concerts/1/seats

VUs = 10
Duration = 30s

RPS = ?
Average Latency = ?
p95 = ?
p99 = ?
Error Rate = ?
```

를 기록한다.

---

# 10. 단계적인 부하 증가

처음부터 매우 큰 부하를 발생시키지 않는다.

```text
10 VUs
↓
50 VUs
↓
100 VUs
↓
200 VUs
↓
300 VUs
```

처럼 증가시킨다.

목표는:

```text
어느 구간에서 성능이 급격히 나빠지는가?
```

를 찾는 것이다.

---

# 11. RPS

RPS는 `Requests Per Second`를 의미한다.

즉:

> 서버가 1초 동안 처리한 HTTP Request의 수

이다.

예:

```text
RPS = 500
```

이면 초당 약 500개의 요청을 처리했다는 뜻이다.

---

# 12. Throughput

Throughput은 시스템이 일정 시간 동안 처리할 수 있는 작업량이다.

HTTP 서버에서는 흔히 RPS로 관찰한다.

예:

```text
10 VU
RPS = 300

100 VU
RPS = 1000

200 VU
RPS = 1050
```

라면 100 VU에서 200 VU로 부하를 두 배 늘렸지만 처리량은 거의 증가하지 않았다.

이 시점에서 시스템 내부의 어떤 Resource가 포화되었을 가능성을 의심할 수 있다.

---

# 13. Latency

Latency는 요청을 보낸 뒤 응답을 받을 때까지 걸린 시간이다.

예:

```text
Request
20:00:00.000

Response
20:00:00.080
```

이면:

```text
Latency = 80ms
```

이다.

---

# 14. Average Latency의 한계

예:

```text
Average Latency = 80ms
```

만 보면 서버가 충분히 빠른 것처럼 보일 수 있다.

하지만 실제 분포가:

```text
99% 요청
50ms

1% 요청
3초
```

라면 일부 사용자는 매우 느린 응답을 경험한다.

따라서 평균만으로 성능을 평가하지 않는다.

---

# 15. Percentile

Phase 3에서는 `p95`, `p99`를 중요하게 본다.

예:

```text
p95 = 300ms
```

의 의미:

> 전체 요청의 95%가 300ms 이하에서 처리되었다.

즉 나머지 5%는 300ms보다 느렸다.

---

# 16. p99

예:

```text
p99 = 2s
```

의 의미:

> 전체 요청의 99%는 2초 이내에 처리되었지만, 가장 느린 1%는 2초보다 오래 걸렸다.

티켓팅처럼 특정 순간에 트래픽이 집중되는 시스템에서는 평균뿐 아니라 Tail Latency를 보는 것이 중요하다.

---

# 17. Error Rate

Load가 증가하면 단순히 느려지는 것뿐 아니라 요청이 실패할 수 있다.

예:

```text
HTTP 500
HTTP 503
Connection Timeout
DB Connection 획득 실패
```

따라서 Error Rate도 반드시 기록한다.

---

# 18. Baseline 기록 예시

| Scenario | VUs | RPS | Avg | p95 | p99 | Error Rate |
|---|---:|---:|---:|---:|---:|---:|
| Seat GET | 10 |  |  |  |  |  |
| Seat GET | 50 |  |  |  |  |  |
| Seat GET | 100 |  |  |  |  |  |
| Seat GET | 200 |  |  |  |  |  |

Phase 3의 모든 개선은 이 Baseline과 비교한다.

---

# 19. 성능 붕괴 지점 찾기

예:

```text
10 VU
p95 = 25ms

50 VU
p95 = 40ms

100 VU
p95 = 70ms

200 VU
p95 = 400ms

300 VU
p95 = 2.5s
```

이런 결과가 나오면 100~200 VU 사이에서 성능이 급격히 악화된 것이다.

그 다음 질문:

```text
왜 200명부터 갑자기 느려지는가?
```

를 조사한다.

---

# 20. Tomcat Thread Pool

Spring MVC에서 HTTP Request는 일반적으로 Tomcat Worker Thread에서 처리된다.

```text
HTTP Request
↓
Tomcat
↓
Thread Pool
↓
Controller
↓
Service
```

즉 동시에 요청을 처리하려면 Worker Thread가 필요하다.

---

# 21. Thread Pool

매 요청마다 새로운 Thread를 무제한 생성하지 않는다.

미리 관리되는 Thread Pool을 사용한다.

예:

```text
Tomcat Worker Thread = 200
Concurrent Request = 500
```

이라면 개념적으로:

```text
200 요청
→ Worker Thread 획득

나머지 요청
→ 처리 가능 시점까지 대기
```

할 수 있다.

---

# 22. Tomcat 설정

Spring Boot에서는 Tomcat Thread 관련 설정을 조정할 수 있다.

```yaml
server:
  tomcat:
    threads:
      max: 200
```

Phase 3에서는 이 설정의 의미를 이해한다.

---

# 23. Thread를 무작정 늘리면 안 되는 이유

예:

```text
Thread 부족
↓
200 → 1000
```

으로 바꾼다고 무조건 성능이 좋아지는 것은 아니다.

Application 내부에서 Database를 사용한다면:

```text
Tomcat Thread
↓
DB Connection 필요
```

하기 때문이다.

즉 다음 병목이 Database Connection일 수 있다.

---

# 24. HikariCP

Spring Boot에서는 일반적으로 HikariCP를 통해 DB Connection Pool을 관리한다.

```text
HTTP Request
↓
Tomcat Thread
↓
HikariCP
↓
DB Connection 획득
↓
PostgreSQL
↓
DB Connection 반환
```

---

# 25. Database Connection

Spring Application이 SQL을 실행하려면 PostgreSQL과 연결된 Connection이 필요하다.

매 요청마다 Connection을 새로 생성하고 종료하면 비용이 크다.

따라서 이미 생성된 Connection을 Pool에 보관하고 재사용한다.

---

# 26. Connection Pool

예:

```text
HikariCP

Connection 1
Connection 2
Connection 3
...
Connection 10
```

Application은 Query가 필요할 때:

```text
Connection 빌림
↓
Query 수행
↓
Connection 반환
```

한다.

---

# 27. Thread Pool과 Connection Pool의 차이

## Thread Pool

```text
HTTP 요청을 처리할 Application Worker
```

## Connection Pool

```text
Database와 통신할 DB Connection
```

예:

```text
Tomcat Threads = 200
Hikari Connections = 10
```

이면 200개의 요청을 Application Thread가 처리할 수 있더라도 DB Query를 동시에 수행할 수 있는 Connection은 제한된다.

---

# 28. Connection Wait

예:

```text
Hikari Pool Size = 10
동시에 DB를 사용하는 요청 = 100
```

이면:

```text
10 Request
→ Connection 획득

90 Request
→ Connection 반환을 기다림
```

이 발생할 수 있다.

---

# 29. 세 가지 Wait

현재 시스템에는 여러 종류의 대기가 존재할 수 있다.

```text
HTTP Request
   │
   ▼
Thread 획득 대기
   │
   ▼
Connection 획득 대기
   │
   ▼
Database Lock 대기
   │
   ▼
Query 실행
```

즉:

```text
Thread Wait
Connection Wait
Lock Wait
```

는 서로 다른 문제다.

---

# 30. Connection Pool 고갈 실험

일부러 Hikari Connection Pool을 작게 설정해본다.

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 5
```

그리고:

```text
Concurrent Requests = 100
```

을 발생시킨다.

관찰:

```text
Latency 증가
Connection 대기
Connection Timeout 발생 여부
```

---

# 31. Hikari Pool Size 비교

다음과 같이 실험한다.

```text
maximum-pool-size = 5
↓
10
↓
20
↓
30
```

각 설정에서 동일한 Load Test를 수행한다.

기록:

```text
RPS
Average Latency
p95
p99
Error Rate
```

---

# 32. Connection을 무작정 늘리면 안 되는 이유

Connection을 늘린다고 무조건 좋아지지 않는다.

PostgreSQL도 동시에 처리할 수 있는 작업량에 한계가 있다.

Connection이 지나치게 많아지면:

```text
DB CPU 증가
Memory 사용 증가
Context Switching 증가
동시에 실행되는 Query 증가
DB Resource 경쟁 증가
```

등이 발생할 수 있다.

따라서 Connection Pool Size도 Trade-off가 존재한다.

---

# 33. Lock Contention과 Phase 2 연결

Phase 2에서 동일 좌석에 Pessimistic Lock을 적용했다.

예:

```text
Seat #1
```

100명이 동시에 예약하면:

```text
Request 1
Lock 획득

Request 2 ~ 100
Lock Wait
```

가 발생할 수 있다.

Phase 2에서는 중복 Reservation이 발생하지 않는지를 검증했다.

Phase 3에서는:

```text
Lock 때문에 실제 API Latency가 얼마나 증가하는가?
```

를 측정한다.

---

# 34. Hot Seat Scenario

여러 사용자가 하나의 Seat를 동시에 예약한다.

```text
100 Users
↓
Seat #1
```

특징:

```text
높은 Lock Contention
```

---

# 35. Distributed Seat Scenario

100명의 사용자가 서로 다른 좌석을 예약한다.

```text
User 1 → Seat 1
User 2 → Seat 2
User 3 → Seat 3
...
User 100 → Seat 100
```

Row Lock은 서로 다른 Row에 걸리므로 동일 좌석 경쟁보다 Lock Contention이 작다.

---

# 36. Hot Seat vs Distributed Seats

동일한 100 Concurrent Requests라도 Traffic Pattern에 따라 결과가 달라질 수 있다.

```text
Hot Seat
100 Requests
↓
1 Row
↓
높은 Lock Contention
```

```text
Distributed Seats
100 Requests
↓
100 Rows
↓
낮은 Lock Contention
```

즉 시스템 부하는 단순히 사용자 수만으로 설명할 수 없다.

---

# 37. Traffic Pattern

실제 부하는 다음 요소에 영향을 받는다.

```text
Concurrent User 수
Request Rate
Read / Write 비율
접근하는 Resource 수
동일 Resource 경쟁 비율
Transaction 수행 시간
Query 수행 시간
```

Phase 3에서는 부하 테스트 시나리오를 명확하게 정의해야 한다.

---

# 38. Slow Query

트래픽이 커질수록 Database Query 성능이 중요해진다.

예:

```sql
SELECT *
FROM seat
WHERE concert_id = ?;
```

Seat가 100 Rows일 때는 빠를 수 있다.

하지만 데이터가 커지면 성능 차이가 나타날 수 있다.

---

# 39. PostgreSQL EXPLAIN

Query가 어떻게 실행되는지 확인하기 위해 `EXPLAIN`을 사용한다.

```sql
EXPLAIN
SELECT *
FROM seat
WHERE concert_id = 1;
```

이를 통해 PostgreSQL이 어떤 Execution Plan을 선택했는지 확인한다.

---

# 40. EXPLAIN ANALYZE

실제 Query를 실행하면서 수행 시간과 실제 처리 Row를 확인하려면:

```sql
EXPLAIN ANALYZE
SELECT *
FROM seat
WHERE concert_id = 1;
```

를 사용한다.

Phase 3에서는 단순 Query 실행 시간뿐 아니라 Execution Plan을 읽는 연습을 한다.

---

# 41. Sequential Scan

Execution Plan에:

```text
Seq Scan
```

이 나타날 수 있다.

의미:

```text
Table의 Row를 순차적으로 확인
```

한다는 것이다.

작은 Table에서는 Sequential Scan이 오히려 효율적일 수도 있다.

---

# 42. Index Scan

적절한 Index가 존재하고 PostgreSQL이 이를 사용하는 것이 유리하다고 판단하면:

```text
Index Scan
```

이 나타날 수 있다.

Index를 통해 필요한 Row 위치를 빠르게 찾는다.

---

# 43. Index

Index는 조회 성능을 높이기 위해 별도의 검색 구조를 유지한다.

예:

```text
seat.concert_id
```

로 공연별 Seat를 자주 조회한다면 Index 후보가 될 수 있다.

하지만:

```text
Index가 많을수록 무조건 좋다
```

는 아니다.

---

# 44. Index 비용

Index는 다음 비용을 가진다.

```text
추가 Storage
INSERT 비용 증가
UPDATE 비용 증가
DELETE 비용 증가
Index 유지 비용
```

따라서 실제 Query Pattern을 기준으로 필요한 Index만 추가한다.

---

# 45. Phase 3 Index 후보

현재 Domain에서 관찰할 후보는 다음과 같다.

```text
seat.concert_id
reservation.user_id
reservation.seat_id
```

단 실제 Index 적용 여부는 Execution Plan을 확인한 뒤 결정한다.

---

# 46. Index 적용 순서

```text
Query 확인
↓
데이터 규모 증가
↓
EXPLAIN ANALYZE 확인
↓
병목 확인
↓
Index 추가
↓
EXPLAIN ANALYZE 재실행
↓
Load Test 재실행
```

---

# 47. JPA N+1 Problem

현재 사용자 Reservation 조회에서는 개념적으로:

```text
Reservation
↓
Seat
↓
Concert
```

관계를 사용한다.

DTO 변환에서:

```java
reservation.getSeat()
           .getConcert()
           .getTitle()
```

같은 접근이 존재한다.

---

# 48. N+1 발생 예시

Reservation 100개를 조회한다고 가정한다.

먼저:

```sql
SELECT *
FROM reservation
WHERE user_id = ?;
```

1개의 Query가 실행된다.

그 후 Lazy Loading 때문에 Reservation마다 Seat 조회가 발생하면:

```text
Seat Query 100개
```

가 추가될 수 있다.

즉:

```text
1 + N
```

개의 Query가 발생한다.

이를 N+1 Problem이라고 한다.

---

# 49. N+1 문제의 영향

데이터가 적을 때는 체감하기 어려울 수 있다.

하지만 Reservation이 100개, 1000개로 늘면 Query 수가 크게 증가한다.

결과:

```text
DB Round Trip 증가
Connection 점유 증가
Latency 증가
DB 부하 증가
```

가 발생할 수 있다.

---

# 50. N+1 확인 방법

Phase 3에서는 Hibernate SQL 로그를 확인한다.

```text
사용자 Reservation 조회 API 1회 호출
```

했을 때:

```text
SQL이 몇 번 실행되는가?
```

를 확인한다.

단순히 기능이 정상 동작하는 것만으로는 충분하지 않다.

---

# 51. N+1 해결 후보

N+1이 실제로 발생한다면 다음 방법을 비교할 수 있다.

```text
Fetch Join
@EntityGraph
DTO Projection
```

단 모든 Relation을 EAGER로 바꾸는 방식은 사용하지 않는다.

---

# 52. Fetch Join

예:

```jpql
select r
from Reservation r
join fetch r.seat s
join fetch s.concert
where r.user.id = :userId
```

처럼 하나의 Query에서 필요한 연관 Entity를 함께 조회할 수 있다.

Phase 3에서는 실제 SQL과 Query 수를 비교한다.

---

# 53. Before / After 측정

모든 개선은 동일한 부하 조건에서 비교한다.

예:

```text
Before

User Reservation API
100 VUs
p95 = 800ms
```

N+1 개선 후:

```text
After

100 VUs
p95 = 150ms
```

처럼 비교한다.

---

# 54. 설정 변경 원칙

다음처럼 여러 설정을 동시에 변경하지 않는다.

```text
Tomcat Thread
+
Hikari Pool
+
Index
+
Fetch Join
```

을 모두 한 번에 변경하면 어떤 변화가 성능에 영향을 주었는지 알 수 없다.

따라서:

```text
Baseline
↓
Change A
↓
Measure
↓
Change B
↓
Measure
↓
Change C
↓
Measure
```

순서를 따른다.

---

# 55. Phase 3 Experiment 1

## Seat 조회 Baseline

### Target

```http
GET /api/concerts/1/seats
```

### Load

```text
10 VUs
50 VUs
100 VUs
200 VUs
```

### Measure

```text
RPS
Average Latency
p95
p99
Error Rate
```

### Goal

현재 시스템의 기본 HTTP + DB 조회 성능을 파악한다.

---

# 56. Phase 3 Experiment 2

## Hikari Pool Size

### Settings

```text
maximum-pool-size = 5
10
20
```

### Load

동일한 조회 부하를 사용한다.

### Goal

Connection Pool Size가 Latency와 Throughput에 미치는 영향을 확인한다.

---

# 57. Phase 3 Experiment 3

## Same Seat Reservation

### Given

```text
Seat #1
AVAILABLE
```

### When

```text
100 Concurrent Reservation Requests
```

### Goal

```text
Pessimistic Lock Contention
Lock Wait
Latency 증가
```

를 관찰한다.

---

# 58. Phase 3 Experiment 4

## Different Seat Reservation

### Given

```text
Seat 1 ~ Seat 100
AVAILABLE
```

### When

100명의 사용자가 각각 다른 Seat를 예약한다.

### Goal

Hot Seat Scenario와 비교한다.

---

# 59. Phase 3 Experiment 5

## Large Dataset + Index

Seat 데이터를 충분히 증가시킨다.

```text
100
↓
10,000
↓
100,000+
```

가능한 범위에서 데이터 규모를 늘린다.

공연별 좌석 조회 Query에 대해 EXPLAIN ANALYZE를 실행한다.

Index 전후 Execution Plan과 Latency를 비교한다.

---

# 60. Phase 3 Experiment 6

## User Reservation N+1

사용자에게 많은 Reservation History를 생성한다.

예:

```text
100 Reservations
```

조회:

```http
GET /api/users/{userId}/reservations
```

확인:

```text
SQL Query Count
Latency
```

N+1이 발생하면 Fetch Join 등의 방식으로 개선 후 다시 측정한다.

---

# 61. Experiment Result Table

| Experiment | Setting | VUs | RPS | Avg | p95 | p99 | Error |
|---|---|---:|---:|---:|---:|---:|---:|
| Seat GET | Baseline | 10 |  |  |  |  |  |
| Seat GET | Baseline | 50 |  |  |  |  |  |
| Seat GET | Baseline | 100 |  |  |  |  |  |
| Seat GET | Hikari 5 | 100 |  |  |  |  |  |
| Seat GET | Hikari 10 | 100 |  |  |  |  |  |
| Same Seat Reserve | Pessimistic | 100 |  |  |  |  |  |
| Different Seat Reserve | Pessimistic | 100 |  |  |  |  |  |

---

# 62. Phase 3에서 관찰해야 할 병목

```text
Tomcat Thread 부족
↓
Request Queue 증가
```

```text
Hikari Connection 부족
↓
Connection Wait 증가
```

```text
Pessimistic Lock 경쟁
↓
Lock Wait 증가
```

```text
Slow Query
↓
Connection 점유 시간 증가
```

```text
N+1
↓
Query 수 증가
```

이 요소들은 서로 영향을 줄 수 있다.

---

# 63. 병목의 연쇄 효과

예를 들어 Slow Query가 발생하면:

```text
Query가 오래 걸림
↓
DB Connection을 오래 점유
↓
Hikari Pool 반환이 늦음
↓
다른 Thread가 Connection 대기
↓
HTTP Latency 증가
↓
Tomcat Thread도 오래 점유
```

즉 하나의 DB 문제는 Application 전체의 병목으로 확산될 수 있다.

---

# 64. SQL 로그

Phase 3에서는 Hibernate SQL 로그를 적극적으로 확인한다.

확인할 것:

```text
API 요청 1회당 SQL 개수
불필요한 Query 존재 여부
N+1 발생 여부
SELECT FOR UPDATE 여부
반복되는 Query
```

---

# 65. PostgreSQL 상태 확인

필요하면 다음 PostgreSQL 관찰 도구를 활용한다.

```text
pg_stat_activity
pg_locks
pg_blocking_pids()
```

부하 상황에서 몇 개의 Transaction이 기다리고 있는지 관찰할 수 있다.

---

# 66. Thread와 Connection의 관계

다음 상황을 생각한다.

```text
Tomcat Thread = 200
Hikari Connection = 10
```

동시에 200개의 DB 요청이 들어오면:

```text
10 Threads
DB Query 수행

190 Threads
Connection 대기
```

할 수 있다.

---

# 67. Connection과 Lock의 관계

다음 상황:

```text
10 DB Connections
```

이 모두 동일 Seat Lock Wait에 걸려 있다면 새로운 요청은 Hikari Connection조차 얻지 못할 수 있다.

즉:

```text
Lock Contention
↓
Connection Pool 고갈
↓
전체 API Latency 증가
```

로 발전할 수 있다.

---

# 68. Hotspot Resource

특정 하나의 Resource에 트래픽이 집중되는 것을 Hotspot으로 볼 수 있다.

예:

```text
Seat #1
↓
10,000 Reservation Requests
```

전체 DB에는 충분한 처리 능력이 있어도 특정 Row의 Lock 때문에 처리량이 제한될 수 있다.

---

# 69. Phase 3에서 하지 말아야 할 판단

잘못된 판단:

```text
p95가 높음
↓
Thread를 늘리자
```

실제 원인이 DB Lock Wait이라면 Thread를 늘려도 해결되지 않는다.

오히려 더 많은 요청이 DB로 몰려 상황이 악화될 수 있다.

따라서 항상:

```text
Measure First
```

가 원칙이다.

---

# 70. Phase 3 구현 순서

```text
1. 현재 Phase 2 코드 기준점 저장

↓

2. k6 설치 및 기본 Script 작성

↓

3. GET /api/concerts/{id}/seats
   Baseline Test

↓

4. 10 / 50 / 100 / 200 VU 결과 기록

↓

5. RPS / Avg / p95 / p99 / Error Rate 분석

↓

6. Tomcat Thread Pool 구조 학습

↓

7. HikariCP Connection Pool 구조 학습

↓

8. Hikari maximum-pool-size를 작게 설정

↓

9. Connection Pool Wait / Timeout 실험

↓

10. Pool Size 변경 후 동일 Load Test

↓

11. Same Seat 동시 예약 Load Test

↓

12. Lock Contention / Latency 관찰

↓

13. Different Seats 동시 예약과 비교

↓

14. PostgreSQL 대량 Seed Data 생성

↓

15. EXPLAIN / EXPLAIN ANALYZE 실험

↓

16. Sequential Scan / Index Scan 비교

↓

17. 필요한 Index 후보 적용

↓

18. 동일 Query / Load Test 재실행

↓

19. User Reservation 조회 SQL 수 확인

↓

20. N+1 발생 여부 확인

↓

21. 필요하면 Fetch Join / EntityGraph 적용

↓

22. 동일 Load Test 재실행

↓

23. 모든 Before / After 결과 기록

↓

24. 실제 병목과 개선 결과 정리

↓

25. Phase 3 완료
```

---

# 71. Phase 3 완료 조건

```text
[ ] k6 부하 테스트 환경 구성

[ ] 실제 HTTP Load Test 실행 가능

[ ] Virtual User 개념 이해

[ ] RPS 개념 이해

[ ] Throughput 개념 이해

[ ] Latency 개념 이해

[ ] Average Latency의 한계 이해

[ ] p95 / p99 의미 설명 가능

[ ] Error Rate 측정 가능

[ ] GET API Baseline 측정

[ ] 10 / 50 / 100 / 200 VU 비교

[ ] 성능이 급격히 악화되는 구간 확인

[ ] Tomcat Thread Pool 구조 이해

[ ] Thread Pool 부족 시 발생하는 현상 이해

[ ] HikariCP 역할 이해

[ ] DB Connection Pool 개념 이해

[ ] Thread Pool과 Connection Pool 차이 설명 가능

[ ] Connection Pool Wait 재현

[ ] Hikari maximum-pool-size 변경 실험

[ ] Connection을 무작정 늘리면 안 되는 이유 설명 가능

[ ] Same Seat Load Test 실행

[ ] Lock Contention에 따른 Latency 증가 확인

[ ] Different Seats Load Test 실행

[ ] Hot Seat와 Distributed Seat 차이 설명 가능

[ ] PostgreSQL EXPLAIN 사용 가능

[ ] EXPLAIN ANALYZE 사용 가능

[ ] Sequential Scan 이해

[ ] Index Scan 이해

[ ] Index의 장단점 설명 가능

[ ] 실제 Query에 Index 적용 전후 비교

[ ] Hibernate SQL Query 수 확인

[ ] N+1 발생 여부 확인

[ ] N+1 발생 시 해결 방법 적용

[ ] Fetch Join 또는 EntityGraph 원리 이해

[ ] 모든 주요 실험에 Before / After 결과 기록

[ ] 실제 시스템 병목을 최소 하나 이상 발견

[ ] 병목 원인과 해결 방법을 설명 가능
```

---

# 72. Phase 3에서 반드시 이해해야 할 개념

## Load Test

```text
Virtual User
Concurrent Request
Load
Traffic Pattern
Baseline
```

## Performance

```text
Throughput
RPS
Latency
Average
Percentile
p95
p99
Tail Latency
Error Rate
```

## Web Server

```text
Tomcat
Worker Thread
Thread Pool
Request Queue
Blocking I/O
```

## Database Connection

```text
DB Connection
Connection Pool
HikariCP
Pool Exhaustion
Connection Wait
Connection Timeout
```

## Database Performance

```text
Execution Plan
EXPLAIN
EXPLAIN ANALYZE
Sequential Scan
Index Scan
Index
Cardinality
```

## JPA Performance

```text
Lazy Loading
N+1
Fetch Join
EntityGraph
DTO Projection
```

## Concurrency Performance

```text
Lock Contention
Lock Wait
Hotspot Resource
Transaction Duration
```

---

# 73. Phase 3에서 가장 중요한 사고방식

Phase 2까지는:

```text
정답이 맞는가?
```

가 가장 중요한 질문이었다.

Phase 3부터는:

```text
정답이 맞는가?

+

얼마나 빨리 처리되는가?

+

트래픽이 늘어도 유지되는가?

+

어디에서 기다리고 있는가?
```

까지 생각해야 한다.

---

# 74. Functional Correctness vs Performance

예:

```text
1000 Requests
모두 성공
```

했다고 해도:

```text
p99 = 30초
```

라면 실제 서비스 품질은 좋다고 보기 어렵다.

반대로 평균 10ms라고 해도 동시성 문제 때문에 같은 좌석이 여러 번 판매된다면 시스템은 잘못된 것이다.

따라서:

```text
Correctness
+
Performance
```

를 함께 고려해야 한다.

---

# 75. Phase 3의 핵심 변화

```text
Phase 1
Single Request Correctness

↓

Phase 2
Concurrent Request Correctness

↓

Phase 3
Performance Under Load
```

즉:

```text
정상 동작
↓
동시성 정합성
↓
부하 상황의 성능
```

으로 발전한다.

---

# 76. Phase 3의 최종 시스템

Phase 3가 끝나면 시스템은 단순히 동시 예약이 안전한 수준을 넘어:

```text
현재 서버가 어느 정도의 부하를 처리하는지 알고 있다.

어디에서 병목이 발생하는지 찾을 수 있다.

Thread / Connection / Lock Wait을 구분할 수 있다.

Slow Query를 Execution Plan으로 분석할 수 있다.

Index를 근거를 가지고 적용할 수 있다.

N+1을 실제 SQL과 성능으로 확인하고 개선할 수 있다.
```

는 상태가 된다.

---

# 77. Phase 3 이후의 한계

Phase 3에서 현재 구조를 충분히 분석하면 다음과 같은 한계가 드러날 수 있다.

```text
조회 요청이 모두 PostgreSQL까지 전달됨
↓
DB Connection 사용
↓
DB Load 증가
```

또는:

```text
인기 좌석 요청이 모두 DB로 진입
↓
Lock Contention
↓
Connection 장시간 점유
↓
Latency 증가
```

또는:

```text
단일 Spring Boot Instance의
처리 능력 한계
```

가 나타날 수 있다.

---

# 78. 이후 Phase 방향

Phase 3 이후에는 실제로 관찰한 병목을 기반으로 기술을 도입한다.

```text
Phase 4
Redis Cache
Redis 기반 상태 관리

Phase 5
Waiting Queue
Traffic Control
Rate Limiting

Phase 6
Kafka
비동기 Event Processing

Phase 7
Scale-out
Distributed System
Load Balancer
```

중요한 점은:

```text
기술을 먼저 넣는 것이 아니라

문제를 먼저 재현하고

그 문제를 해결하기 위해
기술을 도입한다.
```

는 것이다.

---

# 79. Phase 3 한 문장 정의

> **Phase 3 — Load Test & Bottleneck Analysis**  
> 실제 HTTP 부하 테스트를 통해 현재 Spring Boot + PostgreSQL 티켓팅 시스템의 처리량과 Latency를 측정하고, Tomcat Thread Pool, HikariCP Connection Pool, Database Lock, Query Execution Plan, Index 및 JPA N+1 관점에서 병목을 분석한다. 각 변경 사항을 동일한 부하 조건에서 Before/After로 비교하여 성능 개선의 근거를 확보한다.

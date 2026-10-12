# Ticketing: Survive the Traffic — Phase 4

## 1. Phase 4 개요

### 1.1 목표

Phase 4의 목표는 **반복적인 조회 요청을 Redis Cache로 흡수하여 PostgreSQL Read 부하와 DB Connection 사용량을 줄이고, Cache의 정합성·장애·만료 문제를 직접 실험하는 것**이다.

Phase 3에서는 k6를 이용해 현재 시스템의 처리량과 Latency를 측정하고, Lock Contention, HikariCP, Query Execution Plan, Index, JPA N+1 등의 병목을 분석했다.

Phase 4에서는 다음 질문으로 확장한다.

```text
조회 요청이 많을 때
매번 PostgreSQL까지 내려갈 필요가 있는가?
```

현재 구조:

```text
Client
  │
  ▼
Spring Boot
  │
  ▼
PostgreSQL
```

Phase 4 이후:

```text
Client
  │
  ▼
Spring Boot
  │
  ├──── Redis
  │
  ▼
PostgreSQL
```

Redis를 사용하는 목적은 단순히 "Redis가 빠르기 때문"이 아니다.

```text
반복 Read 요청
↓
DB Query 감소
↓
DB Connection 사용 감소
↓
PostgreSQL 부하 감소
↓
핵심 Transaction 처리 여유 확보
```

가 핵심이다.

---

## 1.2 Phase 4 핵심 질문

Phase 4에서는 다음 질문에 답할 수 있어야 한다.

```text
왜 Redis Cache가 필요한가?

어떤 데이터를 캐시해야 하는가?

어떤 데이터는 캐시하면 위험한가?

Cache Hit / Miss는 무엇인가?

Cache-Aside Pattern은 어떻게 동작하는가?

TTL은 왜 필요한가?

Cache Invalidation은 왜 어려운가?

DB와 Redis 값이 다르면 어떤 문제가 생기는가?

Cold Cache와 Warm Cache는 무엇인가?

Cache Stampede는 무엇인가?

Cache Penetration은 무엇인가?

Redis가 장애 나면 서비스는 어떻게 동작해야 하는가?

Redis 적용 전후 효과를 어떤 지표로 검증해야 하는가?
```

---

# 2. Phase 4 시스템 구조

기본 구조:

```text
                ┌──────────────┐
                │    Client    │
                └──────┬───────┘
                       │
                       ▼
                ┌──────────────┐
                │ Spring Boot  │
                └──────┬───────┘
                       │
             ┌─────────┴─────────┐
             │                   │
             ▼                   ▼
      ┌──────────────┐    ┌──────────────┐
      │    Redis     │    │ PostgreSQL   │
      └──────────────┘    └──────────────┘
```

조회 흐름:

```text
Request
  │
  ▼
Redis 조회
  │
  ├─ HIT ─────────────→ Response
  │
  └─ MISS
      │
      ▼
 PostgreSQL 조회
      │
      ▼
 Redis 저장
      │
      ▼
   Response
```

이 구조를 기본적으로 **Cache-Aside Pattern**으로 사용한다.

---

# 3. Phase 4 범위

## 구현 및 학습 대상

```text
Redis

Spring Data Redis

RedisTemplate

JSON Serialization

Cache-Aside Pattern

Cache Hit

Cache Miss

Cold Cache

Warm Cache

TTL

Cache Invalidation

Cache Consistency

Stale Data

Cache Warming

Cache Stampede

Cache Penetration

Redis 장애 Fallback

Cache Hit Rate

k6 Before / After 비교

DB Query 감소 확인
```

---

## Phase 4에서 아직 하지 않는 기능

```text
Redis Waiting Queue

Admission Token

SSE

Distributed Lock

Kafka

Redis Cluster

Redis Sentinel

Server Scale-out

Kubernetes
```

대기열과 트래픽 제어는 이후 Phase에서 다룬다.

---

# 4. 캐시 대상 선정 원칙

캐시가 적합한 데이터는 일반적으로 다음 특징을 가진다.

```text
조회 빈도가 높다.

변경 빈도가 낮다.

여러 사용자가 동일 데이터를 반복 조회한다.

DB 조회를 줄였을 때 효과가 크다.

약간의 Staleness를 허용할 수 있다.
```

캐시가 어려운 데이터:

```text
변경 빈도가 높다.

정확한 최신 상태가 매우 중요하다.

사용자별 데이터다.

Transaction 정합성과 직접 연결된다.
```

---

# 5. 현재 API의 Cache 적합성

현재 주요 조회 API:

```text
GET /api/concerts

GET /api/concerts/{concertId}

GET /api/concerts/{concertId}/seats

GET /api/users/{userId}/reservations
```

| API | 변경 빈도 | 사용자별 | Cache 적합성 |
|---|---:|---:|---|
| 공연 목록 | 낮음 | X | 매우 높음 |
| 공연 상세 | 낮음 | X | 매우 높음 |
| 좌석 목록 | 높음 | X | 주의 필요 |
| 사용자 예약 내역 | 높음 | O | 낮음 |

---

# 6. 첫 번째 Cache 대상

첫 번째 Cache 대상은:

```http
GET /api/concerts/{concertId}
```

예:

```http
GET /api/concerts/1
```

공연 정보:

```text
title

reservationOpenAt

concertAt
```

은 자주 변경되지 않지만 여러 사용자가 반복해서 조회할 가능성이 높다.

기존:

```text
User A ─┐
User B ─┤
User C ─┼─→ PostgreSQL SELECT
User D ─┤
...     │
User N ─┘
```

Redis 적용 후:

```text
첫 요청
Redis MISS
↓
PostgreSQL
↓
Redis 저장

이후 요청
Redis HIT
↓
PostgreSQL 접근 없음
```

---

# 7. 좌석 API를 첫 Cache 대상으로 쓰지 않는 이유

좌석 응답에는 동적인 상태가 있다.

```json
{
  "seatId": 1,
  "section": "A",
  "row": 1,
  "seatNumber": 1,
  "price": 150000,
  "status": "AVAILABLE"
}
```

`status`는:

```text
AVAILABLE
↓
HELD
↓
SOLD
```

로 빠르게 바뀐다.

Redis가:

```text
Seat #1 = AVAILABLE
```

을 가지고 있는데 PostgreSQL이:

```text
Seat #1 = HELD
```

라면 사용자가 잘못된 좌석 상태를 보게 된다.

이를:

```text
Stale Data
```

라고 한다.

따라서 좌석은 캐시 정합성 전략을 별도로 검토한다.

---

# 8. Static Data와 Dynamic Data

좌석에서도 정적인 값과 동적인 값이 나뉜다.

정적:

```text
section
row
seatNumber
price
```

동적:

```text
status
```

후반부에는 다음 전략을 검토할 수 있다.

```text
전체 좌석 응답 Cache
```

또는:

```text
정적 좌석 정보만 Cache
+
상태는 별도 조회
```

하지만 Phase 4 초반에는 공연 상세로 Redis 기본 원리를 익힌다.

---

# 9. Cache-Aside Pattern

동작:

```text
1. Redis 조회

2. 값이 있으면 반환

3. 값이 없으면 DB 조회

4. DB 결과를 Redis에 저장

5. 반환
```

개념 코드:

```java
public ConcertResponse getConcert(Long concertId) {

    String key = "concert:" + concertId;

    ConcertResponse cached = redis.get(key);

    if (cached != null) {
        return cached;
    }

    Concert concert = concertRepository.findById(concertId)
        .orElseThrow(...);

    ConcertResponse response = ConcertResponse.from(concert);

    redis.set(key, response);

    return response;
}
```

---

# 10. Cache Hit

Redis:

```text
concert:1
→ 존재
```

이면:

```text
Cache Hit
```

흐름:

```text
Request
↓
Redis
↓
Response
```

PostgreSQL Query는 발생하지 않는다.

---

# 11. Cache Miss

Redis:

```text
concert:1
→ 없음
```

이면:

```text
Cache Miss
```

흐름:

```text
Request
↓
Redis MISS
↓
PostgreSQL
↓
Redis 저장
↓
Response
```

---

# 12. Cold Cache

Redis가 비어 있는 상태.

```text
concert:1 없음
```

첫 요청:

```text
MISS
↓
DB
↓
Redis 저장
```

이 발생한다.

---

# 13. Warm Cache

Redis에 값이 이미 저장된 상태.

```text
concert:1 존재
```

이후 요청:

```text
HIT
↓
DB Query 없음
```

Phase 4에서는 Cold Cache와 Warm Cache를 구분해 측정한다.

---

# 14. Redis Key 설계

공연 상세:

```text
concert:{concertId}
```

예:

```text
concert:1
concert:2
concert:100
```

가능하면 Key 이름만 봐도 Domain과 식별자를 알 수 있게 한다.

---

# 15. Redis Value와 Serialization

Redis에는 `ConcertResponse`를 그대로 저장할 수 없으므로 직렬화가 필요하다.

```text
Java Object
↓
Serialization
↓
Redis
```

조회:

```text
Redis
↓
Deserialization
↓
Java Object
```

Phase 4에서는 JSON 기반 직렬화를 사용한다.

---

# 16. Redis Docker 구성

Redis는 PostgreSQL과 마찬가지로 Docker로 실행한다.

개념적인 `docker-compose.yml`:

```yaml
services:
  postgres:
    ...

  redis:
    image: redis:7
    container_name: ticketing-redis
    ports:
      - "6379:6379"
```

현재 개발 구조:

```text
Spring Boot
Local

PostgreSQL
Docker

Redis
Docker
```

---

# 17. Spring Data Redis

의존성:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

Spring Boot에서 Redis 연결과 RedisTemplate 등을 사용할 수 있다.

---

# 18. Redis 연결 설정

개념적으로:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

실제 사용 중인 Spring Boot 버전에 맞춰 설정 키를 확인한다.

---

# 19. RedisTemplate vs Spring Cache

구현 후보:

```text
RedisTemplate
```

또는:

```text
@Cacheable
@CacheEvict
```

Phase 4 초반에는 Cache Hit/Miss와 Redis 저장 흐름을 명확히 이해하기 위해 `RedisTemplate` 기반 직접 구현을 우선한다.

원리를 이해한 뒤 Spring Cache Abstraction과 비교한다.

---

# 20. TTL

Redis 값을 무기한 유지하면 오래된 데이터가 남을 수 있다.

```text
TTL
=
Time To Live
```

예:

```text
concert:1

TTL = 10분
```

20:00에 저장됐다면:

```text
20:10 만료
```

된다.

---

# 21. TTL 만료 흐름

```text
Cache 존재
↓
TTL 만료
↓
Key 삭제
↓
다음 Request
↓
MISS
↓
DB
↓
Cache 재생성
```

---

# 22. TTL의 한계

TTL만으로 완벽한 최신성을 보장할 수 없다.

```text
TTL = 10분

20:01
DB 수정

20:10
Redis 만료
```

이면 최대 약 9분 동안 Stale Data가 반환될 수 있다.

---

# 23. Cache Invalidation

데이터가 변경되면 관련 Cache를 명시적으로 제거한다.

```text
Concert Update
↓
DB Update
↓
DEL concert:1
```

다음 조회:

```text
MISS
↓
DB 최신값
↓
Redis 재저장
```

---

# 24. Cache Invalidation이 어려운 이유

예:

```text
DB Update 성공
↓
Redis 삭제 실패
```

하면:

```text
PostgreSQL
최신 값

Redis
이전 값
```

이 된다.

Cache를 도입하면 성능은 개선될 수 있지만 새로운 정합성 문제가 생긴다.

---

# 25. Source of Truth

Phase 4에서 역할은 명확하게 둔다.

```text
PostgreSQL
=
Source of Truth
```

```text
Redis
=
Performance Layer
```

정합성 판단 기준은 PostgreSQL이다.

---

# 26. Stale Data 실험

1. 공연 상세 조회로 Cache 생성
2. PostgreSQL에서 공연 제목 직접 수정
3. Redis는 그대로 유지
4. 다시 API 호출

예상:

```text
Redis의 이전 Title 반환
```

이 실험을 통해 Stale Cache를 직접 확인한다.

---

# 27. Cache Invalidation 실험

DB 수정 후:

```text
DEL concert:1
```

수행.

다음 API 호출:

```text
MISS
↓
DB 최신값 조회
↓
Redis 최신값 저장
```

되는지 확인한다.

---

# 28. Cache Warming

서비스가 막 시작된 시점에는 Cache가 비어 있다.

미리 자주 사용될 데이터를 적재하는 전략:

```text
Cache Warming
```

Phase 4에서는 필수 구현보다는 개념과 필요성을 확인한다.

---

# 29. Cache Stampede

인기 Cache가 만료되는 순간 많은 요청이 동시에 들어오면:

```text
Request 1 → MISS
Request 2 → MISS
Request 3 → MISS
...
Request 100 → MISS
```

모든 요청이 DB로 내려갈 수 있다.

```text
100 Requests
↓
100 DB Queries
```

이를:

```text
Cache Stampede
```

라고 한다.

---

# 30. Cache Stampede 대응 후보

```text
TTL Randomization

Single Flight

Mutex / Short Lock

Early Refresh

Cache Warming
```

모두 한 번에 구현하지 않는다.

Stampede가 실제로 재현되면 최소 하나의 대응 전략을 적용하고 Before/After를 비교한다.

---

# 31. TTL Randomization

다수 Key가 동시에 만료되는 것을 줄이기 위해:

```text
TTL = 기본 TTL + Random Jitter
```

예:

```text
10분 + 0~60초
```

처럼 설정할 수 있다.

---

# 32. Cache Penetration

존재하지 않는 ID를 반복 조회하는 경우:

```http
GET /api/concerts/999999
```

매번:

```text
Redis MISS
↓
DB NOT FOUND
```

가 반복될 수 있다.

이를:

```text
Cache Penetration
```

문제로 볼 수 있다.

---

# 33. Null Caching

존재하지 않는 결과도 아주 짧은 TTL로 캐시하는 전략이 있다.

예:

```text
concert:999999 = NOT_FOUND
TTL = 30초
```

단 실제 Resource 생성 가능성과 Invalidation을 고려해야 한다.

---

# 34. Redis 장애

Redis는 Cache이므로 Redis 장애가 전체 조회 API 장애로 바로 이어지지 않도록 설계할 수 있다.

```text
Redis Error
↓
PostgreSQL Fallback
↓
Response
```

---

# 35. Redis 장애 Fallback의 한계

Redis 장애 시 모든 요청이 DB로 전환되면:

```text
Redis Down
↓
DB Traffic 급증
↓
DB 과부하
```

가 될 수 있다.

따라서 Cache 장애도 이후 Traffic Control과 연결된다.

---

# 36. Redis CLI

직접 Cache를 확인한다.

```bash
docker exec -it ticketing-redis redis-cli
```

주요 명령:

```text
GET concert:1

TTL concert:1

DEL concert:1

EXISTS concert:1
```

학습 환경에서는 Key 확인을 위해 간단한 명령을 사용한다.

---

# 37. Phase 4 Baseline

Redis 적용 전에 반드시 Baseline을 측정한다.

Target:

```http
GET /api/concerts/1
```

조건:

```text
50 VUs
30초
```

기록:

```text
RPS
Avg
p95
p99
Max
Error Rate
DB Query Count
```

---

# 38. Baseline k6 Script

```javascript
import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 50,
    duration: '30s',

    summaryTrendStats: [
        'avg',
        'min',
        'med',
        'p(90)',
        'p(95)',
        'p(99)',
        'max',
    ],
};

export default function () {

    const response = http.get(
        'http://localhost:8080/api/concerts/1'
    );

    check(response, {
        'status is 200': (r) => r.status === 200,
    });
}
```

---

# 39. Before / After 비교

| Scenario | Cache |            RPS |  Avg |     p95 |     p99 | Error | DB Query |
|---|---|---------------:|-----:|--------:|--------:|------:|---------:|
| Concert Detail | OFF |  6302.77 req/s | 7.83 | 19.02ms | 39.03ms |     0 |  189,115 |
| Concert Detail | ON Cold |                |      |         |         |       |       10 |
| Concert Detail | ON Warm | 15911.51 req/s | 3.06 |  6.36ms | 11.01ms |     0 |        1 |

---

# 40. Redis 효과를 Latency만으로 판단하지 않는다

localhost에서는 PostgreSQL도 매우 빠르기 때문에 Redis 적용 후 Latency가 극적으로 줄지 않을 수 있다.

더 중요한 것은:

```text
DB Query 감소

DB Connection 사용 감소

DB CPU 부하 감소

DB가 Transaction 처리에 사용할 여유 증가
```

이다.

---

# 41. 예상 DB Query 변화

Redis OFF:

```text
100,000 HTTP Requests
↓
거의 100,000 DB Reads
```

Warm Cache:

```text
100,000 HTTP Requests
↓
대부분 Redis HIT
↓
DB Read 거의 없음
```

이 변화가 Phase 4의 핵심 성과다.

---

# 42. Cache와 HikariCP

Redis Hit 요청은 PostgreSQL Connection을 필요로 하지 않는다.

따라서 Phase 3에서 배운:

```text
Connection Pool Wait
```

을 줄이는 효과가 기대된다.

```text
Redis Hit 증가
↓
DB Connection 사용 감소
↓
Hikari Pool 여유 증가
```

---

# 43. Cache Hit Rate

예:

```text
Hit = 9,900
Miss = 100
```

이면:

```text
Hit Rate = 99%
```

Hit Rate가 낮다면 다음을 재검토한다.

```text
Cache 대상

Key 설계

TTL

Traffic Pattern
```

---

# 44. Redis가 무조건 더 빠른 것은 아니다

Redis에도 비용이 있다.

```text
Network

Serialization

Deserialization

Redis Command
```

Hit Rate가 낮으면:

```text
Redis 조회
↓
MISS
↓
DB 조회
↓
Redis 저장
```

비용만 추가되어 Cache가 없는 것보다 불리할 수도 있다.

---

# 45. Phase 4 Test 1 — Cache OFF Baseline

### Given

```text
Redis Cache 미적용
```

### When

```text
GET /api/concerts/1
50 VUs
30초
```

### Then

```text
RPS
Avg
p95
p99
Error Rate
DB Query Count
```

기록.

---

# 46. Phase 4 Test 2 — Cold Cache

### Given

```text
Redis Empty
```

### When

```text
GET /api/concerts/1
```

### Then

```text
Redis MISS
DB Query 1회
concert:1 저장
```

확인.

---

# 47. Phase 4 Test 3 — Warm Cache

### Given

```text
concert:1 존재
```

### When

동일 API 재호출.

### Then

```text
Redis HIT
DB Query 없음
```

확인.

---

# 48. Phase 4 Test 4 — Warm Cache Load Test

### Given

```text
concert:1 Warm Cache
```

### When

```text
50 VUs
30초
```

### Then

Cache OFF와 다음을 비교한다.

```text
RPS
p95
p99
Error
DB Query
```

---

# 49. Phase 4 Test 5 — TTL

짧은 테스트 TTL을 설정한다.

```text
TTL = 예: 10초
```

만료 후:

```text
Key 삭제
↓
다음 Request MISS
↓
DB 조회
↓
Cache 재생성
```

확인.

---

# 50. Phase 4 Test 6 — Stale Cache

### Given

```text
Redis = Old Title
DB = New Title
```

### When

```text
GET /api/concerts/1
```

### Then

Cache가 남아 있다면 이전 값이 반환되는지 확인한다.

---

# 51. Phase 4 Test 7 — Invalidation

Concert 수정 시:

```text
DB Update
↓
Cache Delete
```

후 다음 조회에서 최신 값이 Cache에 저장되는지 확인한다.

---

# 52. Phase 4 Test 8 — Redis Failure

Redis Container를 중지한 상태에서:

```http
GET /api/concerts/1
```

호출.

설계에 따라 DB Fallback으로 정상 응답 가능한지 확인한다.

---

# 53. Phase 4 Test 9 — Stampede

### Given

```text
concert:1 없음
```

### When

```text
100 Concurrent Requests
```

### Then

DB Query가 여러 번 발생하는지 확인한다.

발생하면 Cache Stampede 재현 성공.

---

# 54. Phase 4 Test 10 — Stampede 대응

실제로 Stampede가 재현된 경우에만 대응책을 적용한다.

예:

```text
Short Lock
또는
Single Flight
```

같은 조건으로 다시 테스트해 DB Query 감소를 비교한다.

---

# 55. 좌석 Cache 검토

공연 Cache를 완료한 뒤 좌석 조회를 검토한다.

질문:

```text
좌석 전체 Response를 Cache해도 되는가?

status를 얼마나 최신으로 보여줘야 하는가?

정적 좌석 정보와 동적 상태를 분리할 수 있는가?

예약 성공 시 어떤 Key를 Invalidate해야 하는가?
```

좌석 Cache는 정합성 문제가 더 크므로 공연 Cache보다 뒤에 실험한다.

---

# 56. Phase 4 구현 순서

```text
1. Cache 대상 선정

↓

2. GET /api/concerts/{id}
   Redis 적용 전 Baseline

↓

3. DB Query 수 확인

↓

4. Redis Docker 추가

↓

5. Spring Boot ↔ Redis 연결

↓

6. RedisTemplate / Serializer 설정

↓

7. concert:{id} Key 설계

↓

8. Cache-Aside 구현

↓

9. Cold Cache 확인

↓

10. Warm Cache 확인

↓

11. Redis CLI 확인

↓

12. TTL 설정

↓

13. TTL 만료 확인

↓

14. Warm Cache k6 Test

↓

15. Cache OFF vs ON 비교

↓

16. DB Query 감소 확인

↓

17. Stale Data 재현

↓

18. Cache Invalidation 구현

↓

19. Redis 장애 Fallback 실험

↓

20. Cache Stampede 재현

↓

21. 필요하면 Stampede 대응 하나 실험

↓

22. Cache Penetration 개념 확인

↓

23. 공연 목록 Cache 적용 여부 검토

↓

24. 좌석 Cache 가능 범위 검토

↓

25. Phase 4 결과 정리

↓

26. Phase 4 완료
```

---

# 57. Phase 4 완료 조건

```text
[ ] Cache 대상 선정 기준 설명 가능

[ ] 공연 상세를 첫 Cache 대상으로 선택한 이유 설명 가능

[ ] 좌석 상태 전체 Cache의 위험성 설명 가능

[ ] Redis 적용 전 Baseline 확보

[ ] Redis Docker 실행

[ ] Spring Boot ↔ Redis 연결

[ ] Redis Key 설계

[ ] JSON Serialization 이해

[ ] Cache-Aside 구현

[ ] Cache Hit / Miss 이해

[ ] Cold / Warm Cache 확인

[ ] Redis CLI로 값 확인

[ ] TTL 설정 및 만료 확인

[ ] Cache OFF / ON k6 비교

[ ] PostgreSQL Query 감소 확인

[ ] Cache의 핵심 목적이 DB 보호라는 점 설명 가능

[ ] Stale Data 재현

[ ] Cache Invalidation 구현

[ ] Cache Consistency 문제 설명 가능

[ ] Redis 장애 Fallback 검토 또는 구현

[ ] Cache Stampede 개념 이해

[ ] 가능하면 Cache Stampede 재현

[ ] Stampede 대응 전략 최소 하나 설명 가능

[ ] Cache Penetration 개념 이해

[ ] Cache Hit Rate 의미 이해

[ ] Redis가 무조건 빠르지 않은 이유 설명 가능

[ ] Before / After 결과 기록
```

---

# 58. Phase 4에서 반드시 이해해야 할 개념

## Redis

```text
Key
Value
TTL
Expire
Serialization
```

## Cache

```text
Cache Hit
Cache Miss
Cold Cache
Warm Cache
Cache-Aside
Cache Warming
```

## Consistency

```text
Stale Data
Cache Invalidation
Cache Consistency
Eventual Consistency
```

## Failure

```text
Redis Failure
Fallback
Cache Stampede
Cache Penetration
```

## Performance

```text
Cache Hit Rate
DB Query Reduction
DB Connection Reduction
Latency
Throughput
p95
p99
```

---

# 59. Phase 4에서 가장 중요한 사고방식

잘못된 설명:

```text
Redis는 메모리라 빠르니까 사용했다.
```

더 좋은 설명:

```text
공연 정보는 변경 빈도보다 조회 빈도가 훨씬 높고,
다수 사용자가 동일 데이터를 반복 조회한다.

따라서 반복적인 Read Request를 PostgreSQL에서 분리해
DB Query와 Connection 사용량을 줄이고,
DB를 예약·결제와 같은 핵심 Transaction 처리에 집중시키기 위해
Redis Cache를 도입했다.
```

---

# 60. Phase 4의 핵심 변화

Phase 3:

```text
Read Request
↓
PostgreSQL
```

Phase 4:

```text
Read Request
↓
Redis

Cache Miss만
↓
PostgreSQL
```

즉 시스템에:

```text
Cache Layer
```

가 추가된다.

---

# 61. Phase 4의 최종 시스템

Phase 4 완료 후에는:

```text
변경 빈도가 낮고
조회 빈도가 높은 데이터를

Redis에 Cache하고,

Cache Hit / Miss / TTL / Invalidation /
Stale Data / Failure를 직접 제어하며,

Cache 적용 전후의 DB Query와
성능 차이를 측정할 수 있는 시스템
```

이 된다.

---

# 62. Phase 4 이후의 한계

Read 부하는 줄었지만 예약 Write 요청은 여전히 DB까지 직접 들어간다.

```text
10,000 Users
↓
POST /reservations
↓
Spring Boot
↓
PostgreSQL
```

예매 오픈 순간에는:

```text
DB Connection 경쟁

Lock Contention

Transaction 증가

Latency 증가
```

가 발생할 수 있다.

따라서 다음 질문은:

```text
모든 사용자를 동시에
Reservation API까지 보내도 되는가?
```

가 된다.

이 문제는 이후 Waiting Queue / Traffic Control 단계에서 해결한다.

---

# 63. Phase 4 한 문장 정의

> **Phase 4 — Redis Cache & Read Load Reduction**  
> 반복적으로 조회되는 공연 데이터를 Redis에 Cache하여 PostgreSQL Read 부하와 DB Connection 사용량을 줄인다. Cache-Aside Pattern을 기반으로 Cache Hit/Miss, Cold/Warm Cache, TTL, Cache Invalidation, Stale Data, Redis 장애, Cache Stampede를 직접 실험하고, k6를 통해 적용 전후의 처리량·Latency·DB Query 감소 효과를 비교한다.

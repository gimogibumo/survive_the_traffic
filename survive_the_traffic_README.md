# survive_the_traffic README 작성 지시서

## 목표

`/Users/gimgibeom/Downloads/survive_the_traffic` 프로젝트의 제출용 `README.md`를 작성한다. 이 README는 국민은행 AI·플랫폼개발 포트폴리오 제출 및 기술면접 대비용이다. 단순 학습 기록이 아니라, 문제 정의 -> 설계 판단 -> 검증 -> 한계까지 한눈에 보이게 작성한다.

## 반드시 먼저 읽을 파일

작업 전 아래 파일을 순서대로 읽는다.

1. `Phase_1.md`
2. `Phase_2.md`
3. `Phase_3.md`
4. `Phase_3_Result.md`
5. `src/main/java/com/survive_the_traffic/reservation/service/ReservationService.java`
6. `src/main/java/com/survive_the_traffic/reservation/repository/ReservationRepository.java`
7. `src/main/java/com/survive_the_traffic/performance/service/PerformanceSeedService.java`
8. `src/main/resources/db/migration/V2__add_active_reservation_constraint.sql`
9. `src/main/resources/db/migration/V3__add_phase3_query_indexes.sql`
10. `load-tests/k6/*.js`
11. `src/test/java/com/survive_the_traffic/user/service/UserReservationQueryTest.java`

## README 핵심 포지셔닝

프로젝트를 "대규모 티켓팅 전체 시스템"이라고 과장하지 않는다. 정확한 포지셔닝은 다음과 같다.

> 단일 Spring Boot 서버와 PostgreSQL 환경에서 좌석 예약의 정합성을 보장하고, 이후 HTTP 부하 테스트와 쿼리 분석으로 병목을 측정한 백엔드 프로젝트

국민은행 면접 연결 문장은 다음 관점으로 작성한다.

- 금융 플랫폼에서도 예약, 신청, 승인, 거래처럼 동시에 발생하는 요청의 정합성이 중요하다.
- AI·플랫폼 서비스는 개인화만큼 안정성, DB 정합성, 지연 시간, 장애 가능성 관리가 중요하다.
- Redis/Kafka를 붙이기 전에 먼저 현재 구조의 병목을 측정하고 확인하는 태도를 강조한다.

## 추천 README 구조

```md
# Survive the Traffic

## 1. Overview
## 2. Problem
## 3. Tech Stack
## 4. Domain Model
## 5. Phase 1: Single Request Correctness
## 6. Phase 2: Concurrent Request Correctness
## 7. Phase 3: Load Test & Bottleneck Analysis
## 8. Key Technical Decisions
## 9. Test & Performance Results
## 10. How to Run
## 11. What I Learned
## 12. Limitations & Next Steps
```

## 포함해야 할 기술 스택

- Java 21
- Spring Boot 4.1.0
- Spring Data JPA
- PostgreSQL 17.10
- Flyway
- JUnit 5
- AssertJ
- k6 2.2.0
- Docker Compose

## Phase별 요약

### Phase 1

- 공연, 좌석, 예약, 결제의 기본 도메인과 API 구현
- 단일 요청에서 좌석 상태가 `AVAILABLE -> HELD -> SOLD`로 전이되는 흐름 구현
- 예약 만료, 결제 가능 여부, 예외 처리 등 기본 비즈니스 규칙 정리

### Phase 2

- 같은 좌석에 100개의 동시 예약 요청을 보내 race condition 재현
- `@Transactional`만으로 정합성이 보장되지 않는 이유 분석
- pessimistic lock, optimistic lock, DB constraint 비교
- 최종적으로 활성 예약에 대해 DB partial unique index 적용
- `V2__add_active_reservation_constraint.sql`:

```sql
CREATE UNIQUE INDEX uq_active_reservation_per_seat
    ON reservation (seat_id)
    WHERE status IN ('PENDING', 'CONFIRMED');
```

### Phase 3

- k6로 실제 HTTP 부하 테스트 구성
- Seat GET 10/50/100/200 VU 측정
- Hikari pool size 5/10/20 비교
- Hot Seat와 Distributed Seats를 분리해 lock contention 관찰
- 외부 DB lock 10초 재현으로 Lock Wait -> Connection Wait -> HTTP 지연 흐름 확인
- `EXPLAIN ANALYZE`로 seat 조회 실행 계획 확인
- `V3__add_phase3_query_indexes.sql`로 조회 인덱스 추가
- 사용자 예약 조회에서 Lazy Loading과 Fetch Join 비교

## 반드시 넣을 주요 결과

수치는 `Phase_3_Result.md` 기준으로 작성한다. 수치를 바꾸거나 과장하지 않는다.

### Seat GET

- 10 VU: RPS 95.29, Avg 104.73ms, p95 147.12ms, p99 177.62ms, 오류율 0%
- 50 VU: RPS 96.98, Avg 512.09ms, p95 638.47ms, p99 876.60ms, 오류율 0%
- 100 VU: RPS 90.16, Avg 1093.32ms, p95 1228.28ms, p99 1300.69ms, 오류율 0%
- 200 VU: RPS 79.38, Avg 2378.29ms, p95 3337.80ms, p99 4426.09ms, 오류율 3.52%

해석:

- VU가 증가해도 처리량은 약 80~97 RPS 구간에 머물렀고, p95/p99가 크게 증가했다.
- 200 VU에서는 connection reset이 발생했지만 직접 원인은 단정하지 않는다.
- Actuator 표본에서 Hikari pending이 관측되어 connection wait은 확인된 병목 후보로 쓴다.

### Hikari Pool 비교

- Hikari 5, 10, 20 비교에서 일관된 개선 추세가 없었다.
- Hikari 20은 처리량이 약간 높았지만 p99가 높아졌다.
- 따라서 기본값 10을 유지했다고 설명한다.

### Lock Wait

- 일반 Hot Seat 100건: 201 1건 + 409 99건
- Distributed Seats 100건: 201 100건
- 외부 락 10초 실험: transactionid 대기 1개, tuple 대기 9개, Hikari connection 대기 90개
- API p99는 약 9.97초였지만 예약은 여전히 1건만 성공

해석:

- Lock Wait이 connection을 점유하고, 나머지 요청은 connection wait으로 밀리는 흐름을 재현했다.
- 이 실험에서 connection timeout은 발생하지 않았다.

### Index

- 인덱스 없음: Seq Scan, 7.437ms
- Phase 2 복합 unique index: Bitmap Index Scan + Bitmap Heap Scan, 0.788ms
- V3 `concert_id` index: Index Scan, 0.642ms

주의:

- 7.437ms -> 0.642ms 전체 차이를 V3만의 효과로 주장하면 안 된다.
- 기존 복합 인덱스 대비 V3 단일 인덱스 차이는 0.146ms이며 캐시 영향도 포함될 수 있다.
- HTTP latency가 같은 비율로 개선됐다고 쓰지 않는다.

### N+1

- Lazy Loading: SQL 103개
- Fetch Join: SQL 2개
- 같은 HTTP 부하에서 RPS 약 9.57배 증가
- p95 약 83.8% 감소
- p99 약 88.5% 감소
- SQL 감소는 `UserReservationQueryTest`로 회귀 테스트 검증

이 부분은 가장 명확한 개선 성과로 강조해도 된다.

## 실행 명령 예시

README에는 다음 명령을 포함한다.

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=performance ./gradlew --no-daemon bootRun
./gradlew --no-daemon clean test
```

성능 테스트 예시:

```bash
curl -fsS -X POST 'http://localhost:8080/api/performance/seed/seat-read?seatCount=10000'
CONCERT_ID={concertId} VUS=100 DURATION=30s k6 run load-tests/k6/seat-get.js
VUS=100 ITERATIONS=100 k6 run load-tests/k6/hot-seat-reservation.js
VUS=100 ITERATIONS=100 k6 run load-tests/k6/distributed-seat-reservation.js
USER_ID={userId} VUS=100 DURATION=30s k6 run load-tests/k6/user-reservations.js
```

## README 문체 지침

- 한국어 README로 작성한다.
- 포트폴리오 제출용이므로 문장은 간결하고 설명은 구체적으로 쓴다.
- "대규모 트래픽을 완전히 처리했다", "운영 수준 성능을 달성했다" 같은 표현은 쓰지 않는다.
- "로컬 환경 1회 측정", "병목 후보", "확인된 개선", "추가 분석 대상"을 명확히 구분한다.
- Redis, Kafka, 분산락, 대기열은 "미도입 기술" 또는 "다음 단계"로만 언급한다.

## 면접 대비용으로 강조할 질문거리

README를 읽은 면접관이 아래 질문을 할 수 있게 작성한다.

- `@Transactional`이 있는데 왜 중복 예약이 생길 수 있는가?
- optimistic lock과 pessimistic lock의 차이는 무엇인가?
- 왜 최종적으로 DB constraint를 같이 사용했는가?
- partial unique index의 장단점은 무엇인가?
- Hot Seat와 Distributed Seats의 지연 차이를 어떻게 해석했는가?
- Hikari pool을 늘렸는데 왜 무조건 좋아지지 않았는가?
- `EXPLAIN ANALYZE`에서 Seq Scan, Bitmap Scan, Index Scan은 어떻게 다른가?
- N+1은 왜 발생했고 fetch join으로 어떻게 줄였는가?
- Redis/Kafka를 도입한다면 어떤 문제를 해결하기 위해 도입할 것인가?

## 최종 검증 내용

README 하단에 아래 검증 결과를 넣는다.

- 2026-09-08 기준 `./gradlew --no-daemon clean test` 통과
- 테스트 41개 통과, 실패/오류/건너뜀 0개
- k6 스크립트 4개 `k6 inspect` 통과
- 락 재현 스크립트 `bash -n` 통과
- 200 VU 부하의 threshold 실패는 성능 한계 관측 결과로 보존

## 완료 후 확인

README 작성 후 다음을 확인한다.

1. 프로젝트를 과장하지 않았는가?
2. Phase 3 결과 수치가 `Phase_3_Result.md`와 일치하는가?
3. README만 읽어도 문제 정의, 기술 판단, 결과, 한계가 보이는가?
4. 국민은행 AI·플랫폼개발 포트폴리오로 제출해도 질문을 방어할 수 있는가?
5. `survive_the_traffic`이 AI 프로젝트가 아니라 플랫폼 안정성/정합성 프로젝트임을 명확히 했는가?

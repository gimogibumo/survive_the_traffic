# Phase 3 측정 결과

## 환경과 기준

- 측정일: 2026-09-08 (Asia/Seoul).
- Apple M3, 메모리 16 GiB. k6, Spring Boot, Docker PostgreSQL이 같은 로컬 장비를 공유한다.
- Java 21.0.4, Spring Boot 4.1.0, PostgreSQL 17.10, k6 2.2.0.
- performance 프로필, Tomcat 최대 worker 200, Hikari 기본 10, connection timeout 30초.
- 좌석 조회는 같은 concertId=79의 10,000석, 예약 내역은 같은 userId=1156의 확정 예약 100건을 재사용했다. ID는 이번 로컬 seed이며 재현할 때 새 ID로 바꾼다.
- 기준 성능은 **V3 인덱스가 적용된 현재 코드**다. Phase 2 전체 HTTP 성능을 측정한 것으로 해석하지 않는다. N+1 전후는 같은 서버에서 조회 방식만 변경했다.
- 조회는 30초, think time 없는 closed-model 부하다. VU는 동시 실행 주체이며 실제 활성 사용자 수와 같지 않다.
- Latency와 요청 수는 phase:load 태그로 준비 요청을 제외했다. RPS는 해당 k6 Counter의 rate이며 실행 전체 시간 기준이다. 준비 시간이 있는 예약 burst의 RPS는 지속 처리 한계가 아니다.
- 오류율은 http_req_failed다. Hot Seat의 409는 예상 업무 충돌로 분리한다. seed 실패와 응답 check 실패도 threshold로 실행을 실패시킨다.
- 각 조건은 1회 측정했다. 별도 JVM warm-up 구간 없이 10→50→100→200 VU 순서로 실행했고, 풀 변경 시 서버를 재시작했다. JIT, DB 캐시, 로컬 자원 경쟁 영향이 있으므로 운영 용량이나 최적 풀 크기를 단정하지 않는다.

평균은 느린 일부 요청을 숨길 수 있다. p95/p99는 각각 요청의 95%/99%가 해당 시간 이하에 완료됐다는 뜻이다. RPS는 초당 요청 수로 표현한 throughput이며 지연·오류율과 함께 봐야 한다.

## HTTP 결과

시간 단위는 ms다. 원본은 [load-tests/results](load-tests/results)에 있다.

| 실험 | Hikari | VU | RPS | Avg | p95 | p99 | 오류율 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Seat GET | 10 | 10 | 95.29 | 104.73 | 147.12 | 177.62 | 0% |
| Seat GET | 10 | 50 | 96.98 | 512.09 | 638.47 | 876.60 | 0% |
| Seat GET | 10 | 100 | 90.16 | 1093.32 | 1228.28 | 1300.69 | 0% |
| Seat GET | 10 | 200 | 79.38 | 2378.29 | 3337.80 | 4426.09 | 3.52% |
| Seat GET | 5 | 100 | 95.48 | 1029.81 | 1230.71 | 2008.84 | 0% |
| Seat GET | 20 | 100 | 96.03 | 1028.86 | 1289.20 | 1846.79 | 0% |
| Hot Seat | 10 | 100 | 476.20 | 91.49 | 113.92 | 117.33 | 0% |
| Distributed Seats | 10 | 100 | 780.60 | 31.05 | 49.65 | 49.93 | 0% |
| Hot Seat + 외부 락 10초 | 10 | 100 | 10.03 | 9942.14 | 9964.08 | 9965.76 | 0% |
| 예약 내역: Lazy Loading | 10 | 100 | 255.32 | 389.22 | 470.48 | 953.91 | 0% |
| 예약 내역: Fetch Join | 10 | 100 | 2442.47 | 40.86 | 76.12 | 110.11 | 0% |

### 조회와 Connection Wait

10→50 VU부터 처리량이 약 95~97 RPS에 머무르지만 p95는 147→638ms로 늘었다. 100→200 VU에서는 p95가 1.23→3.34초로 늘고 2,670건 중 94건이 connection reset으로 실패했다. 해당 실행은 checks와 http_req_failed threshold를 위반해 exit 99로 끝났다. 성공 요청만의 p99도 약 4.42초다.

Actuator 단일 표본에서 Hikari 10의 pending은 50 VU일 때 37, 200 VU일 때 189였다. 이는 **커넥션 획득 대기가 실제 발생했다**는 증거다. 연결 리셋의 직접 원인은 확정하지 못했다. 200 VU 실패를 DB timeout이나 Tomcat worker 부족으로 단정하지 않는다.

Hikari 20은 10보다 처리량이 약 6.5% 높지만 p99는 약 42% 높았다. Hikari 5도 비슷한 처리량에 더 높은 p99를 보였다. 풀 크기에 따른 일관된 개선 추세가 없어 기본값 10을 유지했다. hikari20-pending-after-run.json은 종료 후 0인 표본이며 부하 중 상태가 아니다.

10,000개 좌석을 매번 ORM으로 로딩하고 JSON으로 직렬화하며 수십 MB/s 이상 전송한다. SQL 실행 외에 결과 전송, ORM 로딩, 직렬화, 클라이언트 자원 경쟁도 병목 후보다. CPU/GC 및 구간별 시간은 측정하지 않았으므로 후보와 확인된 Connection Wait을 구분한다.

### Lock Wait와 정합성

일반 Hot Seat 100건은 **201 1건 + 409 99건**, Distributed Seats는 **201 100건**이었다. 전역 iteration 번호를 사용해 VU별 __ITER 중복으로 인한 가짜 분산 부하를 방지했다. shared-iterations는 최대 100 VU의 burst로, 요청 100개가 정확히 동시에 도착함을 보장하지 않는다.

Hot Seat p95는 113.92ms, 분산 좌석은 49.65ms였다. Hot Seat는 대부분 거절되고 분산 좌석은 모두 INSERT/UPDATE하므로 작업량도 다르다. 지연 차이 전체를 순수 락 대기 시간으로 해석하지 않는다.

별도 재현에서는 테스트 좌석을 외부 PostgreSQL 트랜잭션이 10초간 FOR UPDATE로 잠갔다. [lock-waits.txt](load-tests/results/lock-waits.txt)에 transactionid 대기 1개, tuple 대기 9개가 나타났고, [hikari-lock-pending.json](load-tests/results/hikari-lock-pending.json)은 커넥션 대기 90개를 기록했다. API p99는 약 9.97초였지만 여전히 예약은 1건만 성공했다. holder는 종료 시 ROLLBACK한다.

즉 **Lock Wait → 연결 10개 점유 → 나머지 요청 Connection Wait → HTTP 지연**을 재현했다. 30초 connection timeout은 넘지 않았으며 이 실험에서 connection timeout은 발생하지 않았다.

Tomcat worker는 HTTP 요청을 처리하고 Hikari connection은 DB 통신에 쓰인다. worker가 부족하면 요청은 worker 확보 전 기다리지만, 이번 락 실험은 worker가 있어도 DB 연결이나 행 락을 기다렸다. 스레드나 연결을 늘리면 DB 경쟁이 늘 수 있으며, 이 경우 우선 트랜잭션과 락 보유 시간을 줄여야 한다.

## 인덱스 전후

[index-comparison.sql](load-tests/index-comparison.sql)은 실제 seat 데이터 130,878건을 세션 전용 임시 테이블에 복사하고 ANALYZE한다. 같은 공연 10,000석을 인덱스 없음 → 기존 복합 인덱스 → 추가 단일 컬럼 인덱스 순으로 조회했다. 단계마다 warm-up 1회 후 1회 측정하며 원본 데이터와 인덱스는 변경하지 않는다.

| 조건 | 실행 계획 | Execution Time | Buffer 관측 |
|---|---|---:|---|
| 인덱스 없음 | Seq Scan | 7.437ms | local read 1408 |
| Phase 2 복합 unique | Bitmap Index Scan + Bitmap Heap Scan | 0.788ms | local hit 156 |
| V3와 같은 concert_id 인덱스 추가 | Index Scan | 0.642ms | local hit 114 |

원본: [index-comparison.txt](load-tests/results/index-comparison.txt). 실제 API 대상 쿼리의 [seat-explain.json](load-tests/results/seat-explain.json)도 idx_seat_concert_id Index Scan, 0.849ms를 기록했다. 이 표본은 seed 직후라 예상 행 수 5와 실제 10,000이 달랐다. 임시 비교에서는 ANALYZE 후 예상 9,615로 가까워졌다.

Seq Scan은 전체 행을 읽고 조건을 검사하며 작은 테이블이나 높은 선택 비율에서는 유리할 수 있다. Index/Bitmap Scan은 인덱스로 후보를 찾고 테이블 행을 읽는다. EXPLAIN은 예상 계획, EXPLAIN ANALYZE는 실제 실행 행 수와 시간을 제공한다.

**Phase 2에도 (concert_id, section, row_number, seat_number) unique 인덱스가 있었다.** 7.437→0.642ms 전체 차이를 V3의 효과로 주장하면 잘못이다. 기존 인덱스 대비 추가 인덱스 차이는 0.146ms였고 캐시 차이도 포함된다. HTTP 지연 전체가 같은 비율로 개선된다는 뜻이 아니다. 인덱스에는 저장 공간과 쓰기 유지 비용이 있으며, 나머지 reservation 인덱스까지 효과가 모두 입증된 것은 아니다.

## N+1 전후

[UserReservationQueryTest](src/test/java/com/survive_the_traffic/user/service/UserReservationQueryTest.java)에서 같은 예약 100건의 응답 내용이 같은지 비교하고 Hibernate statement 수를 검증했다. 조회마다 별도 트랜잭션/영속성 컨텍스트를 사용해 이전 조회의 1차 캐시가 N+1을 숨기지 않는다.

| 조회 방식 | SQL 수 | 구성 |
|---|---:|---|
| Lazy Loading | 103 | 사용자 확인 1 + 예약 1 + 좌석 100 + 같은 공연 1 |
| Fetch Join | 2 | 사용자 확인 1 + Reservation/Seat/Concert join 1 |

Fetch Join은 DTO에서 사용할 연관 엔티티를 한 쿼리에 함께 가져와 반복 lazy 조회를 없앤다. 같은 HTTP 부하에서 RPS 약 9.57배, p95 약 83.8% 감소, p99 약 88.5% 감소를 관측했다. SQL 감소는 회귀 테스트로 검증했고 HTTP 배율은 이번 로컬 실행에 한정한다.

이전 조회는 /api/performance/baseline/users/{userId}/reservations에 남겼으며 performance 프로필에서만 활성화된다. 실제 /api/users/{userId}/reservations는 Fetch Join을 사용한다.

## 재현 명령

프로젝트 루트에서 실행한다. seed는 개발용 DB에 데이터를 추가한다. 반환된 ID로 예시의 79, 1156을 대체한다. USER_COUNT/COUNT가 ITERATIONS보다 작으면 예약 스크립트는 실패한다. 락 실험은 로컬 API와 ticketing-postgres 컨테이너를 사용한다.

~~~bash
SPRING_PROFILES_ACTIVE=performance ./gradlew --no-daemon bootRun
curl -fsS -X POST 'http://localhost:8080/api/performance/seed/seat-read?seatCount=10000'
curl -fsS -X POST 'http://localhost:8080/api/performance/seed/user-reservations?reservationCount=100'

CONCERT_ID=79 VUS=10 DURATION=30s k6 run --summary-export load-tests/results/seat-get-vus10.json load-tests/k6/seat-get.js
CONCERT_ID=79 VUS=50 DURATION=30s k6 run --summary-export load-tests/results/seat-get-vus50.json load-tests/k6/seat-get.js
CONCERT_ID=79 VUS=100 DURATION=30s k6 run --summary-export load-tests/results/seat-get-vus100.json load-tests/k6/seat-get.js
CONCERT_ID=79 VUS=200 DURATION=30s k6 run --summary-export load-tests/results/seat-get-vus200.json load-tests/k6/seat-get.js

# 서버 종료 후 Hikari만 5, 20으로 각각 변경하고 같은 ID/100 VU로 측정한다.
HIKARI_MAX_POOL_SIZE=5 SPRING_PROFILES_ACTIVE=performance ./gradlew --no-daemon bootRun
CONCERT_ID=79 VUS=100 DURATION=30s k6 run --summary-export load-tests/results/seat-get-hikari5-vus100.json load-tests/k6/seat-get.js
# 다시 서버 종료 후:
HIKARI_MAX_POOL_SIZE=20 SPRING_PROFILES_ACTIVE=performance ./gradlew --no-daemon bootRun
CONCERT_ID=79 VUS=100 DURATION=30s k6 run --summary-export load-tests/results/seat-get-hikari20-vus100.json load-tests/k6/seat-get.js

# 아래 비교는 기본 Hikari 10 서버에서 실행한다.
USER_ID=1156 QUERY_MODE=lazy VUS=100 DURATION=30s k6 run --summary-export load-tests/results/user-reservations-lazy-vus100.json load-tests/k6/user-reservations.js
USER_ID=1156 VUS=100 DURATION=30s k6 run --summary-export load-tests/results/user-reservations-vus100.json load-tests/k6/user-reservations.js
VUS=100 ITERATIONS=100 k6 run --summary-export load-tests/results/hot-seat-vus100.json load-tests/k6/hot-seat-reservation.js
VUS=100 ITERATIONS=100 k6 run --summary-export load-tests/results/distributed-seat-vus100.json load-tests/k6/distributed-seat-reservation.js
bash load-tests/lock-wait.sh

curl -fsS 'http://localhost:8080/api/performance/explain/concerts/79/seats?analyze=true'
docker exec -i ticketing-postgres psql -U ticketing -d ticketing -v concert_id=79 < load-tests/index-comparison.sql
./gradlew --no-daemon clean test
~~~

## 완료 범위와 한계

- 실제 HTTP 부하, 10/50/100/200 VU, Hikari 5/10/20, p95/p99/오류율을 기록했다.
- Connection Wait와 Lock Wait을 지표로 확인하고 Hot Seat/분산 예약의 정합성을 검증했다.
- 실행 계획과 인덱스 전후, N+1 SQL 수와 HTTP 성능 전후를 비교했다.
- 확인된 개선은 Fetch Join을 통한 N+1 제거다. 풀 증설은 기본 설정에 반영하지 않았다.
- 2026-09-08 최종 검증에서 `./gradlew --no-daemon clean test`의 테스트 41개가 모두 통과했다(실패·오류·건너뜀 0개). k6 스크립트 4개의 `k6 inspect`와 락 재현 스크립트의 `bash -n` 검사도 통과했다. 200 VU 부하의 threshold 실패는 성능 한계 관측 결과로 보존했다.
- 연결 리셋 직접 원인, 반복 측정 신뢰 구간, 분리된 부하 발생기에서의 용량, CPU/GC와 Tomcat worker 대기 시간은 추가 분석 대상이다. 운영 SLA 충족이나 모든 병목 해결을 뜻하지 않는다.

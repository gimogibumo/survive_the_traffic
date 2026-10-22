# Phase 8 — AWS Queue ON/OFF 검증

**완료: 기존 AWS 리소스에서 protocol v2의 50·100·1,000명 ON/OFF 각 1회와 500명 각 3회를 측정했다.**
500명에서 ON은 예약 p95 중앙값과 평균 연결 획득 시간을 줄였지만 사용자 E2E는 늘었고,
전체 완료율은 양쪽 모두 99.8%였다. DB CPU 포화 방지 및 엄격한 예약 요청 rate 제한은 이번 실험으로 입증하지 못했다.
실패·준비 중단·관측 공백을 원본과 함께 보존했다.

## 1. Motivation

AWS에서 Queue join과 WAITING → ADMITTED는 이미 검증했다. 이번 단계에서는
그 기능이 실제 예약 API와 PostgreSQL의 burst를 줄이는지 확인한다.
예약 API latency와 사용자가 Queue를 포함해 기다린 시간은 구분한다.

## 2. AWS Architecture

```text
동일한 로컬 k6 → 기존 ALB → EKS API 2개 → RDS PostgreSQL
                              ↕
                       Valkey Session / Queue ← Worker 1개
```

| 자원 | 고정 조건 |
|---|---|
| EKS | 기존 survive-traffic, ARM64 t4g.medium 노드 |
| API / Worker | 2개 / 1개 |
| 이미지 | survive-the-traffic:6eae8a15a134abd05fcc72df7fa60bf393d15563 |
| RDS | 기존 ticketing-postgres, db.t4g.micro, PostgreSQL 17.9 |
| PostgreSQL max_connections | 실측 설정 79 |
| Valkey | 기존 ticketing-valkey, cache.t4g.micro, TLS + AUTH |
| Hikari | API마다 max 10 / min idle 2 / timeout 30초 |
| API 자원 | request CPU 250m·memory 512Mi, memory limit 1Gi |
| Worker 자원 | request CPU 100m·memory 256Mi, memory limit 512Mi |
| JVM | 이미지 기본 java -jar, 별도 JVM 환경변수 override 없음 |

새 EKS/RDS/Valkey/VPC/노드는 생성하지 않았다. 기존 Secret을 참조하는 임시
`ticketing-phase8-tools` Pod의 psql/redis-cli는 클라이언트 역할만 한다.

## 3. Existing Functional Validation

- 신규 설정을 포함한 로컬 전체 테스트: 129개, 실패/오류 0개, 기존 skip 1개.
- [실험 이미지의 기존 CI/CD 배포 성공](https://github.com/gimogibumo/survive_the_traffic/actions/runs/39744064849).
- 1명 OFF smoke: 로그인·CSRF·예약 1/1 성공.
- 1명 ON smoke: 로그인·CSRF·join·입장·예약 1/1 성공, status 조회 2회.
- 두 API Pod에서 같은 Redis Session으로 인증된 warm-up/Actuator 요청을 수행했다.
- 임시 redis 이미지의 CA bundle 부재는 Amazon Root CA를 지정해 해결했다.
  TLS 검증을 끄지 않았으며 연결 결과는 PONG이었다.

## 4. Experiment Question

동일한 사용자·좌석·이미지·환경에서 Queue ON이 OFF보다 예약 p95/p99,
Hikari pending 및 DB pressure를 줄이는가? 그 대가로 Queue wait와 polling은 얼마나 증가하는가?

## 5. Hypothesis

결과 관측 전에 [사전 계획](results/phase8/protocol.md)을 기록했다.

- H1: OFF에서는 동시 요청 증가에 따라 Hikari pending과 예약 tail latency가 증가한다.
- H2: ON의 admission이 burst를 분산하면 OFF보다 Hikari/DB pressure가 안정적이다.
- H3: ON은 Queue wait와 polling이라는 비용을 가진다.

## 6. Experimental Controls

- 같은 이미지, API 2개, Worker 1개, JVM/Hikari 설정, RDS, Valkey, ALB, k6 머신을 사용한다.
- 매 run API Pod를 교체하고 Pod별 예약 10회, 총 20회의 동일한 warm-up을 수행한다.
- warm-up 전과 후에 전용 공연의 예약·좌석만 초기화한다.
- 매 VU는 자기 계정으로 로그인하고 CSRF를 재발급받은 뒤 절대시간 barrier에서 시작한다.
  protocol v2는 인증 시작을 8 users/s로 고정하여 준비 단계의 bcrypt burst를 측정 대상에서 분리한다.
- 로그인은 별도 prepare 구간이다. 예약 latency와 Hikari acquire delta는 로그인 이후 구간에서 측정한다.
- 시작이 1초 이상 늦거나 로그인 준비/측정 진입 수가 N과 다르면 통제 실패다.
  측정 진입은 OFF의 예약 시도, ON의 join 시도다. join/status에서 실패해 예약에 도달하지 못한 사용자는 실제 측정 실패로 포함한다.
- 50 → 100 → 500 → 1,000명으로 증가한다. 500명은 각 모드 3회 반복한다.
- 최종 500명 실행 순서는 run3 OFF→ON, run4 ON→OFF, run5 OFF→ON이다.
  중단된 run2와 인증 정책이 다른 v1 결과는 원본을 보존하고 최종 표에서 제외한다.
- core telemetry 수집이 실패하거나 Pod가 불안정하면 다음 부하로 넘어가지 않는다.
  수집 성공과 순간 peak 관측은 구분한다. 요청 지연에 따른 관측 공백은 별도로 표시한다.

사전 최근 1시간 CloudWatch 조회에서 RDS credit 약 26.86, 노드 credit 약 78.52,
두 자원의 CPUSurplusCreditsCharged는 0이었다. 노드는 unlimited 모드여서 credit 소모를 함께 관찰한다.
관측 구간의 0은 향후 비용이 발생하지 않는다는 보장이 아니다.

## 7. Test Fixtures

- 전용 공연 ID: **2**. 기존 공연 1번과 좌석 5개는 변경하지 않는다.
- 사용자 1,000명과 좌석 1,000개를 준비하고 `User i → Seat i`로 고정한다.
- 사용자마다 실제 식별자와 고유 이메일을 갖고 같은 임의 테스트 비밀번호를 사용한다.
- bcrypt cost 10 hash를 측정 전 생성하여 RDS에 fixture로 삽입했다.
  회원가입·비밀번호 hash 생성은 실험에 포함하지 않는다. 매 run의 실제 로그인 hash 검증은 prepare 구간이다.
- 비밀번호는 `/tmp/stt-phase8/state.json`에 mode 0600으로 보관하며 Git/결과 파일/로그에 출력하지 않는다.
- [fixture.json](results/phase8/fixture.json)은 synthetic email·userId·seatId만 포함한다.
  각 run metadata에 동일 파일의 SHA-256을 기록한다.
- 초기화 전 전용 공연 제목, 타 사용자 예약 및 결제 존재 여부를 검사한다.
  예상 외 데이터가 있으면 삭제를 거부한다.

## 8. Queue OFF Design

기존 API는 admission 검사를 항상 적용하므로 과거 스크립트만으로 OFF를 구현할 수 없었다.
`QUEUE_ADMISSION_BYPASS_CONCERT_ID=2`일 때 공연 2번만 admission을 생략한다.
기본값 0은 모든 공연의 admission 검사를 유지한다. 다른 공연의 gate 보존은 테스트로 검증했다.

OFF: Session 인증 → CSRF 검증 → ReservationService → PostgreSQL transaction.
Worker를 끄는 것을 OFF로 정의하지 않으며, 측정 중 Worker는 ON/OFF 모두 1개다.

## 9. Queue ON Design

`QUEUE_ADMISSION_BYPASS_CONCERT_ID=0`으로 gate를 적용한다.

```text
VU별 CSRF → login → 새 CSRF → barrier
→ join → status polling → ADMITTED → reservation
```

요청의 사용자 식별자는 서버의 AuthenticatedUser에서 얻는다. userId를 body/query로 보내지 않는다.
Worker는 100ms fixed rate로 전역 최대 50명을 배분한다. 이론상 500명/초이며 실제 처리율 보장은 아니다.
여러 공연이 동시에 기다리면 정렬된 공연 ID에 batch를 나누고 나머지를 작은 ID부터 배분한다.
이번 실험은 공연 하나를 대상으로 하므로 다중 공연 간 공정성을 검증하지 않는다.

초기화는 Worker 0 → 해당 공연의 waiting/admitted/sequence DEL 및
active-concerts에서 해당 멤버 SREM → Worker 1 순서다. Session과 global SET 전체는 삭제하지 않는다.

## 10. Metrics

| 지표 | 수집과 해석 |
|---|---|
| Reservation latency | k6 HTTP duration, avg/p50/p90/p95/p99/max |
| E2E latency | barrier 이후 Queue 진입부터 예약 응답까지, 로그인 제외 |
| Error rate | 1 − 예약 성공 수 / 전체 cohort N. Queue 경로에서의 미완료도 포함 |
| Queue wait | join 요청 시작부터 ADMITTED 관측까지. join·polling 지연 포함 |
| Poll amplification | status 요청 수 / 사용자 수 |
| Hikari | Pod별 active/pending 표본, acquire COUNT/TOTAL_TIME delta, timeout delta |
| Pod CPU/memory | kubectl top을 일정 간격으로 기록. metrics-server 갱신 지연 존재 |
| Queue depth | TLS Valkey의 해당 waiting/admitted ZCARD 표본 |
| DB connections | pg_stat_activity의 client backend·active·Lock wait 표본. 관측 연결 포함 |
| RDS/노드 | CloudWatch DatabaseConnections·CPU·IOPS·FreeableMemory·CPU credit/과금 지표 |

pending 표는 **Pod별 관측 최대값 중 큰 값**이다. 연속 시간의 정확한 최대나 두 Pod의 동시 합계가 아니다.
RDS의 60초 요청 구간과 credit의 더 긴 표본 주기는 짧은 burst와 준비 부하를 완전히 구분하지 못한다.
없는 지표, 빈 CloudWatch 구간, 연결 실패를 0으로 바꾸지 않는다.

## 11. Results

최종 표에는 같은 v2 스크립트·fixture·앱 이미지를 사용하고 인증/출발 통제를 충족한 **12개 run**을 포함한다.
1명 smoke와 v1은 참고 자료다. 500명은 run3/4/5 각 3회이며, 50·100·1,000명은 모드별 1회다.
반복 p95/p99/E2E/Queue wait는 **실행별 percentile의 중앙값**이다. 요청 전체를 합친 pooled percentile이 아니다.
Error는 `1 − 예약 성공 응답 수 / 전체 cohort N`이며 join/status에서의 미완료도 포함한다.
API 응답을 받지 못한 EOF와 서버가 반환한 HTTP 오류를 구분한다.

| Users | Mode | Runs | Reservation p95 ms | p99 ms | Error % | Pending max/Pod | DB connections | Queue wait p95 ms | E2E p95 ms |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 50 | OFF | 1 | 1232.71 | 1254.67 | 0.00 | 21.00 | 16.00 | N/A | 1232.90 |
| 50 | ON | 1 | 903.17 | 982.87 | 0.00 | 8.00 | 15.00 | 2283.65 | 2615.55 |
| 100 | OFF | 1 | 1916.06 | 1980.17 | 0.00 | 43.00 | 22.00 | N/A | 1916.95 |
| 100 | ON | 1 | 1984.66 | 2030.58 | 0.00 | 9.00 | 21.00 | 3415.10 | 4177.00 |
| 500 | OFF | 3 | 6246.35 | 6381.57 | 0.20 | 185.00 | 23.00 | N/A | 6313.05 |
| 500 | ON | 3 | 4191.75 | 5547.52 | 0.20 | 162.00 | 23.00 | 6834.50 | 10219.00 |
| 1000 | OFF | 1 | 16188.94 | 17440.63 | 0.30 | N/A | 23.00 | N/A | 16480.05 |
| 1000 | ON | 1 | 8167.16 | 9015.68 | 0.20 | 178.00 | 23.00 | 8295.60 | 15566.30 |

단위는 latency ms, 연결 수/pending은 개수다. DB connections는 `pg_stat_activity` client backend의 관측 최대이며 관측용 연결도 포함한다.
Pending은 Pod별 관측 최대 중 큰 값이며 반복 표는 모든 run 중 최대다.
**1,000명 OFF의 pending N/A는 수집 실패가 아니라 순간 peak 판단 불가**다.
원본은 0이지만 약 11.9초의 관측 공백이 있어 “대기 없음”을 입증하지 못한다.
같은 이유로 OFF 500 run5의 0도 peak 비교에서 제외한다. 원본 0은 `comparison.json`의
`hikari_pending_max_observed`에 남겼고, 표시용 값과 unresolved flag를 별도로 둔다.
2초 초과 관측 공백과 0 표본의 조합을 보수적으로 N/A로 표시하며 latency/실패 결과는 제외하지 않는다.

### 500명 개별 반복

| Run | 예약 완료 / 500 | 예약 p95 ms | p99 ms | Pending 관측 peak/Pod | Acquire 평균 ms | E2E p95 ms |
|---|---:|---:|---:|---:|---:|---:|
| queue-off-500-run3 | 499 | 6,246.35 | 6,381.57 | 183.00 | 2,745.33 | 6,313.05 |
| queue-off-500-run4 | 499 | 7,039.48 | 7,265.14 | 185.00 | 2,653.98 | 7,057.40 |
| queue-off-500-run5 | 499 | 5,554.84 | 5,664.79 | N/A | 2,486.49 | 5,766.05 |
| queue-on-500-run3 | 500 | 5,188.61 | 5,547.52 | 99.00 | 974.16 | 10,946.45 |
| queue-on-500-run4 | 498 | 4,091.19 | 5,546.50 | 150.00 | 1,590.22 | 9,825.20 |
| queue-on-500-run5 | 499 | 4,191.75 | 5,617.35 | 162.00 | 1,379.00 | 10,219.00 |

### 500명 반복 통계

| Mode | Metric | 평균 ms | 중앙값 ms | 최소–최대 ms |
|---|---|---:|---:|---:|
| OFF | 예약 p95 | 6,280.22 | 6,246.35 | 5,554.84–7,039.48 |
| OFF | 예약 p99 | 6,437.16 | 6,381.57 | 5,664.79–7,265.14 |
| OFF | run별 acquire 평균 | 2,628.60 | 2,653.98 | 2,486.49–2,745.33 |
| OFF | E2E p95 | 6,378.83 | 6,313.05 | 5,766.05–7,057.40 |
| ON | 예약 p95 | 4,490.52 | 4,191.75 | 4,091.19–5,188.61 |
| ON | 예약 p99 | 5,570.46 | 5,547.52 | 5,546.50–5,617.35 |
| ON | run별 acquire 평균 | 1,314.46 | 1,379.00 | 974.16–1,590.22 |
| ON | E2E p95 | 10,330.22 | 10,219.00 | 9,825.20–10,946.45 |

실행 순서는 run3 OFF→ON, run4 ON→OFF, run5 OFF→ON이다. 각 모드 1,500명 중 1,497명 완료다.
OFF는 예약 POST EOF 각 1건, ON은 join EOF가 run4 2건·run5 1건이었다.
ON의 예약 endpoint에 도달한 1,497건은 모두 성공했지만, 전체 cohort 완료율은 99.8%다.
1,000명 OFF는 예약 EOF 3건, ON은 join EOF 2건이었다. 클라이언트 응답 실패만으로 DB rollback을 주장하지 않는다.

### 준비 실패와 제외

- OFF 1 run1: rollout 직후 ALB 502, k6 전 실패. 연속 readiness/CSRF 성공 대기를 추가했다.
- ON 1 run1: 이전 예약 좌석 warm-up 409, k6 전 실패. warm-up 전 DB 초기화도 추가했다.
- OFF 50 run1: 예약 50/50 후 외부 EKS/CloudWatch 관측 실패. telemetry 불완전으로 제외했다.
- v1 OFF 500 run1: 동시 로그인 중 probe timeout과 API 재시작. 로그인 431명·예약 도달 26명으로 cohort 통제 실패다.
  예약 부하 500명 결과로 사용하지 않는다. v2 인증 준비 정책으로 50/100명부터 재측정했다.
- v2 ON 500 run2: 인증 준비 중 외부 연결 중단, 예약 측정 전 종료. 로그인 성공 388명으로 cohort가 형성되지 않았다.
- ON 500 run4: join 실패 2건 때문에 초기 운용 assertion이 중단·복원했다.
  로그인·출발·join 시도 500명은 충족했으므로 실제 측정 결과에 포함하도록 판정을 바로잡았다.
  실패를 제거하려고 새 run으로 교체하지 않았다.

원본 및 판정은 [run-inventory.json](results/phase8/run-inventory.json),
[measurement-notes.md](results/phase8/measurement-notes.md),
[comparison.md](results/phase8/comparison.md), [repeat-statistics.json](results/phase8/repeat-statistics.json)에 있다.

## 12. Queue Depth / Wait Analysis

| ON run | Waiting 관측 최대 | Admitted 관측 최대 | Queue wait p95 ms | Queue 표본 수 | 표본 간격 중앙값/최대 s |
|---|---:|---:|---:|---:|---:|
| queue-on-50-run3 | 0.00 | 50.00 | 2,283.65 | 13 | 0.54 / 1.07 |
| queue-on-100-run2 | 0.00 | 100.00 | 3,415.10 | 15 | 0.58 / 1.15 |
| queue-on-500-run3 | 28.00 | 500.00 | 7,606.90 | 18 | 1.12 / 2.25 |
| queue-on-500-run4 | 19.00 | 498.00 | 6,258.00 | 2 | 15.69 / 15.69 |
| queue-on-500-run5 | 81.00 | 499.00 | 6,834.50 | 17 | 0.94 / 1.54 |
| queue-on-1000-run1 | 183.00 | 998.00 | 8,295.60 | 23 | 0.91 / 1.45 |

50/100명은 waiting의 양수 표본을 포착하지 못했다. 대기가 전혀 없었다는 뜻은 아니다.
500명에서 양수 depth, 1,000명에서 관측 최대 183명을 확인했다. 시간이 지나 waiting 0,
admitted는 성공한 join 수까지 증가했다. 만료 전 admitted가 유지되므로 예약 완료 수나 동시 예약 수와 같지 않다.
ON 500 run3은 burst 후 약 4.18초에 admitted 500을 처음 관측했다.
이 값에는 join 유입과 관측 지연이 포함되므로 Worker 고유 처리율로 해석하지 않는다.
run4는 Queue 표본 2개·간격 약 15.69초여서 drain 곡선이나 처리율 계산에 부적합하다.

명목 admission 상한은 500명/초지만 HTTP join 유입 자체가 시간에 걸쳐 분산된다.
Queue wait에는 join 응답·status 응답·polling 시간이 섞인다. 고정된 batch가 병목으로 동작하여
예약 RPS를 엄격히 제한했다는 인과관계는 현재 집계 자료로 판단할 수 없다.

## 13. Hikari / DB Protection Analysis

| Users | Mode | Acquire 평균 ms (500명은 run 중앙값) | Connection timeout delta | DB 연결 관측 최대 |
|---:|---|---:|---:|---:|
| 50 | OFF | 444.59 | 0.00 | 16.00 |
| 50 | ON | 139.42 | 0.00 | 15.00 |
| 100 | OFF | 699.18 | 0.00 | 22.00 |
| 100 | ON | 218.02 | 0.00 | 21.00 |
| 500 | OFF | 2,653.98 | 0.00 | 23.00 |
| 500 | ON | 1,379.00 | 0.00 | 23.00 |
| 1000 | OFF | 2,382.92 | 0.00 | 23.00 |
| 1000 | ON | 2,154.78 | 0.00 | 23.00 |

500명에서 acquire 평균의 run 중앙값은 OFF 2,653.98ms, ON 1,379.00ms로 약 48% 감소했다.
예약 p95 중앙값은 약 33%, p99 중앙값은 약 13% 감소했다. 표본 pending도 OFF 최대 185,
ON 최대 162를 관측했다. 다만 관측 공백이 있어 pending의 정확한 peak 감소율은 주장하지 않는다.
1,000명 OFF의 긴 지연을 전부 Hikari 대기로 설명할 수는 없다. 누적 acquire 평균은 2.38초이며
HTTP 지연에는 앞단 대기·네트워크·서버 처리도 들어간다.

DB 연결은 500/1,000명에서 양쪽 모두 관측 최대 23이다. API pool 합계 20과 기존/관측 연결의 영향을 받는다.
RDS max_connections 79 고갈은 재현하지 못했고 connection timeout delta는 모두 0이었다.

### CloudWatch RDS (burst를 포함하는 분 단위 구간)

| Users | Mode | CPU % | DatabaseConnections | Read IOPS | Write IOPS |
|---:|---|---:|---:|---:|---:|
| 50 | OFF | 4.44 | 4.00 | 0.33 | 0.90 |
| 50 | ON | 3.83 | 5.00 | 0.23 | 1.80 |
| 100 | OFF | 4.21 | 4.00 | 0.82 | 1.23 |
| 100 | ON | 4.88 | 18.00 | 4.66 | 11.26 |
| 500 | OFF | 4.04–5.57 | 4.00–20.00 | 0.00–1.75 | 0.40–9.02 |
| 500 | ON | 4.36–5.56 | 5.00–20.00 | 0.20–3.75 | 0.77–18.10 |
| 1000 | OFF | 5.58 | 20.00 | 0.15 | 18.35 |
| 1000 | ON | 5.53 | 20.00 | 0.08 | 17.09 |

500명 수치는 run별 구간 최대의 범위다. RDS CPU는 약 4–6%로 포화 근거가 없고 ON/OFF 범위도 겹친다.
CloudWatch의 연결 수가 고해상도 DB 관측 23보다 작은 run도 있다. 짧은 burst와 1분 표본의 시간 차이이며
한쪽 값을 다른 값으로 덮어쓰지 않는다. 분 단위 CPU/IOPS에는 인증 준비·warm-up 일부가 포함될 수 있어
예약 burst만의 순간 부하 또는 DB 보호 효과를 분리해 판단할 수 없다.

### Pod CPU / memory

유효 v2 run에서 Pod별 관측 최대값들의 범위는 API CPU 457–1,446m, memory 336–449Mi,
Worker CPU 41–1,004m, memory 116–214Mi였다. `kubectl top`은 갱신 창이 있는 지표여서
인증·Worker 재기동의 값이 남을 수 있다. 순간 예약 CPU나 ON/OFF 자원 비용의 정밀 비교로 사용하지 않는다.
최종 API 2개·Worker 1개는 Ready이고 재시작 0이다. v1 준비 실패의 재시작은 별도 기록했다.

## 14. Polling Amplification

| Users | Status 호출 / 전체 cohort | calls/user |
|---:|---|---:|
| 50 | 63 / 50 | 1.260 |
| 100 | 100 / 100 | 1.000 |
| 500 | (500 + 498 + 499) / 1500 | 0.998 |
| 1000 | 998 / 1000 | 0.998 |

500/1,000명의 값이 1보다 작은 이유는 join 실패 사용자가 status까지 도달하지 못했기 때문이다.
join에 성공한 사용자는 해당 run에서 status 1회로 ADMITTED를 확인했다.
현재 부하에서 다회 polling 폭증은 재현하지 못했다. 그래도 ON은 정상 사용자마다 join·status 요청이 추가된다.
1초 간격의 장기 대기·재접속·여러 탭 조건에서 SSE의 필요성을 다시 측정해야 한다.

## 15. Trade-offs

500명 예약 p95는 줄었지만 Queue를 포함한 E2E p95 중앙값은 OFF 6.31초에서 ON 10.22초로 늘었다.
반대로 1,000명 단일 run의 E2E p95는 OFF 16.48초, ON 15.57초였다. 부하 구간별 결과를 구분해야 한다.
ON은 추가 Session/Valkey 요청, Worker 및 client 상태 관리 비용을 가진다.
현재 admission은 5분 동안 유효하고 예약 성공 후 소비하지 않으므로 admitted 사용자의 반복·동시 요청을 제한하지 않는다.
입장 batch 설정만으로 예약 API의 동시 실행 수나 RPS 상한이 보장되지는 않는다.

## 16. Limitations

- 500명만 모드별 3회다. 50/100/1,000명은 단일 run이어서 신뢰구간·일반적인 운영 용량을 주장하지 않는다.
- 로컬 macOS→공개 ALB 구간의 EOF가 있었다. ALB idle 연결, 클라이언트, 경로 또는 서버 중 어느 지점인지 확정하지 못했다.
  측정 중 EOF는 cohort 실패에 포함했다. 로그인 준비 중 전면 연결 중단은 별도로 제외했다.
- Actuator 요청도 API와 port-forward를 경유한다. Hikari gap은 500명에서 최대 약 7.70초,
  1,000명 OFF에서 약 11.86초였다. row timestamp는 수집 요청 시작 시각이다.
  gap을 포함한 pending 표본 0을 대기 없음으로 해석하지 않는다.
- Queue/DB/top 관측도 부하를 추가하고 지연될 수 있다. 요청별 trace나 연속 event stream이 아니므로 실제 순간 최대를 놓친다.
- acquire는 COUNT/TOTAL_TIME delta 기반 평균이다. acquire p95/p99 histogram을 수집한 것은 아니다.
- CloudWatch 60초와 EC2 credit 300초 조회는 짧은 burst와 준비 부하를 완전히 분리하지 못한다.
- 단일 공연·고유 좌석·1회 예약 결과다. 같은 좌석 경쟁, 결제, 취소, 다중 공연 공정성·재시도·악의적 반복 요청은 검증 대상이 아니다.
- ON/OFF는 클라이언트 경로도 다르다. join/status/Session 경로에서 자연히 생긴 분산과 Worker admission 상한의 독립 효과를 분리하지 못했다.
- API 매회 교체·20회 warm-up은 JIT/캐시/호스트 노이즈를 완전히 통제하지 못한다.
- RDS/노드는 burstable이다. 크레딧 과금 지표 0은 인프라 자체 비용 0이나 미래 무과금을 뜻하지 않는다.

## 17. Conclusion

- **H1 부분 지지:** OFF에서 사용자 수가 늘며 예약 tail latency가 증가했고 500명에서 평균 연결 획득 대기가 2.5–2.7초였다.
  다만 RDS CPU 포화나 max_connections 고갈은 재현하지 못했다.
- **H2 제한적 지지:** 500명 반복에서 ON의 예약 p95/p99 및 평균 acquire 대기가 감소했다.
  전체 완료율은 개선되지 않았고 DB 연결 최대는 같았다. 100명 예약 p95는 ON이 더 길었다.
  따라서 모든 부하에서 개선되거나 Worker rate limit 때문에 PostgreSQL을 보호했다고 일반화할 수 없다.
- **H3 지지:** 500명에서 Queue wait와 추가 요청을 지불하며 예약 API 쪽 대기가 줄었다. E2E 증가도 함께 보고해야 한다.

현재 근거로 설명할 수 있는 결과는 “500명 반복에서 Queue ON 경로가 예약 tail과 연결 획득 대기를 줄였으며,
그 대가로 Queue 대기와 E2E 비용이 발생했다”이다. 엄격한 예약 유입 상한과 DB CPU 보호는 다음 검증이 필요하다.

### 종료 상태

[runtime-audit.json](results/phase8/runtime-audit.json)에서 다음을 확인했다.

- API 2/2, Worker 1/1 Ready, 같은 실험 이미지, 재시작 0.
- `QUEUE_ADMISSION_BYPASS_CONCERT_ID` override 제거; 미입장 예약은 실제 `403 ADMISSION_REQUIRED`.
- 전용 공연 좌석 1,000개 모두 AVAILABLE, 예약 0건.
- 전용 waiting/admitted 0, global active SET에서 전용 공연 멤버 없음.
- 전용 사용자·좌석은 재실험을 위해 남겼고, 다른 공연/Session/global SET 전체는 삭제하지 않았다.
- 비밀번호는 Git 밖 mode 0600 파일에 남겼다. 재부팅 등으로 `/tmp`가 사라지면 기존 사용자 비밀번호를 추측하지 않는다.

[latest-credit-checkpoint.json](results/phase8/latest-credit-checkpoint.json)의 종료 감사 근처 최신 표본은
RDS credit 약 52.54, 노드 credit 약 81.13, 양쪽 CPUSurplusCreditsCharged 0이었다.
새 EKS/RDS/Valkey/VPC/노드를 만들지 않았다. 이번에 만든 임시 client Pod만 종료 후 삭제했다. [cleanup.json](results/phase8/cleanup.json)에 기록했다.

## 18. Next Phase

1. 별도 내부 management 경로 또는 metrics scrape 체계로 API가 밀릴 때에도 Hikari/Tomcat 관측을 유지한다.
2. 애플리케이션 이미지·부하 조건을 새로 고정한 별도 실험에서 admission 속도와 join 유입량을 분리해 검증한다.
   admitted 접근 권한과 실제 예약 동시 실행/요청 rate 제한의 차이를 설계에 반영한다.
3. 요청별 timing·ALB/클라이언트 연결 기록으로 EOF 발생 구간을 좁힌다. 근거 확보 전 임의 재시도로 실패를 숨기지 않는다.
4. 장기 대기·여러 공연/탭·재접속에서 polling 증폭과 공정성을 측정한 뒤 SSE를 평가한다.
5. 1,000명은 동일 조건 반복을 추가해 변동을 확인한다. 이번 결과로 HPA/Worker scale-out/새 인프라를 도입하지 않았다.

### 실행 방법과 결과 위치

준비물: 기존 AWS 자격 증명, 현재 클러스터 kubeconfig, AWS CLI, kubectl, k6, Java 21/Python 3.
`KUBECTL`은 실제 설치 경로로 지정한다. 아래 Pod는 기존 ConfigMap/Secret을 참조하는 클라이언트다.

```bash
export KUBECTL=/tmp/ticketing-tools/kubectl
"$KUBECTL" apply -f k8s/phase8-tools.yaml
"$KUBECTL" -n ticketing wait --for=condition=Ready pod/ticketing-phase8-tools --timeout=120s
curl -fsS https://www.amazontrust.com/repository/AmazonRootCA1.pem -o /tmp/AmazonRootCA1.pem
"$KUBECTL" -n ticketing exec -i ticketing-phase8-tools -c valkey --   sh -c 'cat > /tmp/phase8-amazon-root-ca.pem' < /tmp/AmazonRootCA1.pem

# 새 campaign일 때만 실행. 기존 private state가 있으면 prepare는 거부한다.
python3 load-tests/scripts/phase8_aws.py prepare --users 1000

# 아래는 실행 형식 예시다. 이미 존재하는 결과 디렉터리를 덮어쓰지 않는다.
python3 load-tests/scripts/phase8_aws.py run --mode OFF --users 500 --repetition 6
python3 load-tests/scripts/phase8_aws.py run --mode ON --users 500 --repetition 6
python3 load-tests/scripts/phase8_aws.py restore
python3 load-tests/scripts/phase8_aws.py audit
python3 load-tests/scripts/phase8_aws.py refresh-cloudwatch
python3 load-tests/scripts/phase8_report.py
python3 load-tests/scripts/phase8_aws.py cleanup-tools
```

`run/pair` 단독 실행 시 종료 후 `restore`를 실행한다. 이번 실제 실행에 사용한 `remaining`은 완료된
500 run3/4/5를 보존하면서 남은 모드와 1,000명을 실행하고 `finally`에서 복원한다.
중간 실패 디렉터리를 자동 덮어쓰지 않으므로 원본과 실패 이유를 보존한 후 새 repetition을 사용한다.
실험 앱 이미지의 SHA와 현재 Git HEAD가 같아야 한다. 비밀번호는 운용 코드가 private state에서 읽어
k6 환경변수로 전달하며 명령·결과 파일에 출력하지 않는다. `prepare`는 기존 AWS 자원을 생성하지 않는다.

- 원본: `results/phase8/queue-{off,on}-{N}-run{R}/`
- fixture/통제: `fixture.json`, `protocol.md`, `runtime-controls.json`
- 집계: `comparison.json/csv/md`, `final-comparison.md`, `repeat-statistics.json`
- 단계 기록: [execution-log.md](results/phase8/execution-log.md)
- 판정·중단·관측 한계: `run-inventory.json`, `measurement-notes.md`, 각 run의 주석 Markdown
- 종료 감사: `runtime-audit.json`, `latest-credit-checkpoint.json`

완료 항목: Session/CSRF/Queue join/입장/예약 기능, 현재 인증 기반 ON/OFF 스크립트,
동일 fixture의 50/100/500/1,000명 실험, 500명 3회 반복, k6/Hikari/Pod/Queue/RDS 자료,
비교·trade-off·한계·재실행 명령 및 기본 운영 설정 복원. 순간 Hikari peak 미포착과 DB CPU 보호 미입증은 위 한계로 남긴다.

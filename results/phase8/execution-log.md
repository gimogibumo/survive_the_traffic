# Phase 8 단계별 실행 기록

수치의 최종 출처는 각 run 원본과 final-comparison.md, repeat-statistics.json이다. 날짜는 각 metadata의 epoch와 run-inventory.json의 UTC를 함께 확인한다.

## Step 1

- 목표: 기존 구조와 운영 조건 확인
- 현재 상태: 기존 EKS/API/Worker/RDS/Valkey 사용
- 변경 파일: 사전 분석·protocol.md
- 실행 명령: `git status; rg queue 코드; kubectl get deployment/pods/ingress`
- 결과: API 2, Worker 1, batch 50/100ms, admission 300초
- 해석: admission은 요청별 rate limiter가 아닌 접근 기간이다.
- 다음 단계: 현재 세션 API와 과거 스크립트 비교

## Step 2

- 목표: Queue OFF 구현 가능성 확인
- 현재 상태: 기존 예약 API는 항상 admission 검사
- 변경 파일: ReservationFacade.java, application.yml, ReservationFacadeTest.java
- 실행 명령: `./gradlew --no-daemon clean test`
- 결과: 129 tests, failure/error 0, 기존 skip 1
- 해석: 공연 하나만 bypass하는 기본 0 설정으로 다른 공연의 gate를 유지한다.
- 다음 단계: 같은 실험 이미지 배포

## Step 3

- 목표: ON/OFF 동일 이미지 배포
- 현재 상태: 기존 CI/CD와 ECR/EKS 재사용
- 변경 파일: 앱 commit 6eae8a1
- 실행 명령: `기존 GitHub Actions deploy workflow`
- 결과: run 39744064849 성공, API/Worker 같은 SHA 이미지
- 해석: 모드별 다른 binary를 사용하지 않는다.
- 다음 단계: 전용 사용자·좌석 준비

## Step 4

- 목표: 기존 업무 데이터와 실험 fixture 분리
- 현재 상태: 기존 공연 1/좌석 5개 보존
- 변경 파일: fixture.json, Phase8PasswordHash.java, phase8-tools.yaml
- 실행 명령: `python3 load-tests/scripts/phase8_aws.py prepare --users 1000`
- 결과: 전용 공연 2, 사용자 1000, 좌석 1000
- 해석: 회원가입/해시 생성은 예약 측정 밖에서 처리한다. 비밀번호는 Git 밖 0600 파일에 둔다.
- 다음 단계: 세션·CSRF 기반 스크립트 준비

## Step 5

- 목표: 현재 HTTP 계약에 맞춘 부하 스크립트
- 현재 상태: 과거 userId 직접 전달 방식은 현재 계약과 다름
- 변경 파일: queue-aws-common.js, queue-on-aws.js, queue-off-aws.js
- 실행 명령: `k6 inspect; 1명 ON/OFF smoke`
- 결과: CSRF/login/입장/예약 기능 성공
- 해석: VU별 계정과 좌석 1:1, 식별은 서버 세션에서 얻는다.
- 다음 단계: 안전한 reset·warm-up·계측 연결

## Step 6

- 목표: 상태·warm-up 고정
- 현재 상태: 반복 실행에서 이전 예약과 queue 상태 제거 필요
- 변경 파일: phase8_aws.py
- 실행 명령: `매 run API rollout → Pod별 예약 10회 → reset`
- 결과: Worker 0에서 공연 2의 3개 key DEL, global SET의 해당 멤버만 SREM, Worker 1
- 해석: Session과 global active SET 전체를 지우지 않는다.
- 다음 단계: 기존 공개 Actuator metric 확인

## Step 7

- 목표: 실제 제공하는 지표 수집
- 현재 상태: 인증된 세션으로 Pod별 Actuator 접근
- 변경 파일: Hikari JSON/JSONL, Queue/DB CSV, kubectl-top.txt, RDS JSON
- 실행 명령: `Hikari 7종 조회; ZCARD; pg_stat_activity; CloudWatch`
- 결과: active/idle/pending/acquire/timeout/max/connections 노출 확인
- 해석: CPU/CloudWatch 및 실시간 표본에는 해상도·지연 한계가 있다.
- 다음 단계: 부하 순차 확대

## Step 8

- 목표: 1/50/100명 예비 검증
- 현재 상태: 초기 rollout 502·stale warm-up 409 및 외부 계측 실패 발생
- 변경 파일: preflight-failure.md, collection-failure.md
- 실행 명령: `phase8_aws.py run/pair`
- 결과: 실패한 준비와 불완전 계측 원본 보존 후 조건을 수정해 다시 실행
- 해석: k6 전 오류와 측정 중 오류를 구분한다.
- 다음 단계: 500명 cohort 형성 확인

## Step 9

- 목표: 인증 준비와 예약 burst 분리
- 현재 상태: v1 동시 로그인 500명에서 probe timeout·재시작
- 변경 파일: protocol.md, queue-aws-common.js
- 실행 명령: `인증 시작 8 users/s인 v2로 50/100명부터 재측정`
- 결과: v1 500명은 로그인 431명, 예약 도달 26명으로 통제 실패
- 해석: 이 결과를 500명 예약 API 성능으로 주장할 수 없다.
- 다음 단계: 같은 v2 조건으로 500명 반복

## Step 10

- 목표: 외부 연결 중단 후 안전한 재개
- 현재 상태: v2 ON 500 run2는 인증 준비 중 network is unreachable
- 변경 파일: interrupted-run.md, remaining 명령
- 실행 명령: `프로세스/Pod 확인 → restore → remaining`
- 결과: API 2/Worker 1 정상, 부분 원본 보존, 500 run3/4/5 사용
- 해석: 기존 완료 결과를 덮어쓰지 않는다. 외부 연결 문제를 DB 과부하로 단정하지 않는다.
- 다음 단계: 500명 각 3회와 1000명 비교

## Step 11

- 목표: 500명 핵심 부하 반복
- 현재 상태: 같은 이미지·fixture·스크립트·8 users/s 인증 준비
- 변경 파일: queue-*-500-run3/4/5
- 실행 명령: `python3 load-tests/scripts/phase8_aws.py remaining`
- 결과: 각 모드 3회, 1497/1500명 완료(각 99.8%). p95 중앙값 OFF 6246.35ms / ON 4191.75ms
- 해석: ON 경로의 예약 tail과 acquire 대기는 감소했지만 E2E p95 중앙값은 6313.05→10219ms로 증가했다.
- 다음 단계: 1000명 ON/OFF

## Step 12

- 목표: 1000명까지 같은 조건 확인
- 현재 상태: 고부하 직전 RDS/노드 credit 약 50.36/91.46, charged 0
- 변경 파일: queue-*-1000-run1, checkpoint-before-1000-cloudwatch.json
- 실행 명령: `remaining → run OFF/ON 1000`
- 결과: OFF 997/1000, p95 16188.94ms; ON 998/1000, p95 8167.16ms
- 해석: 단일 run이며 EOF 3/2건을 실제 결과에 포함한다.
- 다음 단계: 기본 설정·전용 상태 복원

## Step 13

- 목표: 운영 설정 복원과 gate 검증
- 현재 상태: 자동 finally restore 완료
- 변경 파일: runtime-audit.json, latest-credit-checkpoint.json
- 실행 명령: `phase8_aws.py restore; phase8_aws.py audit`
- 결과: API 2/2, Worker 1/1, restart 0; bypass override 없음; 실제 HTTP 403 ADMISSION_REQUIRED
- 해석: 실험 공연 1000석 AVAILABLE/예약 0/Queue 0을 확인했다.
- 다음 단계: 최종 CloudWatch 발행 지표 확정

## Step 14

- 목표: 분 단위 관측 자료 갱신
- 현재 상태: 초기 CloudWatch 발행 지연 보완
- 변경 파일: 각 run rds-metrics.json
- 실행 명령: `phase8_aws.py refresh-cloudwatch`
- 결과: 미완료 ON 500 run2는 건너뛰고 완료 run 조회 갱신
- 해석: RDS CPU 약 4–6%이며 CPU 포화/연결 고갈 보호는 입증하지 못했다.
- 다음 단계: 표본 공백 표시와 최종 집계

## Step 15

- 목표: 실패와 관측 공백을 보존한 집계
- 현재 상태: v2 통제 충족 run 12개
- 변경 파일: phase8_report.py, comparison.*, final-comparison.md, repeat-statistics.json, measurement-notes.md
- 실행 명령: `python3 load-tests/scripts/phase8_report.py`
- 결과: 8개 N/Mode 비교 그룹, 500명 모드별 3회. pending raw 0+2초 초과 gap은 비교 N/A 표시
- 해석: 미측정 peak를 0이라고 주장하지 않고 실제 오류·latency는 제외하지 않는다.
- 다음 단계: 문서·소스·secret 검증

## Step 16

- 목표: 최종 문서와 종료 정리
- 현재 상태: 18개 필수 섹션 작성
- 변경 파일: 최종 Phase 8 문서, validation-checks.json, cleanup.json
- 실행 명령: `Python AST/k6 inspect/git diff --check/결과·링크·secret scan; phase8_aws.py cleanup-tools`
- 결과: 5300명 cohort의 5289건 성공 응답 보존, 운영 복원 확인, 이번 임시 client Pod만 삭제
- 해석: 원인 미확정 EOF·실시간 표본 공백·DB CPU 보호 미입증을 한계로 명시한다.
- 다음 단계: 실험 도구/문서는 작업 트리에 남겨 검토 가능

# Phase 8 사전 실험 계획

결과를 보기 전에 기록한 가설과 통제 조건이다.

- H1: Queue OFF에서 동시 요청이 증가하면 Hikari pending과 예약 tail latency가 증가한다.
- H2: Queue ON에서 admission이 예약 유입을 분산하면 OFF보다 DB/connection pressure가 안정적이다.
- H3: ON의 비용은 Queue 대기와 polling 증가이며, 예약 API latency와 전체 사용자 latency는 다르다.

## 통제

- 기존 EKS `survive-traffic`, API 2개, Worker 1개, 같은 ARM64 이미지 `6eae8a1`.
- 기존 RDS `db.t4g.micro`, Valkey `cache.t4g.micro`, 노드 `t4g.medium` 재사용.
- 전용 공연 1개, 좌석 1,000개, 사용자 1,000개를 사전 생성한다.
- 부하 N명은 같은 fixture의 앞 N명과 1:1 좌석을 사용한다. 각 사용자 예약 요청은 1번이다.
- 매 run 동일한 API Pod 교체, Pod별 예약 10회 warm-up, 실험 데이터 초기화 후 실행한다.
- 매 VU는 CSRF/login/새 CSRF를 수행하고 절대시간 barrier에서 동시에 시작한다.
- 인증 준비 구간을 예약 측정 구간과 분리한다. 1초 이상 늦게 시작한 사용자가 있으면 비교를 무효로 표시한다.
- OFF는 전용 공연의 admission 검사만 생략한다. 다른 공연은 계속 gate를 적용한다.
- Worker는 ON/OFF 모두 측정 중 1개다. Queue 초기화 동안만 0→1로 변경한다.
- 초기화는 해당 공연의 waiting/admitted/sequence와 active-concerts의 해당 멤버만 지운다.
- Session과 다른 공연, 기존 예약·결제 데이터는 삭제하지 않는다.
- ON polling 1초, global batch 50명, worker 100ms, admission 300초를 고정한다.
- 같은 macOS k6 머신(메모리 16 GiB), 같은 ALB를 사용한다.

## 순서

1명 smoke OFF/ON → 50명 OFF/ON → 100명 OFF/ON → 500명 3쌍 → 1,000명 OFF/ON.
500명은 run1 OFF→ON, run2 ON→OFF, run3 OFF→ON으로 순서 효과를 일부 상쇄한다.
1,000명에서도 병목이 나타나지 않으면 해당 수준에서 재현하지 못했다고 기록한다.
추가 1,500/2,000명은 자동 진행하지 않는다.

## 안전·측정 한계

- 사전 CloudWatch에서 CPU credit과 free memory를 확인한다. 새 인스턴스/scale-out은 하지 않는다.
- API 재시작/OOM/Ready 상실 또는 인증 준비 실패 시 다음 고부하로 진행하지 않는다.
- 샘플링한 Hikari pending은 하한이며 정확한 연속 최대값이 아니다.
- CloudWatch 1분 지표와 metrics-server CPU는 초 단위 burst에 대해 주변 인증 부하도 포함할 수 있다.
- PostgreSQL 연결 수는 기존 연결과 관측용 연결을 포함한다. pool 최대 연결 수는 API당 10이다.
- admission은 5분간 유효한 접근 권한이며, admitted 사용자당 요청 횟수나 동시 예약 수를 제한하지 않는다.
- 1초 polling이 admitted 사용자의 예약 요청을 다시 묶을 수 있다.
- 원하는 방향의 결과를 만들기 위해 batch/interval/SQL 지연을 변경하지 않는다.

## Protocol v2 (500명 준비 실패 후 사전 재정의)

동시 로그인 준비 500명에서 health probe timeout과 API 재시작이 발생해 예약 cohort를 만들지 못했다.
예약/Queue burst와 인증 CPU burst를 분리하기 위해 모든 N에서 인증 시작을 8 users/s로 고정한다.
로그인·CSRF 준비를 마친 각 VU는 기존과 같은 절대시간 barrier에서 동시에 측정을 시작한다.
애플리케이션, probe, replica, JVM, Queue batch/interval은 바꾸지 않는다.
기존 v1 50/100명 결과는 참고로 보존하고 최종 비교는 v2로 재측정한다.
실행: 50 run3, 100 run2, 500 run2/3/4, 1000 run1. 모든 run에 protocolVersion=2와 script SHA를 기록한다.

## 외부 연결 중단 후 재개

500 ON run2는 인증 준비 중 외부 네트워크 접근 실패로 종료되어 보존·제외한다.
완료한 v2 50 run3 및 100 run2는 유지한다. 남은 실행은 500 run3 OFF→ON, run4 ON→OFF, run5 OFF→ON의 세 쌍과 1000 run1 OFF→ON이다.
이미지·k6 script SHA·fixture·인증 시작 8 users/s·예약 barrier·계측 정책은 동일하다. 실험 운용 코드에 remaining 재개 명령과 미완료 CloudWatch 건너뛰기만 추가했다.

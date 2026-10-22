# 측정 및 판정 기준 보완 기록

이 파일은 실제 결과를 숨기지 않기 위해 운용·집계 변경을 기록한다. 애플리케이션 이미지와 k6 측정 스크립트는 protocol v2 내내 동일하다.

1. v1 500명 동시 로그인에서 cohort를 만들지 못해 인증 시작만 8 users/s인 v2로 재정의했다. v1은 참고로 보존하고 50/100명도 v2로 다시 측정했다.
2. ON 500 run2는 외부 연결 중단으로 측정 전 종료됐다. 완료된 결과를 덮어쓰지 않고 500 run3/4/5를 사용했다.
3. ON 500 run4에서 join 실패 때문에 예약 도달 수가 N보다 작았다. 처음의 운용 assertion은 이를 통제 실패로 취급해 기본 설정을 복원했지만, 로그인·출발·join 시도 N명은 충족됐다. assertion과 집계를 수정해 측정 후 발생한 실패를 cohort 미완료에 포함했다. 실패를 지우기 위해 재실행하지 않았다.
4. Hikari/Queue 샘플 요청이 지연될 수 있다. 수집 성공을 연속 peak 포착으로 해석하지 않고 표본 수/최대 간격을 결과 JSON에 추가했다. OFF 500 run5의 pending 0에는 관측 공백 주석을 붙인다.
5. 누적 acquire delta는 로그인 이후 before부터 after까지의 COUNT/TOTAL_TIME으로 계산한다. 샘플링한 pending peak와 다른 증거다.

## HTTP EOF 기록

| Run | 관측 | 전체 cohort 미완료 |
|---|---|---:|
| queue-off-500-run3 | 클라이언트 POST EOF 1건 | 1명 |
| queue-off-500-run4 | 클라이언트 POST EOF 1건 | 1명 |
| queue-off-500-run5 | 클라이언트 POST EOF 1건 | 1명 |
| queue-on-500-run3 | 클라이언트 POST EOF 0건 | 0명 |
| queue-on-500-run4 | 클라이언트 POST EOF 2건 | 2명 |
| queue-on-500-run5 | 클라이언트 POST EOF 1건 | 1명 |

HTTP 0/EOF는 클라이언트가 정상 HTTP 응답을 받지 못한 결과다. 특정 서버 예외나 DB rollback을 증명하지 않는다. ON run4/5의 예약 endpoint 성공률과 전체 cohort 완료율을 구분한다. 초기 OFF run3 대상 Pod의 로그는 교체 후 NotFound로 조회하지 못했다.

## 최종 추가 확인

1,000명 OFF는 예약 EOF 3건, ON은 join EOF 2건이다. OFF의 pending 표본 최대는 raw 0이지만 최대 약 11.86초 gap이 있어 비교 peak는 N/A로 표시한다. 500 OFF run5도 같은 보수적 표시를 적용한다. 2초 초과 gap+0 표본은 표기 규칙이며, 해당 run의 latency/실패·누적 acquire 결과를 제거하는 규칙이 아니다.

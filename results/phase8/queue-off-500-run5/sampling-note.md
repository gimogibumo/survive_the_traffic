# Hikari 샘플의 부하 구간 관측 공백

두 Pod의 요청 시작 시각은 burst 약 -0.005초 다음 약 +7.24/+7.70초로 건너뛴다. 실제 예약 max 약 5.80초보다 긴 간격이므로 pending 관측 최대 0은 부하 중 peak를 관측한 값이 아니다. Actuator가 같은 API 서버/port-forward를 거치는 방식의 한계다. 실시간 pending 최대를 0으로 해석하지 않는다. before/after acquire COUNT/TOTAL_TIME은 누적 delta이므로 별도로 유지한다. k6의 로그인·출발 통제와 실제 오류·latency 결과는 보존한다.

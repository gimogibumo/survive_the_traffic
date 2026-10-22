1명 OFF 준비 중 API rollout 직후 ALB GET /api/csrf가 HTTP 502를 반환했다. k6는 실행되지 않았고 예약 부하는 시작하지 않았다. 비교 결과에서 제외한다. ALB readiness/CSRF 연속 성공 확인을 추가한 뒤 별도 run으로 재실행한다.

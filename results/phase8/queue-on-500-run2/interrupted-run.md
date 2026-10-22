# 비교 제외: 외부 연결 중단

로그인 준비 중 localhost port-forward connection reset과 ALB network is unreachable이 기록되었다. 로그인 성공 388명, 실패 1명이며 준비 중 종료된 VU가 있어 500명 cohort가 형성되지 않았다. 예약 단계에 도달하지 않았고 측정 전 Hikari snapshot도 없다. 원인을 API/DB 과부하로 단정하지 않는다. 종료 후 조회한 현재 API 2개는 ready, restartCount 0이다. 원본 k6-summary와 로그는 보존한다. 500명은 run3/4/5에서 모드별 3회 새로 비교한다.

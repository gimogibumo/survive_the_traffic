import csv
import json
import math
import time
import urllib.request
from datetime import datetime


BASE_URL = "http://localhost:8080"

# 이번 실험 이름에 맞게 바꿔도 됨
OUTPUT = "metrics-800-batch10-poll1.csv"

# Actuator 자체가 부하를 만들지 않도록 250ms 간격
INTERVAL = 0.25


METRICS = {
    "active": "hikaricp.connections.active",
    "pending": "hikaricp.connections.pending",
    "acquire": "hikaricp.connections.acquire",

    "process_cpu": "process.cpu.usage",
    "system_cpu": "system.cpu.usage",

    "threads": "jvm.threads.live",
}


# --------------------------------------------------
# Actuator metric 조회
# --------------------------------------------------

def fetch(metric_name):
    url = f"{BASE_URL}/actuator/metrics/{metric_name}"

    with urllib.request.urlopen(
        url,
        timeout=2
    ) as response:

        data = json.loads(
            response.read()
        )

    return {
        measurement["statistic"]:
            measurement["value"]

        for measurement
        in data["measurements"]
    }


# --------------------------------------------------
# 숫자 안전 변환
#
# Actuator metric에서 간혹
# "NaN", "Infinity", 문자열 등이 들어올 수 있으므로
# float 변환 후 유효한 숫자인지 검사
# --------------------------------------------------

def to_float(value):

    try:
        number = float(value)

        if math.isnan(number):
            return None

        if math.isinf(number):
            return None

        return number

    except (TypeError, ValueError):
        return None


# --------------------------------------------------
# 처음 acquire 누적값 baseline
# --------------------------------------------------

start_acquire = fetch(
    METRICS["acquire"]
)

start_count = to_float(
    start_acquire.get("COUNT")
)

start_total = to_float(
    start_acquire.get("TOTAL_TIME")
)

if start_count is None:
    start_count = 0.0

if start_total is None:
    start_total = 0.0


# --------------------------------------------------
# Max 값 저장
# --------------------------------------------------

max_active = 0.0
max_pending = 0.0

max_process_cpu = 0.0
max_system_cpu = 0.0

max_threads = 0.0


print()
print("===== METRIC WATCH START =====")

print(
    f"baseline acquire count = "
    f"{start_count}"
)

print(
    f"baseline acquire total = "
    f"{start_total:.6f} sec"
)

print(
    f"interval               = "
    f"{INTERVAL} sec"
)

print(
    f"output                 = "
    f"{OUTPUT}"
)

print()
print("Run k6 in another terminal.")
print("Ctrl+C after k6 finishes.")
print()


# --------------------------------------------------
# CSV 기록
# --------------------------------------------------

with open(
    OUTPUT,
    "w",
    newline=""
) as file:

    writer = csv.writer(file)

    writer.writerow([
        "timestamp",

        "active",
        "pending",

        "acquire_count",
        "acquire_total_time",

        "process_cpu",
        "system_cpu",

        "live_threads",
    ])

    try:

        while True:

            # ------------------------------------------
            # Hikari active
            # ------------------------------------------

            active_raw = fetch(
                METRICS["active"]
            ).get("VALUE")

            active = to_float(
                active_raw
            )


            # ------------------------------------------
            # Hikari pending
            # ------------------------------------------

            pending_raw = fetch(
                METRICS["pending"]
            ).get("VALUE")

            pending = to_float(
                pending_raw
            )


            # ------------------------------------------
            # Hikari acquire
            # ------------------------------------------

            acquire = fetch(
                METRICS["acquire"]
            )

            acquire_count = to_float(
                acquire.get("COUNT")
            )

            acquire_total = to_float(
                acquire.get("TOTAL_TIME")
            )


            # ------------------------------------------
            # JVM Process CPU
            # ------------------------------------------

            process_cpu_raw = fetch(
                METRICS["process_cpu"]
            ).get("VALUE")

            process_cpu = to_float(
                process_cpu_raw
            )


            # ------------------------------------------
            # 전체 System CPU
            # ------------------------------------------

            system_cpu_raw = fetch(
                METRICS["system_cpu"]
            ).get("VALUE")

            system_cpu = to_float(
                system_cpu_raw
            )


            # ------------------------------------------
            # JVM live thread
            # ------------------------------------------

            threads_raw = fetch(
                METRICS["threads"]
            ).get("VALUE")

            threads = to_float(
                threads_raw
            )


            # ------------------------------------------
            # Max 갱신
            #
            # None이면 해당 샘플은 무시
            # ------------------------------------------

            if active is not None:

                max_active = max(
                    max_active,
                    active
                )


            if pending is not None:

                max_pending = max(
                    max_pending,
                    pending
                )


            if process_cpu is not None:

                max_process_cpu = max(
                    max_process_cpu,
                    process_cpu
                )


            if system_cpu is not None:

                max_system_cpu = max(
                    max_system_cpu,
                    system_cpu
                )


            if threads is not None:

                max_threads = max(
                    max_threads,
                    threads
                )


            # ------------------------------------------
            # CSV 저장
            #
            # None은 빈칸으로 기록
            # ------------------------------------------

            writer.writerow([
                datetime.now().isoformat(),

                active
                if active is not None
                else "",

                pending
                if pending is not None
                else "",

                acquire_count
                if acquire_count is not None
                else "",

                acquire_total
                if acquire_total is not None
                else "",

                process_cpu
                if process_cpu is not None
                else "",

                system_cpu
                if system_cpu is not None
                else "",

                threads
                if threads is not None
                else "",
            ])

            file.flush()


            # ------------------------------------------
            # 터미널 출력용 값
            #
            # metric이 None이면 화면에는 N/A 출력
            # ------------------------------------------

            active_text = (
                f"{active:.0f}"
                if active is not None
                else "N/A"
            )

            pending_text = (
                f"{pending:.0f}"
                if pending is not None
                else "N/A"
            )

            process_cpu_text = (
                f"{process_cpu:.3f}"
                if process_cpu is not None
                else "N/A"
            )

            system_cpu_text = (
                f"{system_cpu:.3f}"
                if system_cpu is not None
                else "N/A"
            )

            threads_text = (
                f"{threads:.0f}"
                if threads is not None
                else "N/A"
            )


            print(
                f"active={active_text:>4} "
                f"pending={pending_text:>4} "
                f"procCPU={process_cpu_text:>6} "
                f"sysCPU={system_cpu_text:>6} "
                f"threads={threads_text:>5}"
            )


            time.sleep(
                INTERVAL
            )


    except KeyboardInterrupt:

        print()
        print(
            "Metric watcher stopped."
        )


# --------------------------------------------------
# 종료 시 acquire 누적값 다시 조회
# --------------------------------------------------

end_acquire = fetch(
    METRICS["acquire"]
)

end_count = to_float(
    end_acquire.get("COUNT")
)

end_total = to_float(
    end_acquire.get("TOTAL_TIME")
)


if end_count is None:
    end_count = start_count

if end_total is None:
    end_total = start_total


# --------------------------------------------------
# 이번 실험에서 증가한 값만 계산
# --------------------------------------------------

delta_count = (
    end_count
    -
    start_count
)

delta_total = (
    end_total
    -
    start_total
)


if delta_count > 0:

    avg_acquire_ms = (
        delta_total
        /
        delta_count
    ) * 1000

else:

    avg_acquire_ms = 0.0


# --------------------------------------------------
# 최종 결과
# --------------------------------------------------

print()
print("===== RESULT =====")

print(
    f"max active          = "
    f"{max_active}"
)

print(
    f"max pending         = "
    f"{max_pending}"
)

print(
    f"max process cpu     = "
    f"{max_process_cpu:.3f}"
)

print(
    f"max system cpu      = "
    f"{max_system_cpu:.3f}"
)

print(
    f"max live threads    = "
    f"{max_threads}"
)

print(
    f"acquire count delta = "
    f"{delta_count}"
)

print(
    f"acquire total delta = "
    f"{delta_total:.6f} sec"
)

print(
    f"avg acquire         = "
    f"{avg_acquire_ms:.3f} ms"
)

print(
    f"csv                 = "
    f"{OUTPUT}"
)

print()
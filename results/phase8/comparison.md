| Run | Protocol | Reservation p95 ms | p99 ms | Error % | Pending max/Pod | DB connections | Queue wait p95 ms | Polls/user | E2E p95 ms | Control | Telemetry |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|
| queue-off-1-run2 | 1 | 126.10 | 126.10 | 0.00 | 0.00 | 7.00 | N/A | 0.00 | 126.00 | valid | complete |
| queue-on-1-run2 | 1 | 64.02 | 64.02 | 0.00 | 0.00 | 7.00 | 1281.00 | 2.00 | 1345.00 | valid | partial |
| queue-off-50-run1 | 1 | 1093.83 | 1099.49 | 0.00 | 17.00 | 17.00 | N/A | 0.00 | 1094.65 | valid | partial |
| queue-off-50-run2 | 1 | 1064.39 | 1070.15 | 0.00 | 14.00 | 16.00 | N/A | 0.00 | 1064.10 | valid | complete |
| queue-off-50-run3 | 2 | 1232.71 | 1254.67 | 0.00 | 21.00 | 16.00 | N/A | 0.00 | 1232.90 | valid | complete |
| queue-on-50-run2 | 1 | 1223.06 | 1245.84 | 0.00 | 8.00 | 17.00 | 1733.45 | 1.00 | 2385.00 | valid | complete |
| queue-on-50-run3 | 2 | 903.17 | 982.87 | 0.00 | 8.00 | 15.00 | 2283.65 | 1.26 | 2615.55 | valid | complete |
| queue-off-100-run1 | 1 | 2169.41 | 2171.07 | 0.00 | 27.00 | 23.00 | N/A | 0.00 | 2170.00 | valid | complete |
| queue-off-100-run2 | 2 | 1916.06 | 1980.17 | 0.00 | 43.00 | 22.00 | N/A | 0.00 | 1916.95 | valid | complete |
| queue-on-100-run1 | 1 | 1595.26 | 1911.47 | 0.00 | 23.00 | 23.00 | 2787.05 | 1.00 | 4084.00 | valid | complete |
| queue-on-100-run2 | 2 | 1984.66 | 2030.58 | 0.00 | 9.00 | 21.00 | 3415.10 | 1.00 | 4177.00 | valid | complete |
| queue-off-500-run1 | 1 | 2495.67 | 2508.90 | 94.80 | 0.00 | 18.00 | N/A | 0.00 | 2497.25 | invalid | complete |
| queue-off-500-run3 | 2 | 6246.35 | 6381.57 | 0.20 | 183.00 | 23.00 | N/A | 0.00 | 6313.05 | valid | complete |
| queue-off-500-run4 | 2 | 7039.48 | 7265.14 | 0.20 | 185.00 | 23.00 | N/A | 0.00 | 7057.40 | valid | complete |
| queue-off-500-run5 | 2 | 5554.84 | 5664.79 | 0.20 | N/A | 23.00 | N/A | 0.00 | 5766.05 | valid | complete |
| queue-on-500-run3 | 2 | 5188.61 | 5547.52 | 0.00 | 99.00 | 23.00 | 7606.90 | 1.00 | 10946.45 | valid | complete |
| queue-on-500-run4 | 2 | 4091.19 | 5546.50 | 0.40 | 150.00 | 23.00 | 6258.00 | 1.00 | 9825.20 | valid | complete |
| queue-on-500-run5 | 2 | 4191.75 | 5617.35 | 0.20 | 162.00 | 23.00 | 6834.50 | 1.00 | 10219.00 | valid | complete |
| queue-off-1000-run1 | 2 | 16188.94 | 17440.63 | 0.30 | N/A | 23.00 | N/A | 0.00 | 16480.05 | valid | complete |
| queue-on-1000-run1 | 2 | 8167.16 | 9015.68 | 0.20 | 178.00 | 23.00 | 8295.60 | 1.00 | 15566.30 | valid | complete |

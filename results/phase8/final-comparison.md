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

Protocol v2, valid control, core telemetry collected successfully.
Repeated percentiles are medians of run percentiles; they are not pooled percentiles.
Error is cohort noncompletion, including queue-path failures.
Pending N/A: zero samples with an observation gap over 2s cannot establish a zero peak. Raw samples are preserved.

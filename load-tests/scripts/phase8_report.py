#!/usr/bin/env python3
"""Summarize measured runs; missing samples stay missing rather than becoming zero."""
import csv
import datetime as dt
import json
from pathlib import Path
import statistics
import re

ROOT = Path(__file__).resolve().parents[2] / 'results/phase8'


def statistic(measurements, name='VALUE'):
    return next((item['value'] for item in measurements if item['statistic'] == name), None)


def read_csv(path):
    with path.open() as source:
        return [{key: float(value) for key, value in row.items()} for row in csv.DictReader(source)]


def summarize(directory):
    metadata = json.loads((directory / 'metadata.json').read_text())
    if 'end' not in metadata or not (directory / 'k6-summary.json').exists():
        return None
    metrics = json.loads((directory / 'k6-summary.json').read_text())['metrics']
    n = metadata['users']
    def values(name):
        return metrics.get(name, {}).get('values', {})
    latency = values('reservation_latency')
    wait = values('queue_wait_time')
    pending, active, intervals = {}, {}, []
    for file in directory.glob('hikari-*.jsonl'):
        rows = [json.loads(line) for line in file.read_text().splitlines()]
        pod = rows[0]['pod'] if rows else file.stem
        pending_values = [statistic(row['metrics']['hikaricp.connections.pending'])
                          for row in rows if 'hikaricp.connections.pending' in row['metrics']]
        active_values = [statistic(row['metrics']['hikaricp.connections.active'])
                         for row in rows if 'hikaricp.connections.active' in row['metrics']]
        pending[pod] = max(pending_values) if pending_values else None
        active[pod] = max(active_values) if active_values else None
        intervals += [b['timestamp'] - a['timestamp'] for a, b in zip(rows, rows[1:])]
    before = json.loads((directory / 'hikari-before.json').read_text())
    after = json.loads((directory / 'hikari-after.json').read_text())
    acquire_count = acquire_time = timeouts = 0
    for pod, data in after.items():
        name = 'hikaricp.connections.acquire'
        if name in data and name in before[pod]:
            acquire_count += statistic(data[name]['measurements'], 'COUNT') - statistic(before[pod][name]['measurements'], 'COUNT')
            acquire_time += statistic(data[name]['measurements'], 'TOTAL_TIME') - statistic(before[pod][name]['measurements'], 'TOTAL_TIME')
        name = 'hikaricp.connections.timeout'
        if name in data and name in before[pod]:
            timeouts += statistic(data[name]['measurements'], 'COUNT') - statistic(before[pod][name]['measurements'], 'COUNT')
    queue = read_csv(directory / 'queue-depth.csv')
    db = read_csv(directory / 'db-connections.csv')
    errors = json.loads((directory / 'observer-errors.json').read_text())
    pod_samples = {'api': [], 'worker': []}
    for line in (directory / 'kubectl-top.txt').read_text().splitlines():
        parts = line.split()
        if len(parts) != 3:
            continue
        role = 'api' if parts[0].startswith('ticketing-api-') else \
               'worker' if parts[0].startswith('ticketing-queue-worker-') else None
        if role:
            cpu = float(parts[1][:-1]) if parts[1].endswith('m') else float(parts[1])*1000
            memory = re.fullmatch(r'([0-9.]+)(Ki|Mi|Gi)', parts[2])
            if memory:
                mib = float(memory[1]) * {'Ki': 1/1024, 'Mi': 1, 'Gi': 1024}[memory[2]]
                pod_samples[role].append((cpu, mib))
    cloudwatch = json.loads((directory / 'rds-metrics.json').read_text()) if (directory / 'rds-metrics.json').exists() else {}
    cw = {item['Id']: item.get('Values', []) for item in cloudwatch.get('MetricDataResults', [])}
    done = values('reservation_success').get('passes', 0)
    attempts = values('reservation_status').get('count', 0)
    row = {
        'run': directory.name, 'users': n, 'mode': metadata['mode'],
        'protocol_version': metadata.get('protocolVersion', 1),
        'reservation_p50_ms': latency.get('med'), 'reservation_p95_ms': latency.get('p(95)'),
        'reservation_p99_ms': latency.get('p(99)'), 'reservation_max_ms': latency.get('max'),
        'reservation_avg_ms': latency.get('avg'),
        'reservation_successes': done, 'reservation_attempts': attempts,
        'error_rate': 1 - done / n,
        'reservation_endpoint_error_rate': values('http_req_failed{endpoint:reservation}').get('rate'),
        'queue_join_failures': values('queue_join_success').get('fails', 0),
        'login_successes': values('login_success').get('passes', 0),
        'late_users': values('late_users').get('count', 0),
        'burst_lateness_p99_ms': values('burst_lateness_ms').get('p(99)'),
        'queue_wait_p95_ms': wait.get('p(95)'), 'queue_wait_p99_ms': wait.get('p(99)'),
        'end_to_end_p95_ms': values('reservation_end_to_end').get('p(95)'),
        'poll_count': values('queue_status_poll_count').get('count', 0),
        'polls_per_user': values('queue_status_poll_count').get('count', 0) / n,
        'queue_depth_max_observed': max((r['waiting'] for r in queue), default=None),
        'admitted_max_observed': max((r['admitted'] for r in queue), default=None),
        'queue_sample_interval_median_s': statistics.median([b['timestamp'] - a['timestamp'] for a, b in zip(queue, queue[1:])]) if len(queue) > 1 else None,
        'queue_sample_count': len(queue),
        'queue_sample_interval_max_s': max((b['timestamp'] - a['timestamp'] for a, b in zip(queue, queue[1:])), default=None),
        'hikari_pending_max_per_pod': pending,
        'hikari_pending_max_observed': max((v for v in pending.values() if v is not None), default=None),
        'hikari_active_max_per_pod': active,
        'hikari_sample_interval_median_s': statistics.median(intervals) if intervals else None,
        'hikari_sample_interval_max_s': max(intervals, default=None),
        'hikari_sampling_gap_over_2s': any(interval > 2 for interval in intervals),
        'acquire_count_delta': acquire_count,
        'acquire_mean_ms': acquire_time * 1000 / acquire_count if acquire_count > 0 and acquire_time >= 0 else None,
        'metric_counter_reset': acquire_count < 0 or acquire_time < 0 or timeouts < 0,
        'connection_timeout_delta': timeouts,
        'db_connections_max_observed': max((r['connections'] for r in db), default=None),
        'db_active_max_observed': max((r['active'] for r in db), default=None),
        'db_lock_waiters_max_observed': max((r['waiting'] for r in db), default=None),
        'db_sample_count': len(db),
        'http_req_failed': values('http_req_failed').get('rate'),
        'http_reqs': values('http_reqs').get('count'),
        'iteration_duration': values('iteration_duration'),
        'observer_errors': errors, 'k6_exit': metadata['k6ExitCode'],
        'core_telemetry_complete': not errors and bool(queue) and bool(db) and
                                   len(pending) == 2 and all(v is not None for v in pending.values()) and
                                   all(pod_samples.values()),
        'valid_control': values('login_success').get('passes', 0) == n and
                         values('login_success').get('fails', 0) == 0 and
                         (metadata.get('protocolVersion', 1) < 2 or values('auth_ready').get('count', 0) == n) and
                         values('late_users').get('count', 0) == 0 and
                         (sum(values('queue_join_success').get(k, 0) for k in ['passes', 'fails']) == n
                          if metadata['mode'] == 'ON' else attempts == n),
    }
    for role, samples in pod_samples.items():
        row[role + '_cpu_max_observed_millicores'] = max((sample[0] for sample in samples), default=None)
        row[role + '_memory_max_observed_mib'] = max((sample[1] for sample in samples), default=None)
    for key, identifier in [('rds_connections_coarse_max', 'rds0'), ('rds_cpu_coarse_max_percent', 'rds1'),
                            ('rds_read_iops_coarse_max', 'rds2'), ('rds_write_iops_coarse_max', 'rds3')]:
        row[key] = max(cw.get(identifier, []), default=None)
    # A zero sample across a long observation gap cannot establish a zero peak.
    # Preserve the raw zero, but do not present it as comparable absence of pressure.
    row['hikari_pending_peak_unresolved'] = row['hikari_pending_max_observed'] == 0 and any(i > 2 for i in intervals)
    row['hikari_pending_max_for_comparison'] = (None if row['hikari_pending_peak_unresolved']
                                               else row['hikari_pending_max_observed'])
    full = next((sample['timestamp'] for sample in queue if sample['admitted'] >= n), None)
    row['cohort_admission_observed_seconds'] = full - metadata['burstAt'] if full is not None and metadata['mode'] == 'ON' else None
    return row


def number(value):
    return 'N/A' if value is None else f'{value:.2f}'


if __name__ == '__main__':
    rows = []
    inventory = []
    for directory in sorted(ROOT.glob('queue-*')):
        if not directory.is_dir():
            continue
        notes = [str(path.relative_to(ROOT)) for path in sorted(directory.glob('*.md'))
                 if path.name != 'metadata.md']
        entry = {'run': directory.name, 'notes': notes, 'status': 'preflight-only'}
        if (directory / 'metadata.json').exists():
            metadata = json.loads((directory / 'metadata.json').read_text())
            entry.update({'status': 'interrupted', 'users': metadata['users'],
                          'mode': metadata['mode'], 'protocolVersion': metadata.get('protocolVersion', 1)})
            for key in ['start', 'end']:
                if key in metadata:
                    entry[key + 'Utc'] = dt.datetime.fromtimestamp(metadata[key], dt.timezone.utc).isoformat()
            row = summarize(directory)
            if row:
                rows.append(row)
                entry['status'] = ('primary' if row['protocol_version'] == 2 and
                                   row['valid_control'] and row['core_telemetry_complete'] else 'reference-or-excluded')
        inventory.append(entry)
    (ROOT / 'run-inventory.json').write_text(json.dumps(inventory, indent=2, ensure_ascii=False))
    rows.sort(key=lambda row: (row['users'], row['run']))
    (ROOT / 'comparison.json').write_text(json.dumps(rows, indent=2, ensure_ascii=False))
    fields = ['run', 'users', 'mode', 'reservation_p95_ms', 'reservation_p99_ms',
              'error_rate', 'hikari_pending_max_observed', 'db_connections_max_observed',
              'db_active_max_observed', 'acquire_mean_ms', 'queue_wait_p95_ms',
              'queue_depth_max_observed', 'polls_per_user', 'end_to_end_p95_ms',
              'hikari_pending_max_for_comparison', 'hikari_pending_peak_unresolved',
              'valid_control', 'core_telemetry_complete']
    with open(ROOT / 'comparison.csv', 'w') as output:
        writer = csv.DictWriter(output, fieldnames=fields, extrasaction='ignore')
        writer.writeheader()
        writer.writerows(rows)
    text = ['| Run | Protocol | Reservation p95 ms | p99 ms | Error % | Pending max/Pod | DB connections | Queue wait p95 ms | Polls/user | E2E p95 ms | Control | Telemetry |',
            '|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|']
    for row in rows:
        text.append('| ' + ' | '.join([row['run'], str(row['protocol_version']), number(row['reservation_p95_ms']),
            number(row['reservation_p99_ms']), number(row['error_rate']*100),
            number(row['hikari_pending_max_for_comparison']), number(row['db_connections_max_observed']),
            number(row['queue_wait_p95_ms']), number(row['polls_per_user']),
            number(row['end_to_end_p95_ms']),
            'valid' if row['valid_control'] else 'invalid',
            'complete' if row['core_telemetry_complete'] else 'partial']) + ' |')
    (ROOT / 'comparison.md').write_text('\n'.join(text) + '\n')
    primary = [row for row in rows if row['protocol_version'] == 2 and row['valid_control'] and row['core_telemetry_complete']]
    grouped = []
    repeated_metrics = ['reservation_p95_ms', 'reservation_p99_ms', 'hikari_pending_max_observed',
                        'acquire_mean_ms', 'queue_wait_p95_ms', 'end_to_end_p95_ms', 'polls_per_user']
    for users in sorted({row['users'] for row in primary}):
        for mode in ['OFF', 'ON']:
            group = [row for row in primary if row['users'] == users and row['mode'] == mode]
            if not group:
                continue
            result = {'users': users, 'mode': mode, 'runs': [row['run'] for row in group], 'repetitions': len(group),
                      'error_rate': 1-sum(row['reservation_successes'] for row in group)/(users*len(group)),
                      'pending_max_all_runs': max((row['hikari_pending_max_for_comparison'] for row in group
                                                  if row['hikari_pending_max_for_comparison'] is not None), default=None),
                      'pending_unresolved_runs': [row['run'] for row in group if row['hikari_pending_peak_unresolved']],
                      'db_connections_max_all_runs': max(row['db_connections_max_observed'] for row in group)}
            for metric in repeated_metrics:
                samples = [row[metric] for row in group if row[metric] is not None]
                result[metric] = {'mean': statistics.mean(samples), 'median': statistics.median(samples),
                                  'min': min(samples), 'max': max(samples)} if samples else None
            grouped.append(result)
    (ROOT / 'repeat-statistics.json').write_text(json.dumps(grouped, indent=2))
    final = ['| Users | Mode | Runs | Reservation p95 ms | p99 ms | Error % | Pending max/Pod | DB connections | Queue wait p95 ms | E2E p95 ms |',
             '|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|']
    for row in grouped:
        def med(key):
            return number(row[key]['median']) if row[key] else 'N/A'
        final.append('| ' + ' | '.join([str(row['users']), row['mode'], str(row['repetitions']),
            med('reservation_p95_ms'), med('reservation_p99_ms'), number(row['error_rate']*100),
            number(row['pending_max_all_runs']), number(row['db_connections_max_all_runs']),
            med('queue_wait_p95_ms'), med('end_to_end_p95_ms')]) + ' |')
    (ROOT / 'final-comparison.md').write_text('\n'.join(final) + '\n\nProtocol v2, valid control, core telemetry collected successfully.\nRepeated percentiles are medians of run percentiles; they are not pooled percentiles.\nError is cohort noncompletion, including queue-path failures.\nPending N/A: zero samples with an observation gap over 2s cannot establish a zero peak. Raw samples are preserved.\n')
    print('\n'.join(text))

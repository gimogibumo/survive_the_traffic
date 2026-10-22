#!/usr/bin/env python3
"""Phase 8: isolated fixtures, paired bursts and safe telemetry. Never logs credentials."""
import argparse
import csv
import datetime as dt
import hashlib
import http.cookiejar
from http.cookies import SimpleCookie
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import threading
import time
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / 'results/phase8'
PRIVATE = Path(os.environ.get('PHASE8_STATE_DIR', '/tmp/stt-phase8'))
KUBE = os.environ.get('KUBECTL', '/tmp/ticketing-tools/kubectl')
NS = ['-n', 'ticketing']
POD = 'ticketing-phase8-tools'
METRICS = ['hikaricp.connections', 'hikaricp.connections.active',
           'hikaricp.connections.idle', 'hikaricp.connections.pending',
           'hikaricp.connections.acquire', 'hikaricp.connections.timeout',
           'hikaricp.connections.max']


def log(message):
    print(dt.datetime.now(dt.timezone.utc).isoformat() + ' ' + message, flush=True)


def command(args, stdin=None, timeout=400):
    result = subprocess.run(args, input=stdin, text=True, capture_output=True, timeout=timeout)
    if result.returncode:
        # Do not include SQL, HTTP bodies or process environment in exceptions.
        raise RuntimeError(f'{Path(args[0]).name} {args[1]} failed (exit {result.returncode})')
    return result.stdout


def kubectl(*args, **kwargs):
    return command([KUBE, *NS, *args], **kwargs)


def db(sql):
    return kubectl('exec', '-i', POD, '-c', 'db', '--', 'psql', '-X', '-qAt',
                   '-v', 'ON_ERROR_STOP=1', stdin=sql)


def redis(*args):
    # Password comes from REDISCLI_AUTH injected by the existing Kubernetes Secret.
    return kubectl('exec', POD, '-c', 'valkey', '--', 'sh', '-c',
                   'exec redis-cli --tls --cacert /tmp/phase8-amazon-root-ca.pem --sni "$REDIS_HOST" -h "$REDIS_HOST" -p "$REDIS_PORT" --raw "$@"',
                   'redis-cli', *map(str, args))


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False))


def state():
    return json.loads((PRIVATE / 'state.json').read_text())


def request(base, path, headers=None, body=None, opener=None):
    req = urllib.request.Request(base + path, headers=headers or {},
                                 data=json.dumps(body).encode() if body is not None else None)
    with (opener.open(req, timeout=30) if opener else
          urllib.request.urlopen(req, timeout=30)) as response:
        return json.load(response), response.headers


def login(base, email, password):
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    request(base, '/api/csrf', opener=opener)
    token = next(c.value for c in jar if c.name == 'XSRF-TOKEN')
    request(base, '/api/auth/login', {'Content-Type': 'application/json',
            'X-XSRF-TOKEN': token}, {'email': email, 'password': password}, opener)
    return '; '.join(c.name + '=' + c.value for c in jar if c.name == 'SESSION')


def prepare(count):
    if (PRIVATE / 'state.json').exists():
        raise RuntimeError('Campaign already exists; reuse its fixtures instead of reseeding')
    RESULTS.mkdir(parents=True, exist_ok=True)
    PRIVATE.mkdir(mode=0o700, parents=True, exist_ok=True)
    PRIVATE.chmod(0o700)
    assert 20 <= count <= 1000
    deployment = json.loads(kubectl('get', 'deployment', 'ticketing-api', '-o', 'json'))
    container = deployment['spec']['template']['spec']['containers'][0]
    assert container['image'].endswith(':' + command(['git', 'rev-parse', 'HEAD']).strip())
    assert not any(e['name'] == 'QUEUE_ADMISSION_BYPASS_CONCERT_ID' for e in container['env'])
    campaign = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d%H%M%S')
    password = secrets.token_urlsafe(9)
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    crypto = sorted(cache.glob('org.springframework.security/spring-security-crypto/*/*/*.jar'))[-1]
    logging = sorted(cache.glob('commons-logging/commons-logging/*/*/*.jar'))[-1]
    encoded = command(['java', '--class-path', str(crypto) + os.pathsep + str(logging),
                       str(ROOT / 'load-tests/scripts/Phase8PasswordHash.java')], stdin=password + '\n')
    assert encoded.startswith('$2a$10$') and len(encoded) == 60
    sql = f"""
WITH c AS (
 INSERT INTO concert(title,reservation_open_at,concert_at,created_at)
 VALUES ('Phase 8 Queue Validation {campaign}',now()-interval '1 day',now()+interval '30 days',now())
 RETURNING id
), s AS (
 INSERT INTO seat(concert_id,section,row_number,seat_number,price,status,version)
 SELECT c.id,'P8',(i-1)/100+1,(i-1)%100+1,10000,'AVAILABLE',0
 FROM c CROSS JOIN generate_series(1,{count}) i
 RETURNING id,row_number,seat_number
), u AS (
 INSERT INTO users(email,name,password_hash,created_at)
 SELECT 'queue-load-{campaign}-'||lpad(i::text,4,'0')||'@example.com',
 'Phase8 fixture '||i,'{encoded}',now() FROM generate_series(1,{count}) i
 RETURNING id,email
)
SELECT json_build_object('concertId',(SELECT id FROM c),
 'users',(SELECT json_agg(json_build_object('userId',id,'email',email) ORDER BY email) FROM u),
 'seats',(SELECT json_agg(id ORDER BY row_number,seat_number) FROM s));
"""
    seeded = json.loads(db(sql).strip())
    fixture = [{**u, 'seatId': seat} for u, seat in zip(seeded['users'], seeded['seats'])]
    assert len(fixture) == count
    write_json(RESULTS / 'fixture.json', fixture)
    data = {'campaign': campaign, 'password': password, 'concertId': seeded['concertId'],
            'count': count, 'image': container['image']}
    fd = os.open(PRIVATE / 'state.json', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as output:
        json.dump(data, output)
    log(f'Prepared concert={seeded["concertId"]}, users={count}, seats={count}; credentials are private')


def reset_database(s):
    cid = int(s['concertId'])
    # Guard every destructive query with the exact campaign-owned concert.
    guard = db(f"SELECT count(*) FROM concert WHERE id={cid} AND title='Phase 8 Queue Validation {s['campaign']}';")
    assert guard.strip() == '1'
    foreign = db(f"""SELECT count(*) FROM reservation r JOIN seat s ON s.id=r.seat_id
WHERE s.concert_id={cid} AND (EXISTS(SELECT 1 FROM payment p WHERE p.reservation_id=r.id)
 OR NOT EXISTS(SELECT 1 FROM users u WHERE u.id=r.user_id AND u.email LIKE 'queue-load-{s['campaign']}-%@example.com'));""")
    assert foreign.strip() == '0', 'Unexpected data in fixture: refusing cleanup'
    db(f"""BEGIN;
DELETE FROM reservation r USING seat s WHERE r.seat_id=s.id AND s.concert_id={cid};
UPDATE seat SET status='AVAILABLE',version=0 WHERE concert_id={cid};
COMMIT;""")


def reset(s):
    reset_database(s)
    cid = int(s['concertId'])
    kubectl('scale', 'deployment/ticketing-queue-worker', '--replicas=0')
    for _ in range(50):
        if not json.loads(kubectl('get', 'pods', '-l', 'app=ticketing-queue-worker', '-o', 'json'))['items']:
            break
        time.sleep(1)
    else:
        raise RuntimeError('Worker did not stop; queue was not reset')
    redis('EVAL', 'redis.call("DEL",KEYS[1],KEYS[2],KEYS[3]); return redis.call("SREM",KEYS[4],ARGV[1])',
          4, f'queue:waiting:{cid}', f'queue:admitted:{cid}', f'queue:sequence:{cid}', 'queue:active-concerts', cid)
    kubectl('scale', 'deployment/ticketing-queue-worker', '--replicas=1')
    kubectl('rollout', 'status', 'deployment/ticketing-queue-worker', '--timeout=180s')


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


class Observers:
    def __init__(self, directory, cookie):
        self.directory, self.cookie = directory, cookie
        self.processes, self.targets, self.threads = [], [], []
        self.stop = threading.Event()
        self.errors = []

    def connect(self):
        pods = json.loads(kubectl('get', 'pods', '-l', 'app=ticketing-api', '-o', 'json'))['items']
        pods = [p for p in pods if not p['metadata'].get('deletionTimestamp')]
        assert len(pods) == 2
        for pod in pods:
            name = pod['metadata']['name']
            port = free_port()
            stream = open(PRIVATE / f'{name}-forward.log', 'w')
            process = subprocess.Popen([KUBE, 'port-forward', *NS, 'pod/' + name,
                                        f'{port}:8080'], stdout=stream, stderr=stream)
            stream.close()
            self.processes.append(process)
            base = f'http://127.0.0.1:{port}'
            for _ in range(30):
                try:
                    names, _ = request(base, '/actuator/metrics', {'Cookie': self.cookie})
                    available = [m for m in METRICS if m in names['names']]
                    self.targets.append((name, base, available))
                    break
                except Exception:
                    time.sleep(0.5)
            else:
                raise RuntimeError('Cannot access per-pod Actuator')

    def snapshot(self, filename):
        data = {}
        for name, base, available in self.targets:
            data[name] = {metric: request(base, '/actuator/metrics/' + metric,
                                         {'Cookie': self.cookie})[0] for metric in available}
        write_json(self.directory / filename, data)
        return data

    def warmup(self, s, fixture):
        cid = s['concertId']
        redis('ZADD', f'queue:admitted:{cid}', int(time.time()*1000)+300000, fixture[0]['userId'])
        for index, (_, base, _) in enumerate(self.targets):
            _, response_headers = request(base, '/api/csrf', {'Cookie': self.cookie})
            cookies = SimpleCookie()
            for header in response_headers.get_all('Set-Cookie', []):
                cookies.load(header)
            token = cookies['XSRF-TOKEN'].value
            headers = {'Cookie': self.cookie + '; XSRF-TOKEN=' + token,
                       'X-XSRF-TOKEN': token, 'Content-Type': 'application/json'}
            for user in fixture[index*10:(index+1)*10]:
                response, _ = request(base, '/api/reservations', headers,
                                      {'concertId': cid, 'seatId': user['seatId']})
                assert response['status'] == 'PENDING'

    def background(self, name, callback):
        def guarded():
            try:
                callback()
            except Exception as error:
                self.errors.append({'observer': name, 'error': type(error).__name__})
        thread = threading.Thread(target=guarded, daemon=True)
        thread.start()
        self.threads.append(thread)

    def start(self, cid):
        for pod, base, available in self.targets:
            def hikari(pod=pod, base=base, available=available):
                with open(self.directory / f'hikari-{pod}.jsonl', 'w') as output:
                    while not self.stop.is_set():
                        row = {'timestamp': time.time(), 'pod': pod, 'metrics': {}}
                        for metric in ['hikaricp.connections.active', 'hikaricp.connections.pending']:
                            if metric in available:
                                row['metrics'][metric] = request(base, '/actuator/metrics/' + metric,
                                                                {'Cookie': self.cookie})[0]['measurements']
                        output.write(json.dumps(row) + '\n')
                        output.flush()
                        self.stop.wait(0.25)
            self.background(pod, hikari)

        def queue():
            with open(self.directory / 'queue-depth.csv', 'w') as output:
                writer = csv.writer(output)
                writer.writerow(['timestamp', 'waiting', 'admitted'])
                while not self.stop.is_set():
                    values = redis('EVAL', 'return {redis.call("ZCARD",KEYS[1]),redis.call("ZCARD",KEYS[2])}',
                                   2, f'queue:waiting:{cid}', f'queue:admitted:{cid}').splitlines()
                    writer.writerow([time.time(), *map(int, values)])
                    output.flush()
                    self.stop.wait(0.1)
        self.background('queue', queue)

        def postgres():
            with open(self.directory / 'db-connections.csv', 'w') as output:
                writer = csv.writer(output)
                writer.writerow(['timestamp', 'connections', 'active', 'waiting'])
                while not self.stop.is_set():
                    row = db("SELECT count(*),count(*) FILTER(WHERE state='active'),count(*) FILTER(WHERE wait_event_type='Lock') FROM pg_stat_activity WHERE backend_type='client backend';").strip().split('|')
                    writer.writerow([time.time(), *map(int, row)])
                    output.flush()
                    self.stop.wait(0.5)
        self.background('postgres', postgres)

        def top():
            with open(self.directory / 'kubectl-top.txt', 'w') as output:
                while not self.stop.is_set():
                    output.write(str(time.time()) + '\n' + kubectl('top', 'pod'))
                    output.flush()
                    self.stop.wait(2)
        self.background('pod', top)

    def close(self):
        self.stop.set()
        for thread in self.threads:
            thread.join(timeout=20)
        for process in self.processes:
            process.terminate()
            process.wait(timeout=10)
        write_json(self.directory / 'observer-errors.json', self.errors)


def cloudwatch(start, end):
    # Capture the containing minute; mark this coarse window explicitly.
    query_start = int(start) // 60 * 60
    query_end = (int(end) // 60 + 1) * 60
    queries = []
    for index, name in enumerate(['DatabaseConnections', 'CPUUtilization', 'ReadIOPS',
                                  'WriteIOPS', 'FreeableMemory', 'CPUCreditBalance',
                                  'CPUSurplusCreditsCharged']):
        queries.append({'Id': 'rds' + str(index), 'MetricStat': {'Metric': {
            'Namespace': 'AWS/RDS', 'MetricName': name, 'Dimensions': [
                {'Name': 'DBInstanceIdentifier', 'Value': 'ticketing-postgres'}]},
            'Period': 60, 'Stat': 'Maximum' if name != 'FreeableMemory' else 'Minimum'}})
    for index, name in enumerate(['CPUCreditBalance', 'CPUSurplusCreditsCharged', 'CPUUtilization']):
        queries.append({'Id': 'node' + str(index), 'MetricStat': {'Metric': {
            'Namespace': 'AWS/EC2', 'MetricName': name, 'Dimensions': [
                {'Name': 'InstanceId', 'Value': 'i-0717ef29b3a07393c'}]},
            'Period': 300, 'Stat': 'Maximum'}})
    result = json.loads(command(['aws', 'cloudwatch', 'get-metric-data', '--metric-data-queries',
                                json.dumps(queries), '--start-time', str(query_start), '--end-time', str(query_end),
                                '--region', 'ap-northeast-2', '--output', 'json']))
    result['QueryWindow'] = {'measurementStart': start, 'measurementEnd': end,
                             'cloudwatchStart': query_start, 'cloudwatchEnd': query_end}
    return result


def run(mode, count, repetition):
    s = state()
    assert 1 <= count <= s['count'] <= 1000
    directory = RESULTS / f'queue-{mode.lower()}-{count}-run{repetition}'
    directory.mkdir(exist_ok=False)
    log(f'Preparing {directory.name}: API rollout and equal warm-up')
    fixture = json.loads((RESULTS / 'fixture.json').read_text())
    expected_sha = command(['git', 'rev-parse', 'HEAD']).strip()
    assert s['image'].endswith(':' + expected_sha), 'Image and source commit must match'
    env = {'name': 'QUEUE_ADMISSION_BYPASS_CONCERT_ID',
           'value': str(s['concertId'] if mode == 'OFF' else 0)}
    patch = {'spec': {'template': {'metadata': {'annotations': {'phase8-run': directory.name}},
                                  'spec': {'containers': [{'name': 'ticketing-api', 'env': [env]}]}}}}
    kubectl('patch', 'deployment', 'ticketing-api', '--type=strategic', '-p', json.dumps(patch))
    kubectl('rollout', 'status', 'deployment/ticketing-api', '--timeout=300s')
    deployments = json.loads(kubectl('get', 'deployment', '-o', 'json'))['items']
    for d in deployments:
        if d['metadata']['name'] in ['ticketing-api', 'ticketing-queue-worker']:
            assert d['spec']['template']['spec']['containers'][0]['image'] == s['image']
    host = kubectl('get', 'ingress', 'ticketing-api', '-o', 'jsonpath={.status.loadBalancer.ingress[0].hostname}').strip()
    base = 'http://' + host
    consecutive = 0
    for _ in range(60):
        try:
            request(base, '/actuator/health/readiness')
            request(base, '/api/csrf')
            consecutive += 1
            if consecutive >= 5:
                break
        except Exception:
            consecutive = 0
        time.sleep(1)
    else:
        raise RuntimeError('ALB did not become stable after rollout')
    cookie = login(base, fixture[0]['email'], s['password'])
    observer = Observers(directory, cookie)
    process = None
    try:
        observer.connect()
        log('Available Hikari metrics: ' + ', '.join(observer.targets[0][2]))
        reset_database(s)
        observer.warmup(s, fixture)
        log('Warm-up completed; resetting only the experiment concert')
        reset(s)
        observer.snapshot('hikari-before-auth.json')
        # All VUs authenticate first; the same absolute-time barrier releases the burst.
        barrier_seconds = max(12, int(count / 8) + 15)
        burst_at = int((time.time() + barrier_seconds) * 1000)
        started = time.time()
        metadata = {'start': started, 'burstAt': burst_at / 1000, 'gitCommit': expected_sha,
                    'image': s['image'], 'apiReplicas': 2, 'workerReplicas': 1,
                    'concertId': s['concertId'], 'seatCount': s['count'], 'users': count,
                    'mode': mode, 'repetition': repetition, 'pollInterval': 1,
                    'admissionBatch': 50, 'workerIntervalMs': 100, 'admissionTtlSeconds': 300,
                    'rdsClass': 'db.t4g.micro', 'valkeyClass': 'cache.t4g.micro',
                    'warmupReservations': 20, 'loginBarrierSeconds': barrier_seconds,
                    'protocolVersion': 2, 'authStartRatePerSecond': 8,
                    'fixtureSha256': hashlib.sha256((RESULTS / 'fixture.json').read_bytes()).hexdigest(),
                    'k6ScriptSha256': hashlib.sha256((ROOT / 'load-tests/k6/queue-aws-common.js').read_bytes()).hexdigest(),
                    'k6Machine': 'same local macOS host; 16 GiB memory',
                    'command': f'USERS={count} CONCERT_ID={s["concertId"]} k6 run load-tests/k6/queue-{mode.lower()}-aws.js (other inputs via environment)'}
        write_json(directory / 'metadata.json', metadata)
        (directory / 'metadata.md').write_text('\n'.join(f'- {k}: {v}' for k, v in metadata.items()) + '\n')
        runtime_env = {**os.environ, 'BASE_URL': base, 'TEST_PASSWORD': s['password'],
                       'CONCERT_ID': str(s['concertId']), 'USERS': str(count),
                       'BURST_AT_MS': str(burst_at), 'FIXTURE_FILE': str(RESULTS / 'fixture.json'),
                       'AUTH_START_RATE': '8',
                       'SUMMARY_FILE': str(directory / 'k6-summary.json'), 'K6_NO_USAGE_REPORT': 'true'}
        log(f'Starting {directory.name}; authentication preparation={barrier_seconds}s')
        with open(directory / 'k6-output.txt', 'w') as output:
            process = subprocess.Popen(['k6', 'run', '--quiet', str(ROOT / f'load-tests/k6/queue-{mode.lower()}-aws.js')],
                                       env=runtime_env, stdout=output, stderr=subprocess.STDOUT)
            while time.time() < burst_at/1000 - 2 and process.poll() is None:
                time.sleep(0.5)
            observer.snapshot('hikari-before.json')
            metadata['measurementStart'] = time.time()
            observer.start(s['concertId'])
            log(f'{directory.name}: burst released; telemetry active')
            code = process.wait(timeout=200)
        time.sleep(3)
        after = observer.snapshot('hikari-after.json')
        metadata.update({'end': time.time(), 'k6ExitCode': code})
        write_json(directory / 'metadata.json', metadata)
        (directory / 'metadata.md').write_text('\n'.join(f'- {k}: {v}' for k, v in metadata.items()) + '\n')
        # A fresh final query later fills CloudWatch publication lag without inventing data.
        try:
            rds_metrics = cloudwatch(metadata['measurementStart'], time.time() + 1)
        except RuntimeError:
            rds_metrics = {'collectionError': 'CloudWatch request failed; refresh after connectivity recovers',
                           'measurementStart': metadata['measurementStart'], 'end': metadata['end']}
        write_json(directory / 'rds-metrics.json', rds_metrics)
        summary = json.loads((directory / 'k6-summary.json').read_text())
        metrics = summary['metrics']
        result = {key: metrics.get(key, {}).get('values') for key in [
            'login_success', 'reservation_success', 'reservation_latency', 'queue_wait_time',
            'queue_status_poll_count', 'late_users', 'burst_lateness_ms']}
        write_json(directory / 'result.json', result)
        log(f'Finished {directory.name}: ' + json.dumps(result))
        assert metrics['login_success']['values']['rate'] == 1, 'Login failure invalidates comparison'
        assert metrics.get('auth_ready', {}).get('values', {}).get('count') == count, 'Incomplete authentication preparation'
        assert metrics['late_users']['values']['count'] == 0, 'Late authentication invalidates burst control'
        # Failures after the release (including queue join/status) are outcomes,
        # not grounds for removing a run from the comparison.
        entered = (metrics.get('queue_join_success', {}).get('values', {}) if mode == 'ON'
                   else metrics.get('reservation_success', {}).get('values', {}))
        assert entered.get('passes', 0) + entered.get('fails', 0) == count, 'Incomplete burst cohort'
        pods = json.loads(kubectl('get', 'pods', '-l', 'app=ticketing-api', '-o', 'json'))['items']
        write_json(directory / 'pod-health-after.json', [{'name': p['metadata']['name'],
                   'containers': [{'ready': c['ready'], 'restartCount': c['restartCount'],
                                   'lastTerminationReason': c.get('lastState', {}).get('terminated', {}).get('reason')}
                                  for c in p['status']['containerStatuses']]} for p in pods])
        assert all(c['restartCount'] == 0 and c['ready'] for p in pods for c in p['status']['containerStatuses']), 'API instability: stop increasing load'
    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        observer.close()
    assert not observer.errors, 'Incomplete core telemetry; rerun this load before increasing it'


def restore():
    s = state()
    kubectl('set', 'env', 'deployment/ticketing-api', 'QUEUE_ADMISSION_BYPASS_CONCERT_ID-')
    kubectl('patch', 'deployment', 'ticketing-api', '--type=merge', '-p',
            json.dumps({'spec': {'template': {'metadata': {'annotations': {'phase8-run': None}}}}}))
    kubectl('rollout', 'status', 'deployment/ticketing-api', '--timeout=300s')
    kubectl('scale', 'deployment/ticketing-queue-worker', '--replicas=1')
    kubectl('rollout', 'status', 'deployment/ticketing-queue-worker', '--timeout=180s')
    # Restore the admission gate before attempting any optional fixture cleanup.
    reset(s)
    log('Default admission gate restored; worker=1; isolated reservations and queue state cleared')


def audit():
    """Read current controls without exporting Secret values or database endpoints."""
    s = state()
    report = {'timestampUtc': dt.datetime.now(dt.timezone.utc).isoformat(), 'deployments': []}
    deployments = json.loads(kubectl('get', 'deployments', '-o', 'json'))['items']
    for deployment in deployments:
        if deployment['metadata']['name'] not in ['ticketing-api', 'ticketing-queue-worker']:
            continue
        c = deployment['spec']['template']['spec']['containers'][0]
        report['deployments'].append({'name': deployment['metadata']['name'],
            'replicas': deployment['spec']['replicas'], 'ready': deployment['status'].get('readyReplicas', 0),
            'image': c['image'], 'bypass': [e.get('value') for e in c['env']
                                         if e['name'] == 'QUEUE_ADMISSION_BYPASS_CONCERT_ID']})
    pods = json.loads(kubectl('get', 'pods', '-o', 'json'))['items']
    report['pods'] = [{'name': p['metadata']['name'], 'ready': c['ready'],
                       'restartCount': c['restartCount']}
                      for p in pods if p['metadata']['labels'].get('app') in ['ticketing-api', 'ticketing-queue-worker']
                      for c in p['status'].get('containerStatuses', [])]
    cid = int(s['concertId'])
    report['fixtureCounts'] = db(f"SELECT (SELECT count(*) FROM seat WHERE concert_id={cid}),"
        f"(SELECT count(*) FROM seat WHERE concert_id={cid} AND status='AVAILABLE'),"
        f"(SELECT count(*) FROM reservation r JOIN seat s ON s.id=r.seat_id WHERE s.concert_id={cid});").strip()
    report['queue'] = list(map(int, redis('EVAL',
        'return {redis.call("ZCARD",KEYS[1]),redis.call("ZCARD",KEYS[2]),redis.call("SISMEMBER",KEYS[3],ARGV[1])}',
        3, f'queue:waiting:{cid}', f'queue:admitted:{cid}', 'queue:active-concerts', cid).splitlines()))
    if report['queue'] == [0, 0, 0] and all(not d['bypass'] for d in report['deployments']):
        host = kubectl('get', 'ingress', 'ticketing-api', '-o', 'jsonpath={.status.loadBalancer.ingress[0].hostname}').strip()
        base = 'http://' + host
        fixture = json.loads((RESULTS / 'fixture.json').read_text())
        cookie = login(base, fixture[0]['email'], s['password'])
        _, response_headers = request(base, '/api/csrf', {'Cookie': cookie})
        cookies = SimpleCookie()
        for header in response_headers.get_all('Set-Cookie', []):
            cookies.load(header)
        token = cookies['XSRF-TOKEN'].value
        try:
            request(base, '/api/reservations', {'Cookie': cookie + '; XSRF-TOKEN=' + token,
                    'Content-Type': 'application/json', 'X-XSRF-TOKEN': token},
                    {'concertId': cid, 'seatId': fixture[0]['seatId']})
            raise AssertionError('Default admission gate unexpectedly accepted a reservation')
        except urllib.error.HTTPError as error:
            report['gateProbe'] = {'httpStatus': error.code, 'code': json.load(error).get('code')}
            assert report['gateProbe'] == {'httpStatus': 403, 'code': 'ADMISSION_REQUIRED'}
    report['kubectlTop'] = kubectl('top', 'pod')
    now = time.time()
    write_json(RESULTS / 'latest-credit-checkpoint.json', cloudwatch(now-1800, now))
    write_json(RESULTS / 'runtime-audit.json', report)
    log('Runtime audit: ' + json.dumps(report))


def cleanup_tools():
    deployment = json.loads(kubectl('get', 'deployment', 'ticketing-api', '-o', 'json'))
    assert not any(e['name'] == 'QUEUE_ADMISSION_BYPASS_CONCERT_ID'
                   for e in deployment['spec']['template']['spec']['containers'][0]['env'])
    pod = json.loads(kubectl('get', 'pod', POD, '-o', 'json'))
    assert pod['metadata']['labels'].get('app') == 'ticketing-phase8-tools'
    kubectl('delete', 'pod', POD, '--wait=true', '--timeout=90s')
    write_json(RESULTS / 'cleanup.json', {'timestampUtc': dt.datetime.now(dt.timezone.utc).isoformat(),
               'deletedPod': POD, 'existingInfrastructureRetained': True})
    log('Removed only the temporary Phase 8 client Pod; existing infrastructure retained')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['prepare', 'run', 'pair', 'series', 'remaining', 'restore', 'audit', 'cleanup-tools', 'refresh-cloudwatch'])
    parser.add_argument('--users', type=int, default=1000)
    parser.add_argument('--mode', choices=['ON', 'OFF'])
    parser.add_argument('--repetition', type=int, default=1)
    args = parser.parse_args()
    if args.action == 'run' and args.mode is None:
        parser.error('run requires --mode ON or OFF')
    if args.action == 'prepare':
        prepare(args.users)
    elif args.action == 'run':
        run(args.mode, args.users, args.repetition)
    elif args.action == 'pair':
        modes = ['OFF', 'ON'] if args.repetition % 2 else ['ON', 'OFF']
        for mode in modes:
            run(mode, args.users, args.repetition)
    elif args.action in ['series', 'remaining']:
        try:
            plan = ([(500, 3), (500, 4), (500, 5), (1000, 1)] if args.action == 'remaining'
                    else [(50, 3), (100, 2), (500, 2), (500, 3), (500, 4), (1000, 1)])
            for users, repetition in plan:
                log(f'Protocol v2 tier: users={users}, repetition={repetition}')
                for mode in (['OFF', 'ON'] if repetition % 2 else ['ON', 'OFF']):
                    directory = RESULTS / f'queue-{mode.lower()}-{users}-run{repetition}'
                    if args.action == 'remaining' and (directory / 'result.json').exists():
                        meta = json.loads((directory / 'metadata.json').read_text())
                        assert 'end' in meta and meta.get('protocolVersion') == 2
                        assert not json.loads((directory / 'observer-errors.json').read_text())
                        log(f'Preserving completed measurement: {directory.name}')
                        continue
                    run(mode, users, repetition)
        finally:
            restore()
    elif args.action == 'restore':
        restore()
    elif args.action == 'audit':
        audit()
    elif args.action == 'cleanup-tools':
        cleanup_tools()
    else:
        for file in sorted(RESULTS.glob('queue-*/metadata.json')):
            metadata = json.loads(file.read_text())
            if 'end' not in metadata:
                log(f'Skipping incomplete measurement: {file.parent.name}')
                continue
            write_json(file.parent / 'rds-metrics.json', cloudwatch(metadata.get('measurementStart', metadata['start']), metadata['end']))
        log('CloudWatch measurements refreshed')

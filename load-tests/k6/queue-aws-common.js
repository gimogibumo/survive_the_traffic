import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const users = new SharedArray('phase8-fixture', () => JSON.parse(open(__ENV.FIXTURE_FILE)));
const count = Number(__ENV.USERS || 1);
const base = __ENV.BASE_URL;
const password = __ENV.TEST_PASSWORD;
const concertId = Number(__ENV.CONCERT_ID);
const burstAt = Number(__ENV.BURST_AT_MS);
const poll = Number(__ENV.POLL_INTERVAL || 1);
const authRate = Number(__ENV.AUTH_START_RATE || 8);
const loginSuccess = new Rate('login_success');
const joinSuccess = new Rate('queue_join_success');
const admissionSuccess = new Rate('queue_admitted_success');
const reservationSuccess = new Rate('reservation_success');
const reservationLatency = new Trend('reservation_latency', true);
const queueWait = new Trend('queue_wait_time', true);
const polls = new Counter('queue_status_poll_count');
const burstLateness = new Trend('burst_lateness_ms', true);
const lateUsers = new Counter('late_users');
const endToEnd = new Trend('reservation_end_to_end', true);
const reserveStatus = new Counter('reservation_status');
const authReady = new Counter('auth_ready');

export const options = {
  scenarios: { burst: { executor: 'per-vu-iterations', vus: count, iterations: 1,
    maxDuration: '8m', gracefulStop: '10s' } },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    login_success: ['rate==1'], reservation_success: ['rate==1'],
    late_users: ['count==0'],
    'http_req_duration{endpoint:login}': ['max>=0'],
    'http_req_duration{endpoint:reservation}': ['max>=0'],
    'http_req_duration{endpoint:queue_join}': ['max>=0'],
    'http_req_duration{endpoint:queue_status}': ['max>=0'],
    'http_reqs{endpoint:login}': ['count>=0'],
    'http_reqs{endpoint:queue_join}': ['count>=0'],
    'http_reqs{endpoint:queue_status}': ['count>=0'],
    'http_reqs{endpoint:reservation}': ['count>=0'],
    'http_req_failed{endpoint:reservation}': ['rate>=0'],
  },
};

function csrf(phase) {
  const response = http.get(`${base}/api/csrf`, {
    tags: { endpoint: 'csrf', phase }, timeout: '180s',
  });
  const cookies = response.cookies['XSRF-TOKEN'];
  if (response.status !== 200 || !cookies || !cookies.length) fail(`CSRF issuance failed: HTTP ${response.status}`);
  return cookies[0].value;
}

export function run(mode) {
  if (!base || !password || !concertId || !burstAt || users.length < count) {
    fail('BASE_URL, TEST_PASSWORD, CONCERT_ID, BURST_AT_MS and sufficient FIXTURE_FILE are required');
  }
  const user = users[exec.vu.idInTest - 1];
  authReady.add(0);
  // Preparation is paced; the measured reservation/queue burst remains simultaneous.
  sleep((exec.vu.idInTest - 1) / authRate);
  let token = csrf('prepare');
  const login = http.post(`${base}/api/auth/login`, JSON.stringify({ email: user.email, password }), {
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
    tags: { endpoint: 'login', phase: 'prepare' }, timeout: '180s',
  });
  loginSuccess.add(login.status === 200);
  if (login.status !== 200) fail(`Login HTTP ${login.status}`);
  token = csrf('prepare');
  authReady.add(1);
  lateUsers.add(0);
  const remaining = burstAt - Date.now();
  if (remaining > 0) sleep(remaining / 1000);
  const late = Math.max(0, Date.now() - burstAt);
  burstLateness.add(late);
  if (late > 1000) lateUsers.add(1);
  const entered = Date.now();
  const headers = { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token };

  if (mode === 'ON') {
    const joined = http.post(`${base}/api/queues/${concertId}/join`, null, {
      headers, tags: { endpoint: 'queue_join', phase: 'measure' }, timeout: '30s',
    });
    joinSuccess.add(joined.status === 200);
    if (joined.status !== 200) fail(`Queue join HTTP ${joined.status}`);
    let admitted = false;
    while (Date.now() - entered < 90000) {
      polls.add(1);
      const response = http.get(`${base}/api/queues/${concertId}/status`, {
        tags: { endpoint: 'queue_status', phase: 'measure' }, timeout: '30s',
      });
      if (response.status !== 200) fail(`Queue status HTTP ${response.status}`);
      const status = response.json('status');
      if (status === 'ADMITTED') { admitted = true; break; }
      if (status === 'NOT_JOINED') fail('Joined user became NOT_JOINED');
      sleep(poll);
    }
    admissionSuccess.add(admitted);
    if (!admitted) fail('Admission timeout');
    queueWait.add(Date.now() - entered);
  }
  const response = http.post(`${base}/api/reservations`, JSON.stringify({
    concertId, seatId: user.seatId,
  }), { headers, tags: { endpoint: 'reservation', phase: 'measure' }, timeout: '30s' });
  reservationLatency.add(response.timings.duration);
  reservationSuccess.add(response.status === 200);
  reserveStatus.add(1, { status: String(response.status) });
  endToEnd.add(Date.now() - entered);
  check(response, { 'reservation created': (r) => r.status === 200 });
}

export function handleSummary(data) {
  // No setup data, cookies, request bodies or credentials are exported.
  return { [__ENV.SUMMARY_FILE]: JSON.stringify(data, null, 2) };
}

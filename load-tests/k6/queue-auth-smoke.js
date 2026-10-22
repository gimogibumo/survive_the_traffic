import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL;
const CONCERT_ID = __ENV.CONCERT_ID || '1';
const TEST_EMAIL = __ENV.TEST_EMAIL;
const TEST_PASSWORD = __ENV.TEST_PASSWORD;
const admissions = new Counter('admissions');

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1'],
    admissions: ['count==1'],
  },
};

function issueCsrf() {
  const response = http.get(`${BASE_URL}/api/csrf`);
  const cookie = response.cookies['XSRF-TOKEN'];
  const token = cookie && cookie.length ? cookie[0].value : null;
  if (!check(response, {
    'csrf status 200': (r) => r.status === 200,
    'csrf token exists': () => token !== null,
  })) {
    fail('CSRF issuance failed');
  }
  return token;
}

export default function () {
  admissions.add(0);
  if (!BASE_URL || !TEST_EMAIL || !TEST_PASSWORD) {
    fail('BASE_URL, TEST_EMAIL and TEST_PASSWORD are required');
  }
  let token = issueCsrf();
  const login = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
    email: TEST_EMAIL,
    password: TEST_PASSWORD,
  }), {
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
  });
  if (!check(login, { 'login status 200': (r) => r.status === 200 })) {
    fail(`Login failed: HTTP ${login.status}`);
  }

  // Login rotates the session and clears the previous CSRF token.
  token = issueCsrf();
  const joined = http.post(`${BASE_URL}/api/queues/${CONCERT_ID}/join`, null, {
    headers: { 'X-XSRF-TOKEN': token },
  });
  if (!check(joined, { 'queue join status 200': (r) => r.status === 200 })) {
    fail(`Queue join failed: HTTP ${joined.status}`);
  }

  let admitted = false;
  for (let attempt = 0; attempt < 30; attempt++) {
    const response = http.get(`${BASE_URL}/api/queues/${CONCERT_ID}/status`);
    if (!check(response, { 'queue status 200': (r) => r.status === 200 })) {
      fail(`Queue status failed: HTTP ${response.status}`);
    }
    const status = response.json('status');
    console.log(`Queue state: ${status}`);
    if (status === 'ADMITTED') {
      admitted = true;
      admissions.add(1);
      break;
    }
    sleep(1);
  }
  check(admitted, { 'worker admitted the user': (value) => value });
  if (!admitted) {
    fail('Admission timed out');
  }

  // CSRF protection and logout must still work with the Redis session cookie.
  const logout = http.post(`${BASE_URL}/api/auth/logout`, null, {
    headers: { 'X-XSRF-TOKEN': token },
  });
  check(logout, { 'logout status 204': (r) => r.status === 204 });
}

import http from 'k6/http';
import { check, fail } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const vus = Number(__ENV.VUS || 50);
const duration = __ENV.DURATION || '30s';
const reservationCount = Number(__ENV.RESERVATION_COUNT || 100);

export const options = {
  vus,
  duration,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    checks: ['rate==1'],
    'http_req_duration{phase:load}': ['max>=0'],
    'http_reqs{phase:load}': ['count>0'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  if (__ENV.USER_ID) return { userId: Number(__ENV.USER_ID) };
  const response = http.post(
    `${baseUrl}/api/performance/seed/user-reservations?reservationCount=${reservationCount}`
  );

  check(response, {
    'seed user reservation data': (res) => res.status === 200,
  });

  if (response.status !== 200) fail('Reservation history seed failed');
  return response.json();
}

export default function (data) {
  const prefix = __ENV.QUERY_MODE === 'lazy' ? '/api/performance/baseline' : '/api';
  const response = http.get(
    `${baseUrl}${prefix}/users/${data.userId}/reservations`,
    { tags: { phase: 'load' } }
  );

  check(response, {
    'user reservations status is 200': (res) => res.status === 200,
  });
}

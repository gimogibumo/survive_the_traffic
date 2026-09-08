import http from 'k6/http';
import { check, fail } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const vus = Number(__ENV.VUS || 10);
const duration = __ENV.DURATION || '30s';
const seatCount = Number(__ENV.SEAT_COUNT || 10000);

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
  const response = http.post(
    `${baseUrl}/api/performance/seed/seat-read?seatCount=${seatCount}`
  );

  check(response, {
    'seed seat read data': (res) => res.status === 200,
  });

  if (response.status !== 200) fail('Seat seed failed');
  return response.json();
}

export default function (data) {
  const response = http.get(
    `${baseUrl}/api/concerts/${data.concertId}/seats`,
    { tags: { phase: 'load' } }
  );

  check(response, {
    'seat get status is 200': (res) => res.status === 200,
  });
}

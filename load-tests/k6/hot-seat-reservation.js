import http from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const created = new Counter('reservations_created');
const conflicts = new Counter('reservation_conflicts');

http.setResponseCallback(http.expectedStatuses(200, 201, 409));

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const vus = Number(__ENV.VUS || 100);
const iterations = Number(__ENV.ITERATIONS || vus);
const userCount = Number(__ENV.USER_COUNT || iterations);

export const options = {
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    reservations_created: ['count==1'],
    reservation_conflicts: [`count==${iterations - 1}`],
    'http_req_duration{phase:load}': ['max>=0'],
    'http_reqs{phase:load}': [`count==${iterations}`],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    hot_seat_reservation: {
      executor: 'shared-iterations',
      vus,
      iterations,
      maxDuration: __ENV.MAX_DURATION || '30s',
    },
  },
};

export function setup() {
  if (userCount < iterations) fail('USER_COUNT must be >= ITERATIONS');
  const response = http.post(
    `${baseUrl}/api/performance/seed/hot-seat?userCount=${userCount}`
  );

  check(response, {
    'seed hot seat data': (res) => res.status === 200,
  });

  if (response.status !== 200) fail('Hot seat seed failed');
  return response.json();
}

export default function (data) {
  const index = exec.scenario.iterationInTest % data.userIds.length;
  const userId = data.userIds[index];
  const seatId = data.seatIds[0];

  const response = http.post(
    `${baseUrl}/api/reservations`,
    JSON.stringify({ userId, seatId }),
    { headers: { 'Content-Type': 'application/json' }, tags: { phase: 'load' } }
  );

  created.add(response.status === 201 ? 1 : 0);
  conflicts.add(response.status === 409 ? 1 : 0);

  check(response, {
    'hot seat status is 201 or 409': (res) =>
      res.status === 201 || res.status === 409,
  });
}

import http from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const vus = Number(__ENV.VUS || 100);
const iterations = Number(__ENV.ITERATIONS || vus);
const count = Number(__ENV.COUNT || iterations);

export const options = {
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    'http_req_duration{phase:load}': ['max>=0'],
    'http_reqs{phase:load}': [`count==${iterations}`],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    distributed_seat_reservation: {
      executor: 'shared-iterations',
      vus,
      iterations,
      maxDuration: __ENV.MAX_DURATION || '30s',
    },
  },
};

export function setup() {
  if (count < iterations) fail('COUNT must be >= ITERATIONS');
  const response = http.post(
    `${baseUrl}/api/performance/seed/distributed-seats?count=${count}`
  );

  check(response, {
    'seed distributed seat data': (res) => res.status === 200,
  });

  if (response.status !== 200) fail('Distributed seat seed failed');
  return response.json();
}

export default function (data) {
  const index = exec.scenario.iterationInTest % data.userIds.length;
  const userId = data.userIds[index];
  const seatId = data.seatIds[index];

  const response = http.post(
    `${baseUrl}/api/reservations`,
    JSON.stringify({ userId, seatId }),
    { headers: { 'Content-Type': 'application/json' }, tags: { phase: 'load' } }
  );

  check(response, {
    'distributed seat status is 201': (res) => res.status === 201,
  });
}

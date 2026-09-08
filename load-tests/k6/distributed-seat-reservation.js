import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const vus = Number(__ENV.VUS || 100);
const iterations = Number(__ENV.ITERATIONS || vus);
const count = Number(__ENV.COUNT || iterations);

export const options = {
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
  const response = http.post(
    `${baseUrl}/api/performance/seed/distributed-seats?count=${count}`
  );

  check(response, {
    'seed distributed seat data': (res) => res.status === 200,
  });

  return response.json();
}

export default function (data) {
  const index = __ITER % data.userIds.length;
  const userId = data.userIds[index];
  const seatId = data.seatIds[index];

  const response = http.post(
    `${baseUrl}/api/reservations`,
    JSON.stringify({ userId, seatId }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(response, {
    'distributed seat status is 201': (res) => res.status === 201,
  });
}

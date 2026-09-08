import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const vus = Number(__ENV.VUS || 100);
const iterations = Number(__ENV.ITERATIONS || vus);
const userCount = Number(__ENV.USER_COUNT || iterations);

export const options = {
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
  const response = http.post(
    `${baseUrl}/api/performance/seed/hot-seat?userCount=${userCount}`
  );

  check(response, {
    'seed hot seat data': (res) => res.status === 200,
  });

  return response.json();
}

export default function (data) {
  const userId = data.userIds[__ITER % data.userIds.length];
  const seatId = data.seatIds[0];

  const response = http.post(
    `${baseUrl}/api/reservations`,
    JSON.stringify({ userId, seatId }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(response, {
    'hot seat status is 201 or 409': (res) =>
      res.status === 201 || res.status === 409,
  });
}

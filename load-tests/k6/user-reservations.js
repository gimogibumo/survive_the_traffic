import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const vus = Number(__ENV.VUS || 50);
const duration = __ENV.DURATION || '30s';
const reservationCount = Number(__ENV.RESERVATION_COUNT || 100);

export const options = {
  vus,
  duration,
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  const response = http.post(
    `${baseUrl}/api/performance/seed/user-reservations?reservationCount=${reservationCount}`
  );

  check(response, {
    'seed user reservation data': (res) => res.status === 200,
  });

  return response.json();
}

export default function (data) {
  const response = http.get(
    `${baseUrl}/api/users/${data.userId}/reservations`
  );

  check(response, {
    'user reservations status is 200': (res) => res.status === 200,
  });
}

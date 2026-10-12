import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    penetration: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 1,
      maxDuration: '30s',
    },
  },
};

export default function () {

  const response = http.get(
      'http://localhost:8080/api/concerts/999999'
  );

  check(response, {
    'status is 404': (r) => r.status === 404,
  });
}
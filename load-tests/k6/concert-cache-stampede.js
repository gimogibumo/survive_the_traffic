import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    stampede: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 1,
      maxDuration: '10s',
    },
  },
};

export default function () {

  const response = http.get(
      'http://localhost:8080/api/concerts/1'
  );

  check(response, {
    'status is 200': (r) => r.status === 200,
  });
}
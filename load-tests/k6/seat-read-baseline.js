import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 50,
  duration: '30s',
};

export default function () {

  const response = http.get(
      'http://localhost:8080/api/concerts/1/seats'
  );

  check(response, {
    'status is 200': (r) => r.status === 200,
  });
}
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1,
  duration: '5s',
};

export default function () {

  const response = http.get(
      'http://localhost:8080/api/concerts/1/seats'
  );

  check(response, {
    'status is 200': (r) => r.status === 200,
  });

  sleep(1);
}
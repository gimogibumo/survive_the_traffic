import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 50,
  duration: '30s',

  summaryTrendStats: [
    'avg',
    'min',
    'med',
    'p(90)',
    'p(95)',
    'p(99)',
    'max',
  ],
};

export default function () {

  const response = http.get(
      'http://localhost:8080/api/concerts/1'
  );

  check(response, {
    'status is 200': (r) => r.status === 200,
  });
}
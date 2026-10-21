import http from 'k6/http';
import { check } from 'k6';

const BASE_URL =
    __ENV.BASE_URL ||
    'http://localhost:8080';

const RATE =
    Number(__ENV.RATE || 800);

const DURATION =
    __ENV.DURATION || '10s';

export const options = {
  scenarios: {
    hikari_baseline: {
      executor: 'constant-arrival-rate',

      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,

      preAllocatedVUs: 100,
      maxVUs: 300,

      gracefulStop: '5s',
    },
  },

  discardResponseBodies: true,

  summaryTrendStats: [
    'avg',
    'min',
    'med',
    'p(90)',
    'p(95)',
    'p(99)',
    'max',
  ],

  thresholds: {
    http_req_failed: [
      'rate<0.01',
    ],
    checks: [
      'rate>0.99',
    ],
  },
};

export default function () {

  const response =
      http.get(
          `${BASE_URL}/api/performance/hikari-probe`,
          {
            tags: {
              endpoint:
                  'hikari-probe',
            },
          }
      );

  check(
      response,
      {
        'hikari probe returned 200':
            (r) =>
                r.status === 200,
      }
  );
}
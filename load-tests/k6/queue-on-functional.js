import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Trend } from 'k6/metrics';

const BASE_URL =
    __ENV.BASE_URL || 'http://localhost:8080';

const CONCERT_ID =
    Number(__ENV.CONCERT_ID || 900001);

const POLL_INTERVAL_SECONDS = 1.0;
const MAX_WAIT_MS = 30000;

const queueWaitTime =
    new Trend('queue_wait_time', true);

const users = new SharedArray(
    'queue-on-users',
    () => JSON.parse(open('./queue-on-users.json'))
);

export const options = {
  scenarios: {
    queue_on: {
      executor: 'constant-arrival-rate',

      rate: 800,
      timeUnit: '1s',
      duration: '10s',

      preAllocatedVUs: 4000,
      maxVUs: 4500,

      gracefulStop: '30s',
    },
  },

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
    'http_req_duration{endpoint:queue_join}': [
      'max>=0'
    ],

    'http_req_duration{endpoint:queue_status}': [
      'max>=0'
    ],

    'http_req_duration{endpoint:reservation}': [
      'max>=0'
    ],

    queue_wait_time: [
      'max>=0'
    ],
  },
};

export default function () {

  const iterationIndex =
      exec.scenario.iterationInTest;

  const data = users[iterationIndex];

  if (data === undefined) {
    throw new Error(
        `No test data for iteration ${iterationIndex}`
    );
  }

  const userId = data.userId;
  const seatId = data.seatId;

  // ------------------------------------------------
  // 1. Waiting Queue Join
  // ------------------------------------------------

  const joinResponse = http.post(
      `${BASE_URL}/api/queues/${CONCERT_ID}/join`,
      JSON.stringify({
        userId: userId,
      }),
      {
        headers: {
          'Content-Type': 'application/json',
        },

        tags: {
          endpoint: 'queue_join',
        },
      }
  );

  const joined = check(
      joinResponse,
      {
        'queue join status 200': (r) =>
            r.status === 200,
      }
  );

  if (!joined) {
    return;
  }

  // 실제 queue wait는 join이 성공한 시점부터 측정.
  const joinedAt = Date.now();

  // ------------------------------------------------
  // 2. WAITING → ADMITTED
  // ------------------------------------------------

  let admitted = false;

  while (
      Date.now() - joinedAt < MAX_WAIT_MS
      ) {

    const statusResponse = http.get(
        `${BASE_URL}/api/queues/${CONCERT_ID}/status?userId=${userId}`,
        {
          tags: {
            endpoint: 'queue_status',
          },
        }
    );

    const statusOk = check(
        statusResponse,
        {
          'queue status 200': (r) =>
              r.status === 200,
        }
    );

    if (!statusOk) {
      sleep(POLL_INTERVAL_SECONDS);
      continue;
    }

    const body = statusResponse.json();

    if (body.status === 'ADMITTED') {

      admitted = true;

      queueWaitTime.add(
          Date.now() - joinedAt
      );

      break;
    }

    if (body.status === 'NOT_JOINED') {

      check(
          body,
          {
            'joined user must not become NOT_JOINED':
                () => false,
          }
      );

      return;
    }

    sleep(POLL_INTERVAL_SECONDS);
  }

  if (!admitted) {

    check(
        admitted,
        {
          'user admitted within timeout':
              (value) => value === true,
        }
    );

    return;
  }

  // ------------------------------------------------
  // 3. Reservation
  // ------------------------------------------------

  const reservationResponse = http.post(
      `${BASE_URL}/api/reservations`,
      JSON.stringify({
        concertId: CONCERT_ID,
        userId: userId,
        seatId: seatId,
      }),
      {
        headers: {
          'Content-Type': 'application/json',
        },

        tags: {
          endpoint: 'reservation',
        },
      }
  );

  check(
      reservationResponse,
      {
        'reservation status 200': (r) =>
            r.status === 200,
      }
  );
}
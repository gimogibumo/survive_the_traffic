import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

const RATE = 1000;
const DURATION_SECONDS = 10;
const EXPECTED_REQUESTS = RATE * DURATION_SECONDS;

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 반드시 실제 존재하는 User ID를 실행할 때 넘긴다.
const USER_ID = Number(__ENV.USER_ID);

if (!USER_ID) {
  throw new Error(
      'USER_ID is required. Example: USER_ID=3 k6 run ./load-tests/k6/reservation-queue-off.js'
  );
}

const seatIds = new SharedArray('available seat ids', function () {
  return JSON.parse(
      open('./available-seat-ids.json')
  );
});

// 테스트 시작 전에 데이터 수 부족을 바로 차단
if (seatIds.length < EXPECTED_REQUESTS) {
  throw new Error(
      `Not enough seat IDs. ` +
      `required=${EXPECTED_REQUESTS}, actual=${seatIds.length}`
  );
}

export const options = {
  scenarios: {
    queue_off_sustained: {
      executor: 'constant-arrival-rate',

      // 초당 50개의 iteration 시작
      rate: RATE,
      timeUnit: '1s',

      // 10초 동안 유지
      duration: `${DURATION_SECONDS}s`,

      // 미리 확보할 VU
      preAllocatedVUs: 300,

      // 서버가 느려질 경우 추가 VU 허용
      maxVUs: 300,

      gracefulStop: '5s',
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
};

export default function () {

  /*
   * scenario 전체에서 고유한 0-based iteration 번호.
   *
   * iteration 0   -> seatIds[0]
   * iteration 1   -> seatIds[1]
   * ...
   * iteration 499 -> seatIds[499]
   */
  const iterationIndex =
      exec.scenario.iterationInTest;

  const seatId =
      seatIds[iterationIndex];

  // 예상보다 iteration이 더 실행되는 상황 방어
  if (seatId === undefined) {
    throw new Error(
        `No seatId for iteration=${iterationIndex}`
    );
  }

  const payload = JSON.stringify({
    userId: 10,
    seatId: seatId,
  });

  const response = http.post(
      `${BASE_URL}/api/reservations`,
      payload,
      {
        headers: {
          'Content-Type': 'application/json',
        },

        tags: {
          endpoint: 'create-reservation',
        },
      }
  );

  const success =
      response.status === 200 ||
      response.status === 201;

  if (!success) {
    console.log(
        `FAILED ` +
        `iteration=${iterationIndex}, ` +
        `seatId=${seatId}, ` +
        `status=${response.status}, ` +
        `body=${response.body}`
    );
  }

  check(response, {
    'reservation created': (r) =>
        r.status === 200 ||
        r.status === 201,
  });
}
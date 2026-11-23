// 선착순 쿠폰 발급 부하 테스트. 사용자 한 명의 클릭 = HTTP 요청 하나
//   k6 run -e BASE=http://localhost:8080 -e MODE=CONDITIONAL loadtest/coupon-issue.js
// 초당 요청 수를 단계적으로 올리면서(arrival-rate) 응답 시간과 실패율, 초과 발급 여부를 본다
import http from 'k6/http';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const MODE = __ENV.MODE || 'CONDITIONAL';
const QUANTITY = Number(__ENV.QUANTITY || 5000);
const USERS = Number(__ENV.USERS || 20000);
const PEAK = Number(__ENV.PEAK_RPS || 400);

const issued = new Counter('coupon_issued');
const soldOut = new Counter('coupon_sold_out');
const duplicate = new Counter('coupon_duplicate');

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 2000,
      stages: [
        { target: PEAK / 4, duration: '20s' },
        { target: PEAK, duration: '20s' },
        { target: PEAK, duration: '20s' },
      ],
    },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const res = http.post(`${BASE}/api/coupons?quantity=${QUANTITY}`);
  return { couponId: res.json('couponId') };
}

export default function ({ couponId }) {
  const userId = Math.floor(Math.random() * USERS);
  const res = http.post(`${BASE}/api/coupons/${couponId}/issue?userId=${userId}&mode=${MODE}`, null, { timeout: '10s' });
  if (check(res, { 'status 200': (r) => r.status === 200 })) {
    const status = res.json('status');
    if (status === 'ISSUED') issued.add(1);
    else if (status === 'SOLD_OUT') soldOut.add(1);
    else duplicate.add(1);
  }
}

// 부하가 끝난 뒤 DB 기준으로 수량을 넘겼는지 확인한다
export function teardown({ couponId }) {
  const stats = http.get(`${BASE}/api/coupons/${couponId}`).json();
  console.log(`[${MODE}] DB issued=${stats.issued} remaining=${stats.remaining} quantity=${QUANTITY}`);
  check(stats, { 'no over-issue': (s) => s.issued <= QUANTITY && s.issued + s.remaining === QUANTITY });
}

export function handleSummary(data) {
  return { stdout: textSummary(data, { enableColors: false }), [`results/coupon-${MODE}.json`]: JSON.stringify(data, null, 2) };
}

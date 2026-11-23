// 대출 조건 병렬 조회 부하 테스트. 요청 하나가 서버 안에서 모의 금융사 12곳으로 HTTP 호출을 퍼뜨린다
//   k6 run -e BASE=http://localhost:8080 loadtest/quote.js
// 응답 시간과 함께, 부하가 늘수록 기관 호출이 타임아웃으로 떨어지는 비율(quote_timeout_ratio)을 본다
import http from 'k6/http';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const PEAK = Number(__ENV.PEAK_RPS || 60);
const TAG = __ENV.TAG || 'default';

const timeoutRatio = new Rate('quote_timeout_ratio');
const offerRatio = new Rate('quote_offer_ratio');

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
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

export default function () {
  const scenario = 1 + Math.floor(Math.random() * 1000);
  const res = http.get(`${BASE}/api/quotes?strategy=PARALLEL&timeoutMs=2000&retries=1&scenario=${scenario}`, { timeout: '15s' });
  if (check(res, { 'status 200': (r) => r.status === 200 })) {
    // 같은 시나리오면 원래 결과가 같으니, 기관별 결과에서 TIMEOUT이 늘면 서버 쪽 병목 때문이다
    for (const r of res.json('results')) {
      timeoutRatio.add(r.outcome === 'TIMEOUT');
      offerRatio.add(r.outcome === 'OK');
    }
  }
}

export function handleSummary(data) {
  return { stdout: textSummary(data, { enableColors: false }), [`results/quote-${TAG}.json`]: JSON.stringify(data, null, 2) };
}

// k6 부하 테스트. 쿠폰 발급 실행 API를 여러 사용자가 동시에 누르는 상황
//   k6 run -e BASE=http://localhost:8080 -e MODE=CONDITIONAL loadtest/coupon.js
// 결과의 http_req_duration p95와 서버 /actuator/prometheus 지표를 같이 본다
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8080';
const MODE = __ENV.MODE || 'CONDITIONAL';

export const options = {
  scenarios: {
    burst: { executor: 'constant-vus', vus: 10, duration: '30s' },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.post(`${BASE}/api/coupons/run?mode=${MODE}&requests=200&users=180&quantity=50&gapMs=5`, null, { timeout: '60s' });
  check(res, {
    'status 200': (r) => r.status === 200,
    'no over-issue': (r) => r.status === 200 && r.json('overIssued') === 0,
  });
}

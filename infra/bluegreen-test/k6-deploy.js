// Blue-Green 전환 중 nginx를 통해 계속 요청을 보내고, 실패 요청을 타임스탬프와 함께 기록한다.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = 'http://nginx';
const PATH = __ENV.PERF_PATH || '/api/news/it';

export const options = {
  scenarios: { load: { executor: 'constant-vus', vus: Number(__ENV.VUS || 10), duration: __ENV.DURATION || '20m' } },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const body = JSON.stringify({ email: 'bgtest@example.com', password: 'bgtest1234', name: 'bgtest' });
  const params = { headers: { 'Content-Type': 'application/json' } };
  http.post(`${BASE}/api/users/signup`, body, params); // 이미 있으면 실패해도 무시
  const res = http.post(`${BASE}/api/users/login`, body, params);
  if (res.status !== 200) throw new Error(`login failed: ${res.status} ${res.body}`);
  return { token: res.json('token') };
}

export default function (data) {
  const r = http.get(`${BASE}${PATH}`, { headers: { Authorization: `Bearer ${data.token}` }, timeout: '10s' });
  if (!check(r, { 'HTTP 200': (x) => x.status === 200 })) {
    console.error(`FAIL ${Date.now()} ${r.status}`);
  }
  sleep(0.1);
}

export function handleSummary(d) {
  const m = d.metrics;
  const out = {
    total: m.http_reqs.values.count,
    failed: m.http_req_failed.values.passes,
    p95_ms: m.http_req_duration.values['p(95)'],
    p99_ms: m.http_req_duration.values['p(99)'],
    max_ms: m.http_req_duration.values.max,
  };
  return { stdout: 'K6_SUMMARY ' + JSON.stringify(out) + '\n' };
}

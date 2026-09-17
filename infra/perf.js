import http from 'k6/http';
import { check } from 'k6';

const path = __ENV.PERF_PATH || '/api/news/it';

export default function () {
  const headers = __ENV.PERF_TOKEN ? { Authorization: `Bearer ${__ENV.PERF_TOKEN}` } : {};
  const response = http.get(`http://localhost:18080${path}`, { headers });
  check(response, { 'HTTP 200': r => r.status === 200 });
}

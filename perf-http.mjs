import { performance } from 'node:perf_hooks';

const base = process.env.PERF_BASE ?? 'http://localhost:18080';
const path = process.env.PERF_PATH ?? '/api/news/it';
const vus = (process.env.PERF_VUS ?? '50,100,200').split(',').map(Number);
const iterations = Number(process.env.PERF_ITERATIONS ?? 20);

async function request() {
  const start = performance.now();
  try {
    const response = await fetch(base + path);
    await response.arrayBuffer();
    return { ms: performance.now() - start, ok: response.ok };
  } catch {
    return { ms: performance.now() - start, ok: false };
  }
}

async function run(vu) {
  await Promise.all(Array.from({ length: Math.min(vu, 20) }, async () => {
    for (let i = 0; i < 5; i++) await request();
  }));
  const started = performance.now();
  const results = await Promise.all(Array.from({ length: vu }, async () => {
    const local = [];
    for (let i = 0; i < iterations; i++) local.push(await request());
    return local;
  }));
  const samples = results.flat();
  const elapsed = (performance.now() - started) / 1000;
  const latencies = samples.map(x => x.ms).sort((a, b) => a - b);
  const p95 = latencies[Math.ceil(latencies.length * .95) - 1];
  const ok = samples.filter(x => x.ok).length;
  console.log(JSON.stringify({ path, vus: vu, requests: samples.length, successful: ok,
    elapsedSeconds: Number(elapsed.toFixed(3)), rps: Number((samples.length / elapsed).toFixed(2)),
    p95Ms: Number(p95.toFixed(2)) }));
}

for (const vu of vus) await run(vu);

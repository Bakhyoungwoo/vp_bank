const { api, formatMarketNumber, initLayout, MOCK_STOCKS } = window.VAP;

initLayout({ active: 'market' });

const OPENERS = [
  '오늘 국내 증시는 반도체·2차전지 업종을 중심으로 투자심리가 엇갈리는 모습을 보였습니다.',
  '오늘 시장은 대형 기술주의 강세 속에 코스피와 코스닥이 상반된 흐름을 나타냈습니다.',
  '오늘 증시는 미국 증시 마감 흐름의 영향을 받으며 업종별 차별화 장세를 이어갔습니다.',
];

function renderBriefing(marketItems) {
  const wrap = document.getElementById('briefingCard');
  const opener = OPENERS[Math.floor(Math.random() * OPENERS.length)];
  const indexSummary = marketItems.slice(0, 4).map((it) => {
    const cp = Number(it.changePercent || 0);
    return `${it.name} ${it.available ? `${cp >= 0 ? '+' : ''}${cp.toFixed(2)}%` : '조회 불가'}`;
  }).join(' · ');

  wrap.innerHTML = `
    <div class="rounded-3xl border border-zinc-200 bg-white p-6">
      <div class="inline-flex items-center gap-1.5 rounded-full border border-amber-200 bg-amber-50 px-3 py-1 mb-4">
        <iconify-icon icon="solar:hammer-linear" class="text-amber-700 text-xs"></iconify-icon>
        <span class="text-[11px] font-semibold text-amber-700">목업 브리핑</span>
      </div>
      <p class="keep-all text-base leading-relaxed text-zinc-800">${opener}</p>
      ${indexSummary ? `<p class="mt-3 text-sm text-zinc-500">${indexSummary}</p>` : ''}
      <p class="keep-all mt-3 text-sm leading-relaxed text-zinc-600">
        업종별로는 반도체·2차전지 대형주에 매수세가 유입된 반면, 일부 플랫폼·바이오 업종은 차익 실현 매물에 약세를 보였습니다.
        환율과 미국 금리 흐름이 다음 거래일 방향성의 주요 변수로 꼽힙니다.
      </p>
      <p class="mt-4 text-xs text-zinc-400">실제 AI 브리핑 엔진 연동 전까지 표시되는 예시 문구입니다.</p>
    </div>`;
}

function renderMovers() {
  const sorted = [...MOCK_STOCKS].sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent)).slice(0, 6);
  document.getElementById('moversList').innerHTML = sorted.map((s) => {
    const up = s.change >= 0;
    return `
      <a href="stock-detail.html?symbol=${encodeURIComponent(s.symbol)}" class="flex items-center justify-between gap-3 rounded-2xl border border-zinc-200 bg-white p-4 hover:border-emerald-200 hover:shadow-sm transition-all">
        <span class="min-w-0">
          <span class="block text-xs text-zinc-400">${s.market}</span>
          <span class="block font-semibold text-zinc-900 truncate">${s.name}</span>
        </span>
        <span class="text-right shrink-0">
          <span class="block font-display font-bold text-zinc-900">${formatMarketNumber(s.price)}</span>
          <span class="block text-xs font-semibold ${up ? 'text-red-500' : 'text-blue-500'}">${up ? '▲' : '▼'} ${Math.abs(s.changePercent).toFixed(2)}%</span>
        </span>
      </a>`;
  }).join('');
}

async function load() {
  try {
    const res = await api.getMarketOverview();
    const data = res.ok ? await res.json() : { items: [] };
    renderBriefing(data.items || []);
  } catch (e) {
    renderBriefing([]);
  }
}

document.getElementById('regenerateBtn').addEventListener('click', load);

load();
renderMovers();

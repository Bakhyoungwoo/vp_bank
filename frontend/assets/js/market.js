const { api, formatMarketNumber, formatPublished, initLayout, MOCK_STOCKS } = window.VAP;

const layout = initLayout({ active: 'market' });

const INDEX_ORDER = ['KOSPI', 'KOSDAQ', 'NASDAQ', 'S&P500'];

const FEATURES = [
  { href: 'stock-search.html', icon: 'solar:magnifer-linear', title: '종목 검색', desc: '국내외 종목을 검색해 상세 정보를 확인해요.' },
  { href: 'stock-compare.html', icon: 'solar:widget-4-linear', title: '종목 비교', desc: '여러 종목의 지표를 나란히 비교해요.' },
  { href: 'news-impact.html', icon: 'solar:graph-new-up-linear', title: 'AI 뉴스 영향 분석', desc: '뉴스가 시장에 미치는 영향을 AI가 분석해요.' },
  { href: 'market-briefing.html', icon: 'solar:document-text-linear', title: 'AI 시장 브리핑', desc: '오늘의 시황을 AI가 요약해드려요.' },
  { href: 'watchlist.html', icon: 'solar:star-bold', title: '관심종목 브리핑', desc: '내 관심종목의 변화를 모아 AI가 브리핑해드려요.' },
  { href: 'market-chatbot.html', icon: 'solar:chat-round-dots-linear', title: 'AI 증시 챗봇', desc: '궁금한 종목·시장 이슈를 AI에게 물어보세요.' },
];

function renderFeatureMenu() {
  const wrap = document.getElementById('featureMenu');
  wrap.innerHTML = FEATURES.map((f) => `
    <a href="${f.href}" class="cta-magnetic flex items-start gap-4 rounded-2xl border border-zinc-200 bg-white p-5 hover:border-emerald-200 hover:shadow-sm transition-all">
      <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600 text-xl">
        <iconify-icon icon="${f.icon}"></iconify-icon>
      </span>
      <span class="min-w-0">
        <span class="block font-semibold text-zinc-900">${f.title}</span>
        <span class="keep-all block mt-1 text-sm text-zinc-500">${f.desc}</span>
      </span>
    </a>`).join('');
}

function renderPopularStocks() {
  const wrap = document.getElementById('popularStocks');
  wrap.innerHTML = MOCK_STOCKS.slice(0, 6).map((s) => {
    const up = s.change >= 0;
    return `
    <a href="stock-detail.html?symbol=${encodeURIComponent(s.symbol)}" class="flex items-center justify-between gap-3 rounded-2xl border border-zinc-200 bg-white p-5 hover:border-emerald-200 hover:shadow-sm transition-all">
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

function indexCardHTML(item) {
  if (!item) {
    return `<div class="rounded-2xl border border-dashed border-zinc-200 bg-zinc-50 p-5 text-center text-sm text-zinc-400">준비 중</div>`;
  }
  const changeValue = Number(item.change || 0);
  const changePercent = Number(item.changePercent || 0);
  const changeCls = changeValue > 0 ? 'text-red-500' : changeValue < 0 ? 'text-blue-500' : 'text-zinc-400';
  const arrow = changeValue > 0 ? '▲' : changeValue < 0 ? '▼' : '−';
  return `
    <div class="rounded-2xl border border-zinc-200 bg-white p-5 hover:border-emerald-200 hover:shadow-sm transition-all">
      <p class="text-sm font-semibold text-zinc-700">${item.name}<span class="ml-1 text-[11px] font-normal text-zinc-400">${item.unit || ''}</span></p>
      <p class="mt-4 font-display text-2xl font-bold text-zinc-900">${item.available ? formatMarketNumber(item.price) : '-'}</p>
      <p class="mt-1 text-sm font-semibold ${changeCls}">${
        item.available
          ? `${arrow} ${formatMarketNumber(Math.abs(changeValue))} (${changePercent >= 0 ? '+' : ''}${changePercent.toFixed(2)}%)`
          : '조회 불가'
      }</p>
    </div>`;
}

function renderIndexGrid(items) {
  const wrap = document.getElementById('indexGrid');
  if (!items || items.length === 0) {
    wrap.innerHTML = '<div class="col-span-2 lg:col-span-4 rounded-2xl bg-zinc-50 p-8 text-center text-sm text-zinc-400">현재 시세를 불러오지 못했어요.</div>';
    return;
  }
  const byName = (needle) => items.find((it) => (it.name || '').toUpperCase().includes(needle));
  const picked = INDEX_ORDER.map((label) => byName(label.replace('&', '')) || byName(label));
  const extra = items.filter((it) => !picked.includes(it));
  const cards = picked.length && picked.some(Boolean) ? picked : items;
  wrap.innerHTML = cards.map(indexCardHTML).join('') + (picked.some(Boolean) ? extra.map(indexCardHTML).join('') : '');
}

function renderMarketNews(items) {
  const wrap = document.getElementById('marketNewsList');
  wrap.innerHTML = '';
  if (!items || items.length === 0) {
    wrap.innerHTML = '<div class="p-8 text-center text-sm text-zinc-400">관련 뉴스가 아직 없어요.</div>';
    return;
  }
  wrap.innerHTML = items.slice(0, 10).map((item) => `
    <a href="${item.url || '#'}" target="_blank" rel="noopener" class="flex items-center justify-between gap-4 px-5 py-4 hover:bg-zinc-50 transition-colors">
      <span class="keep-all min-w-0 truncate text-sm font-semibold text-zinc-800 hover:text-emerald-600">${item.title || '(제목 없음)'}</span>
      <span class="shrink-0 text-xs text-zinc-400">${[item.press, formatPublished(item.publishedAt || item.time)].filter(Boolean).join(' · ')}</span>
    </a>`).join('');
}

let marketChart = null;

async function loadMarketChart(symbol) {
  const status = document.getElementById('marketChartStatus');
  status.textContent = '차트 데이터를 불러오는 중…';
  try {
    const res = await api.getMarketHistory(symbol, 30);
    const data = res.ok ? await res.json() : { items: [] };
    const rows = Array.isArray(data.items) ? data.items.filter((row) => row.close != null) : [];
    if (rows.length === 0) {
      status.textContent = '차트 데이터를 불러오지 못했어요. Provider 연결을 확인해주세요.';
      if (marketChart) { marketChart.destroy(); marketChart = null; }
      return;
    }
    if (marketChart) marketChart.destroy();
    marketChart = new Chart(document.getElementById('marketChart'), {
      type: 'line',
      data: {
        labels: rows.map((row) => String(row.date).slice(0, 10)),
        datasets: [{
          data: rows.map((row) => Number(row.close)),
          borderColor: '#10b981',
          backgroundColor: 'rgba(16, 185, 129, 0.10)',
          borderWidth: 2,
          pointRadius: 0,
          fill: true,
          tension: 0.25,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { grid: { display: false }, ticks: { maxTicksLimit: 6, color: '#a1a1aa' } },
          y: { grid: { color: '#f4f4f5' }, ticks: { color: '#a1a1aa' } },
        },
      },
    });
    status.textContent = `최근 ${rows.length}개 데이터 · ${data.provider || 'OpenBB'}`;
  } catch (e) {
    status.textContent = '차트 API에 연결할 수 없어요.';
  }
}

async function loadMarket() {
  const updated = document.getElementById('marketUpdatedAt');
  updated.textContent = '시세를 불러오는 중…';
  try {
    const [marketRes, newsRes] = await Promise.all([api.getMarketOverview(), api.getEconomyNews()]);
    const market = marketRes.ok ? await marketRes.json() : { items: [] };
    const news = newsRes.ok ? await newsRes.json() : [];
    renderIndexGrid(market.items || []);
    renderMarketNews(news);
    updated.textContent = `마지막 업데이트 ${new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}`;
  } catch (e) {
    renderIndexGrid([]);
    renderMarketNews([]);
    updated.textContent = '업데이트 실패';
  }
}

document.getElementById('refreshMarketBtn').addEventListener('click', loadMarket);
document.getElementById('marketChartSelect').addEventListener('change', (e) => loadMarketChart(e.target.value));

renderFeatureMenu();
renderPopularStocks();

// 증시 지수/뉴스/차트는 로그인 후에만 조회 가능하므로, 로그인 상태가 확정(또는 변경)될 때마다 다시 불러온다.
layout.onUserChange(() => {
  loadMarket();
  loadMarketChart(document.getElementById('marketChartSelect').value || '^KS11');
});

setInterval(loadMarket, 60000);

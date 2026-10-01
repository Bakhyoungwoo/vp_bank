const { api, initLayout, formatMarketNumber, formatPriceWithKrw, getUsdKrwRate, formatPublished } = window.VAP;

initLayout({ active: 'market' });

const symbol = new URLSearchParams(location.search).get('symbol') || '';
const header = document.getElementById('stockHeader');
const tabsWrap = document.getElementById('stockTabs');
const panels = document.getElementById('tabPanels');
const aiSection = document.getElementById('aiSection');

let detail = null;
let priceChart = null;
let volumeChart = null;
let activeTab = 'overview';
let usdKrwRate = null;
const cache = { financials: null, news: null };

const TABS = [
  { key: 'overview', label: '개요·차트' },
  { key: 'financials', label: '재무' },
  { key: 'news', label: '뉴스' },
];

function value(item, fallback = '-') {
  return item === null || item === undefined || item === '' ? fallback : item;
}

function num(v) {
  if (v === null || v === undefined || v === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function field(row, ...names) {
  const lowered = Object.fromEntries(Object.entries(row || {}).map(([k, v]) => [k.toLowerCase(), v]));
  for (const name of names) {
    const v = lowered[name.toLowerCase()];
    if (v !== undefined && v !== null && v !== '') return v;
  }
  return null;
}

function formatCompact(n) {
  const value = Number(n);
  if (!Number.isFinite(value)) return '-';
  const abs = Math.abs(value);
  if (abs >= 1e9) return `${(value / 1e9).toFixed(1)}B`;
  if (abs >= 1e6) return `${(value / 1e6).toFixed(1)}M`;
  if (abs >= 1e3) return `${(value / 1e3).toFixed(1)}K`;
  return String(value);
}

function priceChangeInfo() {
  const quote = detail.quote || {};
  const rows = (detail.history || []).filter((row) => row.close != null);
  let price = num(field(quote, 'last_price', 'price', 'close', 'regular_market_price'));
  let change = num(field(quote, 'change', 'regular_market_change', 'changes'));
  let changePercent = num(field(quote, 'change_percent', 'regular_market_change_percent', 'changes_percentage'));
  if (price == null && rows.length) price = num(rows[rows.length - 1].close);
  if (rows.length >= 2 && (change == null || changePercent == null)) {
    const last = num(rows[rows.length - 1].close);
    const prev = num(rows[rows.length - 2].close);
    if (last != null && prev != null) {
      if (change == null) change = last - prev;
      if (changePercent == null && prev) changePercent = ((last / prev) - 1) * 100;
    }
  }
  return { price, change, changePercent };
}

function renderHeader() {
  const profile = detail.profile || {};
  const { price, change, changePercent } = priceChangeInfo();
  const up = (change ?? 0) >= 0;
  const changeCls = change == null ? 'text-zinc-400' : up ? 'text-red-500' : 'text-blue-500';
  const arrow = change == null ? '' : up ? '▲' : '▼';
  header.innerHTML = `
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <p class="text-xs font-semibold uppercase tracking-wide text-zinc-400">${value(profile.exchange || profile.exchange_code, 'MARKET')}</p>
        <h1 class="mt-2 text-3xl font-bold tracking-tight text-zinc-900">${value(profile.name || profile.company_name, symbol)}</h1>
        <p class="mt-1 text-sm text-zinc-400">${symbol} · OpenBB/yfinance</p>
      </div>
      <div class="text-right">
        <p class="font-display text-3xl font-bold text-zinc-900">${price == null ? '-' : formatPriceWithKrw(price, detail.quote && detail.quote.currency, usdKrwRate)}</p>
        <p class="mt-1 text-sm font-semibold ${changeCls}">${
          change == null
            ? '데이터 없음'
            : `${arrow} ${formatMarketNumber(Math.abs(change))} (${changePercent >= 0 ? '+' : ''}${(changePercent ?? 0).toFixed(2)}%)`
        }</p>
      </div>
    </div>`;
}

function renderTabButtons() {
  tabsWrap.innerHTML = TABS.map((tab) => `
    <button data-tab="${tab.key}" type="button" class="shrink-0 border-b-2 px-4 py-2.5 text-sm font-semibold transition-colors ${
      activeTab === tab.key ? 'border-emerald-500 text-emerald-600' : 'border-transparent text-zinc-500 hover:text-zinc-800'
    }">${tab.label}</button>`).join('');
  tabsWrap.querySelectorAll('button').forEach((btn) => {
    btn.addEventListener('click', () => {
      if (btn.dataset.tab === activeTab) return;
      activeTab = btn.dataset.tab;
      renderTabButtons();
      renderActivePanel();
    });
  });
}

function renderActivePanel() {
  if (activeTab === 'overview') renderOverviewTab();
  else if (activeTab === 'financials') renderFinancialsTab();
  else renderNewsTab();
}

function renderOverviewTab() {
  panels.innerHTML = `
    <div class="space-y-5">
      <div class="rounded-3xl border border-zinc-200 bg-white p-5">
        <h2 class="font-semibold text-zinc-900">주가 차트</h2>
        <p id="priceChartStatus" class="mt-1 text-xs text-zinc-400">차트 데이터를 불러오는 중…</p>
        <div class="mt-4 h-72"><canvas id="priceChart"></canvas></div>
      </div>
      <div class="rounded-3xl border border-zinc-200 bg-white p-5">
        <h2 class="font-semibold text-zinc-900">거래량 차트</h2>
        <p id="volumeChartStatus" class="mt-1 text-xs text-zinc-400">차트 데이터를 불러오는 중…</p>
        <div class="mt-4 h-48"><canvas id="volumeChart"></canvas></div>
      </div>
      <section class="rounded-3xl border border-zinc-200 bg-white p-5">
        <h2 class="font-semibold text-zinc-900">기업 정보</h2>
        <div id="profileInfo" class="mt-4 grid grid-cols-2 gap-3"></div>
      </section>
    </div>`;
  renderProfile();
  renderPriceChart();
  renderVolumeChart();
}

function renderProfile() {
  const profile = detail.profile || {};
  const items = [
    ['거래소', profile.exchange || profile.exchange_code],
    ['업종', profile.industry || profile.sector],
    ['국가', profile.country],
    ['웹사이트', profile.website],
  ];
  document.getElementById('profileInfo').innerHTML = items.map(([label, item]) => `
    <div class="rounded-2xl bg-zinc-50 p-3"><p class="text-xs text-zinc-400">${label}</p><p class="mt-1 truncate text-sm font-semibold text-zinc-800">${value(item)}</p></div>`).join('');
}

function renderPriceChart() {
  const rows = (detail.history || []).filter((row) => row.close != null);
  const status = document.getElementById('priceChartStatus');
  if (!rows.length) {
    status.textContent = '차트 데이터가 없습니다.';
    return;
  }
  const up = Number(rows[rows.length - 1].close) >= Number(rows[0].close);
  const color = up ? '#ef4444' : '#3b82f6';
  if (priceChart) priceChart.destroy();
  priceChart = new Chart(document.getElementById('priceChart'), {
    type: 'line',
    data: {
      labels: rows.map((row) => String(row.date).slice(0, 10)),
      datasets: [{ data: rows.map((row) => Number(row.close)), borderColor: color, backgroundColor: `${color}1a`, fill: true, pointRadius: 0, borderWidth: 2, tension: .25 }],
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
  status.textContent = `${rows.length}개 데이터 · ${detail.provider || 'OpenBB'}`;
}

function renderVolumeChart() {
  const rows = (detail.history || []).filter((row) => row.volume != null);
  const status = document.getElementById('volumeChartStatus');
  if (!rows.length) {
    status.textContent = '거래량 데이터가 없습니다.';
    return;
  }
  if (volumeChart) volumeChart.destroy();
  volumeChart = new Chart(document.getElementById('volumeChart'), {
    type: 'bar',
    data: {
      labels: rows.map((row) => String(row.date).slice(0, 10)),
      datasets: [{ data: rows.map((row) => Number(row.volume)), backgroundColor: 'rgba(16,185,129,.55)', borderRadius: 2, maxBarThickness: 10 }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { grid: { display: false }, ticks: { maxTicksLimit: 6, color: '#a1a1aa' } },
        y: { grid: { color: '#f4f4f5' }, ticks: { color: '#a1a1aa', callback: (v) => formatCompact(v) } },
      },
    },
  });
  status.textContent = `${rows.length}개 데이터`;
}

function financialRowHTML(row) {
  const period = field(row, 'fiscal_period', 'period', 'date');
  const revenue = field(row, 'revenue', 'total_revenue', 'totalRevenue');
  const operatingIncome = field(row, 'operating_income', 'operatingIncome');
  const netIncome = field(row, 'net_income', 'netIncome', 'net_income_common_stockholders');
  const eps = field(row, 'eps', 'diluted_eps', 'dilutedEPS', 'basic_eps');
  return `<tr class="border-b border-zinc-50">
    <td class="py-2 pr-4">${value(period ? String(period).slice(0, 10) : null)}</td>
    <td class="py-2 pr-4">${revenue == null ? '-' : formatMarketNumber(revenue)}</td>
    <td class="py-2 pr-4">${operatingIncome == null ? '-' : formatMarketNumber(operatingIncome)}</td>
    <td class="py-2 pr-4">${netIncome == null ? '-' : formatMarketNumber(netIncome)}</td>
    <td class="py-2">${eps == null ? '-' : formatMarketNumber(eps)}</td>
  </tr>`;
}

async function renderFinancialsTab() {
  panels.innerHTML = '<p class="py-10 text-center text-sm text-zinc-400">재무 데이터를 불러오는 중…</p>';
  if (!cache.financials) {
    try {
      const response = await api.getStockFinancials(symbol, 8);
      cache.financials = response.ok ? await response.json() : { items: [] };
    } catch (error) {
      cache.financials = { items: [] };
    }
  }
  if (activeTab !== 'financials') return;
  const rows = Array.isArray(cache.financials.items) ? cache.financials.items : [];
  panels.innerHTML = rows.length
    ? `<div class="overflow-x-auto rounded-3xl border border-zinc-200 bg-white p-5">
        <h2 class="font-semibold text-zinc-900">재무 데이터</h2>
        <table class="mt-4 w-full text-xs">
          <thead><tr class="border-b border-zinc-100 text-left text-zinc-400">
            <th class="py-2 pr-4">기준일</th><th class="py-2 pr-4">매출</th><th class="py-2 pr-4">영업이익</th><th class="py-2 pr-4">순이익</th><th class="py-2">EPS</th>
          </tr></thead>
          <tbody>${rows.map(financialRowHTML).join('')}</tbody>
        </table>
      </div>`
    : '<p class="rounded-2xl bg-zinc-50 p-8 text-center text-sm text-zinc-400">재무 데이터를 제공하지 않습니다.</p>';
}

async function renderNewsTab() {
  panels.innerHTML = '<p class="py-10 text-center text-sm text-zinc-400">뉴스를 불러오는 중…</p>';
  if (!cache.news) {
    try {
      const response = await api.getStockNews(symbol, 10);
      cache.news = response.ok ? await response.json() : { items: [] };
    } catch (error) {
      cache.news = { items: [] };
    }
  }
  if (activeTab !== 'news') return;
  const items = Array.isArray(cache.news.items) ? cache.news.items : [];
  panels.innerHTML = items.length
    ? `<div class="divide-y divide-zinc-100 rounded-3xl border border-zinc-200 bg-white">
        ${items.map((item) => `
          <a href="${item.url || '#'}" target="_blank" rel="noopener" class="flex items-center justify-between gap-4 px-5 py-4 hover:bg-zinc-50 transition-colors">
            <span class="keep-all min-w-0 truncate text-sm font-semibold text-zinc-800 hover:text-emerald-600">${value(item.title, '(제목 없음)')}</span>
            <span class="shrink-0 text-xs text-zinc-400">${[item.press, formatPublished(item.publishedAt)].filter(Boolean).join(' · ')}</span>
          </a>`).join('')}
      </div>`
    : '<p class="rounded-2xl bg-zinc-50 p-8 text-center text-sm text-zinc-400">관련 뉴스가 아직 없어요.</p>';
}

function scoreCardHTML(title, scoreObj) {
  const score = scoreObj && typeof scoreObj.score === 'number' ? scoreObj.score : null;
  const evidence = (scoreObj && scoreObj.evidence) || [];
  const pct = score == null ? 0 : Math.max(0, Math.min(100, score));
  return `
    <div class="rounded-2xl bg-zinc-50 p-4">
      <div class="flex items-center justify-between">
        <span class="text-sm font-semibold text-zinc-700">${title}</span>
        <span class="font-display text-lg font-bold text-zinc-900">${score == null ? '-' : score}</span>
      </div>
      <div class="mt-2 h-2 overflow-hidden rounded-full bg-zinc-200">
        <div class="h-full rounded-full bg-emerald-500" style="width:${pct}%"></div>
      </div>
      ${evidence.length ? `<ul class="mt-2 space-y-1">${evidence.map((e) => `<li class="text-xs text-zinc-500">· ${e}</li>`).join('')}</ul>` : ''}
    </div>`;
}

function renderAiResult(data) {
  const result = document.getElementById('aiResult');
  const ready = data.status === 'ready';
  const narrative = data.llmNarrative
    ? String(data.llmNarrative).replace(/</g, '&lt;').replace(/>/g, '&gt;')
    : '';
  result.innerHTML = `
    <div class="rounded-2xl ${ready ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'} p-4 text-sm font-medium">${value(data.summary)}</div>
    ${narrative ? `
      <div class="mt-4 rounded-2xl border border-emerald-100 bg-white p-4 text-sm leading-relaxed text-zinc-700">
        <p class="mb-1.5 text-xs font-semibold text-emerald-600">AI 요약</p>
        ${narrative}
      </div>` : ''}
    <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
      ${scoreCardHTML('성장성', data.growth)}
      ${scoreCardHTML('수익성', data.profitability)}
      ${scoreCardHTML('밸류에이션', data.valuation)}
      ${scoreCardHTML('모멘텀', data.momentum)}
    </div>
    ${(data.positiveFactors || []).length ? `
      <div class="mt-4">
        <p class="text-xs font-semibold text-zinc-400">긍정 요인</p>
        <ul class="mt-1 space-y-1">${data.positiveFactors.map((f) => `<li class="text-sm text-zinc-700">+ ${f}</li>`).join('')}</ul>
      </div>` : ''}
    ${(data.riskFactors || []).length ? `
      <div class="mt-4">
        <p class="text-xs font-semibold text-zinc-400">유의 사항</p>
        <ul class="mt-1 space-y-1">${data.riskFactors.map((f) => `<li class="text-sm text-zinc-500">- ${f}</li>`).join('')}</ul>
      </div>` : ''}
    <p class="mt-4 text-[11px] text-zinc-400">기준 시각 ${formatPublished(data.asOf) || '-'} · ${(data.sources || []).map((s) => s.provider).filter(Boolean).join(', ') || 'openbb/yfinance'}</p>`;
}

async function runAiAnalysis() {
  const btn = document.getElementById('aiAnalyzeBtn');
  const result = document.getElementById('aiResult');
  btn.disabled = true;
  btn.textContent = '분석 중…';
  result.innerHTML = '<p class="py-8 text-center text-sm text-zinc-400">AI가 가격·거래량·재무 데이터를 분석하고 있습니다…</p>';
  try {
    const response = await api.analyzeStock(symbol, 90);
    const data = await response.json().catch(() => null);
    if (!response.ok || !data) throw new Error('AI analysis failed');
    renderAiResult(data);
  } catch (error) {
    result.innerHTML = '<p class="py-8 text-center text-sm text-red-500">AI 분석 서비스에 연결할 수 없습니다.</p>';
  } finally {
    btn.disabled = false;
    btn.textContent = '다시 분석하기';
  }
}

function renderAiSection() {
  aiSection.innerHTML = `
    <div class="rounded-3xl border border-zinc-200 bg-white p-5">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="flex items-center gap-2 font-semibold text-zinc-900"><iconify-icon icon="solar:magic-stick-3-linear" class="text-emerald-500"></iconify-icon>AI 분석</h2>
          <p class="mt-1 text-xs text-zinc-400">가격·거래량·재무 데이터를 기반으로 한 정량 분석이며, 투자 조언이 아닙니다.</p>
        </div>
        <button id="aiAnalyzeBtn" type="button" class="cta-magnetic shrink-0 rounded-full bg-emerald-500 px-5 py-2.5 text-sm font-semibold text-white hover:bg-emerald-600">AI 분석 시작</button>
      </div>
      <div id="aiResult" class="mt-5"></div>
    </div>`;
  document.getElementById('aiAnalyzeBtn').addEventListener('click', runAiAnalysis);
}

async function load() {
  if (!symbol) {
    header.innerHTML = '<p class="rounded-2xl bg-zinc-50 p-8 text-center text-zinc-500">종목 코드가 없습니다.</p>';
    return;
  }
  try {
    const response = await api.getStockDetail(symbol, 90);
    if (!response.ok) throw new Error('stock detail request failed');
    detail = await response.json();
    renderHeader();
    const currency = detail.quote && detail.quote.currency;
    if (currency && currency !== 'KRW') {
      // 원화가 아닌 종목만 환율을 조회해 헤더를 다시 그린다 (국내 종목은 추가 호출 없이 그대로 둔다).
      getUsdKrwRate().then((rate) => {
        usdKrwRate = rate;
        renderHeader();
      });
    }
    renderTabButtons();
    renderActivePanel();
    renderAiSection();
  } catch (error) {
    header.innerHTML = '<p class="rounded-2xl bg-red-50 p-8 text-center text-red-500">종목 상세 데이터를 불러오지 못했습니다.</p>';
  }
}

load();

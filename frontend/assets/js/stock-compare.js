const { api, initLayout, formatPublished, formatPriceWithKrw, getUsdKrwRate } = window.VAP;

initLayout({ active: 'market' });

const MIN_SELECTION = 2;
const MAX_SELECTION = 5;

const searchInput = document.getElementById('compareSearchInput');
const searchResults = document.getElementById('compareSearchResults');
const chipsWrap = document.getElementById('selectedChips');
const runBtn = document.getElementById('runCompareBtn');
const resultWrap = document.getElementById('compareResultWrap');

let selected = [];
let searchDebounce = null;

function value(item, fallback = '-') {
  return item === null || item === undefined || item === '' ? fallback : item;
}

function pct(v, digits = 2) {
  const n = Number(v);
  return Number.isFinite(n) ? `${n > 0 ? '+' : ''}${n.toFixed(digits)}%` : '데이터 없음';
}

function ratio(v, digits = 1) {
  const n = Number(v);
  return Number.isFinite(n) ? `${n.toFixed(digits)}배` : '데이터 없음';
}

function clearSearchResults() {
  searchResults.classList.add('hidden');
  searchResults.innerHTML = '';
}

function renderSearchResults(items) {
  if (!items || items.length === 0) {
    searchResults.innerHTML = '<div class="p-4 text-center text-sm text-zinc-400">검색 결과가 없습니다.</div>';
    searchResults.classList.remove('hidden');
    return;
  }
  searchResults.innerHTML = items.map((item) => `
    <button type="button" data-symbol="${item.symbol}" data-name="${(item.name || item.symbol).replace(/"/g, '&quot;')}"
      class="compare-add-btn flex w-full items-center justify-between gap-3 border-b border-zinc-100 px-4 py-2.5 text-left last:border-b-0 hover:bg-zinc-50">
      <span class="min-w-0">
        <span class="block truncate text-sm font-semibold text-zinc-800">${item.name || item.symbol}</span>
        <span class="block text-xs text-zinc-400">${item.symbol}${item.exchange ? ' · ' + item.exchange : ''}</span>
      </span>
      <span class="shrink-0 text-xs font-semibold text-emerald-600">추가</span>
    </button>`).join('');
  searchResults.classList.remove('hidden');
  searchResults.querySelectorAll('.compare-add-btn').forEach((btn) => {
    btn.addEventListener('click', () => addSymbol(btn.dataset.symbol, btn.dataset.name));
  });
}

async function runSearch(query) {
  try {
    const response = await api.searchStocks(query, 8);
    const data = response.ok ? await response.json() : { items: [] };
    renderSearchResults(data.items || []);
  } catch (error) {
    searchResults.innerHTML = '<div class="p-4 text-center text-sm text-red-500">검색에 실패했습니다.</div>';
    searchResults.classList.remove('hidden');
  }
}

searchInput.addEventListener('input', () => {
  const query = searchInput.value.trim();
  clearTimeout(searchDebounce);
  if (!query) {
    clearSearchResults();
    return;
  }
  searchDebounce = setTimeout(() => runSearch(query), 350);
});

document.addEventListener('click', (e) => {
  if (!searchResults.contains(e.target) && e.target !== searchInput) clearSearchResults();
});

function addSymbol(symbol, name) {
  if (selected.some((s) => s.symbol === symbol)) return;
  if (selected.length >= MAX_SELECTION) {
    resultWrap.innerHTML = `<p class="rounded-xl bg-amber-50 p-4 text-sm text-amber-700">최대 ${MAX_SELECTION}개까지만 비교할 수 있습니다.</p>`;
    return;
  }
  selected = [...selected, { symbol, name }];
  searchInput.value = '';
  clearSearchResults();
  renderChips();
}

function removeSymbol(symbol) {
  selected = selected.filter((s) => s.symbol !== symbol);
  renderChips();
}

function renderChips() {
  chipsWrap.innerHTML = selected.map((s) => `
    <span class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3.5 py-1.5 text-sm font-semibold text-emerald-700">
      ${s.name}
      <button type="button" data-symbol="${s.symbol}" class="remove-chip text-emerald-500 hover:text-emerald-800">
        <iconify-icon icon="solar:close-circle-bold"></iconify-icon>
      </button>
    </span>`).join('');
  chipsWrap.querySelectorAll('.remove-chip').forEach((btn) => {
    btn.addEventListener('click', () => removeSymbol(btn.dataset.symbol));
  });
  runBtn.disabled = selected.length < MIN_SELECTION || selected.length > MAX_SELECTION;
}

let usdKrwRate = null;

const METRIC_ROWS = [
  { label: '현재가', get: (item) => (item.price == null ? '데이터 없음' : formatPriceWithKrw(item.price, item.currency, usdKrwRate)) },
  { label: '최근 기간 수익률', get: (item) => pct(item.periodReturnPercent), cls: (item) => (Number(item.periodReturnPercent) > 0 ? 'text-red-500' : Number(item.periodReturnPercent) < 0 ? 'text-blue-500' : '') },
  { label: '변동성', get: (item) => pct(item.volatilityPercent) },
  { label: '매출 성장률', get: (item) => pct(item.revenueGrowthPercent) },
  { label: '영업이익률', get: (item) => pct(item.operatingMarginPercent) },
  { label: 'PER', get: (item) => ratio(item.per) },
  { label: 'PBR', get: (item) => ratio(item.pbr) },
  { label: 'ROE', get: (item) => pct(item.roe) },
  { label: '부채비율', get: (item) => pct(item.debtRatio, 1) },
  { label: '업종', get: (item) => value(item.sector) },
];

function renderCompareResult(data) {
  const narrative = data.llmNarrative
    ? String(data.llmNarrative).replace(/</g, '&lt;').replace(/>/g, '&gt;')
    : '';
  const rowsHTML = METRIC_ROWS.map((m) => `
    <tr>
      <td class="px-4 py-3 font-semibold text-zinc-500">${m.label}</td>
      ${data.items.map((item) => `<td class="px-4 py-3 text-right font-semibold ${m.cls ? m.cls(item) : 'text-zinc-700'}">${m.get(item)}</td>`).join('')}
    </tr>`).join('');

  resultWrap.innerHTML = `
    ${data.sameSector ? '<p class="mb-4 rounded-xl bg-emerald-50 p-3 text-center text-sm font-semibold text-emerald-700">선택한 종목은 모두 같은 업종입니다.</p>' : ''}
    <div class="overflow-x-auto rounded-2xl border border-zinc-200 bg-white">
      <table class="compare-table w-full text-sm">
        <thead class="bg-zinc-50 text-zinc-500">
          <tr>
            <th class="px-4 py-3 text-left font-semibold">지표</th>
            ${data.items.map((item) => `<th class="px-4 py-3 text-right font-semibold text-zinc-800">${item.name || item.symbol}</th>`).join('')}
          </tr>
        </thead>
        <tbody class="divide-y divide-zinc-100">${rowsHTML}</tbody>
      </table>
    </div>
    ${narrative ? `
      <div class="mt-4 rounded-2xl border border-emerald-100 bg-white p-4 text-sm leading-relaxed text-zinc-700">
        <p class="mb-1.5 text-xs font-semibold text-emerald-600">AI 비교 요약</p>
        ${narrative}
      </div>` : ''}
    <p class="mt-3 text-[11px] text-zinc-400">기준 시각 ${formatPublished(data.asOf) || '-'} · 가격·재무 데이터 기반 정량 비교이며 투자 조언이 아닙니다.</p>`;
}

runBtn.addEventListener('click', async () => {
  runBtn.disabled = true;
  resultWrap.innerHTML = '<p class="py-10 text-center text-sm text-zinc-400">종목을 비교하는 중입니다… (최대 1분 정도 걸릴 수 있어요)</p>';
  try {
    const [response, rate] = await Promise.all([
      api.compareStocks(selected.map((s) => s.symbol), 90),
      getUsdKrwRate(),
    ]);
    usdKrwRate = rate;
    const data = await response.json().catch(() => null);
    if (!response.ok || !data) throw new Error('compare failed');
    renderCompareResult(data);
  } catch (error) {
    resultWrap.innerHTML = '<p class="py-10 text-center text-sm text-red-500">종목 비교에 실패했습니다.</p>';
  } finally {
    runBtn.disabled = selected.length < MIN_SELECTION || selected.length > MAX_SELECTION;
  }
});

renderChips();
resultWrap.innerHTML = '<p class="rounded-2xl border border-dashed border-zinc-200 py-16 text-center text-sm text-zinc-400">비교할 종목을 2개 이상 추가해보세요.</p>';

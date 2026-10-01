const { api, formatPublished, initLayout } = window.VAP;

initLayout({ active: 'market' });

const IMPACT_STYLES = {
  positive: { label: '긍정적', cls: 'bg-red-50 border-red-200 text-red-600', icon: 'solar:arrow-up-linear' },
  negative: { label: '부정적', cls: 'bg-blue-50 border-blue-200 text-blue-600', icon: 'solar:arrow-down-linear' },
  neutral: { label: '중립', cls: 'bg-zinc-100 border-zinc-200 text-zinc-500', icon: 'solar:minus-circle-linear' },
};

const OVERALL_LABELS = {
  positive: '긍정 우세', negative: '부정 우세', mixed: '혼재', neutral: '중립', insufficient_data: '데이터 부족',
};

const searchInput = document.getElementById('newsImpactSearchInput');
const searchResults = document.getElementById('newsImpactSearchResults');
const chipWrap = document.getElementById('selectedSymbolChip');
const runBtn = document.getElementById('runNewsImpactBtn');
const resultWrap = document.getElementById('impactList');

let selected = null;
let searchDebounce = null;

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
      class="impact-add-btn flex w-full items-center justify-between gap-3 border-b border-zinc-100 px-4 py-2.5 text-left last:border-b-0 hover:bg-zinc-50">
      <span class="min-w-0">
        <span class="block truncate text-sm font-semibold text-zinc-800">${item.name || item.symbol}</span>
        <span class="block text-xs text-zinc-400">${item.symbol}${item.exchange ? ' · ' + item.exchange : ''}</span>
      </span>
      <span class="shrink-0 text-xs font-semibold text-emerald-600">선택</span>
    </button>`).join('');
  searchResults.classList.remove('hidden');
  searchResults.querySelectorAll('.impact-add-btn').forEach((btn) => {
    btn.addEventListener('click', () => selectSymbol(btn.dataset.symbol, btn.dataset.name));
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

function selectSymbol(symbol, name) {
  selected = { symbol, name };
  searchInput.value = '';
  clearSearchResults();
  renderChip();
}

function renderChip() {
  if (!selected) {
    chipWrap.innerHTML = '';
    runBtn.disabled = true;
    return;
  }
  chipWrap.innerHTML = `
    <span class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3.5 py-1.5 text-sm font-semibold text-emerald-700">
      ${selected.name}
      <button type="button" id="removeSymbolChip" class="text-emerald-500 hover:text-emerald-800">
        <iconify-icon icon="solar:close-circle-bold"></iconify-icon>
      </button>
    </span>`;
  document.getElementById('removeSymbolChip').addEventListener('click', () => {
    selected = null;
    renderChip();
  });
  runBtn.disabled = false;
}

function pct(v, digits = 2) {
  const n = Number(v);
  return Number.isFinite(n) ? `${n > 0 ? '+' : ''}${n.toFixed(digits)}%` : '데이터 없음';
}

function ratio(v, digits = 2) {
  const n = Number(v);
  return Number.isFinite(n) ? `${n.toFixed(digits)}배` : '데이터 없음';
}

function renderArticleCard(article) {
  const impact = IMPACT_STYLES[article.impactLabel] || IMPACT_STYLES.neutral;
  const keywords = [
    ...(article.matchedKeywords?.positive || []),
    ...(article.matchedKeywords?.negative || []),
  ];
  return `
    <div class="rounded-3xl border border-zinc-200 bg-white p-6">
      <div class="flex items-center justify-between text-xs text-zinc-400 mb-2">
        <span>${article.publisher || '출처 미상'}</span>
        <span>${formatPublished(article.publishedAt) || ''}</span>
      </div>
      <a href="${article.url || '#'}" target="_blank" rel="noopener" class="keep-all font-semibold text-zinc-900 leading-snug hover:text-emerald-600">${article.title || '(제목 없음)'}</a>

      <div class="mt-4 flex flex-wrap items-center gap-2">
        <span class="inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-semibold ${impact.cls}">
          <iconify-icon icon="${impact.icon}"></iconify-icon> ${impact.label}
        </span>
        ${keywords.map((kw) => `<span class="rounded-full bg-zinc-100 px-2.5 py-1 text-[11px] font-semibold text-zinc-500">#${kw}</span>`).join('')}
      </div>
    </div>`;
}

function renderResult(data) {
  const summary = data.summary || {};
  const momentum = data.momentum;
  const narrative = data.llmNarrative
    ? String(data.llmNarrative).replace(/</g, '&lt;').replace(/>/g, '&gt;')
    : '';

  const summaryHTML = `
    <div class="rounded-2xl border border-zinc-200 bg-zinc-50 p-5">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span class="text-sm font-semibold text-zinc-700">뉴스 분류 요약: <span class="text-emerald-600">${OVERALL_LABELS[summary.overallLabel] || summary.overallLabel || '-'}</span></span>
        <span class="text-xs text-zinc-400">긍정 ${summary.positiveCount ?? 0} · 부정 ${summary.negativeCount ?? 0} · 중립 ${summary.neutralCount ?? 0}</span>
      </div>
      ${momentum ? `
      <div class="mt-3 flex flex-wrap gap-4 text-xs text-zinc-500">
        <span>최근 기간 수익률 <b class="text-zinc-800">${pct(momentum.periodReturnPercent)}</b></span>
        <span>거래량/평균 비율 <b class="text-zinc-800">${ratio(momentum.volumeRatio)}</b></span>
      </div>` : '<p class="mt-3 text-xs text-zinc-400">최근 가격 데이터가 부족해 모멘텀을 계산하지 못했습니다.</p>'}
    </div>`;

  const articlesHTML = (data.articles || []).length
    ? data.articles.map(renderArticleCard).join('')
    : '<p class="text-center text-sm text-zinc-400 py-16">분석할 뉴스가 없어요.</p>';

  resultWrap.innerHTML = `
    ${summaryHTML}
    ${narrative ? `
      <div class="rounded-2xl border border-emerald-100 bg-white p-4 text-sm leading-relaxed text-zinc-700">
        <p class="mb-1.5 text-xs font-semibold text-emerald-600">AI 영향 분석</p>
        ${narrative}
      </div>` : ''}
    <div class="space-y-4">${articlesHTML}</div>
    ${(data.riskFactors || []).map((r) => `<p class="text-[11px] text-zinc-400">※ ${r}</p>`).join('')}
    <p class="text-[11px] text-zinc-400">기준 시각 ${formatPublished(data.asOf) || '-'}</p>`;
}

runBtn.addEventListener('click', async () => {
  if (!selected) return;
  runBtn.disabled = true;
  resultWrap.innerHTML = '<p class="py-10 text-center text-sm text-zinc-400">뉴스 영향을 분석하는 중입니다… (최대 1분 정도 걸릴 수 있어요)</p>';
  try {
    const response = await api.analyzeNewsImpact(selected.symbol, 10, 30);
    const data = await response.json().catch(() => null);
    if (!response.ok || !data) throw new Error('news-impact failed');
    renderResult(data);
  } catch (error) {
    resultWrap.innerHTML = '<p class="py-10 text-center text-sm text-red-500">뉴스 영향 분석에 실패했습니다.</p>';
  } finally {
    runBtn.disabled = !selected;
  }
});

renderChip();
resultWrap.innerHTML = '<p class="rounded-2xl border border-dashed border-zinc-200 py-16 text-center text-sm text-zinc-400">분석할 종목을 선택해보세요.</p>';

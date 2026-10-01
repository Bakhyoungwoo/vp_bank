const { api, formatPublished, initLayout } = window.VAP;

const layout = initLayout({ active: 'market' });

const searchInput = document.getElementById('watchlistSearchInput');
const searchResults = document.getElementById('watchlistSearchResults');
const chipWrap = document.getElementById('watchlistChips');
const genBtn = document.getElementById('generateBriefingBtn');
const resultWrap = document.getElementById('briefingResult');
const historyWrap = document.getElementById('briefingHistory');

let watchlist = [];
let searchDebounce = null;

// ── 종목 검색 ────────────────────────────────────────────────────────────

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
      class="watchlist-add-btn flex w-full items-center justify-between gap-3 border-b border-zinc-100 px-4 py-2.5 text-left last:border-b-0 hover:bg-zinc-50">
      <span class="min-w-0">
        <span class="block truncate text-sm font-semibold text-zinc-800">${item.name || item.symbol}</span>
        <span class="block text-xs text-zinc-400">${item.symbol}${item.exchange ? ' · ' + item.exchange : ''}</span>
      </span>
      <span class="shrink-0 text-xs font-semibold text-emerald-600">담기</span>
    </button>`).join('');
  searchResults.classList.remove('hidden');
  searchResults.querySelectorAll('.watchlist-add-btn').forEach((btn) => {
    btn.addEventListener('click', () => addSymbol(btn.dataset.symbol));
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

// ── 관심종목 추가/삭제 ───────────────────────────────────────────────────

async function addSymbol(symbol) {
  searchInput.value = '';
  clearSearchResults();
  if (!layout.requireLogin()) return;
  try {
    await api.addToWatchlist(symbol);
  } catch (e) {}
  await loadWatchlist();
}

async function removeSymbol(symbol) {
  try {
    await api.removeFromWatchlist(symbol);
  } catch (e) {}
  await loadWatchlist();
}

function renderChips() {
  if (!layout.getUser()) {
    chipWrap.innerHTML = '<p class="text-sm text-zinc-400">로그인 후 관심종목을 담을 수 있어요.</p>';
    genBtn.disabled = true;
    return;
  }
  if (watchlist.length === 0) {
    chipWrap.innerHTML = '<p class="text-sm text-zinc-400">관심종목이 없습니다. 위에서 종목을 검색해 추가해보세요.</p>';
    genBtn.disabled = true;
    return;
  }
  chipWrap.innerHTML = watchlist.map((w) => `
    <span class="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3.5 py-1.5 text-sm font-semibold text-emerald-700">
      ${w.symbol}
      <button type="button" data-symbol="${w.symbol}" class="remove-watchlist-btn text-emerald-500 hover:text-emerald-800">
        <iconify-icon icon="solar:close-circle-bold"></iconify-icon>
      </button>
    </span>`).join('');
  chipWrap.querySelectorAll('.remove-watchlist-btn').forEach((btn) => {
    btn.addEventListener('click', () => removeSymbol(btn.dataset.symbol));
  });
  genBtn.disabled = false;
}

async function loadWatchlist() {
  try {
    const res = await api.getWatchlist();
    watchlist = res.ok ? await res.json() : [];
  } catch (e) {
    watchlist = [];
  }
  renderChips();
}

// ── 브리핑 생성/렌더링 (job 폴링) ────────────────────────────────────────

let briefingPollTimer = null;
const BRIEFING_POLL_TIMEOUT_MS = 2 * 60 * 1000;
const BRIEFING_POLL_INTERVAL_MS = 2000;

function setBriefingStatus(message, isError = false) {
  resultWrap.innerHTML = `<p class="py-10 text-center text-sm ${isError ? 'text-red-500' : 'text-zinc-400'}">${message}</p>`;
}

function renderBriefingJobResult(job) {
  const narrative = job.llmNarrative
    ? String(job.llmNarrative).replace(/</g, '&lt;').replace(/>/g, '&gt;')
    : '(서술 없음)';
  resultWrap.innerHTML = `
    <div class="rounded-2xl border border-emerald-100 bg-white p-4 text-sm leading-relaxed text-zinc-700">
      <p class="mb-1.5 text-xs font-semibold text-emerald-600">AI 브리핑</p>
      <p class="keep-all">${narrative}</p>
    </div>
    <p class="text-[11px] text-zinc-400">대상 종목 ${job.symbols || ''} · 기준 시각 ${formatPublished(job.updatedAt) || '-'}</p>`;
}

async function pollBriefingJob(jobId, startedAt) {
  try {
    const res = await api.getBriefingJob(jobId);
    if (!res.ok) throw new Error(`status ${res.status}`);
    const job = await res.json();
    if (job.status === 'COMPLETED') {
      renderBriefingJobResult(job);
      await loadHistory();
      genBtn.disabled = watchlist.length === 0;
      return;
    }
    if (job.status === 'FAILED') {
      setBriefingStatus(job.errorMessage || '브리핑 생성에 실패했습니다.', true);
      genBtn.disabled = watchlist.length === 0;
      return;
    }
    setBriefingStatus(job.status === 'PROCESSING'
      ? '관심종목 브리핑을 생성하는 중입니다… (최대 1분 정도 걸릴 수 있어요)'
      : '브리핑 요청을 처리하고 있어요…');
    if (Date.now() - startedAt < BRIEFING_POLL_TIMEOUT_MS) {
      briefingPollTimer = setTimeout(() => pollBriefingJob(jobId, startedAt), BRIEFING_POLL_INTERVAL_MS);
    } else {
      setBriefingStatus('처리가 오래 걸리고 있어요. 잠시 후 다시 확인해 주세요.', true);
      genBtn.disabled = watchlist.length === 0;
    }
  } catch (error) {
    setBriefingStatus('브리핑 상태를 확인하지 못했습니다.', true);
    genBtn.disabled = watchlist.length === 0;
  }
}

genBtn.addEventListener('click', async () => {
  if (!layout.requireLogin()) return;
  if (briefingPollTimer) clearTimeout(briefingPollTimer);
  genBtn.disabled = true;
  setBriefingStatus('브리핑 요청을 보내고 있어요…');
  try {
    const response = await api.requestBriefingJob();
    const data = await response.json().catch(() => null);
    if (!response.ok || !data) throw new Error((data && data.message) || 'briefing request failed');
    await pollBriefingJob(data.jobId, Date.now());
  } catch (error) {
    const message = error && error.message === '관심종목이 없습니다'
      ? '관심종목이 없습니다. 먼저 종목을 추가해주세요.'
      : '브리핑 생성에 실패했습니다.';
    setBriefingStatus(message, true);
    genBtn.disabled = watchlist.length === 0;
  }
});

// ── 브리핑 이력 ──────────────────────────────────────────────────────────

function renderHistoryItem(h) {
  const narrative = h.llmNarrative
    ? String(h.llmNarrative).replace(/</g, '&lt;').replace(/>/g, '&gt;')
    : '(서술 없음)';
  return `
    <div class="rounded-2xl border border-zinc-200 bg-zinc-50 p-4">
      <div class="flex items-center justify-between text-xs text-zinc-400 mb-1.5">
        <span>${h.symbols || ''}</span>
        <span>${formatPublished(h.generatedAt) || ''}</span>
      </div>
      <p class="keep-all text-sm text-zinc-600 leading-relaxed">${narrative}</p>
    </div>`;
}

async function loadHistory() {
  if (!layout.getUser()) {
    historyWrap.innerHTML = '<p class="text-sm text-zinc-400">로그인 후 브리핑 이력을 확인할 수 있어요.</p>';
    return;
  }
  historyWrap.innerHTML = '<p class="text-sm text-zinc-400">불러오는 중…</p>';
  try {
    const res = await api.getBriefingHistory();
    const data = res.ok ? await res.json() : [];
    historyWrap.innerHTML = data.length
      ? data.map(renderHistoryItem).join('')
      : '<p class="text-sm text-zinc-400">아직 생성한 브리핑이 없어요.</p>';
  } catch (e) {
    historyWrap.innerHTML = '<p class="text-sm text-red-500">이력을 불러오지 못했어요.</p>';
  }
}

// ── 초기화 ───────────────────────────────────────────────────────────────

resultWrap.innerHTML = '<p class="rounded-2xl border border-dashed border-zinc-200 py-16 text-center text-sm text-zinc-400">관심종목을 담고 브리핑을 생성해보세요.</p>';
chipWrap.innerHTML = '<p class="text-sm text-zinc-400">불러오는 중…</p>';
genBtn.disabled = true;

layout.onUserChange((user) => {
  if (user) {
    loadWatchlist();
  } else {
    watchlist = [];
    renderChips();
  }
  loadHistory();
});

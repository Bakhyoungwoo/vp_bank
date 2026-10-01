const { api, initLayout } = window.VAP;

initLayout({ active: 'market' });

const input = document.getElementById('searchInput');
const resultsWrap = document.getElementById('searchResults');
let debounceId = null;

function stockRowHTML(stock) {
  return `
    <a href="stock-detail.html?symbol=${encodeURIComponent(stock.symbol)}" class="block rounded-2xl border border-zinc-200 bg-white p-4 hover:border-emerald-200 hover:shadow-sm transition-all">
      <div class="flex items-center justify-between gap-3">
        <div class="min-w-0">
          <div class="flex items-center gap-2">
            <span class="rounded-full bg-zinc-100 px-2 py-0.5 text-[11px] font-semibold text-zinc-500">${stock.exchange || stock.market || '-'}</span>
            <span class="truncate font-semibold text-zinc-900">${stock.name || stock.symbol}</span>
          </div>
          <span class="mt-1 block text-xs text-zinc-400">${stock.symbol} · ${stock.type || 'EQUITY'}</span>
        </div>
        <span class="shrink-0 text-sm font-semibold text-emerald-600">상세 보기 →</span>
      </div>
    </a>`;
}

async function search(query) {
  const value = query.trim();
  if (!value) {
    resultsWrap.innerHTML = '';
    return;
  }
  resultsWrap.innerHTML = '<p class="py-10 text-center text-sm text-zinc-400">종목을 검색하는 중입니다…</p>';
  try {
    const response = await api.searchStocks(value, 10);
    const data = response.ok ? await response.json() : { items: [] };
    const items = Array.isArray(data.items) ? data.items : [];
    resultsWrap.innerHTML = items.length
      ? items.map(stockRowHTML).join('')
      : '<p class="py-10 text-center text-sm text-zinc-400">검색 결과가 없습니다.</p>';
  } catch (error) {
    resultsWrap.innerHTML = '<p class="py-10 text-center text-sm text-red-500">종목 검색에 실패했습니다.</p>';
  }
}

input.addEventListener('input', () => {
  clearTimeout(debounceId);
  debounceId = setTimeout(() => search(input.value), 350);
});

const initialQuery = new URLSearchParams(location.search).get('query');
if (initialQuery) {
  input.value = initialQuery;
  search(initialQuery);
}

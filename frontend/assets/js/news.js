const { api, CATEGORIES, formatPublished, parseNewsKeywords, initLayout, openAuthModal } = window.VAP;

const layout = initLayout({ active: 'news' });

const searchInput = document.getElementById('searchInput');
const tabsWrap = document.getElementById('categoryTabs');
const statusWrap = document.getElementById('newsStatus');
const listWrap = document.getElementById('newsList');

const params = new URLSearchParams(location.search);
let mode = params.get('q') ? 'search' : 'category';
let currentCategory = params.get('category') || 'it';
let currentQuery = params.get('q') || '';
let debounceId = null;

function categoryBtnClass(active) {
  return 'shrink-0 rounded-full px-3.5 py-1.5 text-xs font-semibold transition-colors ' +
    (active ? 'bg-emerald-500 text-white' : 'bg-zinc-100 text-zinc-500 hover:text-zinc-800');
}

function renderTabs() {
  tabsWrap.innerHTML = '';
  CATEGORIES.forEach((cat) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = cat.label;
    btn.className = categoryBtnClass(mode === 'category' && cat.code === currentCategory);
    btn.addEventListener('click', () => selectCategory(cat.code));
    tabsWrap.appendChild(btn);
  });
}

function syncUrl() {
  const next = new URLSearchParams();
  if (mode === 'search' && currentQuery) next.set('q', currentQuery);
  else next.set('category', currentCategory);
  history.replaceState(null, '', `news.html?${next.toString()}`);
}

function setStatus(message, isError = false) {
  statusWrap.innerHTML = message
    ? `<p class="keep-all py-10 text-center text-sm ${isError ? 'text-red-500' : 'text-zinc-400'}">${message}</p>`
    : '';
}

function recordClick(item) {
  const kws = parseNewsKeywords(item.keywords);
  if (kws.length === 0) return;
  api.recordNewsClick(kws).catch(() => {});
}

async function toggleBookmark(item, iconEl) {
  if (!layout.requireLogin()) return;
  try {
    const res = await api.toggleBookmark({ url: item.url, title: item.title, press: item.press, time: item.publishedAt });
    const msg = await res.text();
    iconEl.setAttribute('icon', msg.includes('저장') ? 'solar:bookmark-bold' : 'solar:bookmark-linear');
  } catch (e) {
    console.error('[bookmark] failed', e);
  }
}

function newsCard(item) {
  const keywords = parseNewsKeywords(item.keywords);
  const chips = keywords.slice(0, 5).map((kw) =>
    `<a href="news.html?q=${encodeURIComponent(kw)}" class="rounded-full bg-emerald-50 border border-emerald-200 px-2.5 py-1 text-[11px] text-emerald-700 hover:bg-emerald-100">#${kw}</a>`
  ).join('');

  const card = document.createElement('div');
  card.className = 'rounded-3xl border border-zinc-200 bg-white p-6 flex flex-col justify-between gap-4 hover:shadow-md transition-shadow';
  card.innerHTML = `
    <div>
      <div class="flex items-center justify-between text-xs text-zinc-400 mb-3">
        <span>${item.press || '출처 미상'}</span>
        <span>${formatPublished(item.publishedAt)}</span>
      </div>
      <a href="${item.url || '#'}" target="_blank" rel="noopener" class="keep-all font-semibold text-zinc-900 leading-snug hover:text-emerald-600 transition-colors">${item.title || '(제목 없음)'}</a>
      ${chips ? `<div class="flex flex-wrap gap-1.5 mt-3">${chips}</div>` : ''}
    </div>
    <div class="flex items-center justify-between">
      <a href="${item.url || '#'}" target="_blank" rel="noopener" class="inline-flex items-center gap-1.5 text-xs text-zinc-500 hover:text-zinc-900">
        <iconify-icon icon="solar:link-linear"></iconify-icon>
        <span>원문 보기</span>
      </a>
      <button type="button" class="bookmark-btn flex h-9 w-9 items-center justify-center rounded-full border border-zinc-200 text-zinc-400 hover:text-emerald-600 hover:border-emerald-300">
        <iconify-icon icon="solar:bookmark-linear"></iconify-icon>
      </button>
    </div>`;

  card.querySelectorAll('a[target="_blank"]').forEach((link) => {
    link.addEventListener('click', () => recordClick(item));
  });
  const bmBtn = card.querySelector('.bookmark-btn');
  bmBtn.addEventListener('click', () => toggleBookmark(item, bmBtn.querySelector('iconify-icon')));

  return card;
}

function renderList(items) {
  listWrap.innerHTML = '';
  if (!items || items.length === 0) {
    setStatus(mode === 'search' ? `"${currentQuery}"에 대한 검색 결과가 없어요.` : '아직 수집된 기사가 없어요.');
    return;
  }
  setStatus('');
  items.forEach((item) => listWrap.appendChild(newsCard(item)));
}

async function loadCategory(category) {
  mode = 'category';
  currentCategory = category;
  currentQuery = '';
  searchInput.value = '';
  renderTabs();
  syncUrl();
  listWrap.innerHTML = '';
  setStatus('불러오는 중…');
  try {
    const res = await api.getNewsByCategory(category);
    renderList(res.ok ? await res.json() : []);
  } catch (e) {
    setStatus('뉴스를 불러오지 못했어요.', true);
  }
}

async function runSearch(query) {
  const value = query.trim();
  if (!value) return;
  mode = 'search';
  currentQuery = value;
  renderTabs();
  syncUrl();
  listWrap.innerHTML = '';
  setStatus('검색하는 중…');
  try {
    const res = await api.searchNews(value);
    renderList(res.ok ? await res.json() : []);
  } catch (e) {
    setStatus('검색에 실패했어요.', true);
  }
}

function selectCategory(code) {
  loadCategory(code);
}

function loadCurrent() {
  if (mode === 'search' && currentQuery) runSearch(currentQuery);
  else loadCategory(currentCategory);
}

searchInput.addEventListener('input', () => {
  clearTimeout(debounceId);
  const value = searchInput.value;
  if (!value.trim()) {
    clearTimeout(debounceId);
    loadCategory(currentCategory);
    return;
  }
  debounceId = setTimeout(() => runSearch(value), 350);
});

if (currentQuery) searchInput.value = currentQuery;
renderTabs();

layout.onUserChange((user) => {
  if (!user) {
    listWrap.innerHTML = '';
    statusWrap.innerHTML = `
      <div class="keep-all py-16 text-center">
        <p class="text-sm text-zinc-400 mb-4">로그인하면 뉴스를 볼 수 있어요.</p>
        <button id="newsLoginBtn" type="button" class="cta-magnetic rounded-full bg-emerald-500 text-white text-sm font-semibold px-6 py-2.5 hover:bg-emerald-600">로그인</button>
      </div>`;
    document.getElementById('newsLoginBtn').addEventListener('click', () => openAuthModal('login'));
    return;
  }
  loadCurrent();
});

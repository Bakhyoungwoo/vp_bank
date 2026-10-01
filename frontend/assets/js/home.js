const { api, CATEGORIES, formatMarketNumber, formatPublished, initLayout, openAuthModal, initReveal } = window.VAP;

const layout = initLayout({ active: 'home' });

let currentCategory = 'it';
let crawlPollTimer = null;

function categoryBtnClass(active) {
  return 'shrink-0 rounded-full px-3.5 py-1.5 text-xs font-semibold transition-colors ' +
    (active ? 'bg-emerald-500 text-white' : 'bg-zinc-100 text-zinc-500 hover:text-zinc-800');
}

function renderCategoryTabs() {
  const wrap = document.getElementById('categoryTabs');
  wrap.innerHTML = '';
  CATEGORIES.forEach((cat) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = cat.label;
    btn.className = categoryBtnClass(cat.code === currentCategory);
    btn.addEventListener('click', () => {
      if (currentCategory === cat.code) return;
      currentCategory = cat.code;
      renderCategoryTabs();
      loadKeywords();
    });
    wrap.appendChild(btn);
  });
}

function renderKeywordList(items) {
  const ul = document.getElementById('keywordList');
  ul.innerHTML = '';
  if (!items || items.length === 0) {
    ul.innerHTML = `
      <li class="keep-all flex flex-col items-center justify-center gap-1 rounded-xl bg-zinc-50 px-4 py-8 text-center min-h-[220px]">
        <p class="text-sm text-zinc-500">아직 뜨는 키워드가 없어요.</p>
        <p class="text-xs text-zinc-400">잠시 후 다시 확인해보세요.</p>
      </li>`;
    return;
  }
  items.forEach((item, idx) => {
    const li = document.createElement('li');
    li.innerHTML = `
      <a href="news.html?q=${encodeURIComponent(item.keyword)}" class="flex items-center justify-between rounded-xl bg-zinc-50 px-4 py-3 hover:bg-emerald-50 transition-colors">
        <span class="flex items-center gap-3">
          <span class="font-display text-emerald-500 font-bold w-5">${idx + 1}</span>
          <span class="text-zinc-800">${item.keyword}</span>
        </span>
        <span class="text-xs text-emerald-600 font-display">언급 ${item.score}회</span>
      </a>`;
    ul.appendChild(li);
  });
}

async function loadKeywords() {
  try {
    const res = await api.getKeywords(currentCategory);
    const data = res.ok ? await res.json() : [];
    renderKeywordList(data);
  } catch (e) {
    renderKeywordList([]);
  }
}

function setCrawlStatus(message, isError = false) {
  const status = document.getElementById('crawlStatus');
  if (!status) return;
  status.textContent = message;
  status.className = `mt-6 text-center text-xs ${isError ? 'text-red-500' : 'text-zinc-400'}`;
}

async function pollCrawlJob(jobId) {
  const startedAt = Date.now();
  const poll = async () => {
    try {
      const res = await api.getNewsCrawlJob(jobId);
      if (!res.ok) throw new Error(`status ${res.status}`);
      const job = await res.json();
      if (job.status === 'COMPLETED') {
        setCrawlStatus('뉴스 업데이트가 완료됐어요.');
        await loadKeywords();
        await loadRecommend();
        return;
      }
      if (job.status === 'FAILED') {
        setCrawlStatus(job.message || '뉴스 업데이트에 실패했어요.', true);
        return;
      }
      setCrawlStatus(job.status === 'PROCESSING' ? '뉴스를 업데이트하고 있어요…' : '업데이트 요청을 처리하고 있어요…');
      if (Date.now() - startedAt < 5 * 60 * 1000) crawlPollTimer = setTimeout(poll, 2000);
      else setCrawlStatus('처리가 오래 걸리고 있어요. 잠시 후 다시 확인해 주세요.', true);
    } catch (e) {
      setCrawlStatus('업데이트 상태를 확인하지 못했어요.', true);
    }
  };
  await poll();
}

async function requestCrawl() {
  const btn = document.getElementById('crawlBtn');
  if (crawlPollTimer) clearTimeout(crawlPollTimer);
  btn.disabled = true;
  setCrawlStatus('업데이트 요청을 보내고 있어요…');
  try {
    const res = await api.requestNewsCrawl(currentCategory);
    if (!res.ok) throw new Error(`request ${res.status}`);
    const { jobId } = await res.json();
    await pollCrawlJob(jobId);
  } catch (e) {
    setCrawlStatus('업데이트 요청에 실패했어요.', true);
  } finally {
    btn.disabled = false;
  }
}

function renderMarket(items) {
  const wrap = document.getElementById('marketList');
  wrap.innerHTML = '';
  if (!items || items.length === 0) {
    wrap.innerHTML = '<div class="sm:col-span-2 lg:col-span-5 rounded-2xl bg-zinc-50 p-8 text-center text-sm text-zinc-400">현재 시세를 불러오지 못했어요.</div>';
    return;
  }
  items.forEach((item) => {
    const changeValue = Number(item.change || 0);
    const changePercent = Number(item.changePercent || 0);
    const changeCls = changeValue > 0 ? 'text-red-500' : changeValue < 0 ? 'text-blue-500' : 'text-zinc-400';
    const arrow = changeValue > 0 ? '▲' : changeValue < 0 ? '▼' : '−';
    const card = document.createElement('div');
    card.className = 'rounded-2xl border border-zinc-200 bg-white p-5 hover:border-emerald-200 hover:shadow-sm transition-all';
    card.innerHTML = `
      <p class="text-sm font-semibold text-zinc-700">${item.name}<span class="ml-1 text-[11px] font-normal text-zinc-400">${item.unit || ''}</span></p>
      <p class="mt-4 font-display text-2xl font-bold text-zinc-900">${item.available ? formatMarketNumber(item.price) : '-'}</p>
      <p class="mt-1 text-sm font-semibold ${changeCls}">${
        item.available
          ? `${arrow} ${formatMarketNumber(Math.abs(changeValue))} (${changePercent >= 0 ? '+' : ''}${changePercent.toFixed(2)}%)`
          : '조회 불가'
      }</p>`;
    wrap.appendChild(card);
  });
}

async function loadMarket() {
  try {
    const res = await api.getMarketOverview();
    const data = res.ok ? await res.json() : { items: [] };
    renderMarket(data.items || []);
  } catch (e) {
    renderMarket([]);
  }
}

function renderRecommendList(items) {
  const wrap = document.getElementById('recommendList');
  wrap.innerHTML = '';
  if (!items || items.length === 0) {
    wrap.innerHTML = '<div class="keep-all rounded-3xl border border-zinc-200 bg-white p-7 text-sm text-zinc-400 md:col-span-3 text-center py-16">아직 준비된 추천 기사가 없어요. 잠시 후 다시 확인해주세요.</div>';
    return;
  }
  items.forEach((item) => {
    const card = document.createElement('div');
    card.className = 'rounded-3xl border border-zinc-200 bg-white p-6 flex flex-col justify-between gap-4 hover:shadow-md transition-shadow';

    const chips = Array.isArray(item.matchedKeywords) && item.matchedKeywords.length > 0
      ? `<div class="flex flex-wrap gap-1.5 mt-3">${item.matchedKeywords.slice(0, 4).map((kw) => `<span class="rounded-full bg-emerald-50 border border-emerald-200 px-2.5 py-1 text-[11px] text-emerald-700">#${kw}</span>`).join('')}</div>`
      : '';

    card.innerHTML = `
      <div>
        <div class="flex items-center justify-between text-xs text-zinc-400 mb-3">
          <span>${item.press || '출처 미상'}</span>
          <span>${formatPublished(item.time)}</span>
        </div>
        <a href="${item.url || '#'}" target="_blank" rel="noopener" class="keep-all font-semibold text-zinc-900 leading-snug hover:text-emerald-600 transition-colors">${item.title || '(제목 없음)'}</a>
        ${chips}
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

    const bmBtn = card.querySelector('.bookmark-btn');
    bmBtn.addEventListener('click', () => toggleBookmark(item, bmBtn.querySelector('iconify-icon')));

    card.querySelectorAll('a[target="_blank"]').forEach((link) => {
      link.addEventListener('click', () => recordClick(item));
    });

    wrap.appendChild(card);
  });
}

async function loadRecommend() {
  const wrap = document.getElementById('recommendList');
  wrap.innerHTML = '<div class="rounded-3xl border border-zinc-200 bg-white p-7 text-sm text-zinc-400 md:col-span-3 text-center py-16">불러오는 중…</div>';
  try {
    const res = await api.getRecommend();
    const data = res.ok ? await res.json() : [];
    renderRecommendList(data);
  } catch (e) {
    renderRecommendList([]);
  }
}

// 기사 클릭 시 키워드를 클릭 로그로 남겨 개인화 추천(/api/news/recommend)에 반영한다.
// 비로그인 상태에서는 401이 나는데, 원문 보기 자체는 막을 이유가 없으므로 조용히 무시한다.
function recordClick(item) {
  if (!Array.isArray(item.keywords) || item.keywords.length === 0) return;
  api.recordNewsClick(item.keywords).catch(() => {});
}

async function toggleBookmark(item, iconEl) {
  if (!layout.requireLogin()) return;
  try {
    const res = await api.toggleBookmark({ url: item.url, title: item.title, press: item.press, time: item.time });
    const msg = await res.text();
    iconEl.setAttribute('icon', msg.includes('저장') ? 'solar:bookmark-bold' : 'solar:bookmark-linear');
  } catch (e) {
    console.error('[bookmark] failed', e);
  }
}

function updateSignupButtons(user) {
  const hero = document.getElementById('heroSignupBtn');
  const cta = document.getElementById('ctaSignupBtn');
  if (hero) hero.hidden = !!user;
  if (cta) cta.hidden = !!user;
}

document.getElementById('heroSignupBtn').addEventListener('click', () => openAuthModal('signup'));
document.getElementById('ctaSignupBtn').addEventListener('click', () => openAuthModal('signup'));
document.getElementById('refreshRecommendBtn').addEventListener('click', loadRecommend);
document.getElementById('crawlBtn').addEventListener('click', requestCrawl);

renderCategoryTabs();
initReveal();

// 증시/키워드/추천은 로그인 후에만 조회 가능하므로, 로그인 상태가 확정(또는 변경)될 때마다 다시 불러온다.
// (여기서 없으면 로그인 전에 실패한 최초 요청이 로그인 후에도 재시도되지 않아 화면이 빈 채로 남는다.)
layout.onUserChange((user) => {
  updateSignupButtons(user);
  loadKeywords();
  loadRecommend();
  loadMarket();
});

setInterval(loadKeywords, 15000);
setInterval(loadMarket, 60000);

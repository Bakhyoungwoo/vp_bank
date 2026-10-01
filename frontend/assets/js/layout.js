(function () {
  const api = window.VAP.api;

  const NAV_LINKS = [
  { id: 'home', href: 'index.html', label: '홈' },
  { id: 'news', href: 'news.html', label: '뉴스' },
  { id: 'market', href: 'market.html', label: '증시' },
];

let currentUser = null;
const userListeners = [];

function notifyUserChange() {
  userListeners.forEach((cb) => cb(currentUser));
}

function headerTemplate(active) {
  const links = NAV_LINKS.map(
    (link) => `
      <a href="${link.href}" class="nav-link text-sm font-semibold px-3 py-2 rounded-full transition-colors ${
        link.id === active ? 'bg-emerald-50 text-emerald-700' : 'text-zinc-600 hover:text-zinc-900'
      }">${link.label}</a>`,
  ).join('');

  return `
    <header class="sticky top-0 z-40 border-b border-zinc-100 bg-white/90 backdrop-blur-sm">
      <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        <a href="index.html" class="flex items-center gap-2 shrink-0">
          <span class="flex h-9 w-9 items-center justify-center rounded-full bg-emerald-50">
            <iconify-icon icon="solar:cpu-bolt-bold" class="text-emerald-600 text-lg"></iconify-icon>
          </span>
          <span class="font-display font-bold text-lg tracking-tight">VAP</span>
        </a>
        <nav class="hidden sm:flex items-center gap-1">${links}</nav>
        <div class="flex items-center gap-2" id="authArea"></div>
      </div>
    </header>`;
}

function footerTemplate() {
  return `
    <footer class="border-t border-zinc-100 py-10">
      <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between gap-4">
        <div class="flex items-center gap-2">
          <span class="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-50">
            <iconify-icon icon="solar:cpu-bolt-bold" class="text-emerald-600 text-lg"></iconify-icon>
          </span>
          <span class="font-display font-bold">VAP</span>
          <span class="keep-all text-sm text-zinc-400 ml-2">— AI 뉴스 추천 서비스</span>
        </div>
        <p class="text-xs text-zinc-400">© 2026 VAP. All rights reserved.</p>
      </div>
    </footer>`;
}

function authModalTemplate() {
  return `
    <div id="authModal" class="hidden fixed inset-0 z-50 flex items-center justify-center bg-zinc-900/40 backdrop-blur-sm px-4">
      <div class="relative w-full max-w-md rounded-3xl border border-zinc-200 bg-white p-6 md:p-8 shadow-2xl">
        <button id="authModalClose" class="absolute top-4 right-4 flex h-9 w-9 items-center justify-center rounded-full text-zinc-400 hover:text-zinc-700 hover:bg-zinc-100">
          <iconify-icon icon="solar:close-circle-linear" class="text-xl"></iconify-icon>
        </button>

        <div class="flex items-center gap-2 mb-6">
          <button id="authTabLogin" class="auth-tab flex-1 rounded-full px-4 py-2.5 text-sm font-semibold bg-emerald-500 text-white">로그인</button>
          <button id="authTabSignup" class="auth-tab flex-1 rounded-full px-4 py-2.5 text-sm font-semibold text-zinc-500 border border-zinc-200">회원가입</button>
        </div>

        <p id="authError" class="hidden keep-all mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"></p>

        <form id="loginForm" class="space-y-4">
          <div>
            <label class="text-xs text-zinc-500">이메일</label>
            <input type="email" name="email" required class="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm text-zinc-900 outline-none focus:border-emerald-400 focus:bg-white" placeholder="you@example.com">
          </div>
          <div>
            <label class="text-xs text-zinc-500">비밀번호</label>
            <input type="password" name="password" required class="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm text-zinc-900 outline-none focus:border-emerald-400 focus:bg-white" placeholder="8자 이상">
          </div>
          <button type="submit" class="cta-magnetic w-full rounded-xl bg-emerald-500 text-white font-semibold text-base px-6 py-3.5 min-h-[48px] hover:bg-emerald-600">로그인</button>
        </form>

        <form id="signupForm" class="hidden space-y-4">
          <div>
            <label class="text-xs text-zinc-500">이메일</label>
            <input type="email" name="email" required class="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm text-zinc-900 outline-none focus:border-emerald-400 focus:bg-white" placeholder="you@example.com">
          </div>
          <div>
            <label class="text-xs text-zinc-500">비밀번호 (8자 이상)</label>
            <input type="password" name="password" required minlength="8" class="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm text-zinc-900 outline-none focus:border-emerald-400 focus:bg-white" placeholder="8자 이상">
          </div>
          <div>
            <label class="text-xs text-zinc-500">닉네임</label>
            <input type="text" name="name" class="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm text-zinc-900 outline-none focus:border-emerald-400 focus:bg-white" placeholder="하윤서">
          </div>
          <div>
            <label class="text-xs text-zinc-500">관심 카테고리</label>
            <select name="interestCategory" class="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm text-zinc-900 outline-none focus:border-emerald-400 focus:bg-white">
              <option value="it">IT</option>
              <option value="economy">경제</option>
              <option value="society">사회</option>
              <option value="politics">정치</option>
              <option value="world">세계</option>
              <option value="culture">문화</option>
            </select>
          </div>
          <button type="submit" class="cta-magnetic w-full rounded-xl bg-emerald-500 text-white font-semibold text-base px-6 py-3.5 min-h-[48px] hover:bg-emerald-600">회원가입</button>
        </form>
      </div>
    </div>`;
}

function bookmarksModalTemplate() {
  return `
    <div id="bookmarksModal" class="hidden fixed inset-0 z-50 flex items-center justify-center bg-zinc-900/40 backdrop-blur-sm px-4">
      <div class="relative w-full max-w-lg max-h-[80vh] flex flex-col rounded-3xl border border-zinc-200 bg-white p-6 md:p-8 shadow-2xl">
        <button id="bookmarksModalClose" class="absolute top-4 right-4 flex h-9 w-9 items-center justify-center rounded-full text-zinc-400 hover:text-zinc-700 hover:bg-zinc-100">
          <iconify-icon icon="solar:close-circle-linear" class="text-xl"></iconify-icon>
        </button>
        <h3 class="text-lg font-bold mb-1">내 북마크</h3>
        <p class="text-sm text-zinc-500 mb-5">저장한 기사를 다시 확인해보세요.</p>
        <div id="bookmarksList" class="space-y-2 overflow-y-auto"></div>
      </div>
    </div>`;
}

function setAuthAreaHTML(container, user) {
  if (!container) return;
  container.innerHTML = '';
  if (user) {
    const bmBtn = document.createElement('button');
    bmBtn.type = 'button';
    bmBtn.className = 'hidden sm:inline-flex items-center gap-1.5 rounded-full border border-zinc-200 px-4 py-2 text-sm text-zinc-600 hover:text-zinc-900 hover:bg-zinc-50';
    bmBtn.textContent = '내 북마크';
    bmBtn.addEventListener('click', openBookmarksModal);

    const nameChip = document.createElement('span');
    nameChip.className = 'hidden sm:inline text-sm text-zinc-500';
    nameChip.textContent = user.nickname + '님';

    const logoutBtn = document.createElement('button');
    logoutBtn.type = 'button';
    logoutBtn.className = 'cta-magnetic inline-flex items-center gap-1.5 rounded-full bg-emerald-500 text-white text-sm font-semibold px-5 py-2.5 hover:bg-emerald-600';
    logoutBtn.textContent = '로그아웃';
    logoutBtn.addEventListener('click', doLogout);

    container.appendChild(bmBtn);
    container.appendChild(nameChip);
    container.appendChild(logoutBtn);
  } else {
    const loginBtn = document.createElement('button');
    loginBtn.type = 'button';
    loginBtn.className = 'cta-magnetic inline-flex items-center gap-1.5 rounded-full bg-emerald-500 text-white text-sm font-semibold px-5 py-2.5 hover:bg-emerald-600';
    loginBtn.textContent = '로그인';
    loginBtn.addEventListener('click', () => openAuthModal('login'));
    container.appendChild(loginBtn);
  }
}

function renderAuthUI() {
  setAuthAreaHTML(document.getElementById('authArea'), currentUser);
}

async function checkAuth() {
  try {
    const res = await api.getMe();
    currentUser = res.ok ? await res.json() : null;
  } catch (e) {
    currentUser = null;
  }
  renderAuthUI();
  notifyUserChange();
}

async function doLogout() {
  try {
    await api.logout();
  } catch (e) {}
  currentUser = null;
  renderAuthUI();
  notifyUserChange();
}

function openAuthModal(tab) {
  document.getElementById('authModal').classList.remove('hidden');
  switchAuthTab(tab || 'login');
}
function closeAuthModal() {
  document.getElementById('authModal').classList.add('hidden');
}
function switchAuthTab(tab) {
  const loginForm = document.getElementById('loginForm');
  const signupForm = document.getElementById('signupForm');
  const tabLogin = document.getElementById('authTabLogin');
  const tabSignup = document.getElementById('authTabSignup');
  document.getElementById('authError').classList.add('hidden');
  const activeCls = 'auth-tab flex-1 rounded-full px-4 py-2.5 text-sm font-semibold bg-emerald-500 text-white';
  const inactiveCls = 'auth-tab flex-1 rounded-full px-4 py-2.5 text-sm font-semibold text-zinc-500 border border-zinc-200';
  if (tab === 'signup') {
    loginForm.classList.add('hidden');
    signupForm.classList.remove('hidden');
    tabSignup.className = activeCls;
    tabLogin.className = inactiveCls;
  } else {
    signupForm.classList.add('hidden');
    loginForm.classList.remove('hidden');
    tabLogin.className = activeCls;
    tabSignup.className = inactiveCls;
  }
}
function showAuthMessage(msg, type) {
  const el = document.getElementById('authError');
  el.textContent = msg;
  el.className = type === 'success'
    ? 'keep-all mb-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700'
    : 'keep-all mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600';
}

async function openBookmarksModal() {
  document.getElementById('bookmarksModal').classList.remove('hidden');
  const list = document.getElementById('bookmarksList');
  list.innerHTML = '<p class="text-sm text-zinc-400 text-center py-8">불러오는 중…</p>';
  try {
    const res = await api.getBookmarks();
    const data = res.ok ? await res.json() : [];
    list.innerHTML = '';
    if (data.length === 0) {
      list.innerHTML = '<p class="keep-all text-sm text-zinc-400 text-center py-8">북마크한 기사가 없어요. 추천 뉴스에서 북마크 아이콘을 눌러보세요.</p>';
      return;
    }
    data.forEach((bm) => {
      const row = document.createElement('div');
      row.className = 'flex items-center justify-between gap-3 rounded-xl bg-zinc-50 border border-zinc-200 px-4 py-3';
      const info = document.createElement('div');
      info.className = 'min-w-0';
      const a = document.createElement('a');
      a.href = bm.newsUrl;
      a.target = '_blank';
      a.rel = 'noopener';
      a.className = 'block truncate text-sm font-semibold text-zinc-900 hover:text-emerald-600';
      a.textContent = bm.title || bm.newsUrl;
      const meta = document.createElement('p');
      meta.className = 'text-xs text-zinc-400 mt-1';
      meta.textContent = [bm.press, bm.publishedAt].filter(Boolean).join(' · ');
      info.appendChild(a);
      info.appendChild(meta);

      const delBtn = document.createElement('button');
      delBtn.type = 'button';
      delBtn.className = 'shrink-0 flex h-8 w-8 items-center justify-center rounded-full border border-zinc-200 text-zinc-400 hover:text-red-500 hover:border-red-200';
      const delIcon = document.createElement('iconify-icon');
      delIcon.setAttribute('icon', 'solar:trash-bin-minimalistic-linear');
      delBtn.appendChild(delIcon);
      delBtn.addEventListener('click', async () => {
        await api.removeBookmark(bm.newsUrl);
        row.remove();
      });

      row.appendChild(info);
      row.appendChild(delBtn);
      list.appendChild(row);
    });
  } catch (e) {
    list.innerHTML = '<p class="text-sm text-red-500 text-center py-8">북마크를 불러오지 못했어요.</p>';
  }
}

function wireAuthModal() {
  document.getElementById('authModalClose').addEventListener('click', closeAuthModal);
  document.getElementById('authModal').addEventListener('click', (e) => {
    if (e.target.id === 'authModal') closeAuthModal();
  });
  document.getElementById('authTabLogin').addEventListener('click', () => switchAuthTab('login'));
  document.getElementById('authTabSignup').addEventListener('click', () => switchAuthTab('signup'));

  document.getElementById('loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      const res = await api.login(fd.get('email'), fd.get('password'));
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        showAuthMessage(err.message || '로그인에 실패했어요.', 'error');
        return;
      }
      closeAuthModal();
      await checkAuth();
    } catch (e) {
      showAuthMessage('서버에 연결할 수 없어요.', 'error');
    }
  });

  document.getElementById('signupForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      const res = await api.signup({
        email: fd.get('email'),
        password: fd.get('password'),
        name: fd.get('name'),
        interestCategory: fd.get('interestCategory'),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        showAuthMessage(err.message || '회원가입에 실패했어요.', 'error');
        return;
      }
      e.target.reset();
      switchAuthTab('login');
      showAuthMessage('회원가입이 완료되었어요. 로그인해주세요.', 'success');
    } catch (e) {
      showAuthMessage('서버에 연결할 수 없어요.', 'error');
    }
  });
}

function wireBookmarksModal() {
  document.getElementById('bookmarksModalClose').addEventListener('click', () => {
    document.getElementById('bookmarksModal').classList.add('hidden');
  });
  document.getElementById('bookmarksModal').addEventListener('click', (e) => {
    if (e.target.id === 'bookmarksModal') document.getElementById('bookmarksModal').classList.add('hidden');
  });
}

/**
 * 모든 페이지 상단/하단에서 공통으로 호출합니다.
 * <div id="app-header"></div> ... <div id="app-footer"></div> 컨테이너가 있어야 합니다.
 */
function initLayout({ active } = {}) {
  const headerMount = document.getElementById('app-header');
  const footerMount = document.getElementById('app-footer');
  if (headerMount) headerMount.outerHTML = headerTemplate(active);
  if (footerMount) footerMount.outerHTML = footerTemplate();

  document.body.insertAdjacentHTML('beforeend', authModalTemplate());
  document.body.insertAdjacentHTML('beforeend', bookmarksModalTemplate());

  wireAuthModal();
  wireBookmarksModal();
  checkAuth();

  return {
    getUser: () => currentUser,
    onUserChange: (cb) => userListeners.push(cb),
    requireLogin: () => {
      if (currentUser) return true;
      openAuthModal('login');
      return false;
    },
  };
}

  window.VAP = window.VAP || {};
  Object.assign(window.VAP, { initLayout, openAuthModal, openBookmarksModal, checkAuth });
})();

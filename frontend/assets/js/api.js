// vp_bank(Spring Boot) 백엔드는 같은 오리진에서 서빙되는 것을 전제로 상대 경로(/api/...)를 호출합니다.
// 일반 <script> 태그로 로드되는 파일이라 ES 모듈을 쓰지 않고, window.VAP 네임스페이스에 결과를 붙입니다.
// (file://로 직접 열어도 동작하게 하기 위함 — type="module"은 file:// 출처에서 CORS로 막힙니다.)
(function () {
  // 운영에서는 같은 오리진을 사용하고(vp_bank가 정적 파일도 서빙),
  // 로컬에서 정적 프론트만 별도 서버(포트 무관: 5500, 5501, 5173, 3000, Live Server 등)로 띄운
  // 개발 환경에서는 Spring Boot(8080)로 요청을 전달한다.
  const isLocalHost = ['localhost', '127.0.0.1'].includes(window.location.hostname);
  const isFrontDevServer = isLocalHost && window.location.port !== '8080';
  const API_BASE_URL = window.VAP_API_BASE_URL || (
    window.location.protocol === 'file:' || isFrontDevServer
      ? 'http://localhost:8080'
      : ''
  );
  const CATEGORIES = [
    { code: 'it', label: 'IT' },
    { code: 'economy', label: '경제' },
    { code: 'society', label: '사회' },
    { code: 'politics', label: '정치' },
    { code: 'world', label: '세계' },
    { code: 'culture', label: '문화' },
  ];

  function request(path, options = {}) {
    return fetch(`${API_BASE_URL}${path}`, {
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      ...options,
    });
  }

  const api = {
    getKeywords: (category) => request(`/api/news/keywords/${category}`),
    getMarketOverview: () => request('/api/market/overview'),
    getMarketHistory: (symbol, days = 30) =>
      request(`/api/market/history/${encodeURIComponent(symbol)}?days=${days}`),
    searchStocks: (query, limit = 10) =>
      request(`/api/stocks/search?query=${encodeURIComponent(query)}&limit=${limit}`),
    getStockDetail: (symbol, days = 30) =>
      request(`/api/stocks/${encodeURIComponent(symbol)}?days=${days}`),
    getStockFinancials: (symbol, limit = 5) =>
      request(`/api/stocks/${encodeURIComponent(symbol)}/financials?limit=${limit}`),
    getStockNews: (symbol, limit = 10) =>
      request(`/api/stocks/${encodeURIComponent(symbol)}/news?limit=${limit}`),
    analyzeStock: (symbol, days = 90) =>
      request(`/api/ai/stocks/${encodeURIComponent(symbol)}/analysis?days=${days}`, { method: 'POST' }),
    compareStocks: (symbols, days = 90) =>
      request(`/api/ai/stocks/compare?symbols=${encodeURIComponent(symbols.join(','))}&days=${days}`, { method: 'POST' }),
    analyzeNewsImpact: (symbol, limit = 10, days = 30) =>
      request(`/api/ai/stocks/${encodeURIComponent(symbol)}/news-impact?limit=${limit}&days=${days}`, { method: 'POST' }),
    getWatchlist: () => request('/api/watchlist'),
    addToWatchlist: (symbol) => request('/api/watchlist', { method: 'POST', body: JSON.stringify({ symbol }) }),
    removeFromWatchlist: (symbol) => request(`/api/watchlist/${encodeURIComponent(symbol)}`, { method: 'DELETE' }),
    getBriefingHistory: () => request('/api/ai/briefing/history'),
    requestBriefingJob: () => request('/api/ai/briefing/jobs', { method: 'POST' }),
    getBriefingJob: (jobId) => request(`/api/ai/briefing/jobs/${encodeURIComponent(jobId)}`),
    getEconomyNews: () => request('/api/news/economy'),
    getRecommend: () => request('/api/news/recommend'),
    recordNewsClick: (keywords) => request('/api/news/click', { method: 'POST', body: JSON.stringify({ keywords }) }),
    getNewsByCategory: (category) => request(`/api/news/${encodeURIComponent(category)}`),
    searchNews: (q) => request(`/api/news/search?q=${encodeURIComponent(q)}`),
    getMe: () => request('/api/users/me'),
    login: (email, password) =>
      request('/api/users/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
    signup: (input) => request('/api/users/signup', { method: 'POST', body: JSON.stringify(input) }),
    logout: () => request('/api/users/logout', { method: 'POST' }),
    getBookmarks: () => request('/api/bookmarks'),
    toggleBookmark: (item) => request('/api/bookmarks', { method: 'POST', body: JSON.stringify(item) }),
    removeBookmark: (url) => request('/api/bookmarks', { method: 'POST', body: JSON.stringify({ url }) }),
    saveSearchHistory: (keyword) => request('/api/search', { method: 'POST', body: JSON.stringify(keyword) }),
    getSearchHistory: () => request('/api/search/history'),
    deleteSearchHistory: () => request('/api/search/history', { method: 'DELETE' }),
    requestNewsCrawl: (category) => request(`/api/internal/news/crawl/async?category=${encodeURIComponent(category)}`, { method: 'POST' }),
    getNewsCrawlJob: (jobId) => request(`/api/internal/news/crawl/jobs/${encodeURIComponent(jobId)}`),
  };

  // /api/news/{category}, /api/news/search는 keywords를 JSON 배열을 문자열로 감싼 형태로 내려준다
  // (예: '["AI","교육"]'). /api/news/recommend는 이미 배열이라 이 함수를 거칠 필요 없다.
  function parseNewsKeywords(raw) {
    if (Array.isArray(raw)) return raw;
    try {
      const parsed = JSON.parse(raw || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
      return [];
    }
  }

  function formatPublished(t) {
    if (!t) return '';
    const d = new Date(t);
    if (isNaN(d.getTime())) return t;
    return d.toLocaleString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  function formatMarketNumber(value) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) return '-';
    return Number(value).toLocaleString('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  // 해외 종목(USD 등)의 현재가 옆에 원화 환산액을 같이 보여주기 위한 환율 조회.
  // 페이지당 한 번만 조회하도록 결과 프라미스를 캐시한다.
  let usdKrwRatePromise = null;
  function getUsdKrwRate() {
    if (!usdKrwRatePromise) {
      usdKrwRatePromise = api.getMarketOverview()
        .then((res) => (res.ok ? res.json() : { items: [] }))
        .then((data) => {
          const item = (data.items || []).find((i) => i.code === 'USD/KRW');
          const rate = item ? Number(item.price) : null;
          return Number.isFinite(rate) ? rate : null;
        })
        .catch(() => null);
    }
    return usdKrwRatePromise;
  }

  // currency가 KRW가 아니고 환율을 알 때만 "달러표시 (₩원화환산)" 형태로 덧붙인다.
  function formatPriceWithKrw(amount, currency, krwRate) {
    const n = Number(amount);
    if (!Number.isFinite(n)) return '-';
    const base = formatMarketNumber(n);
    if (!currency || currency === 'KRW' || typeof krwRate !== 'number' || !Number.isFinite(krwRate)) return base;
    const krw = Math.round(n * krwRate);
    return `${base} ${currency} (₩${krw.toLocaleString('ko-KR')})`;
  }

  window.VAP = window.VAP || {};
  Object.assign(window.VAP, {
    CATEGORIES, api, formatPublished, formatMarketNumber, formatPriceWithKrw, getUsdKrwRate, parseNewsKeywords, API_BASE_URL,
  });
})();

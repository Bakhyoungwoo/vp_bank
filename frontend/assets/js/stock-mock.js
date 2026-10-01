// TODO(backend): vp_bank에 종목 검색/상세/재무 API가 생기면 이 목업을 assets/js/api.js 호출로 교체한다.
// 화면 구조를 먼저 검증하기 위한 자리표시 데이터입니다.
(function () {
  const MOCK_STOCKS = [
    { symbol: '005930', name: '삼성전자', market: 'KOSPI', price: 84200, change: 900, changePercent: 1.08, marketCap: '502.3조', per: 14.2, pbr: 1.3, dividendYield: 2.1, sector: '반도체', ceo: '한종희', listedAt: '1975-06-11', description: '메모리·시스템 반도체와 스마트폰, 가전을 아우르는 국내 대표 종합 전자기업입니다.' },
    { symbol: '000660', name: 'SK하이닉스', market: 'KOSPI', price: 198500, change: -3500, changePercent: -1.73, marketCap: '144.5조', per: 9.8, pbr: 2.1, dividendYield: 0.7, sector: '반도체', ceo: '곽노정', listedAt: '1996-12-26', description: 'HBM과 D램·낸드플래시 등 메모리 반도체를 주력으로 하는 글로벌 반도체 기업입니다.' },
    { symbol: '035420', name: 'NAVER', market: 'KOSPI', price: 214000, change: 1500, changePercent: 0.71, marketCap: '35.1조', per: 21.4, pbr: 1.9, dividendYield: 0.5, sector: 'IT서비스', ceo: '최수연', listedAt: '2002-10-29', description: '검색·커머스·콘텐츠·핀테크 사업을 영위하는 국내 대표 인터넷 플랫폼 기업입니다.' },
    { symbol: '035720', name: '카카오', market: 'KOSPI', price: 41300, change: -450, changePercent: -1.08, marketCap: '18.3조', per: 32.1, pbr: 1.1, dividendYield: 0.2, sector: 'IT서비스', ceo: '정신아', listedAt: '2017-07-10', description: '메신저 카카오톡을 중심으로 커머스·콘텐츠·모빌리티 사업을 운영합니다.' },
    { symbol: '373220', name: 'LG에너지솔루션', market: 'KOSPI', price: 372000, change: 5000, changePercent: 1.36, marketCap: '87.0조', per: 45.6, pbr: 3.4, dividendYield: 0.1, sector: '2차전지', ceo: '김동명', listedAt: '2022-01-27', description: '전기차·ESS용 배터리를 생산하는 글로벌 2차전지 제조사입니다.' },
    { symbol: '247540', name: '에코프로비엠', market: 'KOSDAQ', price: 152300, change: 2100, changePercent: 1.40, marketCap: '13.9조', per: 38.2, pbr: 4.1, dividendYield: 0.0, sector: '2차전지 소재', ceo: '주재환', listedAt: '2019-03-27', description: '2차전지 양극재를 전문 생산하는 소재 기업입니다.' },
    { symbol: 'AAPL', name: 'Apple', market: 'NASDAQ', price: 231.4, change: 1.9, changePercent: 0.83, marketCap: '$3.52T', per: 34.8, pbr: 48.2, dividendYield: 0.4, sector: 'Consumer Electronics', ceo: 'Tim Cook', listedAt: '1980-12-12', description: '아이폰, 맥, 서비스 사업을 영위하는 글로벌 빅테크 기업입니다.' },
    { symbol: 'NVDA', name: 'NVIDIA', market: 'NASDAQ', price: 138.6, change: -2.3, changePercent: -1.63, marketCap: '$3.39T', per: 55.1, pbr: 42.9, dividendYield: 0.03, sector: 'Semiconductors', ceo: 'Jensen Huang', listedAt: '1999-01-22', description: 'AI 가속기와 GPU를 설계하는 반도체 및 컴퓨팅 플랫폼 기업입니다.' },
    { symbol: 'MSFT', name: 'Microsoft', market: 'NASDAQ', price: 425.8, change: 3.1, changePercent: 0.73, marketCap: '$3.16T', per: 36.2, pbr: 11.4, dividendYield: 0.7, sector: 'Software', ceo: 'Satya Nadella', listedAt: '1986-03-13', description: '클라우드(Azure), 오피스, 윈도우 등을 서비스하는 소프트웨어 기업입니다.' },
    { symbol: 'TSLA', name: 'Tesla', market: 'NASDAQ', price: 268.9, change: -6.4, changePercent: -2.32, marketCap: '$862B', per: 118.6, pbr: 14.7, dividendYield: 0.0, sector: 'Automobiles', ceo: 'Elon Musk', listedAt: '2010-06-29', description: '전기차와 에너지 저장, 자율주행 기술을 개발하는 기업입니다.' },
  ];

  function searchStocks(query) {
    const q = (query || '').trim().toLowerCase();
    if (!q) return [];
    return MOCK_STOCKS.filter((s) => s.name.toLowerCase().includes(q) || s.symbol.toLowerCase().includes(q));
  }

  function getStockBySymbol(symbol) {
    return MOCK_STOCKS.find((s) => s.symbol === symbol);
  }

  /** 목업 스파크라인용 상대값 배열 (0~1) */
  function mockSparkline(seed) {
    const points = [];
    let v = 0.5;
    for (let i = 0; i < 24; i += 1) {
      v += (Math.sin(seed + i * 1.7) + Math.sin(seed * 2 + i * 0.6)) * 0.05;
      v = Math.min(0.95, Math.max(0.08, v));
      points.push(v);
    }
    return points;
  }

  function seedFromSymbol(symbol) {
    let seed = 0;
    for (let i = 0; i < symbol.length; i += 1) seed += symbol.charCodeAt(i);
    return seed;
  }

  window.VAP = window.VAP || {};
  Object.assign(window.VAP, { MOCK_STOCKS, searchStocks, getStockBySymbol, mockSparkline, seedFromSymbol });
})();

const { initLayout, searchStocks } = window.VAP;

initLayout({ active: 'market' });

const log = document.getElementById('chatLog');
const form = document.getElementById('chatForm');
const input = document.getElementById('chatInput');

function addBubble(role, text) {
  const row = document.createElement('div');
  row.className = `flex ${role === 'user' ? 'justify-end' : 'justify-start'}`;
  const bubble = document.createElement('div');
  bubble.className = `chat-bubble keep-all rounded-2xl px-4 py-3 text-sm leading-relaxed ${
    role === 'user' ? 'bg-emerald-500 text-white' : 'bg-white border border-zinc-200 text-zinc-700'
  }`;
  bubble.textContent = text;
  row.appendChild(bubble);
  log.appendChild(row);
  log.scrollTop = log.scrollHeight;
  return bubble;
}

function canned(question) {
  const matches = searchStocks(question);
  if (matches.length > 0) {
    const s = matches[0];
    return `${s.name}(${s.symbol})의 현재가는 ${s.price.toLocaleString('ko-KR')}이고 전일 대비 ${s.changePercent >= 0 ? '+' : ''}${s.changePercent.toFixed(2)}%예요. 실제 AI 분석 엔진이 연동되면 더 자세한 답변을 드릴 수 있어요.`;
  }
  return '아직 실제 AI 응답 엔진이 연동되지 않았어요. 백엔드 연동 후에는 실시간 시세와 뉴스를 바탕으로 답변할 예정이에요.';
}

form.addEventListener('submit', (e) => {
  e.preventDefault();
  const question = input.value.trim();
  if (!question) return;
  addBubble('user', question);
  input.value = '';

  const typing = addBubble('assistant', '답변을 준비하고 있어요…');
  setTimeout(() => {
    typing.textContent = canned(question);
  }, 500);
});

addBubble('assistant', '안녕하세요! 궁금한 종목이나 시장 이슈를 물어보세요. (예: "삼성전자 어때?")');

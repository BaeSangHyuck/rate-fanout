// 배포한 API 주소. ?api=http://localhost:8080 으로 덮어쓸 수 있다
const API = new URLSearchParams(location.search).get('api') || window.API_BASE || 'http://localhost:8080';
const $ = id => document.getElementById(id);
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
function seg(id, onChange) {
  const btns = document.querySelectorAll(`#${id} button`);
  btns.forEach(b => b.onclick = () => { btns.forEach(x => x.classList.toggle('on', x === b)); onChange(b.dataset.v); });
}
// 요청 경과 시간을 보여 주고, 오래 걸리면 무료 서버가 깨어나는 중이라고 알린다
async function call(path, params) {
  const url = `${API}${path}?${new URLSearchParams(params)}`;
  if ($('reqUrl')) $('reqUrl').textContent = 'POST ' + url;
  const t0 = performance.now();
  const tick = setInterval(() => $('status').textContent = `요청 중 ${((performance.now() - t0) / 1000).toFixed(1)}초${performance.now() - t0 > 8000 ? ' · 무료 서버가 잠들어 있었다면 깨어나는 데 시간이 걸립니다' : ''}`, 100);
  try {
    const res = await fetch(url, { method: 'POST' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    $('status').textContent = '';
    return await res.json();
  } catch (e) {
    $('status').textContent = `API 호출 실패 (${e.message}). 서버가 깨어나는 중일 수 있으니 잠시 뒤 다시 눌러 주세요.`;
    throw e;
  } finally { clearInterval(tick); }
}

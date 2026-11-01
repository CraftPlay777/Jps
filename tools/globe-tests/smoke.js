// Prueba de humo: ejecuta globe.html completo con un DOM y un WebGL falsos para detectar errores de referencia o de tipos.
const vm = require('vm'), fs = require('fs'), path = require('path');
const ASSETS = path.join(__dirname, '../../app/src/main/assets');
const html = fs.readFileSync(path.join(ASSETS, 'globe.html'), 'utf8');
const js = /<script>([\s\S]*?)<\/script>/.exec(html)[1];
const colors = fs.readFileSync(path.join(ASSETS, 'country_colors.js'), 'utf8');

const logs = [], glCalls = {};
const listeners = {};
function glProxy() {
  const consts = { VERTEX_SHADER: 1, FRAGMENT_SHADER: 2, COMPILE_STATUS: 3, LINK_STATUS: 4, MAX_TEXTURE_SIZE: 5 };
  return new Proxy({}, { get(t, name) {
    if (name in consts) return consts[name];
    if (/^[A-Z0-9_]+$/.test(String(name))) return 1000 + String(name).length;
    return (...a) => {
      glCalls[name] = (glCalls[name] || 0) + 1;
      if (name === 'getParameter') return 4096;
      if (name === 'getShaderParameter' || name === 'getProgramParameter') return true;
      if (name === 'getAttribLocation') return 0;
      if (name === 'getUniformLocation') return {};
      if (name === 'createShader' || name === 'createProgram' || name === 'createBuffer' || name === 'createTexture') return {};
      return undefined;
    };
  }});
}
function ctx2d() {
  return new Proxy({}, { get(t, n) { if (n in t) return t[n]; return () => {}; }, set(t, n, v) { t[n] = v; return true; } });
}
function el(id) {
  const e = { id, style: {}, width: 0, height: 0, clientWidth: 400, clientHeight: 700, checked: false, disabled: false,
    value: '30', textContent: '',
    addEventListener(type, fn) { (listeners[id + ':' + type] = listeners[id + ':' + type] || []).push(fn); },
    getContext(kind) { return kind === '2d' ? ctx2d() : glProxy(); } };
  return e;
}
const els = {};
const spans = [0, 1, 2, 3].map(i => el('span' + i));
const document = { getElementById: id => els[id] || (els[id] = el(id)), createElement: () => el('canvas-x'), querySelectorAll: () => spans };
let frames = 0;
const timers = [];
const fetched = [];
const countriesJson = { features: [
  { properties: { NAME: 'Peru', ISO_A2_EH: 'PE', ISO_A3_EH: 'PER' }, geometry: { type: 'Polygon', coordinates: [[[-81,-18],[-69,-18],[-69,0],[-81,0],[-81,-18]]] } },
  { properties: { NAME: 'Argentina', ISO_A2_EH: 'AR', ISO_A3_EH: 'ARG' }, geometry: { type: 'Polygon', coordinates: [[[-73,-55],[-54,-55],[-54,-22],[-73,-22],[-73,-55]]] } } ] };
const statesJson = { features: [ { geometry: { type: 'Polygon', coordinates: [[[-70,-40],[-60,-40],[-60,-30],[-70,-30],[-70,-40]]] } } ] };
const sandbox = {
  document, console, Math, Float32Array, Uint8Array, Uint16Array, Object, parseInt, parseFloat, RegExp, Array, JSON, Promise,
  location: { hash: '#-12.04,-77.03', search: '?l=fr&b=1&s=1&c=1&f=1' },
  window: null,
  Image: function () { const self = this; setTimeout(() => self.onload && self.onload(), 0); },
  fetch: url => { fetched.push(url); return Promise.resolve({ ok: true, json: () => Promise.resolve(/states/.test(url) ? statesJson : countriesJson) }); },
  requestAnimationFrame: fn => { if (frames++ < 6) setTimeout(() => fn(frames * 20), 0); },
  setTimeout, clearTimeout, devicePixelRatio: 2,
};
sandbox.window = sandbox;
sandbox.window.JpsApp = undefined;
vm.createContext(sandbox);
vm.runInContext(colors, sandbox);
try { vm.runInContext(js, sandbox); } catch (e) { console.log('ERROR AL EJECUTAR init():', e.stack); process.exit(1); }

(async () => {
  await new Promise(r => setTimeout(r, 1500));
  const call = (k, ...a) => (listeners[k] || []).forEach(f => f(...a));
  // tacto: arrastrar, pellizcar y soltar
  const t = (x, y) => ({ clientX: x, clientY: y });
  call('c:touchstart', { preventDefault() {}, touches: [t(100, 100)] });
  call('c:touchmove', { preventDefault() {}, touches: [t(160, 130)] });
  call('c:touchstart', { preventDefault() {}, touches: [t(100, 100), t(200, 100)] });
  call('c:touchmove', { preventDefault() {}, touches: [t(80, 100), t(240, 100)] });
  call('c:touchend', { preventDefault() {}, touches: [] });
  // botones
  call('btnLayers:click'); call('btnSpin:click'); call('btnHome:click'); call('btnMode:click');
  // opciones: apagar y encender sin puente (se aplica directo)
  els.optB.checked = false; call('optB:change');
  els.optB.checked = true; call('optB:change');
  els.optS.checked = true; call('optS:change');
  els.optC.checked = false; call('optC:change');
  els.optC.checked = true; call('optC:change');
  els.optF.checked = true; call('optF:change');
  await new Promise(r => setTimeout(r, 1500));
  // con puente nativo: la opción solo se aplica cuando la app responde
  const sent = [];
  sandbox.window.JpsApp = { setOption: (k, v) => sent.push([k, v]) };
  els.optB.checked = false; call('optB:change');
  console.log('puente recibió:', JSON.stringify(sent));
  sandbox.window.jpsOptionResult('b', true); sandbox.window.jpsOptionResult('s', true);
  await new Promise(r => setTimeout(r, 400));
  console.log('peticiones:', fetched.length, [...new Set(fetched.map(u => u.replace(/\/[^/]*$/, '/…')))].join(' | '));
  const need = ['texImage2D','generateMipmap','drawElements','drawArrays','uniform1i','bufferData','pixelStorei'];
  console.log('gl usado:', need.map(n => n + '=' + (glCalls[n] || 0)).join(' '));
  const missing = need.filter(n => !glCalls[n]);
  // idioma: con ?l=fr los textos fijos salen en francés
  const okLang = els.btnLayers.textContent === 'Calques' && spans[0].textContent.indexOf('Frontières') === 0;
  if (!okLang) console.log('IDIOMA: los textos del globo no se tradujeron');
  console.log(missing.length ? 'FALTAN llamadas GL: ' + missing : 'PRUEBA DE HUMO OK');
  process.exit(missing.length || !okLang ? 1 : 0);
})();

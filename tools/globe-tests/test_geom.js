const g = require('./globe.js');
let fails = 0; const ok = (c, m) => { if (!c) { fails++; console.log("FALLA:", m); } else console.log("ok:", m); };
const st = g.buildStatic();
ok(g.N < 65536 && st.idx.length === 90*180*6, "malla cabe en índices de 16 bits");
const pos = new Float32Array(g.N*3), norm = new Float32Array(g.N*3);
g.buildGeometry(st, g.buildHeights(st, null), 0, pos, norm);
let minDot = 1, nan = false;
for (let n = 0; n < g.N; n++) { const d = norm[n*3]*st.unit[n*3]+norm[n*3+1]*st.unit[n*3+1]+norm[n*3+2]*st.unit[n*3+2]; minDot = Math.min(minDot, d); if (Number.isNaN(pos[n*3])||Number.isNaN(norm[n*3])) nan = true; }
ok(!nan && minDot > 0.999, "esfera lisa: sin NaN y normales hacia fuera (" + minDot.toFixed(5) + ")");
const size = 256, data = new Float32Array(size*size);
for (let i = 0; i < data.length; i++) data[i] = (i % 7) * 900 - 3000;
const h = g.buildHeights(st, g.makeElevSampler(data, size));
g.buildGeometry(st, h, 30, pos, norm);
let bad = false; for (let n = 0; n < g.N; n++) { const l = Math.hypot(norm[n*3], norm[n*3+1], norm[n*3+2]); if (Number.isNaN(l) || Math.abs(l-1) > 1e-4) bad = true; }
ok(!bad, "con relieve: normales unitarias y sin NaN");
const apply = (m, v) => [0,1,2].map(r => m[r]*v[0] + m[4+r]*v[1] + m[8+r]*v[2]);
for (const [lat, lon] of [[-12.04,-77.03],[40,116],[-34.6,-58.4]]) {
  const la = lat*Math.PI/180, lo = lon*Math.PI/180;
  const q = apply(g.mat4Mul(g.mat4RotX(la), g.mat4RotY(-lo)), [Math.cos(la)*Math.sin(lo), Math.sin(la), Math.cos(la)*Math.cos(lo)]);
  ok(Math.abs(q[0]) < 1e-5 && Math.abs(q[1]) < 1e-5 && Math.abs(q[2]-1) < 1e-5, `enfoque (${lat}, ${lon}) queda de frente`);
}
console.log(fails ? "FALLARON " + fails : "GEOMETRÍA OK"); process.exit(fails ? 1 : 0);

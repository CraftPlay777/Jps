const g = require('./globe.js');
let fails = 0;
function ok(c, m) { if (!c) { fails++; console.log("FALLA:", m); } else console.log("ok:", m); }

// GeoJSON sintético (estilo Natural Earth): un país con isla, otro con códigos -99, un hueco y la Antártida
const sq = (x0, y0, x1, y1) => [[x0,y0],[x1,y0],[x1,y1],[x0,y1],[x0,y0]];
const json = { type: "FeatureCollection", features: [
  { properties: { NAME: "Aland", ISO_A2_EH: "AA", ISO_A3_EH: "AAA" }, geometry: { type: "MultiPolygon", coordinates: [
      [ sq(-70, -40, -60, -30), sq(-68, -38, -66, -36) ],      // continente con un hueco (lago)
      [ sq(-50, -20, -49, -19) ] ] } },                         // isla pequeña
  { properties: { NAME: "Francia rara", ISO_A2: "-99", ISO_A2_EH: "FR", ISO_A3: "-99", ADM0_A3: "FRA" },
    geometry: { type: "Polygon", coordinates: [ sq(0, 40, 8, 50) ] } },
  { properties: { NAME: "Sin codigo", ISO_A2: "-99", ISO_A3: "-99" }, geometry: { type: "Polygon", coordinates: [ sq(20, 0, 25, 5) ] } },
  { properties: { NAME: "Antarctica", ISO_A3: "ATA" }, geometry: { type: "Polygon", coordinates: [ sq(-180, -89, 180, -70) ] } },
  { properties: { NAME: "Vacío" }, geometry: null },
]};
const list = g.parseCountries(json);
ok(list.length === 3, "parseCountries: 3 países (sin Antártida ni geometría vacía), obtuve " + list.length);
ok(list[0].iso2 === "AA" && list[0].iso3 === "AAA" && list[0].rings.length === 3, "país con islas y hueco: 3 anillos");
ok(list[1].iso2 === "FR" && list[1].iso3 === "FRA", "códigos -99 se sustituyen por ISO_A2_EH / ADM0_A3");
ok(list[2].iso2 === "" && list[2].iso3 === "", "sin códigos válidos -> vacíos");
ok(g.mainRingIndex(list[0].rings) === 0, "anillo principal = el continente");

// Punto en región (regla par-impar): dentro, en el hueco y fuera
const R = list[0].rings;
const nx = lon => (lon + 180) / 360, ny = lat => g.mercatorV(lat);
ok(g.pointInRings(nx(-65), ny(-31), R) === true, "punto dentro del país");
ok(g.pointInRings(nx(-67), ny(-37), R) === false, "punto dentro del hueco queda fuera");
ok(g.pointInRings(nx(-10), ny(-10), R) === false, "punto fuera");
ok(g.countryAt(list, -35, -65).iso3 === "AAA", "countryAt encuentra el país");
ok(g.countryAt(list, 45, 4).iso3 === "FRA", "countryAt (Francia rara)");
ok(g.countryAt(list, 0, -120) === null, "countryAt en el océano = null");
ok(g.countryAt(list, -35, -65 + 360).iso3 === "AAA", "countryAt normaliza la longitud");

// Colores: tabla del usuario y alternativa por hash
g.setColors({ AR: "#74ACDF", PE: "#D91023", US: "#B22234" });
ok(g.countryColor({ iso2: "AR", name: "Argentina" }) === "#74ACDF", "Argentina celeste");
ok(g.countryColor({ iso2: "US", name: "United States" }) === "#B22234", "Estados Unidos rojo");
ok(g.countryColor({ iso2: "PE", name: "Peru" }) === "#D91023", "Perú rojo");
const hc = g.countryColor({ iso2: "ZZ", name: "Zeta" });
ok(/^hsl\(\d+,65%,55%\)$/.test(hc) && hc === g.countryColor({ iso2: "ZZ", name: "Zeta" }), "color por hash estable: " + hc);

// Estados: se aplanan los anillos de todos los features
const sj = { features: [ { geometry: { type: "Polygon", coordinates: [ sq(0,0,1,1) ] } },
                          { geometry: { type: "MultiPolygon", coordinates: [ [sq(2,2,3,3)], [sq(4,4,5,5)] ] } } ] };
ok(g.parseStates(sj).length === 3, "parseStates aplana 3 anillos");

// renderOverlay con un contexto falso que cuenta las llamadas
function mockCtx() {
  const calls = {}; const rec = n => (...a) => { calls[n] = (calls[n] || 0) + 1; };
  const ctx = { calls };
  ["clearRect","save","restore","beginPath","moveTo","lineTo","closePath","clip","fillRect","drawImage","stroke"].forEach(n => ctx[n] = rec(n));
  return ctx;
}
const S = 2048;
let c1 = mockCtx(); g.renderOverlay(c1, S, list, {}, { b: true, s: false, c: false, f: false }, {});
ok(c1.calls.stroke === 1 && !c1.calls.clip, "solo bordes: 1 trazo y sin rellenos");
let c2 = mockCtx(); g.renderOverlay(c2, S, list, { AAA: g.parseStates(sj) }, { b: true, s: true, c: false, f: false }, {});
ok(c2.calls.stroke === 2, "bordes + estados: 2 trazos (estados y países)");
let c3 = mockCtx(); g.renderOverlay(c3, S, list, {}, { b: false, s: false, c: true, f: false }, {});
ok(c3.calls.clip === 3 && c3.calls.fillRect === 3 && !c3.calls.stroke && !c3.calls.drawImage, "solo colores: 3 rellenos, sin trazos ni banderas");
let c4 = mockCtx(); g.renderOverlay(c4, S, list, {}, { b: false, s: false, c: true, f: true }, { AA: {}, FR: {} });
ok(c4.calls.drawImage === 2, "colores + banderas: 2 banderas dibujadas (las que ya cargaron)");
let c5 = mockCtx(); g.renderOverlay(c5, S, list, {}, { b: false, s: false, c: false, f: true }, { AA: {} });
ok(!c5.calls.drawImage && !c5.calls.clip, "banderas sin colorear: no dibuja nada (depende de colorear)");
let c6 = mockCtx(); g.renderOverlay(c6, S, list, { AAA: g.parseStates(sj) }, { b: false, s: true, c: false, f: false }, {});
ok(!c6.calls.stroke, "estados sin bordes: no dibuja nada (depende de bordes)");

console.log(fails === 0 ? "\nTODAS LAS PRUEBAS NUEVAS PASARON" : "\nFALLARON " + fails);
process.exit(fails ? 1 : 0);

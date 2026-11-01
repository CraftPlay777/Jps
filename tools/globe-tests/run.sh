#!/bin/sh
# Pruebas del globo 3D con Node.js (no necesitan Android ni navegador).
#   test_geom.js : malla de la esfera, relieve, normales y matrices de cámara
#   test2.js     : países, estados, colores, banderas y dibujo del overlay
#   smoke.js     : ejecuta globe.html completo con un DOM y un WebGL simulados
# Uso:  sh tools/globe-tests/run.sh
set -e
cd "$(dirname "$0")"
node -e "
const fs = require('fs');
const html = fs.readFileSync('../../app/src/main/assets/globe.html', 'utf8');
const m = html.match(/<script>([\s\S]*?)<\/script>/);
fs.writeFileSync('globe.js', m[1]);
"
node --check globe.js
node test_geom.js | tail -1
node test2.js | tail -1
node smoke.js | tail -2
echo "Pruebas del globo: OK"

# Jps: contexto para seguir desarrollando la app

Documento de traspaso. Está escrito para que una persona (o una IA) que no conoce el proyecto
pueda entender qué hay, por qué se hizo así y cómo continuar sin romper nada.

- **Repositorio:** github.com/CraftPlay777/Jps · **Paquete:** `com.CraftPlay777.jps`
- **Versión al escribir esto:** 1.9 (versionCode 11)
- **Stack:** Java, Android sin AppCompat (clases `Activity` planas), una sola dependencia: osmdroid 6.1.18
- **Android:** minSdk 23, compileSdk/targetSdk 34 · AGP 8.5.2 · Gradle 8.9 · JDK 17
- **Idioma de la interfaz:** español (los textos están en `res/values/strings.xml`)

> **Estado a tener en cuenta:** las versiones 1.8 (tema claro/oscuro y "Cómo llegar") y 1.9 (fotos e
> información de Wikipedia en la ficha) se escribieron y se revisaron con las herramientas de `tools/`, pero
> **todavía no se han compilado en GitHub Actions ni probado en el móvil**. Lo primero al retomar el proyecto es compilarla y
> corregir lo que salga. Hasta la 1.7.1 todo se compiló y el dueño la probó ("funciona perfecto").

---

## 1. Qué es Jps

Una app de mapas para Android, sin anuncios, sin cuentas y **sin API keys ni Google**. Todo sale de
servicios abiertos basados en OpenStreetMap. Funciones actuales:

- Mapa con búsqueda de lugares, historial, favoritos, mi ubicación, rotación, escala y brújula.
- Cinco servicios de mapa a elegir (OpenStreetMap, OSM Francia, Humanitario, CyclOSM, OpenTopoMap).
- Contorno exacto de la zona buscada (países, provincias, ciudades), con relleno (bandera o color).
- Bordes de países y de estados/provincias en rojo, colorear cada país y usar su bandera (mapa y globo).
- **Globo 3D** separado: esfera con el mapa y modo "Relieve 3D" (montañas y océanos hundidos).
- **Cómo llegar** dentro de la app: vehículo, velocidad personalizada, tiempo, pasos y "no salir del país".
- **Fotos e información** de Wikipedia en la ficha del lugar (galería de Wikimedia Commons + resumen).
- Tema blanco, negro o según el dispositivo.
- Política de privacidad dentro de la app y aviso de aceptación antes de cualquier conexión.

## 2. Cómo se ha trabajado (importante)

El proyecto se desarrolló **conversando con una IA (Claude)**, sin Android Studio:

- El dueño programa desde el móvil: **Termux + Acode**, y compila con **GitHub Actions**
  (`.github/workflows/build.yml`: JDK 17, Gradle 8.9 instalado por la acción, `gradle assembleDebug`,
  sube el APK como artefacto `Jps-debug-apk`). No hay `gradlew` en el repo a propósito.
- Su móvil de pruebas es un **Motorola con Android 10 Go de 32 bits y poca RAM**. Todo se diseñó
  pensando en eso (archivos en flujo, poca memoria, nada de librerías pesadas).
- **La IA no puede compilar ni ejecutar la app.** Por eso cada cambio se verifica así:
  1. `python3 tools/check_project.py` revisa XML, llaves, imports, recursos y referencias.
  2. `sh tools/globe-tests/run.sh` prueba con Node.js la lógica del globo 3D.
  3. Se sube a GitHub; si Actions falla, el dueño pega el log y se corrige. Esa es la prueba real.
- Cada versión se entrega como un `Jps.zip` con el proyecto completo. El dueño lo descomprime sobre su
  carpeta (conservando `.git`) y hace `git add . && git commit && git push`.
  **Preferencia del dueño:** dar siempre UN solo bloque de comandos con este formato:

```
cd /storage/emulated/0/Download/ && mkdir -p Jps JpsMemory && find Jps -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} + && unzip -oq Jps.zip -d Jps && mv -f Jps.zip JpsMemory/ && cd Jps && git add . && git commit -m "Jps X.Y: descripcion" && git push
```

## 3. Reglas del proyecto (decisiones que hay que respetar)

1. **Sin API keys ni servicios de pago.** Solo servicios abiertos y gratuitos.
2. **Ninguna conexión antes de aceptar.** El primer arranque muestra la política; hasta aceptar,
   `map.setUseDataConnection(false)`. Cada función que usa un servicio nuevo pide su propio permiso
   (claves `consent`, `globe_consent`, `regions_consent`, `route_consent`, `info_consent` en `Store`).
3. **Respetar las políticas de uso:** OSM limita las apps a 2 conexiones de teselas (no subir los
   hilos de descarga); Nominatim y el servicio de rutas piden máximo 1 petición por segundo (hay
   `throttle()` en `Nominatim` y `Routing`); siempre un `User-Agent` que identifique la app.
4. **Cada servicio nuevo se documenta:** `PRIVACY.md`, el texto `privacy_html` en `strings.xml`, los
   créditos `about_html` y el README. Si no se actualizan, la política deja de ser cierta.
5. **Sin `java.util.function` ni APIs de nivel > 23 sin protección** (minSdk 23). Se usan interfaces
   propias (por ejemplo `Regions.BitmapCallback`).
6. **Nunca tragarse una excepción en silencio.** Mostrar la causa real (ver `Regions.lastError()`).
   Un fallo oculto ya costó una versión entera (ver sección 8).
7. **Textos de interfaz en `strings.xml`**, en español, con tipografía correcta (…, ¿?, ¡!).

## 4. Mapa del código (`app/src/main/java/com/CraftPlay777/jps/`)

| Archivo | Para qué sirve |
|---|---|
| `MainActivity` | Pantalla principal (~950 líneas): mapa, búsqueda, ficha del lugar, menú, consentimiento, conexión de todos los overlays y controladores. Es el archivo más grande y el primero que conviene dividir. |
| `BaseActivity` | Clase base de todas las pantallas: aplica el tema elegido y se recrea si cambia. **Toda `Activity` nueva debe heredar de ella.** |
| `Theming` | Fuerza el modo día/noche de la app envolviendo el contexto con un `uiMode` forzado; así se aplican los recursos de `values-night/`. |
| `Store` | Todo lo que se guarda en el móvil (`SharedPreferences` "jps"): ajustes, consentimientos, favoritos, historial, última posición. |
| `Place` | Un lugar: nombre, coordenadas, datos de OSM (tipo, id, caja) y si es país. |
| `Nominatim` | Búsqueda de lugares, dirección inversa y contorno (`/search`, `/reverse`, `/lookup` con `polygon_geojson`). |
| `MapSources` | Los cinco servicios de teselas y el diálogo para elegirlos. |
| `Mercator` | Proyección normalizada (0..1) y `frame()`, que mide dónde cae el mundo en la pantalla del mapa. Lo usan todos los overlays propios. |
| `GeoJson` | Lector de GeoJSON por flujo (`JsonReader`): convierte cada anillo en `float[]` Mercator normalizado. |
| `Regions` | Datos de países, estados/provincias y banderas: descarga bajo demanda, caché en `filesDir/geo/`, colores, `countryAt()`. |
| `RegionsOverlay` | Dibuja bordes, estados, colores y banderas de todos los países (con recorte y decimación). |
| `ZoneOverlay` | Rellena la zona buscada (bandera si es país, color si es estado o ciudad). |
| `WikiInfo` | Cliente de Wikipedia/Commons: resuelve el artículo (etiqueta `wikipedia` de OSM o búsqueda), resumen, fotos filtradas (sin banderas ni mapas) y descarga de miniaturas reducidas. |
| `PlaceInfoController` | Bloque de fotos e información dentro de la ficha: permiso, ajuste, descarga en segundo plano, galería y diálogos. |
| `Routing` | Cliente del servicio de rutas (OSRM de FOSSGIS), instrucciones en español y `foreignCountry()` para "no salir del país". |
| `Vehicle` | Vehículos (auto, moto, camión, bicicleta, a pie): perfil de ruta y velocidad por defecto. |
| `RouteController` | Panel "Cómo llegar" de la pantalla principal: vehículo, velocidad, cálculo, resultado y pasos. |
| `RouteOverlay` | Dibuja la ruta (línea azul con borde blanco). |
| `SettingsActivity` | Ajustes: tema, servicio de mapa, mapa oscuro, unidades, fronteras/colores, borrado de datos. |
| `FavoritesActivity`, `InfoActivity` | Lista de favoritos; política de privacidad y "Acerca de". |
| `GlobeActivity` | Globo 3D: `WebView` que carga `assets/globe.html`; sirve los datos (ver sección 5.5). |
| `Ui` | Cabecera común, formato de distancias y duraciones, HTML. |

Recursos y otros:

- `res/layout/activity_main.xml`: pantalla principal (mapa, barra de búsqueda, botones, ficha `card`,
  panel de ruta `route_panel`). `res/values-night/`: colores y tema oscuro.
- `assets/globe.html`: **todo el globo 3D** (WebGL puro, sin librerías) + `assets/country_colors.js`:
  color principal de cada país (ISO2 → #RRGGBB), compartido por Java y por el globo.
- `tools/check_project.py`, `tools/globe-tests/`: verificación sin compilar (sección 7).

## 5. Cómo funciona cada parte

### 5.1 Mapa (osmdroid)
`MapView` con `setTilesScaledToDpi(true)`. La configuración de osmdroid se hace en `onCreate`
**antes** de crear el mapa (user agent, caché en `cacheDir/osmdroid`, teselas en memoria,
caducidad forzada de 7 días como pide la política de OSM). Orden de overlays (de abajo a arriba):
`MapEventsOverlay` → `RegionsOverlay` → zona buscada (relleno y contorno) → rotación → copyright →
escala → ruta → punto de ubicación → marcador. Al insertar overlays nuevos, calcular el índice
respecto a uno existente (`indexOf`) en lugar de usar números fijos.

### 5.2 Búsqueda y zona buscada
`Nominatim.search` devuelve hasta 5 lugares con `osm_type`, `osm_id`, caja y país. Al elegir uno se
pone el marcador, se encuadra la zona y se descarga su contorno exacto (`/lookup` con
`polygon_threshold` según el tamaño de la zona; máximo 300 anillos). Con la opción de bordes activa,
`ZoneOverlay` lo rellena. El contorno se borra al vaciar la búsqueda.

### 5.3 Bordes, estados, colores y banderas (`Regions` + `RegionsOverlay`)
- Países: Natural Earth 1:50M (GeoJSON, ~4 MB, descarga una vez). Estados/provincias: geoBoundaries
  (versión simplificada), **por país y bajo demanda** (los 3 países que más ocupan la pantalla, del
  zoom 4 al 12). Banderas: flagcdn.com (160 px para el mapa, 640 px para la zona buscada).
- `RegionsOverlay` precalcula todo en coordenadas Mercator normalizadas y en cada fotograma solo
  multiplica por el tamaño del mundo en pantalla (`Mercator.frame`). Recorta por caja envolvente y
  descarta puntos a menos de 1,5 px. Los bordes generales se dibujan hasta el zoom 9 (después la
  escala 1:50M ya no es precisa).
- Opciones dependientes: "Estados" necesita "Bordes" y "Usar bandera" necesita "Colorear"
  (`Store.isStatesOn()` / `isFlagsOn()` devuelven el valor efectivo).

### 5.4 Temas
`BaseActivity.attachBaseContext` llama a `Theming.wrap`, que crea un contexto con `uiMode` forzado a
día o noche según el ajuste (0 dispositivo, 1 claro, 2 oscuro). Así Android elige por sí solo los
colores y el tema de `values-night/`. Para añadir un color nuevo hay que definirlo en
`values/colors.xml` **y** en `values-night/colors.xml`. El "Mapa oscuro" es un ajuste distinto: solo
invierte las teselas.

### 5.5 Globo 3D (`GlobeActivity` + `assets/globe.html`)
- Esfera de 91×181 vértices, textura del mapa = teselas OSM z=3 pegadas en un canvas de 2048 px
  (Mercator), subida como textura WebGL. El relieve usa teselas Terrarium z=2 (la elevación se
  decodifica como `R*256 + G + B/256 - 32768`) y se aplica **en CPU** (no hay texturas en el
  vertex shader, que no existe en móviles viejos). Hay un segundo canvas "overlay" con bordes,
  colores y banderas que el shader mezcla sobre el mapa (alfa premultiplicado).
- Una página `file://` no puede leer datos remotos sin CORS, así que `GlobeActivity` intercepta las
  peticiones a `https://jps.local/...` (`shouldInterceptRequest`): descarga con `Regions`, guarda en
  caché y responde con `Access-Control-Allow-Origin: *`. Las opciones se cambian desde el globo
  mediante el puente `JpsApp.setOption(clave, valor)` (claves `b`, `s`, `c`, `f`).
- Las funciones puras del globo (malla, relieve, países, dibujo) se exportan para probarlas en Node.

### 5.6 Fotos e información de Wikipedia
- `Nominatim.search` pide `extratags=1` y guarda en `Place.wikipedia` la etiqueta de OSM (ej. `es:Chaco`).
  `WikiInfo.fetch` usa ese artículo (si es de otro idioma, busca su equivalente en español con `langlinks`);
  si no hay etiqueta y el lugar es una zona (caja > 0,01°), busca por texto con "primer + penúltimo + último"
  componente del nombre. Los puntos sueltos (comercios, pines) sin etiqueta no muestran nada.
- Resumen: `/api/rest_v1/page/summary`. Fotos: `generator=images` + `imageinfo` (miniatura de 480 px),
  solo JPEG de 600×300 o más y sin palabras como flag, escudo, mapa, logo (lista `SKIP`). Máximo 12.
- `PlaceInfoController`: un hilo para el texto y dos para las fotos, caché pequeña en memoria (`LruCache`
  de 4 MB, decodificación RGB_565 con `inSampleSize`). Ajuste `info_on` (por defecto activo) y permiso
  propio `info_consent`. Las fotos nunca se guardan en disco.
- Limitación: los favoritos y los pines de pulsación larga no traen datos de OSM, así que no muestran info.

### 5.7 Cómo llegar
- `RouteController` abre el panel (`route_panel`), pide permiso la primera vez y calcula con
  `Routing.fetch`: `https://routing.openstreetmap.de/routed-{car|bike|foot}/route/v1/driving/...`
  (el prefijo decide el perfil; la parte "driving" se ignora). Siempre pide alternativas.
- Salida = mi ubicación (necesita GPS). Moto y camión usan el perfil de auto.
- Tiempo: se muestran el del servicio y el calculado con la velocidad que escribe el usuario
  (`distancia / velocidad`). La velocidad personalizada se guarda por vehículo (`speed_<id>`).
- "No salir del país": con los polígonos de países, se descartan las alternativas que pasan 6 km o
  más por otro país (se muestrea cada ~1 km). Si el origen y el destino están en países distintos, o
  todas las alternativas salen, no se calcula y se explica el motivo.

## 6. Servicios externos

| Servicio | Para qué | Límites y notas |
|---|---|---|
| `tile.openstreetmap.org` y servidores de OSM Francia, CyclOSM, OpenTopoMap | Teselas del mapa | OSM: máximo 2 conexiones, identificar la app, caché ≥ 7 días. Prohibido descargar teselas en masa. |
| `nominatim.openstreetmap.org` | Buscar lugares, direcciones, contornos | 1 petición/s, `User-Agent` obligatorio. |
| `routing.openstreetmap.de` (FOSSGIS, OSRM) | Rutas | Servidor de demostración: uso razonable y no comercial, 1 petición/s, sin garantías. Guarda las peticiones en sus registros. |
| `raw.githubusercontent.com/nvkelso/natural-earth-vector` | Fronteras de países (50m) | Dominio público. |
| `github.com/wmgeolab/geoBoundaries` | Estados/provincias por país | CC BY 4.0 (hay que dar crédito). |
| `flagcdn.com` | Banderas | Gratuito. |
| `es.wikipedia.org` (y otros idiomas) y `upload.wikimedia.org` | Texto y fotos del lugar | Identificar la app con `User-Agent`; texto CC BY-SA, cada foto con su licencia (se da crédito en "Acerca de"). |
| `s3.amazonaws.com/elevation-tiles-prod` | Relieve del globo (Terrarium) | Datos de Tilezen/Mapzen; pedir atribución. |

## 7. Cómo verificar un cambio (antes de subir)

```
python3 tools/check_project.py      # XML, llaves, imports, recursos, Store, manifest, colores
sh tools/globe-tests/run.sh         # lógica del globo con Node.js
```

Después, `git push` y mirar **Actions → Build APK**. Si falla, copiar el error del log (el primer
`error:` suele ser la causa; los demás son consecuencia).

## 8. Errores que ya ocurrieron (para no repetirlos)

1. `Configuration.getInstance()` de osmdroid devuelve `IConfigurationProvider`, no `Configuration`
   (error de compilación en la v1.0).
2. `File.createTempFile("dl", ...)`: el prefijo debe tener **al menos 3 caracteres**. Lanzaba una
   excepción antes de conectarse y la app mostraba "revisa tu internet" (v1.7 → arreglado en 1.7.1).
   Por eso ahora se muestra el motivo real.
3. `Projection.toPixels` puede colocar puntos en otra copia del mundo cerca del meridiano 180:
   `Mercator.frame` mide con dos puntos siempre dentro de [-180, 180].
4. Un `WebView` con `file://` no puede hacer `fetch` a servicios sin CORS: de ahí `jps.local`.
5. Las filas de un `ListView` con botones dentro necesitan `focusable=false` en el botón.
6. En los `Overlay` de osmdroid hay que dibujar siempre con las coordenadas que da la propia
   proyección; no asumir el tamaño de tesela.

## 9. Limitaciones conocidas

- Los bordes generales son 1:50M: inexactos de cerca; por eso solo se ven hasta el zoom 9. El contorno
  de la búsqueda sí es exacto.
- Los colores de `country_colors.js` son el color principal de cada bandera, puestos a mano: se pueden
  retocar sin tocar código.
- Si el resultado de OSM es un punto (por ejemplo, un comercio) no hay contorno que dibujar.
- Las rutas necesitan internet y dependen de un servidor comunitario sin garantías. No hay navegación
  guiada por voz, reencaminado ni tráfico. El camión no tiene restricciones propias de camión.
- Todo está pensado para un móvil modesto: si algo va lento, reducir datos o resolución antes de
  añadir más trabajo por fotograma.

## 10. Siguientes pasos sugeridos (por orden)

1. **Compilar y probar la 1.9** (fotos e información, tema claro/oscuro y rutas) y corregir lo que falle.
2. Rutas: elegir el origen tocando el mapa o buscándolo, paradas intermedias, ver las alternativas,
   compartir la ruta y guardar rutas recientes.
3. Dividir `MainActivity` en controladores (búsqueda, zona buscada, regiones), como ya se hizo con
   `RouteController`.
4. Pruebas JVM para el código puro de Java (`Routing.instruction`, `GeoJson`, `Mercator`) y subirlas a
   Actions junto con `lint`.
5. Firma de la APK de publicación y versiones automáticas.
6. Mapa vectorial (MapLibre + OpenFreeMap): se vería nítido y fluido, pero aumenta el tamaño de la
   app y obliga a rehacer los overlays. Valorarlo con calma por el móvil de pruebas.
7. Inglés como segundo idioma y mejoras de accesibilidad.
8. Mapas sin conexión: **no** descargar teselas de los servidores de OSM (está prohibido); haría falta
   un proveedor que lo permita o un servidor propio.

## 11. Cómo añadir una función nueva (lista de comprobación)

1. Textos nuevos en `strings.xml` (y colores en `values/` y `values-night/` si los hay).
2. Cualquier ajuste nuevo: método en `Store` y fila en `SettingsActivity`.
3. Si usa un servicio nuevo: pedir permiso antes de conectarse, `User-Agent`, límite de peticiones,
   y actualizar `PRIVACY.md`, `privacy_html`, `about_html` y el README.
4. Pantallas nuevas: heredar de `BaseActivity` y declararlas en el manifest.
5. Operaciones de red y lectura de archivos **fuera del hilo principal**; archivos grandes en flujo.
6. Subir `versionCode` y `versionName` en `app/build.gradle`.
7. `python3 tools/check_project.py` (y las pruebas del globo si se tocó `globe.html`).
8. Entregar el zip con el comando de la sección 2 y pedir el log de Actions si falla.

## 12. Historial de versiones

| Versión | Qué se añadió |
|---|---|
| 1.0 | Mapa con osmdroid, búsqueda de lugares, compilación con GitHub Actions |
| 1.1 | Favoritos, historial, ajustes, política de privacidad y aceptación inicial |
| 1.2 | Contorno exacto de la zona buscada |
| 1.3 | Elegir el servicio de mapa |
| 1.4 | Carga más rápida (host único, caché de 7 días) |
| 1.5 | Globo 3D con modo relieve |
| 1.6 | Pulsación larga en + / − para zoom máximo o mínimo |
| 1.7 / 1.7.1 | Bordes, estados, colores y banderas (mapa y globo); arreglo de la descarga |
| 1.8 | Tema blanco/negro/dispositivo y "Cómo llegar" dentro de la app *(sin probar aún)* |
| 1.9 | Fotos e información de Wikipedia en la ficha del lugar *(sin probar aún)* |

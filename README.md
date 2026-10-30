# Jps

App de mapas para Android, simple, sin anuncios y sin API keys. Usa los mapas de OpenStreetMap (osmdroid).

## Funciones

- Búsqueda de lugares con historial de búsquedas recientes
- Mi ubicación (GPS), zoom, escala y brújula para orientar al norte
- Pulsación larga en + o − para ir directo al zoom máximo o mínimo
- Rotación del mapa con dos dedos
- Pin con pulsación larga (con su dirección)
- Bordes de países y de estados/provincias en rojo, colorear cada país y usar su bandera como color (mapa y globo 3D); al buscar un lugar se pinta solo esa zona
- Globo 3D separado: esfera con el mapa del mundo y modo Relieve 3D (montañas y océanos hundidos, con exageración ajustable)
- Contorno en rojo de la zona buscada (países, provincias, ciudades…) hasta que borras la búsqueda
- Favoritos
- Distancia desde tu posición al lugar seleccionado
- Cómo llegar (abre otra app de mapas), compartir y copiar coordenadas
- Servicio de mapa a elegir en Ajustes (OpenStreetMap, OSM Francia, Humanitario, CyclOSM u OpenTopoMap) y modo de mapa oscuro
- Unidades métricas o imperiales
- Recuerda la última posición del mapa y guarda las teselas 7 días para cargar más rápido
- Política de privacidad dentro de la app, con aceptación en el primer arranque

## Datos y privacidad

Sin cuentas ni rastreadores. Lee la [política de privacidad](PRIVACY.md).

## Compilar

Cada `git push` a `main` compila el APK con GitHub Actions.
Descárgalo en **Actions → Build APK → Artifacts → Jps-debug-apk**.

- Paquete: `com.CraftPlay777.jps`
- Android 6.0+ (minSdk 23)

## Licencia

[MIT](LICENSE). Los datos del mapa son © colaboradores de OpenStreetMap (ODbL).

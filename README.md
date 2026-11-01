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
- Fotos e información de Wikipedia en la ficha del lugar (países, estados, ciudades…), con galería y texto; se puede desactivar en Ajustes
- Cuatro idiomas (español, inglés, 中文 mandarín y français), que se eligen al iniciar y se pueden cambiar en Ajustes
- Favoritos
- Distancia desde tu posición al lugar seleccionado
- Cómo llegar dentro de la app (auto, moto, camión, bici o a pie) con velocidad personalizada, tiempo estimado, pasos y opción de no salir del país
- Compartir y copiar coordenadas
- Tema claro, oscuro o según el dispositivo
- Servicio de mapa a elegir en Ajustes (OpenStreetMap, OSM Francia, Humanitario, CyclOSM u OpenTopoMap) y modo de mapa oscuro
- Unidades métricas o imperiales
- Recuerda la última posición del mapa y guarda las teselas 7 días para cargar más rápido
- Política de privacidad dentro de la app, con aceptación en el primer arranque

## Para desarrolladores

Lee [CONTEXTO.md](CONTEXTO.md): arquitectura, reglas del proyecto, cómo verificar cambios sin compilar
y cómo seguir desarrollando la app.

## Datos y privacidad

Sin cuentas ni rastreadores. Lee la [política de privacidad](PRIVACY.md).

## Compilar

Cada `git push` a `main` compila el APK con GitHub Actions.
Descárgalo en **Actions → Build APK → Artifacts → Jps-debug-apk**.

- Paquete: `com.CraftPlay777.jps`
- Android 6.0+ (minSdk 23)

## Licencia

[MIT](LICENSE). Los datos del mapa son © colaboradores de OpenStreetMap (ODbL).

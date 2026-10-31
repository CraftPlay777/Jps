package com.CraftPlay777.jps;

import android.graphics.Point;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;

/** Proyección Mercator normalizada (0..1) y su relación con la pantalla del mapa. */
final class Mercator {

    static final double MAX_LAT = 85.0511287798;

    private Mercator() {
    }

    static float x(double lon) {
        return (float) ((lon + 180.0) / 360.0);
    }

    static float y(double lat) {
        double l = Math.max(-MAX_LAT, Math.min(MAX_LAT, lat)) * Math.PI / 180.0;
        return (float) (0.5 - Math.log(Math.tan(Math.PI / 4 + l / 2)) / (2 * Math.PI));
    }

    /**
     * Calcula dónde cae el mundo (0..1) en la pantalla: out = {origenX, origenY, tamañoMundoPx}.
     * Un punto normalizado (nx, ny) se dibuja en (origenX + nx * tamaño, origenY + ny * tamaño).
     * Se mide con la propia proyección del mapa para no depender de su escala interna.
     */
    static boolean frame(MapView map, double[] out) {
        if (map.getWidth() == 0 || map.getHeight() == 0) {
            return false;
        }
        Projection pj = map.getProjection();
        IGeoPoint c = map.getMapCenter();
        double lon = ((c.getLongitude() + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        double lat = Math.max(-80.0, Math.min(80.0, c.getLatitude()));

        Point a = new Point();
        Point b = new Point();
        Point cp = new Point();
        // Dos puntos separados 90 grados y siempre dentro de [-180, 180], cerca del centro,
        // para que la proyección no los coloque en copias distintas del mundo.
        double lonA = Math.max(-180.0, Math.min(89.9, lon - 45.0));
        pj.toPixels(new GeoPoint(lat, lonA), a);
        pj.toPixels(new GeoPoint(lat, lonA + 90.0), b);
        double size = (b.x - a.x) * 4.0; // 90 grados = 1/4 del mundo
        if (size < 256.0) {
            return false;
        }
        pj.toPixels(new GeoPoint(lat, lon), cp);
        out[0] = cp.x - x(lon) * size;
        out[1] = cp.y - y(lat) * size;
        out[2] = size;
        return true;
    }
}

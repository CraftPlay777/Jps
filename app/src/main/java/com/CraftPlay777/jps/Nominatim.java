package com.CraftPlay777.jps;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Cliente mínimo de Nominatim (buscador de OpenStreetMap).
 * Son llamadas bloqueantes: ejecútalas siempre fuera del hilo principal.
 */
final class Nominatim {

    private static final String BASE = "https://nominatim.openstreetmap.org";
    // Nominatim exige un User-Agent que identifique la app.
    private static final String USER_AGENT =
            "Jps/1.0 (Android; https://github.com/CraftPlay777/Jps)";

    private Nominatim() {
    }

    /** Busca lugares por texto (hasta 5 resultados). */
    static List<Place> search(String query) throws IOException, JSONException {
        String url = BASE + "/search?format=jsonv2&limit=5&q="
                + URLEncoder.encode(query, "UTF-8");
        JSONArray array = new JSONArray(get(url));
        List<Place> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            Place place = new Place(
                    o.getString("display_name"),
                    o.getDouble("lat"),
                    o.getDouble("lon"));
            place.osmType = o.optString("osm_type", null);
            place.osmId = o.optLong("osm_id", 0);
            JSONArray bb = o.optJSONArray("boundingbox");
            if (bb != null && bb.length() == 4) {
                place.bbox = new double[]{
                        bb.getDouble(0), bb.getDouble(1), bb.getDouble(2), bb.getDouble(3)};
            }
            out.add(place);
        }
        return out;
    }

    /** Devuelve la dirección de unas coordenadas, o null si no hay. */
    static String reverse(double lat, double lon) throws IOException, JSONException {
        String url = BASE + String.format(Locale.US,
                "/reverse?format=jsonv2&zoom=18&lat=%.6f&lon=%.6f", lat, lon);
        JSONObject o = new JSONObject(get(url));
        return o.optString("display_name", null);
    }

    /**
     * Descarga el contorno de un lugar (país, provincia, ciudad...).
     * Devuelve una lista de anillos; vacía si el lugar no tiene área (por ejemplo, un punto).
     */
    static List<List<GeoPoint>> boundary(String osmType, long osmId, double[] bbox)
            throws IOException, JSONException {
        // Cuanto más grande es la zona, más se simplifica la línea (menos puntos, mapa más fluido).
        double size = 1.0;
        if (bbox != null) {
            size = Math.max(Math.abs(bbox[1] - bbox[0]), Math.abs(bbox[3] - bbox[2]));
        }
        double threshold = Math.min(0.01, Math.max(0.0001, size / 2000.0));

        String id = Character.toUpperCase(osmType.charAt(0)) + String.valueOf(osmId);
        String url = BASE + "/lookup?format=jsonv2&polygon_geojson=1&polygon_threshold="
                + String.format(Locale.US, "%.5f", threshold) + "&osm_ids=" + id;

        List<List<GeoPoint>> rings = new ArrayList<>();
        JSONArray array = new JSONArray(get(url));
        if (array.length() == 0) {
            return rings;
        }
        JSONObject geo = array.getJSONObject(0).optJSONObject("geojson");
        if (geo == null) {
            return rings;
        }
        JSONArray coords = geo.optJSONArray("coordinates");
        String type = geo.optString("type", "");
        if (coords == null) {
            return rings;
        }
        if ("Polygon".equals(type)) {
            addPolygon(coords, rings);
        } else if ("MultiPolygon".equals(type)) {
            for (int i = 0; i < coords.length(); i++) {
                addPolygon(coords.getJSONArray(i), rings);
            }
        }

        // Para archipiélagos con miles de islas: nos quedamos con los 300 anillos más grandes.
        Collections.sort(rings, (a, b) -> Integer.compare(b.size(), a.size()));
        while (rings.size() > 300) {
            rings.remove(rings.size() - 1);
        }
        return rings;
    }

    private static void addPolygon(JSONArray polygon, List<List<GeoPoint>> out)
            throws JSONException {
        for (int r = 0; r < polygon.length(); r++) {
            JSONArray ring = polygon.getJSONArray(r);
            List<GeoPoint> points = new ArrayList<>(ring.length());
            for (int i = 0; i < ring.length(); i++) {
                JSONArray c = ring.getJSONArray(i);
                points.add(new GeoPoint(c.getDouble(1), c.getDouble(0))); // GeoJSON: [lon, lat]
            }
            if (points.size() >= 4) {
                out.add(points);
            }
        }
    }

    private static long lastRequestAt = 0;

    /** La política de uso de Nominatim pide como máximo 1 petición por segundo. */
    private static synchronized void throttle() {
        long wait = lastRequestAt + 1000 - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }

    private static String get(String urlString) throws IOException {
        throttle();
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag());
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } finally {
            conn.disconnect();
        }
    }
}

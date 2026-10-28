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
import java.util.ArrayList;
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
            out.add(new Place(
                    o.getString("display_name"),
                    o.getDouble("lat"),
                    o.getDouble("lon")));
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

    private static String get(String urlString) throws IOException {
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

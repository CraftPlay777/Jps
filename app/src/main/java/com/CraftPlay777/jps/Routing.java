package com.CraftPlay777.jps;

import android.location.Location;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cliente del servicio de rutas de OpenStreetMap (OSRM de FOSSGIS: routing.openstreetmap.de).
 * Las llamadas son bloqueantes: úsalas fuera del hilo principal.
 */
final class Routing {

    static final class Step {
        String text;
        double meters;
        double seconds;
        GeoPoint at;
    }

    static final class Route {
        double meters;
        double seconds;
        final List<GeoPoint> points = new ArrayList<>();
        final List<Step> steps = new ArrayList<>();
    }

    /** Error devuelto por el servicio (NoRoute, NoSegment...). */
    static final class RouteException extends IOException {
        final String code;

        RouteException(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    private static final String BASE = "https://routing.openstreetmap.de/routed-";
    private static final String UA = "Jps/1.8 (Android; https://github.com/CraftPlay777/Jps)";
    /** Tramo mínimo en otro país para considerar que la ruta "sale" (la frontera 1:50M no es exacta). */
    private static final double FOREIGN_RUN_METERS = 6000;

    private static long lastRequestAt = 0;

    private Routing() {
    }

    /** Pide la ruta (y alternativas). Devuelve al menos una ruta o lanza excepción. */
    static List<Route> fetch(String profile, GeoPoint from, GeoPoint to) throws IOException, JSONException {
        String url = BASE + profile + "/route/v1/driving/"
                + String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f",
                from.getLongitude(), from.getLatitude(), to.getLongitude(), to.getLatitude())
                + "?overview=full&geometries=geojson&steps=true&alternatives=true&generate_hints=false";
        String body = get(url);
        JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (JSONException e) {
            throw new IOException("respuesta no válida del servicio de rutas");
        }
        String code = root.optString("code", "");
        if (!"Ok".equals(code)) {
            throw new RouteException(code, root.optString("message", code));
        }
        JSONArray routes = root.getJSONArray("routes");
        List<Route> out = new ArrayList<>();
        for (int i = 0; i < routes.length(); i++) {
            out.add(parseRoute(routes.getJSONObject(i)));
        }
        if (out.isEmpty()) {
            throw new RouteException("NoRoute", "NoRoute");
        }
        return out;
    }

    private static Route parseRoute(JSONObject o) throws JSONException {
        Route r = new Route();
        r.meters = o.getDouble("distance");
        r.seconds = o.getDouble("duration");
        JSONArray coords = o.getJSONObject("geometry").getJSONArray("coordinates");
        for (int i = 0; i < coords.length(); i++) {
            JSONArray c = coords.getJSONArray(i);
            r.points.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
        }
        JSONArray legs = o.optJSONArray("legs");
        if (legs != null) {
            for (int l = 0; l < legs.length(); l++) {
                JSONArray steps = legs.getJSONObject(l).optJSONArray("steps");
                if (steps == null) {
                    continue;
                }
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject s = steps.getJSONObject(i);
                    Step st = new Step();
                    st.text = instruction(s);
                    st.meters = s.optDouble("distance", 0);
                    st.seconds = s.optDouble("duration", 0);
                    JSONObject m = s.optJSONObject("maneuver");
                    JSONArray loc = m != null ? m.optJSONArray("location") : null;
                    if (loc != null && loc.length() >= 2) {
                        st.at = new GeoPoint(loc.getDouble(1), loc.getDouble(0));
                    }
                    r.steps.add(st);
                }
            }
        }
        return r;
    }

    // ------------------------------------------------------------ no salir del país

    /**
     * Si la ruta pasa más de unos kilómetros por otro país distinto de "home", devuelve su nombre;
     * si se queda en el país (o solo roza la frontera), devuelve null.
     */
    static String foreignCountry(Regions regions, List<GeoPoint> pts, Regions.Region home) {
        double run = 0;
        double sinceSample = 0;
        GeoPoint prev = null;
        boolean first = true;
        float[] d = new float[1];
        for (GeoPoint p : pts) {
            if (prev != null) {
                Location.distanceBetween(prev.getLatitude(), prev.getLongitude(),
                        p.getLatitude(), p.getLongitude(), d);
                sinceSample += d[0];
            }
            prev = p;
            if (!first && sinceSample < 1000) {
                continue; // una muestra cada ~1 km
            }
            double seg = sinceSample;
            sinceSample = 0;
            first = false;
            Regions.Region c = regions.countryAt(p.getLatitude(), p.getLongitude());
            if (c == null) {
                continue; // mar o sin datos: no cuenta
            }
            if (c == home) {
                run = 0;
            } else {
                run += seg;
                if (run >= FOREIGN_RUN_METERS) {
                    return c.name;
                }
            }
        }
        return null;
    }

    // --------------------------------------------------------------- red y textos

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
        throttle(); // el servicio pide como máximo 1 petición por segundo
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            conn.setRequestProperty("User-Agent", UA);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(45000);
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                throw new IOException("HTTP " + code);
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } finally {
            conn.disconnect();
        }
    }

    /** Mensaje en español para los códigos de error del servicio. */
    static String messageFor(String code) {
        switch (code) {
            case "NoRoute":
                return "No hay una ruta posible entre estos dos puntos con este vehículo.";
            case "NoSegment":
                return "El origen o el destino está demasiado lejos de una vía para este vehículo.";
            case "TooBig":
                return "La ruta es demasiado larga para el servicio.";
            default:
                return "El servicio de rutas respondió con un error (" + code + ").";
        }
    }

    private static String direction(String mod) {
        switch (mod) {
            case "left": return "a la izquierda";
            case "right": return "a la derecha";
            case "slight left": return "ligeramente a la izquierda";
            case "slight right": return "ligeramente a la derecha";
            case "sharp left": return "cerrado a la izquierda";
            case "sharp right": return "cerrado a la derecha";
            case "straight": return "recto";
            case "uturn": return "para dar la vuelta";
            default: return "";
        }
    }

    private static String ordinal(int n) {
        String[] names = {"", "primera", "segunda", "tercera", "cuarta", "quinta",
                "sexta", "séptima", "octava", "novena", "décima"};
        return n >= 1 && n < names.length ? names[n] : n + "ª";
    }

    /** Convierte una maniobra de OSRM en una instrucción en español. */
    static String instruction(JSONObject step) {
        JSONObject m = step.optJSONObject("maneuver");
        String type = m != null ? m.optString("type", "") : "";
        String mod = m != null ? m.optString("modifier", "") : "";
        String name = step.optString("name", "");
        String on = name.isEmpty() ? "" : " por " + name;
        String dir = direction(mod);
        switch (type) {
            case "depart":
                return "Comienza" + on;
            case "arrive":
                if ("left".equals(mod)) return "Llegas a tu destino, a la izquierda";
                if ("right".equals(mod)) return "Llegas a tu destino, a la derecha";
                return "Has llegado a tu destino";
            case "turn":
                return "Gira " + dir + on;
            case "end of road":
                return "Al final de la vía, gira " + dir + on;
            case "fork":
                return "En la bifurcación, mantente " + dir + on;
            case "merge":
                return "Incorpórate " + dir + on;
            case "on ramp":
                return "Toma la incorporación " + dir + on;
            case "off ramp":
                return "Toma la salida " + dir + on;
            case "roundabout":
            case "rotary": {
                int exit = m != null ? m.optInt("exit", 0) : 0;
                return "En la rotonda toma la " + (exit > 0 ? ordinal(exit) + " " : "") + "salida" + on;
            }
            case "roundabout turn":
                return "En la rotonda, gira " + dir + on;
            case "exit roundabout":
            case "exit rotary":
                return "Sal de la rotonda" + on;
            default:
                return "Continúa" + on;
        }
    }
}

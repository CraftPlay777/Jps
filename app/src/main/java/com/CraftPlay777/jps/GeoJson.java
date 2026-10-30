package com.CraftPlay777.jps;

import android.util.JsonReader;
import android.util.JsonToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Lector de GeoJSON por flujo (no carga todo el archivo en memoria).
 * Convierte cada anillo de Polygon/MultiPolygon en coordenadas Mercator normalizadas (0..1).
 */
final class GeoJson {

    /** Un anillo: pts = [x0, y0, x1, y1, ...] en Mercator normalizado, con su caja envolvente. */
    static final class Ring {
        final float[] pts;
        final float minX, minY, maxX, maxY;

        Ring(float[] pts) {
            this.pts = pts;
            float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
            for (int i = 0; i < pts.length; i += 2) {
                float x = pts[i];
                float y = pts[i + 1];
                if (x < x0) x0 = x;
                if (x > x1) x1 = x;
                if (y < y0) y0 = y;
                if (y > y1) y1 = y;
            }
            minX = x0;
            minY = y0;
            maxX = x1;
            maxY = y1;
        }
    }

    static final class Feature {
        String iso2 = "";
        String iso3 = "";
        String name = "";
        final List<Ring> rings = new ArrayList<>();
    }

    private static final class Buf {
        float[] a = new float[256];
        int n;

        void add(float x, float y) {
            if (n + 2 > a.length) {
                a = Arrays.copyOf(a, a.length * 2);
            }
            a[n++] = x;
            a[n++] = y;
        }
    }

    private GeoJson() {
    }

    static List<Feature> read(File file) throws IOException {
        List<Feature> out = new ArrayList<>();
        try (JsonReader r = new JsonReader(new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8), 1 << 16))) {
            r.beginObject();
            while (r.hasNext()) {
                String name = r.nextName();
                if ("features".equals(name)) {
                    r.beginArray();
                    while (r.hasNext()) {
                        out.add(readFeature(r));
                    }
                    r.endArray();
                } else {
                    r.skipValue();
                }
            }
            r.endObject();
        }
        return out;
    }

    private static Feature readFeature(JsonReader r) throws IOException {
        Feature f = new Feature();
        r.beginObject();
        while (r.hasNext()) {
            String name = r.nextName();
            if ("properties".equals(name) && r.peek() == JsonToken.BEGIN_OBJECT) {
                readProps(r, f);
            } else if ("geometry".equals(name) && r.peek() == JsonToken.BEGIN_OBJECT) {
                readGeometry(r, f);
            } else {
                r.skipValue();
            }
        }
        r.endObject();
        return f;
    }

    private static void readProps(JsonReader r, Feature f) throws IOException {
        String a2eh = null, a2 = null, a3eh = null, a3 = null, adm = null, name = null;
        r.beginObject();
        while (r.hasNext()) {
            String k = r.nextName();
            if (r.peek() != JsonToken.STRING) {
                r.skipValue();
                continue;
            }
            String v = r.nextString();
            switch (k) {
                case "ISO_A2_EH": a2eh = v; break;
                case "ISO_A2": a2 = v; break;
                case "ISO_A3_EH": a3eh = v; break;
                case "ISO_A3": a3 = v; break;
                case "ADM0_A3": adm = v; break;
                case "NAME": name = v; break;
                case "shapeName": if (name == null) name = v; break;
                default: break;
            }
        }
        r.endObject();
        f.iso2 = pick(2, a2eh, a2);
        f.iso3 = pick(3, a3eh, a3, adm);
        if (name != null) {
            f.name = name;
        }
    }

    private static String pick(int len, String... options) {
        for (String o : options) {
            if (o != null && o.length() == len && o.indexOf('-') < 0) {
                return o.toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }

    private static void readGeometry(JsonReader r, Feature f) throws IOException {
        r.beginObject();
        while (r.hasNext()) {
            String name = r.nextName();
            if ("coordinates".equals(name) && r.peek() == JsonToken.BEGIN_ARRAY) {
                readNode(r, f, new Buf());
            } else {
                r.skipValue();
            }
        }
        r.endObject();
    }

    /**
     * Lee un array de coordenadas de cualquier profundidad.
     * Devuelve 0 si era una posición, 1 si era un anillo (lista de posiciones) y 2+ si contenía anillos.
     */
    private static int readNode(JsonReader r, Feature f, Buf buf) throws IOException {
        r.beginArray();
        if (r.peek() == JsonToken.NUMBER) {
            double lon = r.nextDouble();
            double lat = r.nextDouble();
            while (r.hasNext()) {
                r.skipValue(); // altitud u otros valores
            }
            r.endArray();
            buf.add(Mercator.x(lon), Mercator.y(lat));
            return 0;
        }
        int kind = 1;
        while (r.hasNext()) {
            kind = readNode(r, f, buf) + 1;
        }
        r.endArray();
        if (kind == 1) {
            if (buf.n >= 6) {
                f.rings.add(new Ring(Arrays.copyOf(buf.a, buf.n)));
            }
            buf.n = 0;
        }
        return kind;
    }
}

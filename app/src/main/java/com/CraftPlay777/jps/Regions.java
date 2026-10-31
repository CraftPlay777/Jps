package com.CraftPlay777.jps;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Datos de países, estados/provincias y banderas. Se descargan bajo demanda (solo si el usuario
 * activó las opciones y aceptó) y se guardan en el almacenamiento de la app.
 */
final class Regions {

    interface BitmapCallback {
        void onBitmap(Bitmap bitmap);
    }

    /** Un país: anillos (Mercator normalizado), caja envolvente y anillo principal. */
    static final class Region {
        final String iso2, iso3, name;
        final List<GeoJson.Ring> rings;
        final int main;
        final float minX, minY, maxX, maxY;
        int color;
        boolean colorSet;

        Region(GeoJson.Feature f) {
            iso2 = f.iso2;
            iso3 = f.iso3;
            name = f.name;
            rings = f.rings;
            main = mainRing(rings);
            float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
            for (GeoJson.Ring r : rings) {
                x0 = Math.min(x0, r.minX);
                y0 = Math.min(y0, r.minY);
                x1 = Math.max(x1, r.maxX);
                y1 = Math.max(y1, r.maxY);
            }
            minX = x0;
            minY = y0;
            maxX = x1;
            maxY = y1;
        }
    }

    // Fronteras de países: Natural Earth (dominio público), escala 1:50 millones.
    private static final String COUNTRIES_URL =
            "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson";
    private static final String UA = "Jps/1.7.1 (Android; https://github.com/CraftPlay777/Jps)";

    private static Regions instance;

    static synchronized Regions get(Context c) {
        if (instance == null) {
            instance = new Regions(c.getApplicationContext());
        }
        return instance;
    }

    private final Context ctx;
    private final File dir;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Integer> table = new HashMap<>();

    private volatile List<Region> countries;
    private boolean countriesLoading;
    private final List<Runnable> okWaiters = new ArrayList<>();
    private final List<Runnable> failWaiters = new ArrayList<>();
    private final Map<String, List<GeoJson.Ring>> states = new ConcurrentHashMap<>();
    private final Set<String> stateJobs = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Map<String, Bitmap> flags = new ConcurrentHashMap<>();
    private final Set<String> flagJobs = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private volatile Runnable changeListener;
    private volatile String lastError = "";

    private Regions(Context ctx) {
        this.ctx = ctx;
        this.dir = new File(ctx.getFilesDir(), "geo");
        loadTable();
    }

    // ------------------------------------------------------------------ colores

    private void loadTable() {
        try (InputStream in = ctx.getAssets().open("country_colors.js")) {
            String s = new String(readAll(in), StandardCharsets.UTF_8);
            JSONObject o = new JSONObject(s.substring(s.indexOf('{'), s.lastIndexOf('}') + 1));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                try {
                    table.put(k, Color.parseColor(o.getString(k)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException | JSONException ignored) {
        }
    }

    /** Color principal del país (el de su bandera); si no hay dato, uno estable según su nombre. */
    int colorFor(Region r) {
        if (!r.colorSet) {
            r.color = colorForCode(r.iso2, r.name);
            r.colorSet = true;
        }
        return r.color;
    }

    int colorForCode(String iso2, String name) {
        Integer c = iso2 == null ? null : table.get(iso2.toUpperCase(Locale.ROOT));
        if (c != null) {
            return c;
        }
        return paletteColor(iso2 != null && !iso2.isEmpty() ? iso2 : name);
    }

    static int paletteColor(String key) {
        int h = key == null ? 0 : key.hashCode();
        float hue = Math.abs(h % 360);
        return Color.HSVToColor(new float[]{hue, 0.65f, 0.85f});
    }

    // --------------------------------------------------------------- geometría

    static int mainRing(List<GeoJson.Ring> rings) {
        int best = 0;
        float bestArea = -1f;
        for (int i = 0; i < rings.size(); i++) {
            GeoJson.Ring r = rings.get(i);
            float area = (r.maxX - r.minX) * (r.maxY - r.minY);
            if (area > bestArea) {
                bestArea = area;
                best = i;
            }
        }
        return best;
    }

    static List<GeoJson.Ring> ringsFromGeoPoints(List<List<GeoPoint>> rings) {
        List<GeoJson.Ring> out = new ArrayList<>();
        for (List<GeoPoint> ring : rings) {
            if (ring.size() < 3) {
                continue;
            }
            float[] pts = new float[ring.size() * 2];
            int i = 0;
            for (GeoPoint g : ring) {
                pts[i++] = Mercator.x(g.getLongitude());
                pts[i++] = Mercator.y(g.getLatitude());
            }
            out.add(new GeoJson.Ring(pts));
        }
        return out;
    }

    // ------------------------------------------------------------------- datos

    List<Region> countries() {
        return countries;
    }

    /** Motivo del último fallo de descarga o lectura (para mostrarlo al usuario). */
    String lastError() {
        return lastError;
    }

    static String describe(Throwable t) {
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m != null && !m.isEmpty() ? ": " + m : "");
    }

    void setChangeListener(Runnable listener) {
        changeListener = listener;
    }

    private void notifyChanged() {
        Runnable l = changeListener;
        if (l != null) {
            main.post(l);
        }
    }

    /** Carga los países (descarga ~4 MB la primera vez). Los avisos se ejecutan en el hilo principal. */
    void loadCountries(Runnable onDone, Runnable onFail) {
        if (countries != null) {
            if (onDone != null) {
                main.post(onDone);
            }
            return;
        }
        synchronized (this) {
            if (onDone != null) {
                okWaiters.add(onDone);
            }
            if (onFail != null) {
                failWaiters.add(onFail);
            }
            if (countriesLoading) {
                return;
            }
            countriesLoading = true;
        }
        pool.execute(() -> {
            List<Region> result = null;
            try {
                List<Region> list = new ArrayList<>();
                for (GeoJson.Feature f : GeoJson.read(countriesFile())) {
                    if (!f.rings.isEmpty() && !"ATA".equals(f.iso3)) {
                        list.add(new Region(f));
                    }
                }
                result = list;
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                lastError = describe(e);
            }
            final List<Region> r = result;
            main.post(() -> finishCountries(r));
        });
    }

    private void finishCountries(List<Region> r) {
        List<Runnable> toRun;
        synchronized (this) {
            countriesLoading = false;
            if (r != null) {
                countries = r;
            }
            toRun = new ArrayList<>(r != null ? okWaiters : failWaiters);
            okWaiters.clear();
            failWaiters.clear();
        }
        for (Runnable w : toRun) {
            w.run();
        }
    }

    List<GeoJson.Ring> statesOf(String iso3) {
        return iso3 == null ? null : states.get(iso3);
    }

    /** Descarga y carga los estados/provincias de un país (una sola vez por país). */
    void loadStates(final String iso3) {
        if (iso3 == null || !iso3.matches("[A-Z]{3}") || states.containsKey(iso3) || !stateJobs.add(iso3)) {
            return;
        }
        pool.execute(() -> {
            try {
                List<GeoJson.Ring> all = new ArrayList<>();
                for (GeoJson.Feature f : GeoJson.read(statesFile(iso3))) {
                    all.addAll(f.rings);
                }
                states.put(iso3, all);
                notifyChanged();
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                lastError = describe(e);
                // Sin datos para ese país: no se vuelve a intentar hasta reiniciar la app.
            }
        });
    }

    /** Bandera pequeña para pintar países; devuelve null hasta que llega y avisa al terminar. */
    Bitmap flag(String iso2) {
        if (iso2 == null || iso2.length() != 2) {
            return null;
        }
        final String k = iso2.toLowerCase(Locale.ROOT);
        Bitmap b = flags.get(k);
        if (b == null && flagJobs.add(k)) {
            pool.execute(() -> {
                try {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inPreferredConfig = Bitmap.Config.RGB_565;
                    Bitmap bm = BitmapFactory.decodeFile(flagFile(k, 160).getPath(), o);
                    if (bm != null) {
                        flags.put(k, bm);
                        notifyChanged();
                    }
                } catch (IOException | RuntimeException | OutOfMemoryError e) {
                lastError = describe(e);
                }
            });
        }
        return b;
    }

    /** Bandera grande para rellenar un país buscado. */
    void flagLarge(String iso2, final BitmapCallback cb) {
        if (iso2 == null || iso2.length() != 2) {
            return;
        }
        final String k = iso2.toLowerCase(Locale.ROOT);
        pool.execute(() -> {
            try {
                final Bitmap bm = BitmapFactory.decodeFile(flagFile(k, 640).getPath());
                if (bm != null) {
                    main.post(() -> cb.onBitmap(bm));
                }
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                lastError = describe(e);
            }
        });
    }

    // ---------------------------------------------------------------- archivos

    File countriesFile() throws IOException {
        return fetch(COUNTRIES_URL, new File(dir, "countries_50m.geojson"), true);
    }

    /** Límites de estados/provincias de un país (geoBoundaries, CC BY 4.0, versión simplificada). */
    File statesFile(String iso3) throws IOException {
        if (!iso3.matches("[A-Z]{3}")) {
            throw new IOException("código no válido");
        }
        String url = "https://github.com/wmgeolab/geoBoundaries/raw/main/releaseData/gbOpen/"
                + iso3 + "/ADM1/geoBoundaries-" + iso3 + "-ADM1_simplified.geojson";
        return fetch(url, new File(dir, "adm1_" + iso3 + ".geojson"), true);
    }

    File flagFile(String iso2, int width) throws IOException {
        if (!iso2.matches("[a-z]{2}")) {
            throw new IOException("código no válido");
        }
        return fetch("https://flagcdn.com/w" + width + "/" + iso2 + ".png",
                new File(dir, "flag" + width + "_" + iso2 + ".png"), false);
    }

    private File fetch(String url, File dest, boolean json) throws IOException {
        if (dest.exists() && dest.length() > 0) {
            return dest;
        }
        if (!Store.isRegionsConsent(ctx)) {
            throw new IOException("sin consentimiento");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("no se pudo crear la carpeta");
        }
        File tmp = File.createTempFile("jps_dl", ".tmp", dir);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestProperty("User-Agent", UA);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + conn.getResponseCode());
            }
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            if (json && !looksLikeJson(tmp)) {
                throw new IOException("respuesta no válida");
            }
            if (!tmp.renameTo(dest) && !dest.exists()) {
                throw new IOException("no se pudo guardar");
            }
            return dest;
        } finally {
            conn.disconnect();
            if (tmp.exists() && !tmp.equals(dest)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    private static boolean looksLikeJson(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            int c;
            while ((c = in.read()) != -1) {
                if (c > ' ') {
                    return c == '{';
                }
            }
        }
        return false;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}

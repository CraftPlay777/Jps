package com.CraftPlay777.jps;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Información y fotos de un lugar desde Wikipedia (texto) y Wikimedia Commons (imágenes).
 * Son llamadas bloqueantes: ejecútalas siempre fuera del hilo principal.
 */
final class WikiInfo {

    // Wikimedia pide un User-Agent que identifique la app.
    private static final String USER_AGENT =
            "Jps/2.0 (Android; https://github.com/CraftPlay777/Jps)";

    /** Idioma de Wikipedia: el de la app (la clase Lang lo deja como idioma por defecto). */
    private static String preferredLang() {
        String l = Locale.getDefault().getLanguage();
        return Lang.indexOf(l) >= 0 ? l : "es";
    }
    private static final int MAX_IMAGES = 12;
    private static final int MAX_IMAGE_BYTES = 3 * 1024 * 1024;

    /** Palabras que delatan banderas, mapas, logos e iconos: no sirven como foto del lugar. */
    private static final String[] SKIP = {
            "flag", "bandera", "escudo", "coat", "map", "mapa", "locator", "location",
            "logo", "icon", "seal", "blason", "armas", "sello", "signature", "firma",
            "diagram", "wiki", "commons", "symbol", "pictogram", "emblem", "banner"
    };

    static final class Info {
        String title;
        String extract;
        String pageUrl;
        final List<String> images = new ArrayList<>();   // miniaturas de unos 480 px
        final List<String> captions = new ArrayList<>(); // nombre de cada archivo
    }

    private WikiInfo() {
    }

    /** Devuelve la información del lugar, o null si Wikipedia no tiene un artículo para él. */
    static Info fetch(Place p) throws IOException, JSONException {
        final String pref = preferredLang();
        String lang = pref;
        String title = null;

        if (p.wikipedia != null && p.wikipedia.indexOf(':') > 0) {
            lang = p.wikipedia.substring(0, p.wikipedia.indexOf(':')).toLowerCase(Locale.ROOT);
            title = p.wikipedia.substring(p.wikipedia.indexOf(':') + 1);
            if (!pref.equals(lang)) {
                // Preferimos el artículo en el idioma de la app si existe.
                String other = translatedTitle(lang, title, pref);
                if (other != null) {
                    lang = pref;
                    title = other;
                }
            }
        } else if (isZone(p)) {
            title = searchTitle(pref, searchQuery(p.name));
        }
        if (title == null || title.isEmpty()) {
            return null;
        }

        JSONObject summary = new JSONObject(get("https://" + lang + ".wikipedia.org/api/rest_v1/page/summary/"
                + encodeTitle(title)));
        if ("disambiguation".equals(summary.optString("type", ""))) {
            return null;
        }
        String extract = summary.optString("extract", "").trim();
        if (extract.isEmpty()) {
            return null;
        }
        Info info = new Info();
        info.title = summary.optString("title", title);
        info.extract = extract;
        JSONObject urls = summary.optJSONObject("content_urls");
        JSONObject desktop = urls != null ? urls.optJSONObject("desktop") : null;
        info.pageUrl = desktop != null ? desktop.optString("page", null) : null;
        if (info.pageUrl == null) {
            info.pageUrl = "https://" + lang + ".wikipedia.org/wiki/" + encodeTitle(title);
        }

        try {
            loadImages(info, lang, title);
        } catch (IOException | JSONException ignored) {
            // Sin fotos: se muestra solo el texto.
        }
        if (info.images.isEmpty()) {
            JSONObject thumb = summary.optJSONObject("thumbnail");
            if (thumb != null && thumb.optString("source", "").startsWith("https://")) {
                info.images.add(thumb.getString("source"));
                info.captions.add(info.title);
            }
        }
        return info;
    }

    /** Descarga una imagen ya reducida (poca memoria). */
    static Bitmap loadBitmap(String url, int reqWidth) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            try (InputStream in = conn.getInputStream()) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_IMAGE_BYTES) {
                        throw new IOException("imagen demasiado grande");
                    }
                }
            }
            byte[] data = out.toByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= reqWidth) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            if (bmp == null) {
                throw new IOException("imagen no válida");
            }
            return bmp;
        } finally {
            conn.disconnect();
        }
    }

    // ------------------------------------------------------------------ internos

    /** Una zona (país, provincia, ciudad) tiene caja grande; un comercio o una casa, no. */
    private static boolean isZone(Place p) {
        if (p.name == null || p.bbox == null) {
            return false;
        }
        return Math.max(Math.abs(p.bbox[1] - p.bbox[0]), Math.abs(p.bbox[3] - p.bbox[2])) > 0.01;
    }

    /** "Resistencia, Departamento San Fernando, Chaco, Argentina" → "Resistencia Chaco Argentina". */
    private static String searchQuery(String name) {
        String[] parts = name.split(",");
        StringBuilder sb = new StringBuilder(parts[0].trim());
        if (parts.length > 2) {
            sb.append(' ').append(parts[parts.length - 2].trim());
        }
        if (parts.length > 1) {
            sb.append(' ').append(parts[parts.length - 1].trim());
        }
        return sb.toString();
    }

    private static String searchTitle(String lang, String query) throws IOException, JSONException {
        JSONObject root = new JSONObject(get("https://" + lang
                + ".wikipedia.org/w/api.php?action=query&format=json&list=search&srlimit=1&srsearch="
                + URLEncoder.encode(query, "UTF-8")));
        JSONObject q = root.optJSONObject("query");
        JSONArray list = q != null ? q.optJSONArray("search") : null;
        if (list == null || list.length() == 0) {
            return null;
        }
        return list.getJSONObject(0).optString("title", null);
    }

    /** Título del mismo artículo en otro idioma de Wikipedia (null si no existe). */
    private static String translatedTitle(String lang, String title, String target) {
        try {
            JSONObject root = new JSONObject(get("https://" + lang
                    + ".wikipedia.org/w/api.php?action=query&format=json&prop=langlinks&lllang="
                    + target + "&redirects=1&titles=" + URLEncoder.encode(title, "UTF-8")));
            JSONObject pages = root.getJSONObject("query").getJSONObject("pages");
            Iterator<String> keys = pages.keys();
            while (keys.hasNext()) {
                JSONArray links = pages.getJSONObject(keys.next()).optJSONArray("langlinks");
                if (links != null && links.length() > 0) {
                    String translated = links.getJSONObject(0).optString("*", "");
                    if (!translated.isEmpty()) {
                        return translated;
                    }
                }
            }
        } catch (IOException | JSONException ignored) {
        }
        return null;
    }

    /** Fotos que usa el artículo, sin banderas, mapas ni logos. */
    private static void loadImages(Info info, String lang, String title)
            throws IOException, JSONException {
        JSONObject root = new JSONObject(get("https://" + lang
                + ".wikipedia.org/w/api.php?action=query&format=json&redirects=1&generator=images"
                + "&gimlimit=50&prop=imageinfo&iiprop=url%7Csize%7Cmime&iiurlwidth=480&titles="
                + URLEncoder.encode(title, "UTF-8")));
        JSONObject query = root.optJSONObject("query");
        JSONObject pages = query != null ? query.optJSONObject("pages") : null;
        if (pages == null) {
            return;
        }
        final List<JSONObject> found = new ArrayList<>();
        Iterator<String> keys = pages.keys();
        while (keys.hasNext()) {
            found.add(pages.getJSONObject(keys.next()));
        }
        Collections.sort(found, (a, b) -> Integer.compare(a.optInt("index", 0), b.optInt("index", 0)));

        for (JSONObject page : found) {
            if (info.images.size() >= MAX_IMAGES) {
                break;
            }
            JSONArray infos = page.optJSONArray("imageinfo");
            if (infos == null || infos.length() == 0) {
                continue;
            }
            JSONObject ii = infos.getJSONObject(0);
            String thumb = ii.optString("thumburl", "");
            String file = page.optString("title", "");
            boolean photo = "image/jpeg".equals(ii.optString("mime", ""))
                    && ii.optInt("width", 0) >= 600
                    && ii.optInt("height", 0) >= 300
                    && thumb.startsWith("https://");
            if (!photo || looksLikeNonPhoto(file)) {
                continue;
            }
            info.images.add(thumb);
            info.captions.add(cleanName(file));
        }
    }

    private static boolean looksLikeNonPhoto(String file) {
        String f = file.toLowerCase(Locale.ROOT);
        for (String bad : SKIP) {
            if (f.contains(bad)) {
                return true;
            }
        }
        return false;
    }

    /** "Archivo:Obelisco de Buenos Aires.jpg" → "Obelisco de Buenos Aires". */
    private static String cleanName(String file) {
        String s = file;
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(colon + 1);
        }
        int dot = s.lastIndexOf('.');
        if (dot > 0) {
            s = s.substring(0, dot);
        }
        return s.replace('_', ' ').trim();
    }

    private static String encodeTitle(String title) throws IOException {
        return URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20");
    }

    private static String get(String urlString) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            try (InputStream in = conn.getInputStream()) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }
}

package com.CraftPlay777.jps;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Globo 3D: una esfera con el mapa del mundo y un modo con relieve (montañas y océanos hundidos).
 * Se dibuja con WebGL dentro de un WebView usando assets/globe.html.
 */
public class GlobeActivity extends Activity {

    static final String EXTRA_LAT = "lat";
    static final String EXTRA_LON = "lon";

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_globe);
        Ui.setupHeader(this, R.string.globe_title);

        web = findViewById(R.id.globe_web);
        web.setBackgroundColor(Color.parseColor("#0B1020"));

        if (Store.isGlobeConsentGiven(this)) {
            loadGlobe();
        } else {
            askConsent();
        }
    }

    private void askConsent() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.globe_consent_title)
                .setMessage(R.string.globe_consent_message)
                .setCancelable(false)
                .setPositiveButton(R.string.consent_accept, (d, w) -> {
                    Store.setGlobeConsentGiven(this, true);
                    loadGlobe();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> finish())
                .show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void loadGlobe() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowContentAccess(false);
        // Identifica la app ante los servidores de teselas, como pide la política de OpenStreetMap.
        s.setUserAgentString(s.getUserAgentString()
                + " Jps/" + versionName() + " (+https://github.com/CraftPlay777/Jps)");

        // El globo pide sus datos de fronteras, estados y banderas a la app (https://jps.local/...).
        web.addJavascriptInterface(new Bridge(), "JpsApp");
        web.setWebViewClient(new WebViewClient() {
            // El globo no navega a ninguna otra página.
            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (!"jps.local".equals(u.getHost())) {
                    return null;
                }
                return serveData(u.getPath());
            }
        });

        double lat = getIntent().getDoubleExtra(EXTRA_LAT, 15.0);
        double lon = getIntent().getDoubleExtra(EXTRA_LON, -30.0);
        boolean ok = Store.isRegionsConsent(this);
        String options = String.format(Locale.US, "?b=%d&s=%d&c=%d&f=%d",
                ok && Store.isBorders(this) ? 1 : 0,
                ok && Store.isStatesOn(this) ? 1 : 0,
                ok && Store.isColors(this) ? 1 : 0,
                ok && Store.isFlagsOn(this) ? 1 : 0);
        web.loadUrl("file:///android_asset/globe.html" + options
                + String.format(Locale.US, "#%.4f,%.4f", lat, lon));
    }

    // ------------------------------------------------------ datos para el globo

    /** Sirve al globo los archivos descargados (se bajan la primera vez, solo con permiso). */
    private WebResourceResponse serveData(String path) {
        Regions regions = Regions.get(this);
        try {
            File f;
            String mime;
            if ("/countries.geojson".equals(path)) {
                f = regions.countriesFile();
                mime = "application/json";
            } else if (path != null && path.startsWith("/states/") && path.endsWith(".geojson")) {
                f = regions.statesFile(path.substring(8, path.length() - 8));
                mime = "application/json";
            } else if (path != null && path.startsWith("/flag/") && path.endsWith(".png")) {
                f = regions.flagFile(path.substring(6, path.length() - 4), 160);
                mime = "image/png";
            } else {
                return dataResponse(404, "Not Found", "text/plain", new ByteArrayInputStream(new byte[0]));
            }
            return dataResponse(200, "OK", mime, new FileInputStream(f));
        } catch (IOException e) {
            return dataResponse(404, "Not Found", "text/plain", new ByteArrayInputStream(new byte[0]));
        }
    }

    private static WebResourceResponse dataResponse(int code, String reason, String mime, InputStream data) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        return new WebResourceResponse(mime, "UTF-8", code, reason, headers, data);
    }

    // ------------------------------------------------ opciones desde el panel de capas

    /** Puente que usa globe.html para guardar las opciones (b, s, c, f) en los ajustes de la app. */
    final class Bridge {
        @JavascriptInterface
        public void setOption(final String key, final boolean value) {
            runOnUiThread(() -> handleOption(key, value));
        }
    }

    private void handleOption(final String key, final boolean value) {
        if (!"b".equals(key) && !"s".equals(key) && !"c".equals(key) && !"f".equals(key)) {
            return;
        }
        if (value && !Store.isRegionsConsent(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.regions_consent_title)
                    .setMessage(R.string.regions_consent_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.consent_accept, (d, w) -> {
                        Store.setRegionsConsent(this, true);
                        applyOption(key, true);
                    })
                    .setNegativeButton(R.string.cancel, (d, w) -> replyOption(key, false))
                    .show();
            return;
        }
        applyOption(key, value);
    }

    private void applyOption(String key, boolean value) {
        switch (key) {
            case "b":
                Store.setBorders(this, value);
                if (!value) {
                    Store.setStates(this, false);
                }
                break;
            case "s":
                Store.setStates(this, value);
                break;
            case "c":
                Store.setColors(this, value);
                if (!value) {
                    Store.setFlags(this, false);
                }
                break;
            default:
                Store.setFlags(this, value);
                break;
        }
        replyOption(key, true);
    }

    private void replyOption(String key, boolean ok) {
        if (web != null) {
            web.evaluateJavascript("window.jpsOptionResult && window.jpsOptionResult('"
                    + key + "'," + ok + ")", null);
        }
    }

    private String versionName() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v != null ? v : "1.0";
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0";
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) {
            web.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (web != null) {
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) {
                parent.removeView(web);
            }
            web.destroy();
            web = null;
        }
    }
}

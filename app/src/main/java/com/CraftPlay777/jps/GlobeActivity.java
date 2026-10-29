package com.CraftPlay777.jps;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Locale;

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

        // El globo no navega a ninguna otra página.
        web.setWebViewClient(new WebViewClient() {
            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }
        });

        double lat = getIntent().getDoubleExtra(EXTRA_LAT, 15.0);
        double lon = getIntent().getDoubleExtra(EXTRA_LON, -30.0);
        web.loadUrl(String.format(Locale.US, "file:///android_asset/globe.html#%.4f,%.4f", lat, lon));
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

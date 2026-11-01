package com.CraftPlay777.jps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fotos e información (Wikipedia) dentro de la ficha del lugar. Pide permiso una sola vez,
 * respeta el interruptor de Ajustes y nunca bloquea la pantalla: todo se descarga en segundo plano.
 */
final class PlaceInfoController {

    private final Activity activity;
    private final ExecutorService infoIo = Executors.newSingleThreadExecutor();
    private final ExecutorService imageIo = Executors.newFixedThreadPool(2);

    private final View block;
    private final TextView askButton;
    private final View gallery;
    private final LinearLayout strip;
    private final TextView extractView;
    private final TextView sourceView;

    // Memoria pequeña para no repetir descargas al volver a un lugar ya visto.
    private final Map<String, WikiInfo.Info> infoCache = new LinkedHashMap<String, WikiInfo.Info>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, WikiInfo.Info> eldest) {
            return size() > 10;
        }
    };
    private final LruCache<String, Bitmap> bitmaps = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private Place current;
    private String currentKey;
    private int token;

    PlaceInfoController(Activity activity) {
        this.activity = activity;
        block = activity.findViewById(R.id.info_block);
        askButton = activity.findViewById(R.id.info_ask);
        gallery = activity.findViewById(R.id.info_gallery_scroll);
        strip = activity.findViewById(R.id.info_gallery);
        extractView = activity.findViewById(R.id.info_extract);
        sourceView = activity.findViewById(R.id.info_source);
        askButton.setOnClickListener(v -> askConsent());
    }

    /** Muestra la información del lugar que se acaba de marcar. */
    void show(Place p) {
        String key = p.osmType + "/" + p.osmId + "/" + Store.isInfoOn(activity)
                + "/" + Store.isInfoConsent(activity);
        if (p == current && key.equals(currentKey)) {
            return; // ya está mostrada (por ejemplo, al volver de Ajustes)
        }
        current = p;
        currentKey = key;
        token++;
        reset();

        // Los puntos puestos a mano o los favoritos no traen datos de OSM: no hay a qué buscar.
        if (p.osmType == null || !Store.isInfoOn(activity)) {
            return;
        }
        if (!Store.isInfoConsent(activity)) {
            block.setVisibility(View.VISIBLE);
            askButton.setVisibility(View.VISIBLE);
            return;
        }
        load(p);
    }

    void hide() {
        current = null;
        currentKey = null;
        token++;
        reset();
    }

    void shutdown() {
        token++;
        infoIo.shutdownNow();
        imageIo.shutdownNow();
    }

    // ------------------------------------------------------------------ internos

    private void reset() {
        block.setVisibility(View.GONE);
        askButton.setVisibility(View.GONE);
        gallery.setVisibility(View.GONE);
        extractView.setVisibility(View.GONE);
        sourceView.setVisibility(View.GONE);
        strip.removeAllViews();
        extractView.setText("");
    }

    private void askConsent() {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.info_consent_title)
                .setMessage(R.string.info_consent_message)
                .setPositiveButton(R.string.consent_accept, (d, w) -> {
                    Store.setInfoConsent(activity, true);
                    if (current != null) {
                        Place p = current;
                        current = null; // fuerza a recargar con el permiso ya dado
                        show(p);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void load(Place p) {
        final int myToken = token;
        final String cacheKey = java.util.Locale.getDefault().getLanguage() + p.osmType + p.osmId;
        WikiInfo.Info cached;
        synchronized (infoCache) {
            cached = infoCache.get(cacheKey);
        }
        if (cached != null) {
            render(cached);
            return;
        }
        block.setVisibility(View.VISIBLE);
        extractView.setVisibility(View.VISIBLE);
        extractView.setText(R.string.info_loading);
        infoIo.execute(() -> {
            WikiInfo.Info info = null;
            try {
                info = WikiInfo.fetch(p);
            } catch (Exception ignored) {
                // Sin conexión o sin artículo: la ficha se queda como estaba.
            }
            final WikiInfo.Info result = info;
            if (result != null) {
                synchronized (infoCache) {
                    infoCache.put(cacheKey, result);
                }
            }
            activity.runOnUiThread(() -> {
                if (myToken != token || activity.isDestroyed()) {
                    return;
                }
                if (result == null) {
                    reset();
                } else {
                    render(result);
                }
            });
        });
    }

    private void render(final WikiInfo.Info info) {
        reset();
        block.setVisibility(View.VISIBLE);
        extractView.setVisibility(View.VISIBLE);
        extractView.setText(info.extract);
        extractView.setOnClickListener(v -> showFull(info));
        sourceView.setVisibility(View.VISIBLE);

        if (info.images.isEmpty()) {
            return;
        }
        gallery.setVisibility(View.VISIBLE);
        final int myToken = token;
        for (int i = 0; i < info.images.size(); i++) {
            final String url = info.images.get(i);
            final String caption = info.captions.get(i);
            final ImageView iv = new ImageView(activity);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(132), dp(96));
            lp.setMarginEnd(dp(6));
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundResource(R.color.divider);
            iv.setContentDescription(caption);
            iv.setOnClickListener(v -> showPhoto(url, caption));
            strip.addView(iv);

            Bitmap hit = bitmaps.get(url);
            if (hit != null) {
                iv.setImageBitmap(hit);
                continue;
            }
            imageIo.execute(() -> {
                Bitmap bmp = null;
                try {
                    bmp = WikiInfo.loadBitmap(url, 480);
                } catch (Exception ignored) {
                }
                final Bitmap result = bmp;
                if (result != null) {
                    bitmaps.put(url, result);
                }
                activity.runOnUiThread(() -> {
                    if (myToken != token || activity.isDestroyed()) {
                        return;
                    }
                    if (result != null) {
                        iv.setImageBitmap(result);
                    } else {
                        iv.setVisibility(View.GONE); // foto que no cargó: se quita
                    }
                });
            });
        }
    }

    private void showFull(final WikiInfo.Info info) {
        new AlertDialog.Builder(activity)
                .setTitle(info.title)
                .setMessage(info.extract + "\n\n" + activity.getString(R.string.info_license))
                .setPositiveButton(R.string.info_open_wikipedia, (d, w) -> openUrl(info.pageUrl))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void showPhoto(String url, String caption) {
        Bitmap bmp = bitmaps.get(url);
        if (bmp == null) {
            return;
        }
        ImageView big = new ImageView(activity);
        big.setAdjustViewBounds(true);
        big.setScaleType(ImageView.ScaleType.FIT_CENTER);
        big.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        big.setImageBitmap(bmp);
        new AlertDialog.Builder(activity)
                .setTitle(caption)
                .setView(big)
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void openUrl(String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}

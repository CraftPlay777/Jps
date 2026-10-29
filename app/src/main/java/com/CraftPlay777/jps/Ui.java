package com.CraftPlay777.jps;

import android.app.Activity;
import android.os.Build;
import android.text.Html;
import android.widget.TextView;

import java.util.Locale;

/** Utilidades de interfaz compartidas por las pantallas. */
final class Ui {

    private Ui() {
    }

    /** Configura la cabecera azul con botón de volver y título. */
    static void setupHeader(Activity activity, int titleRes) {
        ((TextView) activity.findViewById(R.id.header_title)).setText(titleRes);
        activity.findViewById(R.id.btn_back).setOnClickListener(v -> activity.finish());
    }

    static CharSequence html(String source) {
        if (Build.VERSION.SDK_INT >= 24) {
            return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY);
        }
        return Html.fromHtml(source);
    }

    static String formatDistance(double meters, boolean imperial) {
        Locale l = Locale.getDefault();
        if (imperial) {
            double feet = meters * 3.28084;
            if (feet < 528) {
                return String.format(l, "%d ft", Math.round(feet));
            }
            return String.format(l, "%.1f mi", meters / 1609.344);
        }
        if (meters < 1000) {
            return String.format(l, "%d m", Math.round(meters));
        }
        return String.format(l, "%.1f km", meters / 1000.0);
    }
}

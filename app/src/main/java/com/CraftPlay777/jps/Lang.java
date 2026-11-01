package com.CraftPlay777.jps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** Idiomas de la app: español, inglés, chino mandarín y francés. Se elige al primer inicio. */
final class Lang {

    static final String[] CODES = {"es", "en", "zh", "fr"};
    /** Cada idioma se muestra con su propio nombre, para que se reconozca aunque no lo entiendas. */
    static final String[] NAMES = {"Español", "English", "中文（普通话）", "Français"};

    private Lang() {
    }

    static int indexOf(String code) {
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equals(code)) {
                return i;
            }
        }
        return -1;
    }

    /** Idioma que se usa ahora: el elegido o, si aún no eligió, el del móvil (si lo soportamos). */
    static String current(Context c) {
        String saved = Store.getLang(c);
        if (saved != null && indexOf(saved) >= 0) {
            return saved;
        }
        String device = Locale.getDefault().getLanguage();
        return indexOf(device) >= 0 ? device : "es";
    }

    /** Nombre del idioma elegido, tal como se escribe en su idioma. */
    static String currentName(Context c) {
        return NAMES[Math.max(0, indexOf(current(c)))];
    }

    /** Aplica el idioma elegido a los textos de la pantalla (igual que Theming con el tema). */
    static Context wrap(Context base) {
        String code = Store.getLang(base);
        if (code == null || indexOf(code) < 0) {
            return base;
        }
        Locale locale = new Locale(code);
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }

    /**
     * Diálogo para elegir idioma. Al elegir se guarda y se recrea la pantalla.
     * Con cancelable = false (primer inicio) no se puede cerrar sin elegir.
     */
    static AlertDialog showChooser(Activity activity, boolean cancelable) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle(R.string.language_dialog_title)
                .setCancelable(cancelable)
                .setSingleChoiceItems(NAMES, indexOf(current(activity)), (dialog, which) -> {
                    Store.setLang(activity, CODES[which]);
                    dialog.dismiss();
                    activity.recreate();
                });
        if (cancelable) {
            b.setNegativeButton(R.string.cancel, null);
        }
        return b.show();
    }
}

package com.CraftPlay777.jps;

import android.content.Context;
import android.content.res.Configuration;

/** Tema de la app: 0 = según el dispositivo, 1 = claro (blanco), 2 = oscuro (negro). */
final class Theming {

    static final int DEVICE = 0;
    static final int LIGHT = 1;
    static final int DARK = 2;

    private Theming() {
    }

    /**
     * Fuerza el modo día/noche de la app sin depender del sistema. Con eso se aplican
     * automáticamente los recursos de values-night (colores y tema oscuro).
     */
    static Context wrap(Context context) {
        Context base = Lang.wrap(context); // idioma elegido
        int mode = Store.getThemeMode(base);
        if (mode == DEVICE) {
            return base;
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        int night = mode == DARK ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(config);
    }
}

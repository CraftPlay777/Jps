package com.CraftPlay777.jps;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

/** Base de todas las pantallas: aplica el tema y el idioma elegidos y se recrea si cambian. */
public abstract class BaseActivity extends Activity {

    private int themeAtCreate;
    private String langAtCreate;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Theming.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        themeAtCreate = Store.getThemeMode(this);
        langAtCreate = Store.getLang(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String lang = Store.getLang(this);
        boolean langChanged = lang == null ? langAtCreate != null : !lang.equals(langAtCreate);
        if (langChanged || Store.getThemeMode(this) != themeAtCreate) {
            recreate();
        }
    }
}

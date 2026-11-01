package com.CraftPlay777.jps;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

/** Base de todas las pantallas: aplica el tema elegido y se recrea si el tema cambia. */
public abstract class BaseActivity extends Activity {

    private int themeAtCreate;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Theming.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        themeAtCreate = Store.getThemeMode(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Store.getThemeMode(this) != themeAtCreate) {
            recreate();
        }
    }
}

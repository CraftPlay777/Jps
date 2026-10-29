package com.CraftPlay777.jps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;

/** Ajustes: apariencia del mapa, unidades, borrado de datos y accesos a información legal. */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.setupHeader(this, R.string.settings);

        Switch dark = findViewById(R.id.sw_dark);
        dark.setChecked(Store.isDarkMap(this));
        dark.setOnCheckedChangeListener((button, checked) -> Store.setDarkMap(this, checked));

        Switch imperial = findViewById(R.id.sw_imperial);
        imperial.setChecked(Store.isImperial(this));
        imperial.setOnCheckedChangeListener((button, checked) -> Store.setImperial(this, checked));

        Switch keepOn = findViewById(R.id.sw_keep_on);
        keepOn.setChecked(Store.isKeepScreenOn(this));
        keepOn.setOnCheckedChangeListener((button, checked) -> Store.setKeepScreenOn(this, checked));

        findViewById(R.id.row_clear_history).setOnClickListener(v -> {
            Store.clearHistory(this);
            Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.row_clear_favorites).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setMessage(R.string.clear_favorites_confirm)
                        .setPositiveButton(R.string.delete_action, (d, w) -> {
                            Store.clearFavorites(this);
                            Toast.makeText(this, R.string.favorites_cleared, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show());

        findViewById(R.id.row_privacy).setOnClickListener(v -> openPage(InfoActivity.PAGE_PRIVACY));
        findViewById(R.id.row_about).setOnClickListener(v -> openPage(InfoActivity.PAGE_ABOUT));
    }

    private void openPage(String page) {
        Intent i = new Intent(this, InfoActivity.class);
        i.putExtra(InfoActivity.EXTRA_PAGE, page);
        startActivity(i);
    }
}

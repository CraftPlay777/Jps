package com.CraftPlay777.jps;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lista de lugares favoritos. Al tocar uno, vuelve al mapa y lo muestra. */
public class FavoritesActivity extends Activity {

    private final List<Place> items = new ArrayList<>();
    private ArrayAdapter<Place> adapter;
    private View emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);
        Ui.setupHeader(this, R.string.favorites);

        ListView list = findViewById(R.id.favorites_list);
        emptyText = findViewById(R.id.empty_text);

        adapter = new ArrayAdapter<Place>(this, 0, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView != null
                        ? convertView
                        : getLayoutInflater().inflate(R.layout.item_favorite, parent, false);
                Place p = getItem(position);
                ((TextView) row.findViewById(R.id.fav_name)).setText(p.name);
                ((TextView) row.findViewById(R.id.fav_coords)).setText(
                        String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon));
                row.findViewById(R.id.btn_delete).setOnClickListener(v -> {
                    Store.removeFavorite(FavoritesActivity.this, position);
                    reload();
                });
                return row;
            }
        };
        list.setAdapter(adapter);

        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= items.size()) {
                return;
            }
            Place p = items.get(position);
            Intent result = new Intent();
            result.putExtra("name", p.name);
            result.putExtra("lat", p.lat);
            result.putExtra("lon", p.lon);
            setResult(RESULT_OK, result);
            finish();
        });

        reload();
    }

    private void reload() {
        items.clear();
        items.addAll(Store.getFavorites(this));
        adapter.notifyDataSetChanged();
        emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }
}

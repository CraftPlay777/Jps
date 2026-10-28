package com.CraftPlay777.jps;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Jps: mapa con búsqueda de lugares, mi ubicación, marcadores, capas y compartir.
 * Usa OpenStreetMap (osmdroid), así que no necesita API key.
 */
public class MainActivity extends Activity {

    private static final int REQ_LOCATION = 1;
    private static final String PREFS = "jps";

    private static final XYTileSource TOPO = new XYTileSource(
            "OpenTopoMap", 0, 17, 256, ".png",
            new String[]{
                    "https://a.tile.opentopomap.org/",
                    "https://b.tile.opentopomap.org/",
                    "https://c.tile.opentopomap.org/"},
            "\u00A9 OpenStreetMap contributors, SRTM | \u00A9 OpenTopoMap (CC-BY-SA)");

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Place> currentResults = new ArrayList<>();

    private MapView map;
    private MyLocationNewOverlay myLocation;
    private Marker marker;
    private Place selected;
    private boolean topo;

    private EditText searchInput;
    private View clearButton;
    private ListView resultsList;
    private ArrayAdapter<String> resultsAdapter;
    private View card;
    private TextView cardTitle;
    private TextView cardCoords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configuración de osmdroid: antes de crear el mapa.
        Configuration cfg = Configuration.getInstance();
        cfg.setUserAgentValue(getPackageName());
        cfg.setOsmdroidBasePath(new File(getCacheDir(), "osmdroid"));
        cfg.setOsmdroidTileCache(new File(getCacheDir(), "osmdroid/tiles"));

        setContentView(R.layout.activity_main);

        searchInput = findViewById(R.id.search_input);
        clearButton = findViewById(R.id.btn_clear);
        resultsList = findViewById(R.id.results_list);
        card = findViewById(R.id.card);
        cardTitle = findViewById(R.id.card_title);
        cardCoords = findViewById(R.id.card_coords);
        map = findViewById(R.id.map);

        setupMap();
        setupSearch();
        setupButtons();

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!sp.contains("lat")) {
            // Primera vez: pedimos ubicación para centrar el mapa en el usuario.
            if (hasLocationPermission()) {
                startLocation();
            } else {
                requestLocationPermission();
            }
        }
    }

    // ------------------------------------------------------------------ mapa

    private void setupMap() {
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        map.setMinZoomLevel(3.0);
        map.setMaxZoomLevel(19.0);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        topo = sp.getBoolean("topo", false);
        applyTileSource();

        double lat = Double.longBitsToDouble(sp.getLong("lat", Double.doubleToLongBits(20.0)));
        double lon = Double.longBitsToDouble(sp.getLong("lon", Double.doubleToLongBits(0.0)));
        double zoom = sp.getFloat("zoom", 3.0f);
        map.getController().setZoom(zoom);
        map.getController().setCenter(new GeoPoint(lat, lon));

        // Toques en el mapa: se añade primero para que los marcadores tengan prioridad.
        map.getOverlays().add(0, new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                hideKeyboard();
                hideResults();
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                showPlace(new Place(null, p.getLatitude(), p.getLongitude()), false);
                return true;
            }
        }));

        map.getOverlays().add(new CopyrightOverlay(this));

        ScaleBarOverlay scale = new ScaleBarOverlay(map);
        scale.setAlignBottom(true);
        scale.setScaleBarOffset(dp(12), dp(32));
        map.getOverlays().add(scale);

        myLocation = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        map.getOverlays().add(myLocation);
    }

    private void applyTileSource() {
        map.setTileSource(topo ? TOPO : TileSourceFactory.MAPNIK);
    }

    private void toggleLayer() {
        topo = !topo;
        applyTileSource();
        Toast.makeText(this,
                topo ? R.string.layer_relief : R.string.layer_standard,
                Toast.LENGTH_SHORT).show();
    }

    // -------------------------------------------------------------- búsqueda

    private void setupSearch() {
        resultsAdapter = new ArrayAdapter<String>(this, R.layout.item_result, new ArrayList<String>());
        resultsList.setAdapter(resultsAdapter);
        resultsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < currentResults.size()) {
                Place p = currentResults.get(position);
                hideResults();
                showPlace(p, true);
            }
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void doSearch() {
        final String q = searchInput.getText().toString().trim();
        if (q.isEmpty()) {
            return;
        }
        hideKeyboard();
        io.execute(() -> {
            try {
                List<Place> found = Nominatim.search(q);
                runOnUiThread(() -> showResults(found));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.error_network,
                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showResults(List<Place> found) {
        if (found.isEmpty()) {
            Toast.makeText(this, R.string.no_results, Toast.LENGTH_SHORT).show();
            return;
        }
        if (found.size() == 1) {
            showPlace(found.get(0), true);
            return;
        }
        currentResults.clear();
        currentResults.addAll(found);
        resultsAdapter.clear();
        for (Place p : found) {
            resultsAdapter.add(p.name);
        }
        resultsAdapter.notifyDataSetChanged();
        resultsList.setVisibility(View.VISIBLE);
    }

    private void hideResults() {
        resultsList.setVisibility(View.GONE);
    }

    // ------------------------------------------------------ marcador y ficha

    private void showPlace(Place p, boolean zoomTo) {
        selected = p;

        if (marker == null) {
            marker = new Marker(map);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(getResources().getDrawable(R.drawable.ic_pin, getTheme()));
            marker.setOnMarkerClickListener((m, mapView) -> true);
            map.getOverlays().add(marker);
        }
        GeoPoint point = new GeoPoint(p.lat, p.lon);
        marker.setPosition(point);

        cardTitle.setText(p.name != null ? p.name : getString(R.string.searching_address));
        cardCoords.setText(String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon));
        card.setVisibility(View.VISIBLE);

        if (zoomTo) {
            map.getController().animateTo(point, 16.0, 800L);
        }
        map.invalidate();

        if (p.name == null) {
            // Pin puesto con pulsación larga: buscamos su dirección.
            io.execute(() -> {
                String name = null;
                try {
                    name = Nominatim.reverse(p.lat, p.lon);
                } catch (Exception ignored) {
                }
                final String result = name != null ? name : getString(R.string.pin_dropped);
                runOnUiThread(() -> {
                    if (selected == p) {
                        p.name = result;
                        cardTitle.setText(result);
                    }
                });
            });
        }
    }

    private void clearSelection() {
        selected = null;
        card.setVisibility(View.GONE);
        if (marker != null) {
            map.getOverlays().remove(marker);
            marker = null;
            map.invalidate();
        }
    }

    private void shareSelected() {
        if (selected == null) {
            return;
        }
        String link = String.format(Locale.US,
                "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=17/%.6f/%.6f",
                selected.lat, selected.lon, selected.lat, selected.lon);
        String text = (selected.name != null ? selected.name + "\n" : "") + link;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, getString(R.string.share_place)));
    }

    // ----------------------------------------------------------------- botones

    private void setupButtons() {
        findViewById(R.id.btn_search).setOnClickListener(v -> doSearch());
        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            searchInput.setText("");
            hideResults();
            clearSelection();
        });
        findViewById(R.id.btn_zoom_in).setOnClickListener(v -> map.getController().zoomIn());
        findViewById(R.id.btn_zoom_out).setOnClickListener(v -> map.getController().zoomOut());
        findViewById(R.id.btn_layers).setOnClickListener(v -> toggleLayer());
        findViewById(R.id.btn_my_location).setOnClickListener(v -> {
            if (hasLocationPermission()) {
                startLocation();
            } else {
                requestLocationPermission();
            }
        });
        findViewById(R.id.btn_share).setOnClickListener(v -> shareSelected());
        findViewById(R.id.btn_close_card).setOnClickListener(v -> clearSelection());
    }

    // --------------------------------------------------------------- ubicación

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
    }

    private void startLocation() {
        myLocation.enableMyLocation();
        GeoPoint here = myLocation.getMyLocation();
        if (here != null) {
            map.getController().animateTo(here, 17.0, 800L);
        } else {
            Toast.makeText(this, R.string.finding_location, Toast.LENGTH_SHORT).show();
            myLocation.runOnFirstFix(() -> runOnUiThread(() -> {
                GeoPoint g = myLocation.getMyLocation();
                if (g != null) {
                    map.getController().animateTo(g, 17.0, 800L);
                }
            }));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) {
            return;
        }
        if (hasLocationPermission()) {
            startLocation();
        } else {
            Toast.makeText(this, R.string.location_denied, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------ ciclo de vida

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        if (hasLocationPermission()) {
            myLocation.enableMyLocation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveView();
        myLocation.disableMyLocation();
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
        map.onDetach();
    }

    @Override
    public void onBackPressed() {
        if (resultsList.getVisibility() == View.VISIBLE) {
            hideResults();
        } else if (card.getVisibility() == View.VISIBLE) {
            clearSelection();
        } else {
            super.onBackPressed();
        }
    }

    private void saveView() {
        IGeoPoint c = map.getMapCenter();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong("lat", Double.doubleToLongBits(c.getLatitude()))
                .putLong("lon", Double.doubleToLongBits(c.getLongitude()))
                .putFloat("zoom", (float) map.getZoomLevelDouble())
                .putBoolean("topo", topo)
                .apply();
    }

    // ----------------------------------------------------------------- utilidades

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
        searchInput.clearFocus();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

package com.CraftPlay777.jps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Jps: mapa con búsqueda, mi ubicación, favoritos, historial, capas y ajustes.
 * Usa OpenStreetMap (osmdroid), así que no necesita API key.
 */
public class MainActivity extends Activity {

    private static final int REQ_LOCATION = 1;
    private static final int REQ_FAVORITE = 2;

    private static final int MENU_FAVORITES = 1;
    private static final int MENU_SETTINGS = 2;
    private static final int MENU_PRIVACY = 3;
    private static final int MENU_ABOUT = 4;
    private static final int MENU_REPORT = 5;

    private static final String ISSUES_URL = "https://github.com/CraftPlay777/Jps/issues";

    private static final XYTileSource TOPO = new XYTileSource(
            "OpenTopoMap", 0, 17, 256, ".png",
            new String[]{
                    "https://a.tile.opentopomap.org/",
                    "https://b.tile.opentopomap.org/",
                    "https://c.tile.opentopomap.org/"},
            "\u00A9 OpenStreetMap contributors, SRTM | \u00A9 OpenTopoMap (CC-BY-SA)");

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService boundaryIo = Executors.newSingleThreadExecutor();
    private final List<Place> currentResults = new ArrayList<>();
    private final List<String> historyItems = new ArrayList<>();

    private MapView map;
    private MyLocationNewOverlay myLocation;
    private ScaleBarOverlay scale;
    private Marker marker;
    private FolderOverlay boundary;
    private int boundaryToken;
    private Place selected;
    private boolean topo;
    private boolean showingHistory;
    private AlertDialog consentDialog;

    private EditText searchInput;
    private View clearButton;
    private ListView resultsList;
    private ArrayAdapter<String> resultsAdapter;
    private View card;
    private TextView cardTitle;
    private TextView cardCoords;
    private ImageButton favoriteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configuración de osmdroid: antes de crear el mapa.
        IConfigurationProvider cfg = Configuration.getInstance();
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
        favoriteButton = findViewById(R.id.btn_favorite);
        map = findViewById(R.id.map);

        setupMap();
        setupSearch();
        setupButtons();

        if (Store.isConsentGiven(this)) {
            onConsentGiven();
        } else {
            // Hasta que el usuario acepte, la app no hace ninguna conexión.
            map.setUseDataConnection(false);
        }
    }

    // ------------------------------------------------------------- privacidad

    private void maybeAskConsent() {
        if (Store.isConsentGiven(this)) {
            return;
        }
        if (consentDialog != null && consentDialog.isShowing()) {
            return;
        }
        consentDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.consent_title)
                .setMessage(R.string.consent_message)
                .setCancelable(false)
                .setPositiveButton(R.string.consent_accept, (d, w) -> {
                    Store.setConsentGiven(this, true);
                    onConsentGiven();
                })
                .setNegativeButton(R.string.consent_read, (d, w) -> openPage(InfoActivity.PAGE_PRIVACY))
                .setNeutralButton(R.string.consent_exit, (d, w) -> finish())
                .show();
    }

    private void onConsentGiven() {
        map.setUseDataConnection(true);
        applyTileSource();
        if (!Store.hasPosition(this)) {
            // Primera vez: centramos el mapa en el usuario.
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

        topo = Store.isTopo(this);
        applyTileSource();

        map.getController().setZoom((double) Store.getZoom(this));
        map.getController().setCenter(new GeoPoint(Store.getLat(this), Store.getLon(this)));

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

        RotationGestureOverlay rotation = new RotationGestureOverlay(map);
        rotation.setEnabled(true);
        map.getOverlays().add(rotation);

        map.getOverlays().add(new CopyrightOverlay(this));

        scale = new ScaleBarOverlay(map);
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

    /** Aplica los ajustes guardados (se llama al volver de la pantalla de ajustes). */
    private void applySettings() {
        boolean dark = Store.isDarkMap(this);
        map.getOverlayManager().getTilesOverlay()
                .setColorFilter(dark ? TilesOverlay.INVERT_COLORS : null);

        scale.setUnitsOfMeasure(Store.isImperial(this)
                ? ScaleBarOverlay.UnitsOfMeasure.imperial
                : ScaleBarOverlay.UnitsOfMeasure.metric);

        if (Store.isKeepScreenOn(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        updateCardInfo();
        map.invalidate();
    }

    // -------------------------------------------------------------- búsqueda

    private void setupSearch() {
        resultsAdapter = new ArrayAdapter<String>(this, R.layout.item_result, new ArrayList<String>());
        resultsList.setAdapter(resultsAdapter);
        resultsList.setOnItemClickListener((parent, view, position, id) -> {
            if (showingHistory) {
                if (position < historyItems.size()) {
                    String q = historyItems.get(position);
                    searchInput.setText(q);
                    searchInput.setSelection(q.length());
                    doSearch();
                }
                return;
            }
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

        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && searchInput.getText().length() == 0) {
                showHistory();
            }
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
                if (s.length() > 0) {
                    if (showingHistory) {
                        hideResults();
                    }
                } else {
                    clearBoundary();
                    if (searchInput.hasFocus()) {
                        showHistory();
                    }
                }
            }
        });
    }

    private void doSearch() {
        final String q = searchInput.getText().toString().trim();
        if (q.isEmpty()) {
            return;
        }
        hideKeyboard();
        Store.addHistory(this, q);
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
        showingHistory = false;
        currentResults.clear();
        currentResults.addAll(found);
        resultsAdapter.clear();
        for (Place p : found) {
            resultsAdapter.add(p.name);
        }
        resultsList.setVisibility(View.VISIBLE);
    }

    private void showHistory() {
        List<String> history = Store.getHistory(this);
        if (history.isEmpty()) {
            hideResults();
            return;
        }
        showingHistory = true;
        historyItems.clear();
        historyItems.addAll(history);
        resultsAdapter.clear();
        resultsAdapter.addAll(history);
        resultsList.setVisibility(View.VISIBLE);
    }

    private void hideResults() {
        showingHistory = false;
        resultsList.setVisibility(View.GONE);
    }

    // ------------------------------------------------------ marcador y ficha

    private void showPlace(Place p, boolean zoomTo) {
        selected = p;
        if (p.osmType != null) {
            loadBoundary(p);
        }

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
        updateCardInfo();
        card.setVisibility(View.VISIBLE);

        if (zoomTo) {
            zoomToPlace(p, point);
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

    /** Actualiza coordenadas, distancia a mi posición y la estrella de favorito. */
    private void updateCardInfo() {
        if (selected == null) {
            return;
        }
        StringBuilder sb = new StringBuilder(
                String.format(Locale.US, "%.5f, %.5f", selected.lat, selected.lon));
        GeoPoint here = myLocation.getMyLocation();
        if (here != null) {
            float[] result = new float[1];
            Location.distanceBetween(here.getLatitude(), here.getLongitude(),
                    selected.lat, selected.lon, result);
            sb.append("  \u00B7  ").append(getString(R.string.distance_from_you,
                    Ui.formatDistance(result[0], Store.isImperial(this))));
        }
        cardCoords.setText(sb.toString());
        favoriteButton.setImageResource(Store.isFavorite(this, selected)
                ? R.drawable.ic_star : R.drawable.ic_star_border);
    }

    // ------------------------------------------------------- contorno de la zona

    /** Si el lugar es una zona grande (país, provincia, ciudad) encuadra toda la zona. */
    private void zoomToPlace(Place p, GeoPoint point) {
        if (p.bbox != null) {
            double south = p.bbox[0];
            double north = p.bbox[1];
            double west = p.bbox[2];
            double east = p.bbox[3];
            if (Math.max(north - south, east - west) > 0.01) {
                map.zoomToBoundingBox(new BoundingBox(north, east, south, west), true, dp(80));
                return;
            }
        }
        map.getController().animateTo(point, 16.0, 800L);
    }

    /** Descarga el contorno del lugar y lo dibuja con líneas rojas. */
    private void loadBoundary(Place p) {
        clearBoundary();
        if (p.osmType == null || "node".equals(p.osmType)) {
            return; // un punto no tiene contorno
        }
        final int token = boundaryToken;
        boundaryIo.execute(() -> {
            try {
                List<List<GeoPoint>> rings = Nominatim.boundary(p.osmType, p.osmId, p.bbox);
                runOnUiThread(() -> {
                    if (token == boundaryToken) {
                        showBoundary(rings);
                    }
                });
            } catch (Exception ignored) {
                // Sin contorno: se queda solo el marcador.
            }
        });
    }

    private void showBoundary(List<List<GeoPoint>> rings) {
        if (rings.isEmpty()) {
            return;
        }
        FolderOverlay folder = new FolderOverlay();
        for (List<GeoPoint> ring : rings) {
            Polygon poly = new Polygon();
            poly.setPoints(ring);
            poly.getFillPaint().setColor(Color.TRANSPARENT);
            poly.getOutlinePaint().setColor(Color.RED);
            poly.getOutlinePaint().setStrokeWidth(dp(3));
            // Sin ventana de información, y los toques siguen pasando al mapa.
            poly.setOnClickListener((polygon, mv, pos) -> false);
            folder.add(poly);
        }
        boundary = folder;
        map.getOverlays().add(1, folder);
        map.invalidate();
    }

    private void clearBoundary() {
        boundaryToken++;
        if (boundary != null) {
            map.getOverlays().remove(boundary);
            boundary = null;
            map.invalidate();
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

    private void toggleFavorite() {
        if (selected == null) {
            return;
        }
        String name = selected.name != null
                ? selected.name
                : String.format(Locale.US, "%.5f, %.5f", selected.lat, selected.lon);
        boolean now = Store.toggleFavorite(this, new Place(name, selected.lat, selected.lon));
        Toast.makeText(this,
                now ? R.string.favorite_added : R.string.favorite_removed,
                Toast.LENGTH_SHORT).show();
        updateCardInfo();
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

    private void copyCoordinates() {
        if (selected == null) {
            return;
        }
        String text = String.format(Locale.US, "%.6f, %.6f", selected.lat, selected.lon);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("coordenadas", text));
            Toast.makeText(this, R.string.coords_copied, Toast.LENGTH_SHORT).show();
        }
    }

    /** Abre el lugar en otra app de mapas o navegación instalada en el móvil. */
    private void openDirections() {
        if (selected == null) {
            return;
        }
        Uri uri = Uri.parse(String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f",
                selected.lat, selected.lon, selected.lat, selected.lon));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    // ----------------------------------------------------------------- botones

    private void setupButtons() {
        findViewById(R.id.btn_menu).setOnClickListener(this::showMenu);
        findViewById(R.id.btn_search).setOnClickListener(v -> doSearch());
        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            searchInput.setText("");
            hideResults();
            clearSelection();
            clearBoundary();
        });
        findViewById(R.id.btn_zoom_in).setOnClickListener(v -> map.getController().zoomIn());
        findViewById(R.id.btn_zoom_out).setOnClickListener(v -> map.getController().zoomOut());
        findViewById(R.id.btn_compass).setOnClickListener(v -> {
            map.setMapOrientation(0f);
            map.invalidate();
        });
        findViewById(R.id.btn_layers).setOnClickListener(v -> toggleLayer());
        findViewById(R.id.btn_my_location).setOnClickListener(v -> {
            if (hasLocationPermission()) {
                startLocation();
            } else {
                requestLocationPermission();
            }
        });

        findViewById(R.id.btn_favorite).setOnClickListener(v -> toggleFavorite());
        findViewById(R.id.btn_close_card).setOnClickListener(v -> clearSelection());
        findViewById(R.id.btn_directions).setOnClickListener(v -> openDirections());
        findViewById(R.id.btn_share).setOnClickListener(v -> shareSelected());
        findViewById(R.id.btn_copy).setOnClickListener(v -> copyCoordinates());
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, MENU_FAVORITES, 0, R.string.favorites);
        menu.getMenu().add(0, MENU_SETTINGS, 1, R.string.settings);
        menu.getMenu().add(0, MENU_PRIVACY, 2, R.string.privacy_title);
        menu.getMenu().add(0, MENU_ABOUT, 3, R.string.about_title);
        menu.getMenu().add(0, MENU_REPORT, 4, R.string.report_problem);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_FAVORITES:
                    startActivityForResult(new Intent(this, FavoritesActivity.class), REQ_FAVORITE);
                    return true;
                case MENU_SETTINGS:
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                case MENU_PRIVACY:
                    openPage(InfoActivity.PAGE_PRIVACY);
                    return true;
                case MENU_ABOUT:
                    openPage(InfoActivity.PAGE_ABOUT);
                    return true;
                case MENU_REPORT:
                    openUrl(ISSUES_URL);
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void openPage(String page) {
        Intent i = new Intent(this, InfoActivity.class);
        i.putExtra(InfoActivity.EXTRA_PAGE, page);
        startActivity(i);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FAVORITE && resultCode == RESULT_OK && data != null) {
            String name = data.getStringExtra("name");
            double lat = data.getDoubleExtra("lat", 0);
            double lon = data.getDoubleExtra("lon", 0);
            clearBoundary();
            showPlace(new Place(name, lat, lon), true);
        }
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
                    updateCardInfo();
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
        applySettings();
        if (Store.isConsentGiven(this) && hasLocationPermission()) {
            myLocation.enableMyLocation();
        }
        maybeAskConsent();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Store.isConsentGiven(this)) {
            saveView();
        }
        myLocation.disableMyLocation();
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (consentDialog != null) {
            consentDialog.dismiss();
        }
        io.shutdown();
        boundaryIo.shutdown();
        map.onDetach();
    }

    @Override
    public void onBackPressed() {
        if (resultsList.getVisibility() == View.VISIBLE) {
            hideResults();
        } else if (card.getVisibility() == View.VISIBLE) {
            clearSelection();
        } else if (boundary != null) {
            searchInput.setText("");
            clearBoundary();
        } else {
            super.onBackPressed();
        }
    }

    private void saveView() {
        IGeoPoint c = map.getMapCenter();
        Store.savePosition(this, c.getLatitude(), c.getLongitude(),
                (float) map.getZoomLevelDouble(), topo);
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

package com.CraftPlay777.jps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Cómo llegar" dentro de la app: elige vehículo y velocidad, calcula la ruta con el servicio de
 * OpenStreetMap, la dibuja en el mapa y muestra distancia y tiempo.
 */
final class RouteController {

    interface Host {
        /** Mi ubicación actual, o null si todavía no hay. */
        GeoPoint here();

        /** Pide el permiso de ubicación o activa la búsqueda del GPS. */
        void askLocation();

        /** Muestra u oculta la ficha del lugar (solo se muestra si hay un lugar marcado). */
        void setPlaceCardVisible(boolean visible);

        void openInOtherApp();

        int dp(int value);
    }

    private final Activity activity;
    private final MapView map;
    private final Overlay anchor;
    private final Host host;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final View panel;
    private final TextView destView;
    private final Spinner vehicleSpinner;
    private final EditText speedInput;
    private final Switch staySwitch;
    private final Button goButton;
    private final Button stepsButton;
    private final TextView resultView;

    private Place destination;
    private Routing.Route route;
    private RouteOverlay overlay;
    private int token;
    private boolean binding;

    RouteController(Activity activity, MapView map, Overlay anchor, Host host) {
        this.activity = activity;
        this.map = map;
        this.anchor = anchor;
        this.host = host;

        panel = activity.findViewById(R.id.route_panel);
        destView = activity.findViewById(R.id.route_dest);
        vehicleSpinner = activity.findViewById(R.id.route_vehicle);
        speedInput = activity.findViewById(R.id.route_speed);
        staySwitch = activity.findViewById(R.id.route_stay);
        goButton = activity.findViewById(R.id.btn_route_go);
        stepsButton = activity.findViewById(R.id.btn_route_steps);
        resultView = activity.findViewById(R.id.route_result);

        String[] names = new String[Vehicle.ALL.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = activity.getString(Vehicle.ALL[i].nameRes);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                activity, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vehicleSpinner.setAdapter(adapter);
        vehicleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!binding) {
                    Store.setVehicle(RouteController.this.activity, Vehicle.ALL[position].id);
                    showSpeedFor(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        goButton.setOnClickListener(v -> calculate());
        stepsButton.setOnClickListener(v -> showSteps());
        activity.findViewById(R.id.btn_route_external).setOnClickListener(v -> host.openInOtherApp());
        activity.findViewById(R.id.btn_route_close).setOnClickListener(v -> close());
    }

    boolean isOpen() {
        return panel.getVisibility() == View.VISIBLE;
    }

    /** Abre el panel para llegar al lugar marcado. */
    void open(Place dest) {
        destination = dest;
        removeOverlay();
        route = null;
        token++;
        destView.setText(dest.name != null && !dest.name.isEmpty()
                ? dest.name
                : String.format(Locale.US, "%.5f, %.5f", dest.lat, dest.lon));
        binding = true;
        vehicleSpinner.setSelection(Vehicle.indexOf(Store.getVehicle(activity)));
        binding = false;
        showSpeedFor(vehicleSpinner.getSelectedItemPosition());
        staySwitch.setChecked(Store.isRouteStay(activity));
        resultView.setText("");
        stepsButton.setVisibility(View.GONE);
        goButton.setEnabled(true);
        panel.setVisibility(View.VISIBLE);
        host.setPlaceCardVisible(false);
    }

    void close() {
        if (!isOpen() && overlay == null) {
            return;
        }
        token++; // descarta cualquier cálculo en curso
        removeOverlay();
        route = null;
        panel.setVisibility(View.GONE);
        host.setPlaceCardVisible(true);
    }

    void shutdown() {
        io.shutdown();
    }

    // ------------------------------------------------------------------ velocidad

    private static String speedText(double kmh) {
        return kmh == Math.floor(kmh)
                ? String.valueOf((long) kmh)
                : String.format(Locale.US, "%.1f", kmh);
    }

    private void showSpeedFor(int position) {
        Vehicle v = Vehicle.ALL[position];
        float custom = Store.getSpeed(activity, v.id);
        speedInput.setText(speedText(custom > 0f ? custom : v.defaultKmh));
    }

    /** Lee la velocidad escrita; null (con aviso) si no es válida. Guarda la personalizada. */
    private Double readSpeed(Vehicle v) {
        String text = speedInput.getText().toString().trim().replace(',', '.');
        double kmh;
        try {
            kmh = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            kmh = 0;
        }
        if (!(kmh > 0) || kmh > 400) {
            Toast.makeText(activity, R.string.route_bad_speed, Toast.LENGTH_SHORT).show();
            return null;
        }
        Store.setSpeed(activity, v.id, Math.abs(kmh - v.defaultKmh) < 0.05 ? 0f : (float) kmh);
        return kmh;
    }

    // -------------------------------------------------------------------- cálculo

    private void calculate() {
        if (destination == null) {
            return;
        }
        final Vehicle vehicle = Vehicle.ALL[vehicleSpinner.getSelectedItemPosition()];
        final Double kmh = readSpeed(vehicle);
        if (kmh == null) {
            return;
        }
        if (!Store.isRouteConsent(activity)) {
            new AlertDialog.Builder(activity)
                    .setMessage(R.string.route_consent_message)
                    .setPositiveButton(R.string.consent_accept, (d, w) -> {
                        Store.setRouteConsent(activity, true);
                        calculate();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        final GeoPoint here = host.here();
        if (here == null) {
            Toast.makeText(activity, R.string.route_no_location, Toast.LENGTH_LONG).show();
            host.askLocation();
            return;
        }
        final boolean stay = staySwitch.isChecked();
        Store.setRouteStay(activity, stay);
        hideKeyboard();

        Regions regions = Regions.get(activity);
        if (stay && regions.countries() == null) {
            // Para no salir del país necesitamos los bordes de los países.
            if (!Store.isRegionsConsent(activity)) {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.regions_consent_title)
                        .setMessage(R.string.regions_consent_message)
                        .setPositiveButton(R.string.consent_accept, (d, w) -> {
                            Store.setRegionsConsent(activity, true);
                            calculate();
                        })
                        .setNegativeButton(R.string.cancel, (d, w) -> {
                            staySwitch.setChecked(false);
                            calculate();
                        })
                        .show();
                return;
            }
            resultView.setText(R.string.route_loading_borders);
            goButton.setEnabled(false);
            final int mine = ++token;
            regions.loadCountries(() -> {
                if (mine == token) {
                    goButton.setEnabled(true);
                    calculate();
                }
            }, () -> {
                if (mine == token) {
                    goButton.setEnabled(true);
                    resultView.setText(activity.getString(R.string.regions_error,
                            Regions.get(activity).lastError()));
                }
            });
            return;
        }
        request(vehicle, kmh, here, stay);
    }

    private void request(final Vehicle vehicle, final double kmh, GeoPoint hereIn, final boolean stay) {
        resultView.setText(R.string.route_calculating);
        goButton.setEnabled(false);
        stepsButton.setVisibility(View.GONE);
        removeOverlay();
        final int mine = ++token;
        final GeoPoint from = new GeoPoint(hereIn);
        final GeoPoint to = new GeoPoint(destination.lat, destination.lon);

        io.execute(() -> {
            Routing.Route chosen = null;
            String message = null;
            String note = null;
            try {
                List<Routing.Route> routes = Routing.fetch(vehicle.profile, from, to);
                chosen = routes.get(0);
                if (stay) {
                    Regions regions = Regions.get(activity);
                    Regions.Region home = regions.countryAt(from.getLatitude(), from.getLongitude());
                    Regions.Region target = regions.countryAt(to.getLatitude(), to.getLongitude());
                    if (home == null || target == null) {
                        note = activity.getString(R.string.route_unverified);
                    } else if (home != target) {
                        chosen = null;
                        message = activity.getString(R.string.route_different_countries,
                                home.name, target.name);
                    } else {
                        Routing.Route inside = null;
                        String foreign = null;
                        for (Routing.Route r : routes) {
                            String f = Routing.foreignCountry(regions, r.points, home);
                            if (f == null) {
                                inside = r;
                                break;
                            }
                            if (foreign == null) {
                                foreign = f;
                            }
                        }
                        if (inside == null) {
                            chosen = null;
                            message = activity.getString(R.string.route_leaves_country, home.name, foreign);
                        } else {
                            chosen = inside;
                        }
                    }
                }
            } catch (Routing.RouteException e) {
                chosen = null;
                message = Routing.messageFor(e.code);
            } catch (IOException | JSONException | RuntimeException | OutOfMemoryError e) {
                chosen = null;
                message = activity.getString(R.string.route_error, Regions.describe(e));
            }
            final Routing.Route result = chosen;
            final String msg = message;
            final String extra = note;
            activity.runOnUiThread(() -> {
                if (mine != token) {
                    return;
                }
                goButton.setEnabled(true);
                if (result == null) {
                    resultView.setText(msg);
                } else {
                    show(result, kmh, extra);
                }
            });
        });
    }

    private void show(Routing.Route r, double kmh, String note) {
        route = r;
        boolean imperial = Store.isImperial(activity);
        double secondsAtSpeed = r.meters / (kmh / 3.6);
        String text = activity.getString(R.string.route_summary,
                Ui.formatDistance(r.meters, imperial), speedText(kmh),
                Ui.formatDuration(secondsAtSpeed), Ui.formatDuration(r.seconds));
        if (note != null) {
            text += "\n" + note;
        }
        resultView.setText(text);
        stepsButton.setVisibility(r.steps.isEmpty() ? View.GONE : View.VISIBLE);

        removeOverlay();
        overlay = new RouteOverlay(r.points, host.dp(5));
        int at = map.getOverlays().indexOf(anchor);
        if (at < 0) {
            map.getOverlays().add(overlay);
        } else {
            map.getOverlays().add(at, overlay);
        }
        if (r.points.size() >= 2) {
            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(r.points), true, host.dp(140));
        }
        map.invalidate();
    }

    private void removeOverlay() {
        if (overlay != null) {
            map.getOverlays().remove(overlay);
            overlay = null;
            map.invalidate();
        }
    }

    private void showSteps() {
        if (route == null || route.steps.isEmpty()) {
            return;
        }
        final List<Routing.Step> steps = route.steps;
        boolean imperial = Store.isImperial(activity);
        String[] items = new String[steps.size()];
        for (int i = 0; i < items.length; i++) {
            Routing.Step s = steps.get(i);
            items[i] = (i + 1) + ". " + s.text
                    + (s.meters > 0 ? "  \u00B7  " + Ui.formatDistance(s.meters, imperial) : "");
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.route_steps_title)
                .setItems(items, (d, which) -> {
                    GeoPoint at = steps.get(which).at;
                    if (at != null) {
                        map.getController().animateTo(at, 17.0, 800L);
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(speedInput.getWindowToken(), 0);
        }
        speedInput.clearFocus();
    }
}

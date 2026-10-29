package com.CraftPlay777.jps;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Todo lo que la app guarda en el dispositivo: ajustes, favoritos, historial y última posición. */
final class Store {

    private static final String PREFS = "jps";
    private static final int MAX_HISTORY = 8;

    private Store() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------- ajustes

    static boolean isConsentGiven(Context c) {
        return sp(c).getBoolean("consent", false);
    }

    static void setConsentGiven(Context c, boolean value) {
        sp(c).edit().putBoolean("consent", value).apply();
    }

    static boolean isDarkMap(Context c) {
        return sp(c).getBoolean("dark_map", false);
    }

    static void setDarkMap(Context c, boolean value) {
        sp(c).edit().putBoolean("dark_map", value).apply();
    }

    static boolean isImperial(Context c) {
        return sp(c).getBoolean("imperial", false);
    }

    static void setImperial(Context c, boolean value) {
        sp(c).edit().putBoolean("imperial", value).apply();
    }

    static boolean isKeepScreenOn(Context c) {
        return sp(c).getBoolean("keep_on", false);
    }

    static void setKeepScreenOn(Context c, boolean value) {
        sp(c).edit().putBoolean("keep_on", value).apply();
    }

    // ----------------------------------------------------- última posición

    static boolean hasPosition(Context c) {
        return sp(c).contains("lat");
    }

    static double getLat(Context c) {
        return Double.longBitsToDouble(sp(c).getLong("lat", Double.doubleToLongBits(20.0)));
    }

    static double getLon(Context c) {
        return Double.longBitsToDouble(sp(c).getLong("lon", Double.doubleToLongBits(0.0)));
    }

    static float getZoom(Context c) {
        return sp(c).getFloat("zoom", 3.0f);
    }

    static boolean isTopo(Context c) {
        return sp(c).getBoolean("topo", false);
    }

    static void savePosition(Context c, double lat, double lon, float zoom, boolean topo) {
        sp(c).edit()
                .putLong("lat", Double.doubleToLongBits(lat))
                .putLong("lon", Double.doubleToLongBits(lon))
                .putFloat("zoom", zoom)
                .putBoolean("topo", topo)
                .apply();
    }

    // ------------------------------------------------------------ favoritos

    static List<Place> getFavorites(Context c) {
        List<Place> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(sp(c).getString("favorites", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                out.add(new Place(o.optString("name", ""), o.getDouble("lat"), o.getDouble("lon")));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    private static void saveFavorites(Context c, List<Place> list) {
        JSONArray array = new JSONArray();
        try {
            for (Place p : list) {
                JSONObject o = new JSONObject();
                o.put("name", p.name == null ? "" : p.name);
                o.put("lat", p.lat);
                o.put("lon", p.lon);
                array.put(o);
            }
        } catch (JSONException ignored) {
        }
        sp(c).edit().putString("favorites", array.toString()).apply();
    }

    private static int indexOf(List<Place> list, double lat, double lon) {
        for (int i = 0; i < list.size(); i++) {
            Place p = list.get(i);
            if (Math.abs(p.lat - lat) < 0.00001 && Math.abs(p.lon - lon) < 0.00001) {
                return i;
            }
        }
        return -1;
    }

    static boolean isFavorite(Context c, Place place) {
        return indexOf(getFavorites(c), place.lat, place.lon) >= 0;
    }

    /** Añade o quita el lugar. Devuelve true si ahora es favorito. */
    static boolean toggleFavorite(Context c, Place place) {
        List<Place> list = getFavorites(c);
        int index = indexOf(list, place.lat, place.lon);
        boolean nowFavorite;
        if (index >= 0) {
            list.remove(index);
            nowFavorite = false;
        } else {
            list.add(0, place);
            nowFavorite = true;
        }
        saveFavorites(c, list);
        return nowFavorite;
    }

    static void removeFavorite(Context c, int index) {
        List<Place> list = getFavorites(c);
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            saveFavorites(c, list);
        }
    }

    static void clearFavorites(Context c) {
        sp(c).edit().remove("favorites").apply();
    }

    // ------------------------------------------------------------ historial

    static List<String> getHistory(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(sp(c).getString("history", "[]"));
            for (int i = 0; i < array.length(); i++) {
                out.add(array.getString(i));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    static void addHistory(Context c, String query) {
        List<String> list = getHistory(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).equalsIgnoreCase(query)) {
                list.remove(i);
            }
        }
        list.add(0, query);
        while (list.size() > MAX_HISTORY) {
            list.remove(list.size() - 1);
        }
        JSONArray array = new JSONArray();
        for (String s : list) {
            array.put(s);
        }
        sp(c).edit().putString("history", array.toString()).apply();
    }

    static void clearHistory(Context c) {
        sp(c).edit().remove("history").apply();
    }
}

package com.CraftPlay777.jps;

import android.app.Activity;
import android.app.AlertDialog;

import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;

/** Servicios de mapa de código abierto entre los que el usuario puede elegir. */
final class MapSources {

    static final class Source {
        final String id;
        final int nameRes;
        final ITileSource tiles;

        Source(String id, int nameRes, ITileSource tiles) {
            this.id = id;
            this.nameRes = nameRes;
            this.tiles = tiles;
        }
    }

    private static final String OSM = "\u00A9 OpenStreetMap contributors";

    /** El primero es el predeterminado. */
    static final Source[] ALL = {
            new Source("osm", R.string.source_osm, TileSourceFactory.MAPNIK),

            new Source("osmfr", R.string.source_osmfr, new XYTileSource(
                    "OSMFrance", 0, 19, 256, ".png",
                    new String[]{
                            "https://a.tile.openstreetmap.fr/osmfr/",
                            "https://b.tile.openstreetmap.fr/osmfr/",
                            "https://c.tile.openstreetmap.fr/osmfr/"},
                    OSM + " | OpenStreetMap France")),

            new Source("hot", R.string.source_hot, new XYTileSource(
                    "OSMHumanitarian", 0, 19, 256, ".png",
                    new String[]{
                            "https://a.tile.openstreetmap.fr/hot/",
                            "https://b.tile.openstreetmap.fr/hot/",
                            "https://c.tile.openstreetmap.fr/hot/"},
                    OSM + " | Humanitarian OpenStreetMap Team, OpenStreetMap France")),

            new Source("cyclosm", R.string.source_cyclosm, new XYTileSource(
                    "CyclOSM", 0, 19, 256, ".png",
                    new String[]{
                            "https://a.tile-cyclosm.openstreetmap.fr/cyclosm/",
                            "https://b.tile-cyclosm.openstreetmap.fr/cyclosm/",
                            "https://c.tile-cyclosm.openstreetmap.fr/cyclosm/"},
                    OSM + " | CyclOSM, OpenStreetMap France")),

            new Source("topo", R.string.source_topo, new XYTileSource(
                    "OpenTopoMap", 0, 17, 256, ".png",
                    new String[]{
                            "https://a.tile.opentopomap.org/",
                            "https://b.tile.opentopomap.org/",
                            "https://c.tile.opentopomap.org/"},
                    OSM + ", SRTM | \u00A9 OpenTopoMap (CC-BY-SA)")),
    };

    private MapSources() {
    }

    static Source byId(String id) {
        for (Source s : ALL) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return ALL[0];
    }

    /** Muestra la lista de servicios; al elegir uno lo guarda y avisa con onChanged. */
    static void showChooser(Activity activity, Runnable onChanged) {
        String current = Store.getMapSource(activity);
        CharSequence[] names = new CharSequence[ALL.length];
        int checked = 0;
        for (int i = 0; i < ALL.length; i++) {
            names[i] = activity.getString(ALL[i].nameRes);
            if (ALL[i].id.equals(current)) {
                checked = i;
            }
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.setting_map_source)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    Store.setMapSource(activity, ALL[which].id);
                    dialog.dismiss();
                    if (onChanged != null) {
                        onChanged.run();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}

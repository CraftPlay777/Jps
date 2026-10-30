package com.CraftPlay777.jps;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dibuja sobre el mapa los bordes de todos los países (rojo), los estados/provincias (rojo más fino),
 * y opcionalmente cada país relleno con su color o con su bandera.
 * Los datos son de escala pequeña, así que solo se ven hasta cierto zoom.
 */
final class RegionsOverlay extends Overlay {

    static final double MAX_ZOOM = 9.0;
    static final double STATES_MIN_ZOOM = 4.0;
    static final double STATES_MAX_ZOOM = 12.5;

    private static final int RED = 0xFFE00000;
    private static final int RED_SOFT = 0xDDE53935;

    private final Regions data;
    private boolean borders, states, colors, flags;

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint statePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path statePath = new Path();
    private final ArrayList<Path> paths = new ArrayList<>();
    private final Matrix matrix = new Matrix();
    private final RectF src = new RectF();
    private final RectF dst = new RectF();
    private final Map<String, BitmapShader> shaders = new HashMap<>();
    private final double[] frame = new double[3];

    RegionsOverlay(Regions data, float density) {
        this.data = data;
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(RED);
        borderPaint.setStrokeWidth(2f * density);
        borderPaint.setStrokeJoin(Paint.Join.ROUND);
        statePaint.setStyle(Paint.Style.STROKE);
        statePaint.setColor(RED_SOFT);
        statePaint.setStrokeWidth(1f * density);
        statePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        flagPaint.setStyle(Paint.Style.FILL);
        flagPaint.setAlpha(215);
    }

    void setOptions(boolean borders, boolean states, boolean colors, boolean flags) {
        this.borders = borders;
        this.states = borders && states;
        this.colors = colors;
        this.flags = colors && flags;
    }

    boolean isActive() {
        return borders || colors;
    }

    @Override
    public void draw(Canvas canvas, MapView map, boolean shadow) {
        if (shadow || !(borders || colors)) {
            return;
        }
        double zoom = map.getZoomLevelDouble();
        if (zoom > MAX_ZOOM) {
            return;
        }
        List<Regions.Region> list = data.countries();
        if (list == null || !Mercator.frame(map, frame)) {
            return;
        }
        double s = frame[2];
        int w = map.getWidth();
        int h = map.getHeight();
        double margin = map.getMapOrientation() == 0f ? 32.0 : Math.hypot(w, h);

        for (int copy = -1; copy <= 1; copy++) {
            double ox = frame[0] + copy * s;
            double oy = frame[1];
            float minX = (float) ((-margin - ox) / s);
            float maxX = (float) ((w + margin - ox) / s);
            float minY = (float) ((-margin - oy) / s);
            float maxY = (float) ((h + margin - oy) / s);
            if (maxX < 0f || minX > 1f || maxY < 0f || minY > 1f) {
                continue;
            }
            drawCopy(canvas, list, ox, oy, s, minX, maxX, minY, maxY, zoom);
        }
    }

    private void drawCopy(Canvas canvas, List<Regions.Region> list, double ox, double oy, double s,
                          float minX, float maxX, float minY, float maxY, double zoom) {
        int n = 0;
        for (Regions.Region c : list) {
            if (c.maxX < minX || c.minX > maxX || c.maxY < minY || c.minY > maxY) {
                continue;
            }
            Path p;
            if (n < paths.size()) {
                p = paths.get(n);
            } else {
                p = new Path();
                paths.add(p);
            }
            n++;
            p.rewind();
            p.setFillType(Path.FillType.EVEN_ODD);
            for (GeoJson.Ring r : c.rings) {
                if (r.maxX < minX || r.minX > maxX || r.maxY < minY || r.minY > maxY) {
                    continue;
                }
                appendRing(p, r, ox, oy, s);
            }
            if (colors) {
                fillRegion(canvas, c, p, ox, oy, s);
            }
        }

        if (states && zoom >= STATES_MIN_ZOOM && zoom <= STATES_MAX_ZOOM) {
            statePath.rewind();
            for (Regions.Region c : list) {
                if (c.maxX < minX || c.minX > maxX || c.maxY < minY || c.minY > maxY) {
                    continue;
                }
                List<GeoJson.Ring> st = data.statesOf(c.iso3);
                if (st == null) {
                    continue;
                }
                for (GeoJson.Ring r : st) {
                    if (r.maxX < minX || r.minX > maxX || r.maxY < minY || r.minY > maxY) {
                        continue;
                    }
                    appendRing(statePath, r, ox, oy, s);
                }
            }
            canvas.drawPath(statePath, statePaint);
        }

        if (borders) {
            for (int i = 0; i < n; i++) {
                canvas.drawPath(paths.get(i), borderPaint);
            }
        }
    }

    private void fillRegion(Canvas canvas, Regions.Region c, Path p, double ox, double oy, double s) {
        fillPaint.setColor((data.colorFor(c) & 0x00FFFFFF) | (140 << 24));
        canvas.drawPath(p, fillPaint);
        if (!flags) {
            return;
        }
        Bitmap bmp = data.flag(c.iso2); // null hasta que llega; entonces se vuelve a dibujar
        if (bmp == null) {
            return;
        }
        GeoJson.Ring m = c.rings.get(c.main);
        dst.set((float) (ox + m.minX * s), (float) (oy + m.minY * s),
                (float) (ox + m.maxX * s), (float) (oy + m.maxY * s));
        if (dst.width() < 1f || dst.height() < 1f) {
            return;
        }
        BitmapShader sh = shaders.get(c.iso2);
        if (sh == null) {
            sh = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shaders.put(c.iso2, sh);
        }
        src.set(0f, 0f, bmp.getWidth(), bmp.getHeight());
        matrix.setRectToRect(src, dst, Matrix.ScaleToFit.FILL);
        sh.setLocalMatrix(matrix);
        flagPaint.setShader(sh);
        canvas.drawPath(p, flagPaint);
    }

    /** Añade un anillo al path omitiendo puntos a menos de 1,5 px del anterior. */
    static void appendRing(Path p, GeoJson.Ring ring, double ox, double oy, double s) {
        float[] pts = ring.pts;
        float lx = 0f, ly = 0f;
        boolean first = true;
        for (int i = 0; i < pts.length; i += 2) {
            float x = (float) (ox + pts[i] * s);
            float y = (float) (oy + pts[i + 1] * s);
            if (first) {
                p.moveTo(x, y);
                lx = x;
                ly = y;
                first = false;
            } else {
                float dx = x - lx;
                float dy = y - ly;
                if (dx * dx + dy * dy < 2.25f) {
                    continue;
                }
                p.lineTo(x, y);
                lx = x;
                ly = y;
            }
        }
        p.close();
    }

    /** Pide los estados de los países más visibles (se llama cuando el mapa se detiene). */
    void requestStates(MapView map) {
        if (!states) {
            return;
        }
        double zoom = map.getZoomLevelDouble();
        if (zoom < STATES_MIN_ZOOM || zoom > STATES_MAX_ZOOM) {
            return;
        }
        List<Regions.Region> list = data.countries();
        if (list == null || map.getWidth() == 0) {
            return;
        }
        BoundingBox bb = map.getBoundingBox();
        float vMinX = Mercator.x(bb.getLonWest());
        float vMaxX = Mercator.x(bb.getLonEast());
        float vMinY = Mercator.y(bb.getLatNorth());
        float vMaxY = Mercator.y(bb.getLatSouth());
        if (vMinX > vMaxX) { // cruza el antimeridiano
            vMinX = 0f;
            vMaxX = 1f;
        }
        // Los 3 países que más ocupan la pantalla.
        Regions.Region[] top = new Regions.Region[3];
        float[] topArea = new float[3];
        for (Regions.Region c : list) {
            float w = Math.min(c.maxX, vMaxX) - Math.max(c.minX, vMinX);
            float h = Math.min(c.maxY, vMaxY) - Math.max(c.minY, vMinY);
            if (w <= 0f || h <= 0f || c.iso3.isEmpty()) {
                continue;
            }
            float area = w * h;
            for (int i = 0; i < top.length; i++) {
                if (top[i] == null || area > topArea[i]) {
                    for (int j = top.length - 1; j > i; j--) {
                        top[j] = top[j - 1];
                        topArea[j] = topArea[j - 1];
                    }
                    top[i] = c;
                    topArea[i] = area;
                    break;
                }
            }
        }
        for (Regions.Region c : top) {
            if (c != null) {
                data.loadStates(c.iso3);
            }
        }
    }
}

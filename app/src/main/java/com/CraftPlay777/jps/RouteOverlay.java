package com.CraftPlay777.jps;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import java.util.List;

/** Dibuja la ruta calculada: una línea azul con borde blanco sobre el mapa. */
final class RouteOverlay extends Overlay {

    private final float[] pts;
    private final float minX, minY, maxX, maxY;
    private final Paint casing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final double[] frame = new double[3];

    RouteOverlay(List<GeoPoint> points, float widthPx) {
        pts = new float[points.size() * 2];
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        int i = 0;
        for (GeoPoint g : points) {
            float x = Mercator.x(g.getLongitude());
            float y = Mercator.y(g.getLatitude());
            pts[i++] = x;
            pts[i++] = y;
            x0 = Math.min(x0, x);
            x1 = Math.max(x1, x);
            y0 = Math.min(y0, y);
            y1 = Math.max(y1, y);
        }
        minX = x0;
        minY = y0;
        maxX = x1;
        maxY = y1;

        casing.setStyle(Paint.Style.STROKE);
        casing.setColor(0xFFFFFFFF);
        casing.setStrokeWidth(widthPx + 4f);
        casing.setStrokeCap(Paint.Cap.ROUND);
        casing.setStrokeJoin(Paint.Join.ROUND);

        line.setStyle(Paint.Style.STROKE);
        line.setColor(0xFF1A73E8);
        line.setStrokeWidth(widthPx);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas canvas, MapView map, boolean shadow) {
        if (shadow || pts.length < 4 || !Mercator.frame(map, frame)) {
            return;
        }
        double s = frame[2];
        int w = map.getWidth();
        int h = map.getHeight();
        double margin = map.getMapOrientation() == 0f ? 32 : Math.hypot(w, h);
        for (int copy = -1; copy <= 1; copy++) {
            double ox = frame[0] + copy * s;
            double oy = frame[1];
            if (ox + maxX * s < -margin || ox + minX * s > w + margin
                    || oy + maxY * s < -margin || oy + minY * s > h + margin) {
                continue;
            }
            path.rewind();
            float lx = 0, ly = 0, x = 0, y = 0;
            boolean first = true;
            for (int i = 0; i < pts.length; i += 2) {
                x = (float) (ox + pts[i] * s);
                y = (float) (oy + pts[i + 1] * s);
                if (first) {
                    path.moveTo(x, y);
                    first = false;
                    lx = x;
                    ly = y;
                    continue;
                }
                float dx = x - lx;
                float dy = y - ly;
                if (dx * dx + dy * dy < 2.25f) {
                    continue; // menos de 1,5 px: no hace falta dibujarlo
                }
                path.lineTo(x, y);
                lx = x;
                ly = y;
            }
            if (x != lx || y != ly) {
                path.lineTo(x, y); // el último punto siempre
            }
            canvas.drawPath(path, casing);
            canvas.drawPath(path, line);
        }
    }
}

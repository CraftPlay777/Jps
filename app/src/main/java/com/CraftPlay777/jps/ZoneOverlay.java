package com.CraftPlay777.jps;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import java.util.List;

/** Rellena la zona buscada (con su bandera si es un país, o con un color si es estado o ciudad). */
final class ZoneOverlay extends Overlay {

    private final List<GeoJson.Ring> rings;
    private final int main;
    private final Paint colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();
    private final RectF src = new RectF();
    private final RectF dst = new RectF();
    private final double[] frame = new double[3];
    private Bitmap flag;
    private BitmapShader shader;

    ZoneOverlay(List<GeoJson.Ring> rings, int main, int color) {
        this.rings = rings;
        this.main = main;
        colorPaint.setStyle(Paint.Style.FILL);
        colorPaint.setColor((color & 0x00FFFFFF) | (110 << 24));
        flagPaint.setStyle(Paint.Style.FILL);
        flagPaint.setAlpha(225);
    }

    void setFlag(Bitmap bitmap) {
        if (bitmap != null) {
            flag = bitmap;
            shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
    }

    @Override
    public void draw(Canvas canvas, MapView map, boolean shadow) {
        if (shadow || rings.isEmpty() || !Mercator.frame(map, frame)) {
            return;
        }
        double s = frame[2];
        int w = map.getWidth();
        int h = map.getHeight();
        double margin = map.getMapOrientation() == 0f ? 32.0 : Math.hypot(w, h);
        GeoJson.Ring m = rings.get(main);

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
            path.rewind();
            path.setFillType(Path.FillType.EVEN_ODD);
            for (GeoJson.Ring r : rings) {
                if (r.maxX < minX || r.minX > maxX || r.maxY < minY || r.minY > maxY) {
                    continue;
                }
                RegionsOverlay.appendRing(path, r, ox, oy, s);
            }
            canvas.drawPath(path, colorPaint);
            if (shader != null) {
                dst.set((float) (ox + m.minX * s), (float) (oy + m.minY * s),
                        (float) (ox + m.maxX * s), (float) (oy + m.maxY * s));
                if (dst.width() >= 1f && dst.height() >= 1f) {
                    src.set(0f, 0f, flag.getWidth(), flag.getHeight());
                    matrix.setRectToRect(src, dst, Matrix.ScaleToFit.FILL);
                    shader.setLocalMatrix(matrix);
                    flagPaint.setShader(shader);
                    canvas.drawPath(path, flagPaint);
                }
            }
        }
    }
}

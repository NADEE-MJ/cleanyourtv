package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * The moving backdrops of the screensaver styles (screensaver_style): aurora clouds, dusk dunes and ripples.
 * Drawn in code at a capped frame rate (no video, no images); everything keeps moving, so nothing burns in.
 * Idle (draws nothing, schedules nothing) until {@link #start(String, float)}.
 */
public final class ScreensaverView extends View {

    static final String AURORA = "aurora";
    static final String DUNES = "dunes";
    static final String RIPPLES = "ripples";

    /** ~30 fps for the faster scenes; aurora changes so slowly that 20 fps looks the same. */
    private static final long FRAME_MS = 33L;
    private static final long AURORA_FRAME_MS = 50L;
    private static final int BLOBS = 5;
    private static final int RINGS = 7;
    private static final long RING_LIFE_MS = 9000L;
    /** Aurora colours are rebuilt this often; the hue drifts too slowly to need it every frame. */
    private static final long HUE_STEP_MS = 2000L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Path path = new Path();
    private final RadialGradient[] blobs = new RadialGradient[BLOBS];
    private final PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private final float[] hsv = new float[3];
    private LinearGradient sky;
    private long hueBuiltAt = Long.MIN_VALUE;
    private String style;
    private float strength = 1f;
    private long startedAt;
    private boolean running;

    public ScreensaverView(Context context) {
        this(context, null);
    }

    public ScreensaverView(Context context, AttributeSet attrs) {
        super(context, attrs);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
    }

    /** style is AURORA, DUNES or RIPPLES; strength (0..1) scales the aurora's brightness, e.g. under a clock. */
    void start(String style, float strength) {
        this.style = style;
        this.strength = strength;
        hueBuiltAt = Long.MIN_VALUE;
        startedAt = SystemClock.uptimeMillis();
        running = true;
        setVisibility(VISIBLE);
        invalidate();
    }

    void stop() {
        running = false;
    }

    static boolean isScene(String style) {
        return AURORA.equals(style) || DUNES.equals(style) || RIPPLES.equals(style);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        sky = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!running || style == null) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long t = SystemClock.uptimeMillis() - startedAt;
        switch (style) {
            case AURORA: drawAurora(canvas, w, h, t); break;
            case DUNES: drawDunes(canvas, w, h, t); break;
            case RIPPLES: drawRipples(canvas, w, h, t); break;
            default: return;
        }
        postInvalidateDelayed(AURORA.equals(style) ? AURORA_FRAME_MS : FRAME_MS);
    }

    private void drawAurora(Canvas canvas, int w, int h, long t) {
        canvas.drawColor(0xFF04060C);
        if (t - hueBuiltAt >= HUE_STEP_MS) {
            hueBuiltAt = t;
            int alpha = Math.round(128 * strength);
            for (int i = 0; i < BLOBS; i++) {
                hsv[0] = ((150 + i * 45 + (float) Math.sin(t * 0.00009) * 30f) % 360 + 360) % 360;
                hsv[1] = 0.7f;
                hsv[2] = 0.55f;
                int c = Color.HSVToColor(alpha, hsv);
                // Unit radius at the origin; the matrix below moves and scales it each frame.
                blobs[i] = new RadialGradient(0, 0, 1f, c, c & 0x00FFFFFF, Shader.TileMode.CLAMP);
            }
        }
        paint.setXfermode(add);
        float base = Math.min(w, h);
        for (int i = 0; i < BLOBS; i++) {
            float x = w / 2f + (float) Math.sin(t * 0.00013 * (i + 1) * 0.5 + i * 2) * w * 0.38f;
            float y = h / 2f + (float) Math.cos(t * 0.00017 * (i + 1) * 0.5 + i) * h * 0.32f;
            float r = base * (0.47f + 0.17f * (float) Math.sin(t * 0.0002 + i));
            matrix.setScale(r, r);
            matrix.postTranslate(x, y);
            blobs[i].setLocalMatrix(matrix);
            paint.setShader(blobs[i]);
            canvas.drawCircle(x, y, r, paint);
        }
        paint.setShader(null);
        paint.setXfermode(null);
    }

    private static final int[] DUNE_COLORS = {0xFF7A3A63, 0xFF4F2554, 0xFF2D1642, 0xFF150A24};

    private void drawDunes(Canvas canvas, int w, int h, long t) {
        if (sky == null) {
            sky = new LinearGradient(0, 0, 0, h, new int[]{0xFF2B1B3D, 0xFFB5546B, 0xFFF0A066},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        }
        paint.setShader(sky);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        // The sun crosses the sky over hours, so it never sits in one place for long.
        float sx = w * 0.5f + (float) Math.sin(t * 0.00003 * 0.2) * w * 0.3f;
        paint.setColor(0xE6FFE2B4);
        canvas.drawCircle(sx, h * 0.42f, h * 0.06f, paint);
        float step = w / 120f;
        for (int k = 0; k < DUNE_COLORS.length; k++) {
            double sp = 0.00002 * (k + 1);
            float baseY = h * (0.52f + k * 0.13f);
            float amp1 = h * 0.04f, amp2 = h * 0.017f;
            path.reset();
            path.moveTo(0, h);
            for (float x = 0; x <= w + step; x += step) {
                double xs = x * 640.0 / w;   // the shape is defined on a 640-wide canvas
                float y = baseY + (float) (Math.sin(xs * 0.012 + t * sp * 8 + k * 3) * amp1
                        + Math.sin(xs * 0.027 + t * sp * 5 + k) * amp2);
                path.lineTo(x, y);
            }
            path.lineTo(w, h);
            path.close();
            paint.setColor(DUNE_COLORS[k]);
            canvas.drawPath(path, paint);
        }
    }

    private void drawRipples(Canvas canvas, int w, int h, long t) {
        canvas.drawColor(Color.BLACK);
        float maxR = Math.min(w, h) * 0.42f;
        for (int i = 0; i < RINGS; i++) {
            long offset = t + i * RING_LIFE_MS / RINGS;
            long cycle = offset / RING_LIFE_MS;
            float p = (offset % RING_LIFE_MS) / (float) RING_LIFE_MS;
            int seed = (int) ((cycle * 7 + i * 13) % 97);
            float x = w * (0.06f + 0.88f * ((seed * 53) % 100) / 100f);
            float y = h * (0.08f + 0.84f * ((seed * 29) % 100) / 100f);
            hsv[0] = (seed * 37) % 360;
            hsv[1] = 0.6f;
            hsv[2] = 0.9f;
            stroke.setColor(Color.HSVToColor(Math.round(140 * (1 - p)), hsv));
            canvas.drawCircle(x, y, p * maxR, stroke);
        }
    }
}

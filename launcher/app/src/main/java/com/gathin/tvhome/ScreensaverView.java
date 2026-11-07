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
    /** Still backgrounds for Home (home_background): drawn once, nothing keeps redrawing them. */
    static final String MIDNIGHT = "midnight";
    static final String GRAPHITE = "graphite";
    /** Light and soft-focus, after Apple TV's default home screen. */
    static final String DAYLIGHT = "daylight";
    static final String OCEAN = "ocean";
    static final String SUNSET = "sunset";

    /** Still backgrounds: three sky stops (top-left to bottom-right), two pools of light, and a darkening behind the
     *  header (top) so white text stays readable on the light ones. */
    private static int[] stillColors(String style) {
        switch (style) {
            case MIDNIGHT: return new int[]{0xFF0B1230, 0xFF121A44, 0xFF070914, 0x553A4FC8, 0x447A3FB8, 0};
            // Apple TV's default: sky blue at the top, a hazy near-white middle, warm sand low down.
            case DAYLIGHT: return new int[]{0xFFBCD8EE, 0xFFCDD0CA, 0xFFAFA685, 0xB3FFFFFF, 0x99C9B98C, 0x4D000000};
            case OCEAN:    return new int[]{0xFF06263A, 0xFF0B3F55, 0xFF041722, 0x5531B6C8, 0x44206E9E, 0};
            case SUNSET:   return new int[]{0xFF2A1438, 0xFF5A2346, 0xFF1A0B1F, 0x55FF8A4C, 0x44C2407A, 0};
            default:       return new int[]{0xFF26272B, 0xFF1A1B1E, 0xFF0E0E10, 0x33FFFFFF, 0x22A0A4B0, 0};
        }
    }

    /** Aurora is drawn this many times smaller and scaled up: the blur hides it, and it costs a fraction. */
    private static final int AURORA_SCALE = 6;
    private android.graphics.Bitmap small;
    private Canvas smallCanvas;
    private final Paint scaled = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final android.graphics.Rect dst = new android.graphics.Rect();
    private LinearGradient headerScrim;

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
    private long hueBuiltAt = -1;   // -1: not built yet
    private String style;
    private float strength = 1f;
    private long startedAt;
    private boolean running;
    /** Home runs the scenes slower than the screensaver, and darker so tiles stay readable. 0: the style's own. */
    private long frameMs;
    private int dim;
    private RadialGradient stillGlowA, stillGlowB;

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
        start(style, strength, 0, 0);
    }

    /** frameMs 0 keeps the style's own rate; dim is a colour drawn over the scene (0 for none). */
    void start(String style, float strength, long frameMs, int dim) {
        this.frameMs = frameMs;
        this.dim = dim;
        this.style = style;
        this.strength = strength;
        hueBuiltAt = -1;
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

    static boolean isStill(String style) {
        return MIDNIGHT.equals(style) || GRAPHITE.equals(style) || DAYLIGHT.equals(style)
                || OCEAN.equals(style) || SUNSET.equals(style);
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
        stillGlowA = null;
        headerScrim = null;
        if (small != null) {
            small.recycle();
            small = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!running || style == null) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long t = SystemClock.uptimeMillis() - startedAt;
        switch (style) {
            case AURORA: drawAuroraSmall(canvas, w, h, t); break;
            case DUNES: drawDunes(canvas, w, h, t); break;
            case RIPPLES: drawRipples(canvas, w, h, t); break;
            case MIDNIGHT:
            case GRAPHITE:
            case DAYLIGHT:
            case OCEAN:
            case SUNSET:
                drawStill(canvas, w, h);
                return;   // still: drawn again only when the view is
            default: return;
        }
        if (dim != 0) canvas.drawColor(dim);
        postInvalidateDelayed(frameMs > 0 ? frameMs : AURORA.equals(style) ? AURORA_FRAME_MS : FRAME_MS);
    }

    /** A still backdrop in the spirit of Apple TV's: a soft gradient with two pools of light. Drawn once. */
    private void drawStill(Canvas canvas, int w, int h) {
        int[] c = stillColors(style);
        if (stillGlowA == null) {
            sky = new LinearGradient(0, 0, w * 0.3f, h, new int[]{c[0], c[1], c[2]}, null, Shader.TileMode.CLAMP);
            stillGlowA = new RadialGradient(w * 0.3f, h * 0.12f, Math.max(w, h) * 0.55f,
                    c[3], c[3] & 0x00FFFFFF, Shader.TileMode.CLAMP);
            stillGlowB = new RadialGradient(w * 0.15f, h * 0.95f, Math.max(w, h) * 0.5f,
                    c[4], c[4] & 0x00FFFFFF, Shader.TileMode.CLAMP);
            headerScrim = c[5] == 0 ? null : new LinearGradient(0, 0, 0, h * 0.32f, c[5], 0, Shader.TileMode.CLAMP);
        }
        paint.setShader(sky);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(stillGlowA);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(stillGlowB);
        canvas.drawRect(0, 0, w, h, paint);
        if (headerScrim != null) {
            paint.setShader(headerScrim);
            canvas.drawRect(0, 0, w, h * 0.32f, paint);
        }
        paint.setShader(null);
    }

    private void drawAuroraSmall(Canvas canvas, int w, int h, long t) {
        int sw = Math.max(1, w / AURORA_SCALE), sh = Math.max(1, h / AURORA_SCALE);
        if (small == null) {
            small = android.graphics.Bitmap.createBitmap(sw, sh, android.graphics.Bitmap.Config.ARGB_8888);
            smallCanvas = new Canvas(small);
        }
        drawAurora(smallCanvas, sw, sh, t);
        dst.set(0, 0, w, h);
        canvas.drawBitmap(small, null, dst, scaled);
    }

    private void drawAurora(Canvas canvas, int w, int h, long t) {
        canvas.drawColor(0xFF04060C);
        if (hueBuiltAt < 0 || t - hueBuiltAt >= HUE_STEP_MS) {
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

package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Folders on Home: the owner groups apps with the tile menu's "Move to folder". A folder holds at most
 * {@link #MAX_APPS} apps, laid out 4 x 3 and never scrolled. Stored in the launcher's own preferences as JSON,
 * so it survives app updates; a theme push does not touch it. An app in a folder is not shown on the grid.
 */
final class Folders {

    static final int MAX_APPS = 12;
    /** The component class name prefix a folder tile carries, so it has a stable key for the saved order. */
    static final String KEY_PREFIX = "folder:";

    private static final String PREFS = "folders";
    private static final String KEY = "json";

    static final class Folder {
        final String id;
        String name;
        final List<String> apps = new ArrayList<>();

        Folder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        boolean full() {
            return apps.size() >= MAX_APPS;
        }
    }

    private Folders() {}

    static List<Folder> load(Context ctx) {
        List<Folder> out = new ArrayList<>();
        String raw = prefs(ctx).getString(KEY, null);
        if (raw == null) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Folder f = new Folder(o.getString("id"), o.optString("name", ""));
                JSONArray apps = o.optJSONArray("apps");
                if (apps != null) {
                    for (int k = 0; k < apps.length() && f.apps.size() < MAX_APPS; k++) f.apps.add(apps.getString(k));
                }
                out.add(f);
            }
        } catch (JSONException e) {
            return new ArrayList<>();   // unreadable: start without folders rather than crash Home
        }
        return out;
    }

    static boolean save(Context ctx, List<Folder> folders) {
        JSONArray arr = new JSONArray();
        try {
            for (Folder f : folders) {
                JSONObject o = new JSONObject();
                o.put("id", f.id);
                o.put("name", f.name);
                JSONArray apps = new JSONArray();
                for (String a : f.apps) apps.put(a);
                o.put("apps", apps);
                arr.put(o);
            }
        } catch (JSONException e) {
            return false;
        }
        return prefs(ctx).edit().putString(KEY, arr.toString()).commit();
    }

    static Folder find(List<Folder> folders, String id) {
        for (Folder f : folders) if (f.id.equals(id)) return f;
        return null;
    }

    /** Moves pkg into the folder (out of any other one). folderId null makes a new folder named newName. */
    static boolean moveInto(Context ctx, String pkg, String folderId, String newName) {
        List<Folder> all = load(ctx);
        Folder target;
        if (folderId == null) {
            target = new Folder(newId(all), newName);
            all.add(target);
        } else {
            target = find(all, folderId);
            if (target == null) return false;
        }
        if (target.apps.contains(pkg)) return true;
        if (target.full()) return false;
        for (Folder f : all) f.apps.remove(pkg);
        target.apps.add(pkg);
        return save(ctx, all);
    }

    /** Takes pkg out of its folder; an emptied folder goes away. */
    static boolean remove(Context ctx, String pkg) {
        List<Folder> all = load(ctx);
        for (Folder f : all) f.apps.remove(pkg);
        all.removeIf(f -> f.apps.isEmpty());
        return save(ctx, all);
    }

    static boolean rename(Context ctx, String folderId, String name) {
        List<Folder> all = load(ctx);
        Folder f = find(all, folderId);
        if (f == null) return false;
        f.name = name;
        return save(ctx, all);
    }

    /** Deletes the folder; its apps go back on the grid. */
    static boolean delete(Context ctx, String folderId) {
        List<Folder> all = load(ctx);
        all.removeIf(f -> f.id.equals(folderId));
        return save(ctx, all);
    }

    private static String newId(List<Folder> all) {
        int n = all.size() + 1;
        while (find(all, "f" + n) != null) n++;
        return "f" + n;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** A folder tile: a frosted plate with its first four apps in miniature, two by two, as on tvOS. */
    static final class TileDrawable extends Drawable {
        private final Drawable[] minis;
        private final Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint slot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Rect cell = new Rect();

        TileDrawable(List<Drawable> art) {
            minis = art.subList(0, Math.min(4, art.size())).toArray(new Drawable[0]);
            plate.setColor(0xFF34323A);
            slot.setColor(0x1FFFFFFF);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float radius = b.height() * 0.08f;
            r.set(b);
            canvas.drawRect(r, plate);   // the card's own outline rounds the corners
            float pad = b.height() * 0.11f, gap = b.height() * 0.07f;
            float cw = (b.width() - 2 * pad - gap) / 2f, ch = (b.height() - 2 * pad - gap) / 2f;
            for (int i = 0; i < 4; i++) {
                float x = b.left + pad + (i % 2) * (cw + gap), y = b.top + pad + (i / 2) * (ch + gap);
                r.set(x, y, x + cw, y + ch);
                if (i >= minis.length) {
                    canvas.drawRoundRect(r, radius * 0.5f, radius * 0.5f, slot);
                    continue;
                }
                Drawable d = minis[i];
                cell.set(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom));
                canvas.save();
                canvas.clipRect(cell);
                int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
                if (iw > 0 && ih > 0 && (float) iw / ih < 1.3f) {
                    // A square icon: centred on a dim slot instead of stretched into the banner shape.
                    canvas.drawRoundRect(r, radius * 0.5f, radius * 0.5f, slot);
                    int s = Math.round(Math.min(cell.width(), cell.height()) * 0.8f);
                    int cx = cell.centerX(), cy = cell.centerY();
                    d.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
                } else {
                    d.setBounds(cell);
                }
                d.draw(canvas);
                canvas.restore();
            }
        }

        @Override public void setAlpha(int alpha) { plate.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { plate.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
        @Override public int getIntrinsicWidth() { return 320; }
        @Override public int getIntrinsicHeight() { return 180; }
    }
}

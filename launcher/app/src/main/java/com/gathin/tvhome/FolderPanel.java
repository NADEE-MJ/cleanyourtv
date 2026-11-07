package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * An open folder: a glass panel over the grid with its apps 4 x 3 (Folders.MAX_APPS, never scrolled). OK opens an
 * app, hold OK (or Menu) asks the host for that app's menu, Back or Home closes the panel. Focus moves by hand
 * inside the panel, so it never escapes to the grid behind.
 */
final class FolderPanel {

    interface Host {
        void launchFromFolder(AppAdapter.Entry app);
        void onFolderAppMenu(AppAdapter.Entry folder, AppAdapter.Entry app, View anchor);
    }

    private static final int COLS = 4;
    /** Folders hold 12 (Folders.MAX_APPS); the Hidden apps panel shows up to this many, 4 rows, smaller tiles. */
    static final int MAX_TILES = 16;
    private static final long HOLD_MS = 550L;
    private static final long FADE_MS = 200L;

    private final Activity activity;
    private final FrameLayout root;
    private final Host host;
    private final float density;
    private FrameLayout overlay;
    private LinearLayout panel;
    private final List<View> tiles = new ArrayList<>();
    private AppAdapter.Entry folder;
    private Runnable onClosed;
    private boolean closing;
    private boolean holdFired;

    FolderPanel(Activity activity, FrameLayout root, Host host) {
        this.activity = activity;
        this.root = root;
        this.host = host;
        this.density = activity.getResources().getDisplayMetrics().density;
    }

    boolean isOpen() {
        return overlay != null && !closing;
    }

    /** countText sits right of the title ("3 of 12"); hint, when not null, is a line under the tiles. */
    void open(AppAdapter.Entry folder, String countText, String hint, boolean animate, Runnable onClosed) {
        if (overlay != null || folder.members == null) return;
        this.folder = folder;
        this.onClosed = onClosed;
        closing = false;
        overlay = new FrameLayout(activity);
        overlay.setClickable(true);
        overlay.setBackgroundColor(0xA6000000);

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(36), dp(28), dp(36), dp(26));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF27262C);   // solid: the grid behind showed through the title
        bg.setCornerRadius(dp(30));
        bg.setStroke(Math.max(1, dp(1)), 0x2EFFFFFF);
        panel.setBackground(bg);
        panel.setElevation(dp(20));

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        TextView title = new TextView(activity);
        title.setText(folder.label);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(30);
        title.setTypeface(Typeface.create(activity.getString(R.string.font_light), Typeface.NORMAL));
        title.setSingleLine(true);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView count = new TextView(activity);
        count.setText(countText);
        count.setTextColor(0x8CFFFFFF);
        count.setTextSize(15);
        count.setTypeface(Typeface.create(activity.getString(R.string.font_medium), Typeface.NORMAL));
        head.addView(count);
        panel.addView(head);

        boolean fourRows = folder.members.size() > Folders.MAX_APPS;
        int tileW = dp(fourRows ? 128 : 176), tileH = Math.round(tileW * 0.5625f), gap = dp(fourRows ? 16 : 22);
        tiles.clear();
        LinearLayout row = null;
        for (int i = 0; i < folder.members.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = i == 0 ? dp(22) : dp(4);
                panel.addView(row, rlp);
            }
            // Each tile with its name under it, shown while it has focus, as on the Home grid.
            LinearLayout cell = new LinearLayout(activity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setClipChildren(false);
            TextView name = new TextView(activity);
            name.setText(folder.members.get(i).label);
            name.setTextColor(0xF2FFFFFF);
            name.setTextSize(fourRows ? 13 : 15);
            name.setTypeface(Typeface.create(activity.getString(R.string.font_medium), Typeface.NORMAL));
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            name.setAlpha(0f);
            cell.addView(tile(folder.members.get(i), i, name), new LinearLayout.LayoutParams(tileW, tileH));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(tileW, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(fourRows ? 8 : 12);
            cell.addView(name, nlp);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % COLS != 0) clp.leftMargin = gap;
            row.addView(cell, clp);
        }
        if (hint != null) {
            TextView h = new TextView(activity);
            h.setText(hint);
            h.setTextColor(0x8CFFFFFF);
            h.setTextSize(13);
            h.setTypeface(Typeface.create(activity.getString(R.string.font_medium), Typeface.NORMAL));
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hlp.topMargin = dp(10);
            panel.addView(h, hlp);
        }
        panel.setClipChildren(false);
        panel.setClipToPadding(false);

        overlay.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (animate) {
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(FADE_MS).start();
            panel.setScaleX(0.94f);
            panel.setScaleY(0.94f);
            panel.animate().scaleX(1f).scaleY(1f).setDuration(FADE_MS + 40)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    void close(boolean animate) {
        if (overlay == null || closing) return;
        closing = true;
        FrameLayout gone = overlay;
        Runnable finish = () -> {
            root.removeView(gone);
            if (overlay == gone) {
                overlay = null;
                panel = null;
                closing = false;
            }
            Runnable then = onClosed;
            onClosed = null;
            if (then != null) then.run();
        };
        if (!animate) {
            finish.run();
            return;
        }
        gone.animate().alpha(0f).setDuration(FADE_MS - 40).withEndAction(finish).start();
    }

    private View tile(AppAdapter.Entry app, int index, TextView name) {
        FrameLayout card = new FrameLayout(activity);
        card.setFocusable(true);
        card.setClipToOutline(true);
        final float radius = dp(12);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        GradientDrawable plate = new GradientDrawable();
        plate.setColor(0xFF3A3842);
        card.setBackground(plate);
        ImageView img = new ImageView(activity);
        // A copy: the folder tile behind draws the same drawable with its own bounds.
        Drawable art = app.banner != null ? app.banner : app.icon;
        if (art != null && art.getConstantState() != null) art = art.getConstantState().newDrawable().mutate();
        img.setImageDrawable(art);
        img.setScaleType(app.banner != null ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        if (app.banner == null) img.setPadding(dp(22), dp(14), dp(22), dp(14));
        card.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0);
        ring.setCornerRadius(radius);
        ring.setStroke(dp(3), 0xFFFFFFFF);
        card.setOnFocusChangeListener((v, has) -> {
            v.setForeground(has ? ring : null);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).translationZ(has ? dp(10) : 0)
                    .setDuration(150).start();
            name.animate().alpha(has ? 1f : 0f).setDuration(150).start();
        });
        card.setOnKeyListener((v, keyCode, event) -> onKey(index, app, v, keyCode, event));
        tiles.add(card);
        return card;
    }

    private boolean onKey(int index, AppAdapter.Entry app, View v, int keyCode, KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int n = tiles.size();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down && index % COLS > 0) tiles.get(index - 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down && index % COLS < COLS - 1 && index + 1 < n) tiles.get(index + 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down && index >= COLS) tiles.get(index - COLS).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                // The last row may be short: go to its last tile rather than nowhere.
                if (down && index + COLS < n) tiles.get(index + COLS).requestFocus();
                else if (down && (index / COLS) < (n - 1) / COLS) tiles.get(n - 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (down && event.getRepeatCount() == 0) close(true);
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (!down) host.onFolderAppMenu(folder, app, v);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                // The launcher times the hold itself (see MainActivity's hold OK): repeats past HOLD_MS open the menu.
                if (down) {
                    if (event.getRepeatCount() == 0) holdFired = false;
                    else if (!holdFired && event.getEventTime() - event.getDownTime() >= HOLD_MS) {
                        holdFired = true;
                        host.onFolderAppMenu(folder, app, v);
                    }
                    return true;
                }
                if (!holdFired) host.launchFromFolder(app);
                holdFired = false;
                return true;
            default:
                return false;
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}

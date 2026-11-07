package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Home screen's sidebar, after the Apple TV app's: a panel that slides in over the left of the grid without
 * moving it. Opened by Left on the grid's first column or the menu button; Back, Right or Home closes it and
 * focus goes back where it came from. Rows close the sidebar before they act, except those that only change a
 * value in place (the sleep timer).
 */
final class Sidebar {

    /** One row: its label, an optional value shown at its right, and what OK does. */
    static final class Row {
        final String label;
        final Supplier<String> value;
        final Runnable action;
        final boolean staysOpen;

        Row(String label, Supplier<String> value, Runnable action, boolean staysOpen) {
            this.label = label;
            this.value = value;
            this.action = action;
            this.staysOpen = staysOpen;
        }
    }

    private static final long SLIDE_MS = 220L;

    private final Activity activity;
    private final FrameLayout root;
    private final float density;
    private final Typeface medium;
    private final Typeface light;
    private FrameLayout overlay;
    private LinearLayout panel;
    private final List<View> rowViews = new ArrayList<>();
    private final List<TextView> valueViews = new ArrayList<>();
    private List<Row> rows;
    private Runnable onClosed;
    private boolean closing;

    Sidebar(Activity activity, FrameLayout root) {
        this.activity = activity;
        this.root = root;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.medium = Typeface.create(activity.getString(R.string.font_medium), Typeface.NORMAL);
        this.light = Typeface.create(activity.getString(R.string.font_light), Typeface.NORMAL);
    }

    boolean isOpen() {
        return overlay != null && !closing;
    }

    /** onClosed puts focus back (the tile or button it came from); it runs after the panel has gone. */
    void open(List<Row> rows, boolean animate, Runnable onClosed) {
        if (overlay != null) return;
        this.rows = rows;
        this.onClosed = onClosed;
        closing = false;
        overlay = new FrameLayout(activity);
        overlay.setClickable(true);   // nothing behind it takes a click while it is up
        View scrim = new View(activity);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xB3000000, 0x59000000, 0x00000000}));
        overlay.addView(scrim, new FrameLayout.LayoutParams(dp(640), ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        panel.setPadding(pad, dp(22), pad, dp(22));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF21C1A20);
        bg.setCornerRadius(dp(24));
        bg.setStroke(Math.max(1, dp(1)), 0x1FFFFFFF);
        panel.setBackground(bg);
        panel.setElevation(dp(12));

        TextView title = new TextView(activity);
        title.setText(R.string.sidebar_title);
        title.setTextColor(0x99FFFFFF);
        title.setTextSize(13);
        title.setTypeface(medium);
        title.setLetterSpacing(0.18f);
        title.setAllCaps(true);
        title.setPadding(dp(14), 0, dp(14), dp(14));
        panel.addView(title);

        rowViews.clear();
        valueViews.clear();
        for (int i = 0; i < rows.size(); i++) panel.addView(buildRow(rows.get(i), i));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        lp.leftMargin = dp(24);
        overlay.addView(panel, lp);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (animate) {
            scrim.setAlpha(0f);
            scrim.animate().alpha(1f).setDuration(SLIDE_MS).start();
            panel.setTranslationX(-dp(330));
            panel.animate().translationX(0f).setDuration(SLIDE_MS)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
        if (!rowViews.isEmpty()) rowViews.get(0).requestFocus();
    }

    /** Re-reads the rows' values (the sleep timer's minutes left). */
    void refresh() {
        if (rows == null) return;
        for (int i = 0; i < valueViews.size(); i++) {
            Supplier<String> v = rows.get(i).value;
            valueViews.get(i).setText(v != null ? v.get() : "");
        }
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
        gone.animate().alpha(0f).setDuration(SLIDE_MS - 60).start();
        panel.animate().translationX(-dp(330)).setDuration(SLIDE_MS - 60)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(finish).start();
    }

    private View buildRow(Row row, int index) {
        LinearLayout v = new LinearLayout(activity);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setFocusable(true);
        v.setFocusableInTouchMode(false);
        v.setPadding(dp(16), dp(13), dp(16), dp(13));
        StateListDrawable states = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(0xFFFFFFFF);
        on.setCornerRadius(dp(14));
        states.addState(new int[]{android.R.attr.state_focused}, on);
        states.addState(new int[]{}, new ColorDrawable(0));
        v.setBackground(states);
        ColorStateList ink = new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{0xFF0B0B0D, 0xE6FFFFFF});
        ColorStateList dim = new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{0x990B0B0D, 0x80FFFFFF});

        TextView label = new TextView(activity);
        label.setText(row.label);
        label.setTextSize(19);
        label.setTypeface(medium);
        label.setTextColor(ink);
        label.setDuplicateParentStateEnabled(true);
        label.setSingleLine(true);
        v.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(activity);
        value.setTextSize(16);
        value.setTypeface(light);
        value.setTextColor(dim);
        value.setDuplicateParentStateEnabled(true);
        value.setSingleLine(true);
        value.setText(row.value != null ? row.value.get() : "");
        v.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        valueViews.add(value);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(2);
        v.setLayoutParams(lp);
        v.setOnClickListener(x -> {
            if (closing) return;
            if (row.staysOpen) {
                row.action.run();
                refresh();
            } else {
                // Close first, without the slide, so whatever the row opens (a popup, an app) takes focus cleanly.
                Runnable then = onClosed;
                onClosed = null;
                close(false);
                if (then != null) then.run();
                row.action.run();
            }
        });
        // The panel moves its own focus: the default search would leave it for the grid behind.
        v.setOnKeyListener((x, keyCode, event) -> {
            boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down && index > 0) rowViews.get(index - 1).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down && index < rowViews.size() - 1) rowViews.get(index + 1).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    // On DOWN, and the UP eaten too, so the Activity never sees half a Back press.
                    if (down && event.getRepeatCount() == 0) close(true);
                    return true;
                default:
                    return false;
            }
        });
        rowViews.add(v);
        return v;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}

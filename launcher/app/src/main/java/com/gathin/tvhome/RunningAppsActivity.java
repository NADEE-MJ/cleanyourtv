package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Process;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Running apps, from the sidebar: memory in use, then the apps used in the last hours, newest first. Android 14
 * lets an app stop only its own background processes, so a row's OK opens the system page for that app, where
 * Force stop is one press away. Needs usage access (PACKAGE_USAGE_STATS, granted over ADB).
 */
public class RunningAppsActivity extends Activity {

    private static final long WINDOW_MS = 12 * 60 * 60 * 1000L;
    private static final int MAX_ROWS = 12;

    private float density;
    private Typeface medium, light;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        medium = Typeface.create(getString(R.string.font_medium), Typeface.NORMAL);
        light = Typeface.create(getString(R.string.font_light), Typeface.NORMAL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();   // again after Force stop and Back, so the list is current
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1D2430, 0xFF0B0D12}));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(64), dp(40), dp(64), dp(24));
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        page.addView(text(getString(R.string.running_apps), 40, light, 0xFFFFFFFF));
        ActivityManager am = getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mem);
        long used = mem.totalMem - mem.availMem;
        TextView memText = text(getString(R.string.running_apps_memory, gb(used), gb(mem.totalMem)), 17, medium, 0xB3FFFFFF);
        memText.setPadding(0, dp(6), 0, dp(10));
        page.addView(memText);
        page.addView(bar(mem.totalMem > 0 ? (float) used / mem.totalMem : 0f),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));

        if (!hasUsageAccess()) {
            TextView t = text(getString(R.string.running_apps_no_access), 17, medium, 0xE6FFFFFF);
            t.setPadding(0, dp(28), 0, 0);
            page.addView(t);
            setContentView(root);
            return;
        }
        TextView hint = text(getString(R.string.running_apps_hint), 14, medium, 0x80FFFFFF);
        hint.setPadding(0, dp(14), 0, dp(10));
        page.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(4));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        List<UsageStats> recent = recentApps();
        if (recent.isEmpty()) {
            list.addView(text(getString(R.string.running_apps_empty), 17, medium, 0xB3FFFFFF));
        }
        PackageManager pm = getPackageManager();
        long now = System.currentTimeMillis();
        View first = null;
        for (UsageStats s : recent) {
            View row = row(pm, s.getPackageName(), ago(now - s.getLastTimeUsed()));
            if (row == null) continue;
            list.addView(row);
            if (first == null) first = row;
        }
        setContentView(root);
        if (first != null) first.requestFocus();
    }

    private boolean hasUsageAccess() {
        AppOpsManager ops = getSystemService(AppOpsManager.class);
        int mode = android.os.Build.VERSION.SDK_INT >= 29
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName())
                : checkOpOld(ops);
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @SuppressWarnings("deprecation")
    private int checkOpOld(AppOpsManager ops) {
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
    }

    /** Launchable apps other than this one, used in the window, newest first, at most MAX_ROWS. */
    private List<UsageStats> recentApps() {
        UsageStatsManager usm = getSystemService(UsageStatsManager.class);
        long now = System.currentTimeMillis();
        Map<String, UsageStats> all = usm.queryAndAggregateUsageStats(now - WINDOW_MS, now);
        List<UsageStats> out = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (UsageStats s : all.values()) {
            String pkg = s.getPackageName();
            if (pkg.equals(getPackageName()) || s.getLastTimeUsed() < now - WINDOW_MS
                    || s.getTotalTimeInForeground() <= 0) continue;
            if (pm.getLeanbackLaunchIntentForPackage(pkg) == null && pm.getLaunchIntentForPackage(pkg) == null) continue;
            out.add(s);
        }
        out.sort((a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));
        return out.size() > MAX_ROWS ? out.subList(0, MAX_ROWS) : out;
    }

    private View row(PackageManager pm, String pkg, String when) {
        CharSequence label;
        Drawable icon;
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            label = pm.getApplicationLabel(info);
            Drawable banner = pm.getApplicationBanner(info);
            icon = banner != null ? banner : pm.getApplicationIcon(info);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setFocusable(true);
        v.setPadding(dp(14), dp(8), dp(18), dp(8));
        StateListDrawable states = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(0xFFFFFFFF);
        on.setCornerRadius(dp(16));
        GradientDrawable off = new GradientDrawable();
        off.setColor(0x0FFFFFFF);
        off.setCornerRadius(dp(16));
        states.addState(new int[]{android.R.attr.state_focused}, on);
        states.addState(new int[]{}, off);
        v.setBackground(states);

        ImageView img = new ImageView(this);
        img.setImageDrawable(icon);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(80), dp(45));
        ilp.rightMargin = dp(20);
        v.addView(img, ilp);

        TextView name = text(label.toString(), 19, medium, 0);
        name.setTextColor(ink(0xFF0B0B0D, 0xF2FFFFFF));
        name.setDuplicateParentStateEnabled(true);
        v.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView ago = text(when, 16, light, 0);
        ago.setTextColor(ink(0x990B0B0D, 0x99FFFFFF));
        ago.setDuplicateParentStateEnabled(true);
        ago.setPadding(0, 0, dp(24), 0);
        v.addView(ago);

        TextView close = text(getString(R.string.running_apps_close), 15, medium, 0);
        close.setTextColor(ink(0xFFFFFFFF, 0xE6FFFFFF));
        close.setDuplicateParentStateEnabled(true);
        close.setPadding(dp(16), dp(6), dp(16), dp(6));
        StateListDrawable pill = new StateListDrawable();
        GradientDrawable pillOn = new GradientDrawable();
        pillOn.setColor(0xFF0B0B0D);
        pillOn.setCornerRadius(dp(20));
        GradientDrawable pillOff = new GradientDrawable();
        pillOff.setColor(0);
        pillOff.setCornerRadius(dp(20));
        pillOff.setStroke(Math.max(1, dp(1)), 0x55FFFFFF);
        pill.addState(new int[]{android.R.attr.state_focused}, pillOn);
        pill.addState(new int[]{}, pillOff);
        close.setBackground(pill);
        v.addView(close);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        v.setLayoutParams(lp);
        v.setOnClickListener(x -> {
            Intent info = SystemSettings.appInfo(this, pkg);
            try {
                if (info == null) throw new ActivityNotFoundException();
                startActivity(info);
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            }
        });
        return v;
    }

    private View bar(float fraction) {
        FrameLayout track = new FrameLayout(this);
        GradientDrawable t = new GradientDrawable();
        t.setColor(0x1AFFFFFF);
        t.setCornerRadius(dp(4));
        track.setBackground(t);
        View fill = new View(this);
        GradientDrawable f = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF5AC8FA, 0xFFFF9F43});
        f.setCornerRadius(dp(4));
        fill.setBackground(f);
        track.addOnLayoutChangeListener((v, l, tp, r, b, ol, ot, or, ob) -> {
            int w = Math.round((r - l) * Math.max(0f, Math.min(1f, fraction)));
            if (fill.getLayoutParams() == null || fill.getLayoutParams().width != w) {
                fill.setLayoutParams(new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        });
        track.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        return track;
    }

    private String ago(long ms) {
        long min = ms / 60_000L;
        if (min < 1) return getString(R.string.running_apps_now);
        if (min < 60) return getString(R.string.running_apps_ago_min, (int) min);
        return getString(R.string.running_apps_ago_hours, (int) (min / 60));
    }

    private static String gb(long bytes) {
        return String.format(java.util.Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private TextView text(String s, float sp, Typeface face, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(face);
        if (color != 0) t.setTextColor(color);
        t.setSingleLine(false);
        return t;
    }

    private static ColorStateList ink(int focused, int normal) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{focused, normal});
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}

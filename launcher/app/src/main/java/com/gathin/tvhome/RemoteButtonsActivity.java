package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Launcher settings > Remote buttons: "Add a button" listens for one press on the remote, then offers apps and
 * actions for it; a saved button's OK offers Change and Remove. Saved as app_keys entries (RemoteButtons) that the
 * key helper reads at once. D-pad, OK, Back, Home, power and volume are never taken, so the TV stays usable.
 */
public class RemoteButtonsActivity extends Activity {

    /** Action words RemoteKeys understands, offered before the apps. */
    private static final String[] ACTIONS = {"ignore", "home", "settings", "sleep", "mute", "screensaver", "recents"};
    private static final int[] ACTION_LABELS = {R.string.remote_action_ignore, R.string.remote_action_home,
            R.string.settings, R.string.remote_action_sleep, R.string.remote_action_mute,
            R.string.screensaver_start, R.string.remote_action_recents};

    private float density;
    private boolean listening;
    private TextView prompt;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        build();
    }

    @Override
    protected void onPause() {
        stopListening();   // never leave the key helper passing everything through
        super.onPause();
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF111418);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(64), dp(40), dp(64), dp(24));
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(text(getString(R.string.remote_buttons), 34, R.string.font_light, 0xFFFFFFFF));
        TextView hint = text(getString(R.string.remote_buttons_hint), 14, R.string.font_medium, 0x8CFFFFFF);
        hint.setPadding(0, dp(8), 0, dp(14));
        page.addView(hint);

        prompt = text(getString(R.string.remote_buttons_listening), 22, R.string.font_medium, 0xFFFFFFFF);
        prompt.setGravity(Gravity.CENTER);
        prompt.setPadding(0, dp(40), 0, dp(40));
        prompt.setVisibility(View.GONE);
        page.addView(prompt);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View add = row(getString(R.string.remote_buttons_add), "");
        add.setOnClickListener(v -> startListening());
        list.addView(add);
        for (Map.Entry<String, String> e : RemoteButtons.load(this).entrySet()) {
            View r = row(RemoteButtons.buttonName(e.getKey()), targetLabel(e.getValue()));
            r.setOnClickListener(v -> editButton(e.getKey()));
            list.addView(r);
        }
        setContentView(root);
        add.requestFocus();
    }

    private void startListening() {
        listening = true;
        RemoteButtons.capturing = true;
        prompt.setVisibility(View.VISIBLE);
    }

    private void stopListening() {
        listening = false;
        RemoteButtons.capturing = false;
        if (prompt != null) prompt.setVisibility(View.GONE);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!listening) return super.dispatchKeyEvent(event);
        if (event.getAction() != KeyEvent.ACTION_UP) return true;   // act on the release, eat the press
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            stopListening();
            return true;
        }
        if (RemoteButtons.reserved(code)) {
            Toast.makeText(this, R.string.remote_buttons_reserved, Toast.LENGTH_SHORT).show();
            return true;
        }
        stopListening();
        String key = RemoteButtons.buttonKey(event);
        // Vendor buttons (TCL's Netflix and the like) reach only the window in front, never the key helper.
        if (code >= 1000) Toast.makeText(this, R.string.remote_buttons_home_only, Toast.LENGTH_LONG).show();
        pickTarget(key);
        return true;
    }

    private void editButton(String key) {
        String[] choices = {getString(R.string.remote_buttons_change), getString(R.string.remote_buttons_remove)};
        new AlertDialog.Builder(this)
                .setTitle(RemoteButtons.buttonName(key))
                .setItems(choices, (d, which) -> {
                    if (which == 0) {
                        pickTarget(key);
                    } else {
                        Map<String, String> all = RemoteButtons.load(this);
                        all.remove(key);
                        saved(all);
                    }
                })
                .show();
    }

    /** Actions first, then every launchable app by name. */
    private void pickTarget(String key) {
        List<String> labels = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < ACTIONS.length; i++) {
            labels.add(getString(ACTION_LABELS[i]));
            targets.add(ACTIONS[i]);
        }
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        found.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        List<String[]> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            apps.add(new String[]{String.valueOf(ri.loadLabel(pm)), pkg});
        }
        apps.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
        for (String[] a : apps) {
            labels.add(a[0]);
            targets.add(a[1]);
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.remote_buttons_pick, RemoteButtons.buttonName(key)))
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    Map<String, String> all = RemoteButtons.load(this);
                    all.put(key, targets.get(which));
                    saved(all);
                })
                .show();
    }

    private void saved(Map<String, String> all) {
        if (!RemoteButtons.save(this, all)) {
            Toast.makeText(this, R.string.library_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        HomeKeyService.reloadConfig();
        // Home rebuilds on its next resume when Launcher settings' change count moved (its own vendor-key map too).
        SharedPreferences choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        build();
    }

    private String targetLabel(String target) {
        for (int i = 0; i < ACTIONS.length; i++) if (ACTIONS[i].equals(target)) return getString(ACTION_LABELS[i]);
        try {
            return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(target, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return target;
        }
    }

    private View row(String label, String value) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setFocusable(true);
        v.setPadding(dp(18), dp(12), dp(18), dp(12));
        StateListDrawable states = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(0xFFFFFFFF);
        on.setCornerRadius(dp(14));
        GradientDrawable off = new GradientDrawable();
        off.setColor(0x14FFFFFF);
        off.setCornerRadius(dp(14));
        states.addState(new int[]{android.R.attr.state_focused}, on);
        states.addState(new int[]{}, off);
        v.setBackground(states);
        TextView l = text(label, 18, R.string.font_medium, 0);
        l.setTextColor(ink(0xFF0B0B0D, 0xF2FFFFFF));
        l.setDuplicateParentStateEnabled(true);
        v.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView r = text(value, 16, R.string.font_light, 0);
        r.setTextColor(ink(0x990B0B0D, 0x99FFFFFF));
        r.setDuplicateParentStateEnabled(true);
        v.addView(r);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView text(String s, float sp, int font, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(UiFont.get(this, font));
        if (color != 0) t.setTextColor(color);
        return t;
    }

    private static ColorStateList ink(int focused, int normal) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{focused, normal});
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}

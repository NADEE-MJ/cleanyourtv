package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Apps the owner hid from Home with the tile menu's Hide. Config.load adds them to hidden_apps; the sidebar's
 * Hidden apps lists them to show again. A theme's own hidden_apps are not in here and stay hidden.
 */
final class HiddenApps {

    private static final String PREFS = "hidden_apps";
    private static final String KEY = "packages";

    private HiddenApps() {}

    static Set<String> load(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(KEY, new HashSet<>()));
    }

    static boolean set(Context ctx, String pkg, boolean hidden) {
        Set<String> all = load(ctx);
        if (hidden ? !all.add(pkg) : !all.remove(pkg)) return true;
        return prefs(ctx).edit().putStringSet(KEY, all).commit();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

/**
 * The sidebar's sleep timer: after the chosen minutes the TV goes to standby, whatever is on screen. Runs in the
 * launcher's process, which the bound key helper (HomeKeyService) keeps alive; standby itself is that helper's
 * global action, so without the helper the timer cannot be set. Any reboot or standby clears it.
 */
final class SleepTimer {

    /** The steps the sidebar row cycles through; 0 is off. */
    static final int[] STEPS = {0, 15, 30, 45, 60, 90};
    private static final long WARN_MS = 60_000L;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static long endsAt;   // uptime ms, 0 when off
    private static int minutes;
    private static Context app;

    private static final Runnable warn = () -> {
        if (app != null) Toast.makeText(app, R.string.sleep_timer_warning, Toast.LENGTH_LONG).show();
    };
    private static final Runnable fire = () -> {
        endsAt = 0;
        minutes = 0;
        HomeKeyService.sleepNow();
    };

    private SleepTimer() {}

    static boolean available() {
        return HomeKeyService.canOpenPowerMenu() && android.os.Build.VERSION.SDK_INT >= 28;
    }

    /** The next step after the current one (Off after the last). Returns the minutes now set. */
    static int cycle(Context ctx) {
        int next = 0;
        for (int i = 0; i < STEPS.length; i++) {
            if (STEPS[i] == minutes) next = STEPS[(i + 1) % STEPS.length];
        }
        set(ctx, next);
        return next;
    }

    static void set(Context ctx, int mins) {
        app = ctx.getApplicationContext();
        handler.removeCallbacks(warn);
        handler.removeCallbacks(fire);
        minutes = mins;
        if (mins <= 0) {
            endsAt = 0;
            return;
        }
        long ms = mins * 60_000L;
        endsAt = SystemClock.uptimeMillis() + ms;
        handler.postDelayed(fire, ms);
        if (ms > WARN_MS) handler.postDelayed(warn, ms - WARN_MS);
    }

    /** Minutes left, rounded up; 0 when off. */
    static int minutesLeft() {
        if (endsAt == 0) return 0;
        long left = endsAt - SystemClock.uptimeMillis();
        return left <= 0 ? 0 : (int) ((left + 59_999L) / 60_000L);
    }
}

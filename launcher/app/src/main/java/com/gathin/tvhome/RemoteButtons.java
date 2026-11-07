package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remote buttons the owner remapped on the TV (Launcher settings > Remote buttons): button -> app or action word,
 * in app_keys form ("190:com.app", "scan:490:mute"). Config.load adds them after the theme's app_keys, so they win.
 * While the screen waits for a button press, {@link #capturing} makes the key helper pass every key through.
 */
final class RemoteButtons {

    private static final String PREFS = "remote_buttons";
    private static final String KEY = "entries";
    /** Set by RemoteButtonsActivity while it listens for the button to change; read by HomeKeyService. */
    static volatile boolean capturing;

    private RemoteButtons() {}

    /** Buttons that keep the TV usable: they are never offered for remapping. */
    static boolean reserved(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_WAKEUP: case KeyEvent.KEYCODE_SLEEP: case KeyEvent.KEYCODE_ESCAPE:
                return true;
            default:
                return false;
        }
    }

    /** The button's key in app_keys form: its keycode, or its scancode when Android gives it no keycode. */
    static String buttonKey(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_UNKNOWN) return String.valueOf(event.getKeyCode());
        return "scan:" + event.getScanCode();
    }

    /** "190" -> "YouTube button"-ish label from Android's name: KEYCODE_PROG_RED -> "Prog red". */
    static String buttonName(String key) {
        if (key.startsWith("scan:")) return "Button " + key.substring(5);
        int code;
        try {
            code = Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return key;
        }
        String name = KeyEvent.keyCodeToString(code);
        if (name.startsWith("KEYCODE_")) name = name.substring(8);
        if (name.matches("\\d+")) return "Button " + name;
        name = name.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** button key -> target (package or action word), in the order they were added. */
    static Map<String, String> load(Context ctx) {
        Map<String, String> out = new LinkedHashMap<>();
        String raw = prefs(ctx).getString(KEY, "");
        for (String line : raw.split("\n")) {
            int at = line.lastIndexOf('=');
            if (at > 0 && at < line.length() - 1) out.put(line.substring(0, at), line.substring(at + 1));
        }
        return out;
    }

    static boolean save(Context ctx, Map<String, String> map) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (b.length() > 0) b.append('\n');
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return prefs(ctx).edit().putString(KEY, b.toString()).commit();
    }

    /** The owner's mappings as app_keys entries. */
    static List<String> appKeys(Context ctx) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : load(ctx).entrySet()) out.add(e.getKey() + ":" + e.getValue());
        return out;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

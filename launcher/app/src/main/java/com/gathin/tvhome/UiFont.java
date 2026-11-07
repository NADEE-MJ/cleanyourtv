package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;

/**
 * The launcher's fonts for views built in code. res/values/fonts.xml names them as @font/... resources (a locale
 * can swap in a system family name instead); Typeface.create() on such a string silently gives the system font.
 */
final class UiFont {
    private UiFont() {}

    static Typeface get(Context ctx, int fontString) {
        String value = ctx.getString(fontString);
        if (value.contains("/font/")) {
            String name = value.substring(value.lastIndexOf('/') + 1);
            int dot = name.indexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            int id = ctx.getResources().getIdentifier(name, "font", ctx.getPackageName());
            if (id != 0) {
                try {
                    return ctx.getResources().getFont(id);
                } catch (Resources.NotFoundException ignored) {
                    // fall through to the system family
                }
            }
            return Typeface.DEFAULT;
        }
        return Typeface.create(value, Typeface.NORMAL);
    }
}

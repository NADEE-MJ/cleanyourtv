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
        // A bundled font is a file path (renamed by the release build); a locale's system family is a plain name.
        if (value.indexOf('/') >= 0) {
            // By id, not by name: the release build shrinks resource names, so a lookup by name finds nothing.
            int id = fontString == R.string.font_light ? R.font.outfit_light
                    : fontString == R.string.font_extralight ? R.font.outfit_extralight
                    : fontString == R.string.font_medium ? R.font.outfit_medium
                    : fontString == R.string.font_regular ? R.font.outfit_regular
                    : R.font.outfit;
            try {
                return ctx.getResources().getFont(id);
            } catch (Resources.NotFoundException ignored) {
                return Typeface.DEFAULT;
            }
        }
        return Typeface.create(value, Typeface.NORMAL);
    }
}

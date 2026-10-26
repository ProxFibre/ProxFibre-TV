package com.proxfibre.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.List;

/**
 * The ambience wallpapers, drawn from a palette of soft radial glows instead of shipped photos:
 * no extra megabytes in the APK and no licence to chase. The same recipe paints the Home
 * background (small, upscaled) and the full-screen carousel "Wallpapers" mode (large, with a very
 * slow zoom), so a fond looks the same in both places.
 */
final class Wallpapers {
    static final String[] KEYS = {"aurora", "midnight", "sunset", "ocean", "graphite", "ember", "jade", "violet"};
    static final String[] NAMES = {"Aurora", "Midnight", "Sunset", "Ocean", "Graphite", "Ember", "Jade", "Violet"};

    /** Carousel items point at a wallpaper with this prefix instead of a URL or a file. */
    static final String PREFIX = "wall:";

    private Wallpapers() {
    }

    static boolean isWall(String path) {
        return path != null && path.startsWith(PREFIX);
    }

    static String key(String path) {
        return path.substring(PREFIX.length());
    }

    /** Base fill for a recipe. */
    static int base(String key) {
        switch (key) {
            case "midnight": return 0xFF04060F;
            case "sunset": return 0xFF140812;
            case "ocean": return 0xFF031418;
            case "graphite": return 0xFF0C0C0E;
            case "ember": return 0xFF160705;
            case "jade": return 0xFF04120C;
            case "violet": return 0xFF0B0518;
            default: return 0xFF080C1E;
        }
    }

    /** Five glow colours, in the order the blobs are painted. */
    static int[] colors(String key) {
        switch (key) {
            case "midnight": return new int[]{0xFF1B2A6B, 0xFF0F3D63, 0xFF2B1B5E, 0xFF14254F, 0xFF22306E};
            case "sunset": return new int[]{0xFFB0412E, 0xFF8E2C6E, 0xFFD08A2E, 0xFF6B2A8E, 0xFFC0563A};
            case "ocean": return new int[]{0xFF0C6E86, 0xFF0B8C76, 0xFF1A4FA0, 0xFF0E5E6E, 0xFF2A7FB0};
            case "graphite": return new int[]{0xFF3A3A42, 0xFF2A2C34, 0xFF4A4A52, 0xFF26262C, 0xFF34363E};
            case "ember": return new int[]{0xFFB23A2A, 0xFF8E2C1E, 0xFFD0752E, 0xFF5E1A18, 0xFFC9563A};
            case "jade": return new int[]{0xFF1E8C62, 0xFF0B7F6E, 0xFF2E9E4A, 0xFF14584A, 0xFF3AB07A};
            case "violet": return new int[]{0xFF6E3AB0, 0xFF2F2F9E, 0xFFB02FA0, 0xFF3A1E6E, 0xFF5E4AC0};
            default: return new int[]{0xFF4B2DB0, 0xFF0B7F8C, 0xFFB02F6E, 0xFFC27A2C, 0xFF2150C0};
        }
    }

    /** The recipe drawn at any size: base colour, then five soft glows. */
    static Bitmap render(String key, int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(base(key));
        int[] cols = colors(key);
        float[][] blobs = {      // centre x, centre y, radius (fractions of the width)
                {0.14f, 0.18f, 0.62f},
                {0.88f, 0.22f, 0.50f},
                {0.72f, 0.92f, 0.55f},
                {0.20f, 0.95f, 0.42f},
                {0.50f, 0.45f, 0.38f}};
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        for (int i = 0; i < cols.length; i++) {
            int col = cols[i];
            p.setShader(new RadialGradient(blobs[i][0] * w, blobs[i][1] * h, blobs[i][2] * w,
                    new int[]{col & 0xD9FFFFFF, col & 0x59FFFFFF, col & 0x00FFFFFF},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        }
        // A faint sheen from the top-left gives the flat glows a little depth.
        p.setShader(new LinearGradient(0, 0, w * 0.6f, h, 0x1AFFFFFF, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return b;
    }

    /** One carousel item per fond: full-bleed, no title, no button. */
    static List<Suggestions.Item> items() {
        List<Suggestions.Item> out = new ArrayList<>();
        for (int i = 0; i < KEYS.length; i++) {
            Suggestions.Item it = new Suggestions.Item();
            it.id = 7000 + i;
            it.type = "wall";
            it.title = NAMES[i];
            it.overview = "";
            it.year = "";
            it.genres = "";
            it.logo = "";
            it.backdrop = PREFIX + KEYS[i];
            it.link = KEYS[i];
            out.add(it);
        }
        return out;
    }
}

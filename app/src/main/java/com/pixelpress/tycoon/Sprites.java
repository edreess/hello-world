package com.pixelpress.tycoon;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Procedural pixel-art sprites, drawn pixel-by-pixel at low resolution and
 * rendered scaled with nearest-neighbor for a crisp Kairosoft-style look.
 * Palette is sampled from the reference shop-floor artwork (muted greens,
 * warm grays, paper whites).
 */
public final class Sprites {

    // shared palette
    public static final int OUTLINE = 0xFF2E3236;
    public static final int PAPER = 0xFFEFEAD8;
    public static final int PAPER_SHADE = 0xFFD6D0BC;
    public static final int RED = 0xFFB2524A;
    public static final int SCREEN = 0xFF6E93B4;
    public static final int SCREEN_DARK = 0xFF3A4750;
    public static final int STEEL = 0xFFB0B5BA;
    public static final int STEEL_DARK = 0xFF83888D;

    public static final int FLOOR_A = 0xFF9A9DA1;
    public static final int FLOOR_B = 0xFF92959A;
    public static final int GROUT = 0xFF7E8186;
    public static final int WALL = 0xFFF1EFE8;
    public static final int WALL_SHADE = 0xFFDDDAD0;
    public static final int WALL_BASE = 0xFFB9B6AC;

    private static final int[] TIER_ACCENT = {0, 0xFFA07040, 0xFFC6CCD2, 0xFFD4AF37};

    // body palettes per category: {main, dark, light}
    private static final int[][] BODY = {
        {0xFFA8AEB4, 0xFF7E848A, 0xFFC6CCD2}, // DIGITAL: steel gray
        {0xFF6E8E6E, 0xFF4E6E52, 0xFF93B093}, // OFFSET: machine green
        {0xFF708090, 0xFF505E6C, 0xFF93A3B3}, // CTP: slate
        {0xFF7A997A, 0xFF567456, 0xFF9EBB9E}, // FINISH: pale line green
    };

    private static final int[] SHIRT = {0xFF30343A, 0xFF4A6B8A, 0xFF8A4A4A, 0xFF4A7A55};
    private static final int SKIN = 0xFFE8C09A, HAIR = 0xFF4A3628,
            PANTS = 0xFF56617A, SHOES = 0xFF2E3236;

    /** Tiny pixel buffer with drawing helpers. */
    private static final class Px {
        final int w, h;
        final int[] a;
        Px(int w, int h) { this.w = w; this.h = h; a = new int[w * h]; }
        void set(int x, int y, int c) {
            if (x >= 0 && x < w && y >= 0 && y < h) a[y * w + x] = c;
        }
        void rect(int x, int y, int rw, int rh, int c) {
            for (int j = y; j < y + rh; j++) for (int i = x; i < x + rw; i++) set(i, j, c);
        }
        void frame(int x, int y, int rw, int rh, int c) {
            rect(x, y, rw, 1, c); rect(x, y + rh - 1, rw, 1, c);
            rect(x, y, 1, rh, c); rect(x + rw - 1, y, 1, rh, c);
        }
        Bitmap bmp() { return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888); }
    }

    // cached machine sprites per type id
    private static final java.util.Map<String, Bitmap> cache = new java.util.HashMap<>();
    private static Bitmap[][] workerFrames; // [variant][frame]

    public static Bitmap machine(Catalog.MachineType t) {
        Bitmap b = cache.get(t.id);
        if (b == null) {
            switch (t.cat) {
                case Catalog.CAT_DIGITAL: b = digital(t); break;
                case Catalog.CAT_OFFSET: b = offset(t); break;
                case Catalog.CAT_CTP: b = ctp(t); break;
                default: b = finisher(t); break;
            }
            cache.put(t.id, b);
        }
        return b;
    }

    /** Digital press: upright production copier with console and side tray. */
    private static Bitmap digital(Catalog.MachineType t) {
        int[] c = BODY[Catalog.CAT_DIGITAL];
        Px p = new Px(30, 24);
        // body
        p.rect(4, 4, 18, 16, c[0]);
        p.frame(4, 4, 18, 16, OUTLINE);
        p.rect(5, 5, 16, 2, c[2]);              // top highlight
        p.rect(5, 17, 16, 2, c[1]);             // bottom shade
        // console
        p.rect(15, 1, 8, 5, c[1]);
        p.frame(15, 1, 8, 5, OUTLINE);
        p.rect(17, 2, 4, 2, SCREEN);
        // front panel details
        p.rect(6, 9, 6, 4, c[1]);
        p.frame(6, 9, 6, 4, OUTLINE);
        p.rect(14, 10, 2, 2, RED);
        p.rect(17, 10, 2, 2, SCREEN_DARK);
        // tier accent stripe + dots
        p.rect(5, 15, 16, 1, TIER_ACCENT[t.tier]);
        for (int i = 0; i < t.tier; i++) p.rect(6 + i * 3, 7, 2, 1, TIER_ACCENT[t.tier]);
        // output tray with paper
        p.rect(22, 12, 6, 2, STEEL_DARK);
        p.rect(22, 9, 5, 3, PAPER);
        p.rect(22, 11, 5, 1, PAPER_SHADE);
        // feet
        p.rect(5, 20, 3, 3, OUTLINE);
        p.rect(18, 20, 3, 3, OUTLINE);
        return p.bmp();
    }

    /** Offset press: long body, roller towers, feeder and delivery piles. */
    private static Bitmap offset(Catalog.MachineType t) {
        int[] c = BODY[Catalog.CAT_OFFSET];
        int towers = t.tier + 1; // 2..4 printing units
        Px p = new Px(38, 24);
        p.rect(2, 8, 34, 10, c[0]);
        p.frame(2, 8, 34, 10, OUTLINE);
        p.rect(3, 9, 32, 2, c[2]);
        p.rect(3, 15, 32, 2, c[1]);
        // printing unit towers with rollers
        for (int i = 0; i < towers; i++) {
            int x = 5 + i * 8;
            p.rect(x, 3, 6, 6, c[0]);
            p.frame(x, 3, 6, 6, OUTLINE);
            p.rect(x + 1, 4, 4, 1, c[2]);
            p.rect(x + 1, 6, 2, 2, STEEL);   // roller
            p.rect(x + 3, 6, 2, 2, STEEL_DARK);
        }
        // console + red stop button
        p.rect(28, 4, 7, 5, c[1]);
        p.frame(28, 4, 7, 5, OUTLINE);
        p.rect(29, 5, 3, 2, SCREEN);
        p.rect(33, 5, 1, 1, RED);
        // tier stripe
        p.rect(3, 14, 32, 1, TIER_ACCENT[t.tier]);
        // delivery pile (left) and feeder pile (right)
        p.rect(3, 12, 5, 3, PAPER);
        p.rect(3, 14, 5, 1, PAPER_SHADE);
        p.rect(30, 12, 5, 3, PAPER);
        p.rect(30, 14, 5, 1, PAPER_SHADE);
        // feet
        p.rect(4, 18, 3, 5, OUTLINE);
        p.rect(31, 18, 3, 5, OUTLINE);
        p.rect(17, 18, 3, 5, OUTLINE);
        return p.bmp();
    }

    /** CTP platesetter: low flatbed with lid, console, aluminum plate out. */
    private static Bitmap ctp(Catalog.MachineType t) {
        int[] c = BODY[Catalog.CAT_CTP];
        Px p = new Px(30, 20);
        p.rect(2, 7, 22, 8, c[0]);
        p.frame(2, 7, 22, 8, OUTLINE);
        p.rect(3, 8, 20, 2, c[2]);
        p.rect(3, 12, 20, 2, c[1]);
        // curved lid hint
        p.rect(5, 4, 14, 4, c[2]);
        p.frame(5, 4, 14, 4, OUTLINE);
        // console
        p.rect(23, 3, 6, 7, c[1]);
        p.frame(23, 3, 6, 7, OUTLINE);
        p.rect(24, 4, 4, 3, SCREEN);
        p.rect(24, 8, 2, 1, RED);
        // aluminum plate sliding out
        p.rect(0, 10, 6, 3, STEEL);
        p.rect(0, 12, 6, 1, STEEL_DARK);
        // tier dots
        for (int i = 0; i < t.tier; i++) p.rect(4 + i * 3, 6, 2, 1, TIER_ACCENT[t.tier]);
        // feet
        p.rect(3, 15, 3, 4, OUTLINE);
        p.rect(20, 15, 3, 4, OUTLINE);
        return p.bmp();
    }

    /** Finishing line: conveyor with paper stacks, like the reference image. */
    private static Bitmap finisher(Catalog.MachineType t) {
        int[] c = BODY[Catalog.CAT_FINISH];
        Px p = new Px(38, 20);
        // head unit
        p.rect(2, 3, 10, 12, c[0]);
        p.frame(2, 3, 10, 12, OUTLINE);
        p.rect(3, 4, 8, 2, c[2]);
        p.rect(3, 11, 8, 2, c[1]);
        p.rect(4, 7, 3, 2, SCREEN);
        p.rect(8, 7, 1, 1, RED);
        // conveyor bed
        p.rect(12, 8, 24, 5, c[1]);
        p.frame(12, 8, 24, 5, OUTLINE);
        p.rect(13, 9, 22, 1, 0xFF3E4A42); // belt
        // paper stacks riding the belt
        for (int i = 0; i < 2 + t.tier; i++) {
            int x = 14 + i * 5;
            if (x + 3 > 35) break;
            p.rect(x, 5, 3, 4, PAPER);
            p.rect(x, 8, 3, 1, PAPER_SHADE);
            p.frame(x, 5, 3, 4, 0);
        }
        // tier stripe on head
        p.rect(3, 13, 8, 1, TIER_ACCENT[t.tier]);
        // legs
        p.rect(3, 15, 2, 4, OUTLINE);
        p.rect(14, 13, 2, 6, OUTLINE);
        p.rect(32, 13, 2, 6, OUTLINE);
        return p.bmp();
    }

    private static final String[] WORKER_STAND = {
        "...hhhh...",
        "..hhhhhh..",
        "..hffffh..",
        "..hfffff..",
        "...ffff...",
        "..tttttt..",
        ".tttttttt.",
        ".t.tttt.t.",
        ".f.tttt.f.",
        "...pppp...",
        "...pppp...",
        "...p..p...",
        "...p..p...",
        "...o..o...",
    };
    private static final String[] WORKER_WALK = {
        "...hhhh...",
        "..hhhhhh..",
        "..hffffh..",
        "..hfffff..",
        "...ffff...",
        "..tttttt..",
        ".tttttttt.",
        ".t.tttt.t.",
        ".f.tttt.f.",
        "...pppp...",
        "..pp..pp..",
        "..p....p..",
        "..o....o..",
        "..........",
    };

    public static Bitmap worker(int variant, int frame) {
        if (workerFrames == null) {
            workerFrames = new Bitmap[SHIRT.length][2];
            for (int v = 0; v < SHIRT.length; v++) {
                workerFrames[v][0] = fromMap(WORKER_STAND, SHIRT[v]);
                workerFrames[v][1] = fromMap(WORKER_WALK, SHIRT[v]);
            }
        }
        return workerFrames[variant % SHIRT.length][frame % 2];
    }

    private static Bitmap fromMap(String[] rows, int shirt) {
        int h = rows.length, w = rows[0].length();
        Px p = new Px(w, h);
        for (int y = 0; y < h; y++) {
            String r = rows[y];
            for (int x = 0; x < r.length(); x++) {
                int col;
                switch (r.charAt(x)) {
                    case 'h': col = HAIR; break;
                    case 'f': col = SKIN; break;
                    case 't': col = shirt; break;
                    case 'p': col = PANTS; break;
                    case 'o': col = SHOES; break;
                    default: col = Color.TRANSPARENT;
                }
                p.set(x, y, col);
            }
        }
        return p.bmp();
    }

    private Sprites() {}
}

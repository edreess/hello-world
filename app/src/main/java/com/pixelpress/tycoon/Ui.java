package com.pixelpress.tycoon;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;

/**
 * All rendering and input for the game, in a 960x540 virtual canvas.
 * Immediate-mode UI: buttons are rebuilt every frame and hit-tested on tap.
 */
public class Ui {
    public static final int VW = 960, VH = 540;

    // panels
    static final int P_NONE = 0, P_JOBS = 1, P_PICK_MACHINE = 2, P_PICK_WORKER = 3,
            P_STAFF = 4, P_HIRE = 5, P_SHOP = 6, P_MARKET = 7, P_HELP = 8;

    // button actions
    static final int A_NONE = 0, A_CLOSE = 1, A_SPEED = 2, A_OPEN = 3, A_ACCEPT = 4,
            A_SIDEBAR = 5, A_PICK_M = 6, A_PICK_W = 7, A_TRAIN = 8, A_FIRE = 9,
            A_HIRE = 10, A_BUY = 11, A_SELL = 12, A_SCROLL = 13, A_NEWGAME = 14,
            A_HIREPANEL = 15, A_SLOT = 16;

    // layout
    static final int HUD_H = 40;
    static final int BAR_Y = 490;
    static final int SIDE_X = 664;
    static final int[] SLOT_CX = {115, 335, 555};
    static final int[] SLOT_CY = {195, 350};
    static final int PANEL_X = 40, PANEL_Y = 52, PANEL_W = 880, PANEL_H = 432;
    static final int ROW_H = 54;

    // colors
    static final int COL_HUD = 0xFF3E5244;
    static final int COL_PANEL = 0xFFF1EBD8;
    static final int COL_PANEL_ROW = 0xFFE7E0CA;
    static final int COL_SIDE = 0xFF343A40;
    static final int COL_SIDE_ROW = 0xFF42494F;
    static final int COL_BTN = 0xFF5A7A5F;
    static final int COL_BTN_DK = 0xFF44604A;
    static final int COL_BTN_DISABLED = 0xFF8A8F94;
    static final int COL_TEXT_DARK = 0xFF33362E;
    static final int COL_GOOD = 0xFF7FBF6A;
    static final int COL_BAD = 0xFFD86A5A;
    static final int COL_GOLD = 0xFFE8C05A;

    public int panel = P_NONE;
    public int scroll = 0;
    public Job pendingJob;      // job being assigned
    public Machine pendingMachine;

    private final Paint paint = new Paint();
    private final Paint text = new Paint();
    private final Rect tmpRect = new Rect();
    private double clock;

    private static final class Btn {
        final Rect r; final int action, p1;
        Btn(Rect r, int action, int p1) { this.r = r; this.action = action; this.p1 = p1; }
    }
    private final List<Btn> buttons = new ArrayList<>();

    public Ui() {
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        text.setAntiAlias(false);
        paint.setAntiAlias(false);
    }

    // ================================================================ draw

    public void draw(Canvas c, Game g, double dt) {
        clock += dt;
        buttons.clear();

        drawFloor(c);
        drawMachines(c, g, dt);
        drawWorkers(c, g, dt);
        drawSidebar(c, g);
        drawHud(c, g);
        drawBottomBar(c, g);
        drawPanel(c, g);
        drawToasts(c, g);
    }

    private void drawFloor(Canvas c) {
        // wall
        paint.setColor(WALL());
        c.drawRect(0, HUD_H, SIDE_X, 112, paint);
        paint.setColor(Sprites.WALL_SHADE);
        c.drawRect(0, 96, SIDE_X, 104, paint);
        paint.setColor(Sprites.WALL_BASE);
        c.drawRect(0, 104, SIDE_X, 112, paint);
        // window strip on the wall
        paint.setColor(0xFFBFD2DC);
        for (int x = 40; x < SIDE_X - 80; x += 200) {
            c.drawRect(x, 52, x + 90, 88, paint);
            paint.setColor(0xFF9FB6C4);
            c.drawRect(x, 72, x + 90, 74, paint);
            paint.setColor(0xFFBFD2DC);
        }
        // floor tiles
        int tile = 46;
        for (int ty = 112; ty < BAR_Y; ty += tile) {
            for (int tx = 0; tx < SIDE_X; tx += tile) {
                boolean alt = ((tx / tile) + (ty / tile)) % 2 == 0;
                paint.setColor(alt ? Sprites.FLOOR_A : Sprites.FLOOR_B);
                c.drawRect(tx, ty, Math.min(tx + tile, SIDE_X), Math.min(ty + tile, BAR_Y), paint);
            }
        }
        paint.setColor(Sprites.GROUT);
        for (int ty = 112; ty < BAR_Y; ty += tile) c.drawRect(0, ty, SIDE_X, ty + 1, paint);
        for (int tx = 0; tx < SIDE_X; tx += tile) c.drawRect(tx, 112, tx + 1, BAR_Y, paint);
    }

    private int WALL() { return Sprites.WALL; }

    private void drawMachines(Canvas c, Game g, double dt) {
        for (int i = 0; i < Game.SLOTS; i++) {
            int cx = SLOT_CX[i % 3], cy = SLOT_CY[i / 3];
            Machine m = g.slots[i];
            if (m == null) {
                paint.setColor(0x30000000);
                c.drawRect(cx - 70, cy - 30, cx + 70, cy + 42, paint);
                text.setColor(0x66FFFFFF);
                text.setTextSize(14);
                drawCentered(c, "EMPTY SLOT", cx, cy + 8);
                buttons.add(new Btn(new Rect(cx - 70, cy - 42, cx + 70, cy + 48), A_SLOT, i));
                continue;
            }
            m.animT += dt;
            Bitmap b = Sprites.machine(m.type());
            int scale = 4;
            int w = b.getWidth() * scale, h = b.getHeight() * scale;
            tmpRect.set(cx - w / 2, cy + 44 - h, cx + w / 2, cy + 44);
            c.drawBitmap(b, null, tmpRect, paint);

            if (m.busy()) {
                // blinking activity light
                if (((int) (m.animT * 3)) % 2 == 0) {
                    paint.setColor(COL_GOOD);
                    c.drawRect(cx + w / 2 - 12, cy + 44 - h + 4, cx + w / 2 - 4, cy + 44 - h + 12, paint);
                }
                // progress bar
                double pr = m.job.progress();
                paint.setColor(0xFF20242A);
                c.drawRect(cx - 46, cy - h + 26, cx + 46, cy - h + 36, paint);
                paint.setColor(COL_GOOD);
                c.drawRect(cx - 44, cy - h + 28, (float) (cx - 44 + 88 * pr), cy - h + 34, paint);
            }
            buttons.add(new Btn(new Rect(cx - 76, cy + 44 - h - 12, cx + 76, cy + 48), A_SLOT, i));
        }
    }

    private void drawWorkers(Canvas c, Game g, double dt) {
        int scale = 4;
        for (int i = 0; i < g.workers.size(); i++) {
            Worker w = g.workers.get(i);
            w.animT += dt;
            if (w.wx == 0 && w.wy == 0) {
                w.wx = 60 + g.rng.nextInt(540);
                w.wy = 400 + g.rng.nextInt(60);
            }
            double targetX, targetY;
            if (w.job != null && w.job.machine != null) {
                Machine m = w.job.machine;
                targetX = SLOT_CX[m.slot % 3] - 84;
                targetY = SLOT_CY[m.slot / 3] + 6;
            } else {
                if ((w.tx == 0 && w.ty == 0) || (Math.abs(w.tx - w.wx) < 3 && Math.abs(w.ty - w.wy) < 3)) {
                    if (g.rng.nextInt(120) == 0 || w.tx == 0) {
                        w.tx = 40 + g.rng.nextInt(560);
                        w.ty = 390 + g.rng.nextInt(70);
                    }
                }
                targetX = w.tx;
                targetY = w.ty;
            }
            double speed = 60 * dt;
            double dx = targetX - w.wx, dy = targetY - w.wy;
            double dist = Math.sqrt(dx * dx + dy * dy);
            boolean moving = dist > 3;
            if (moving) {
                w.wx += dx / dist * Math.min(speed, dist);
                w.wy += dy / dist * Math.min(speed, dist);
            }
            int frame = moving ? ((int) (w.animT * 6)) % 2 : (w.job != null ? ((int) (w.animT * 2)) % 2 : 0);
            Bitmap b = Sprites.worker(w.spriteVariant, frame);
            int bw = b.getWidth() * scale, bh = b.getHeight() * scale;
            tmpRect.set((int) w.wx - bw / 2, (int) w.wy - bh, (int) w.wx + bw / 2, (int) w.wy);
            c.drawBitmap(b, null, tmpRect, paint);
        }
    }

    private void drawSidebar(Canvas c, Game g) {
        paint.setColor(COL_SIDE);
        c.drawRect(SIDE_X, HUD_H, VW, BAR_Y, paint);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(16);
        c.drawText("ACTIVE JOBS " + g.active.size() + "/" + Game.MAX_ACTIVE_JOBS, SIDE_X + 10, HUD_H + 22, text);

        int y = HUD_H + 32;
        for (int i = 0; i < g.active.size() && y + 50 < BAR_Y; i++) {
            Job j = g.active.get(i);
            Catalog.JobTemplate t = j.tpl();
            paint.setColor(COL_SIDE_ROW);
            c.drawRect(SIDE_X + 6, y, VW - 6, y + 48, paint);
            text.setTextSize(14);
            text.setColor(0xFFFFFFFF);
            c.drawText(t.name, SIDE_X + 12, y + 16, text);

            double daysLeft = (j.deadlineHour - g.hours) / Game.HOURS_PER_DAY;
            String dl = daysLeft < 0 ? "LATE!" : String.format("%.1fd", daysLeft);
            text.setColor(daysLeft < 1.5 ? COL_BAD : 0xFFB9BDC1);
            text.setTextSize(13);
            c.drawText(dl, VW - 58, y + 16, text);

            if (j.phase == Job.PH_UNASSIGNED) {
                text.setColor(((int) (clock * 2)) % 2 == 0 ? COL_GOLD : 0xFFFFFFFF);
                c.drawText("TAP TO ASSIGN", SIDE_X + 12, y + 38, text);
            } else {
                text.setColor(0xFF9FD59A);
                c.drawText(j.phaseName(), SIDE_X + 12, y + 38, text);
                paint.setColor(0xFF20242A);
                c.drawRect(SIDE_X + 130, y + 28, VW - 14, y + 40, paint);
                paint.setColor(COL_GOOD);
                double pr = j.progress();
                c.drawRect(SIDE_X + 132, y + 30, (float) (SIDE_X + 132 + (VW - 16 - SIDE_X - 132) * pr), y + 38, paint);
            }
            buttons.add(new Btn(new Rect(SIDE_X + 6, y, VW - 6, y + 48), A_SIDEBAR, i));
            y += 52;
        }
        if (g.active.isEmpty()) {
            text.setColor(0xFF8A9096);
            text.setTextSize(14);
            c.drawText("No jobs yet.", SIDE_X + 12, HUD_H + 56, text);
            c.drawText("Check JOBS below!", SIDE_X + 12, HUD_H + 76, text);
        }
    }

    private void drawHud(Canvas c, Game g) {
        paint.setColor(COL_HUD);
        c.drawRect(0, 0, VW, HUD_H, paint);
        text.setTextSize(18);
        text.setColor(g.money < 0 ? COL_BAD : COL_GOLD);
        c.drawText("$" + Game.fmt((long) g.money), 12, 27, text);
        text.setColor(0xFFB9E0FF);
        c.drawText("★" + g.rep, 190, 27, text);
        text.setColor(0xFFFFFFFF);
        c.drawText("Lv" + g.companyLevel(), 290, 27, text);
        c.drawText(g.dateString(), 370, 27, text);

        // speed button
        Rect sp = new Rect(VW - 76, 4, VW - 8, HUD_H - 4);
        paint.setColor(COL_BTN_DK);
        c.drawRect(sp.left, sp.top, sp.right, sp.bottom, paint);
        text.setColor(0xFFFFFFFF);
        drawCentered(c, Game.SPEED_LABELS[g.speedIdx], sp.centerX(), sp.centerY() + 6);
        buttons.add(new Btn(sp, A_SPEED, 0));
    }

    private void drawBottomBar(Canvas c, Game g) {
        paint.setColor(0xFF2A2E33);
        c.drawRect(0, BAR_Y, VW, VH, paint);
        String[] labels = {"JOBS", "STAFF", "SHOP", "MARKET", "?"};
        int[] panels = {P_JOBS, P_STAFF, P_SHOP, P_MARKET, P_HELP};
        int bw = 170, gap = 14, x = 16;
        for (int i = 0; i < labels.length; i++) {
            int w = i == 4 ? 60 : bw;
            Rect r = new Rect(x, BAR_Y + 6, x + w, VH - 6);
            paint.setColor(panel == panels[i] ? COL_BTN : COL_BTN_DK);
            c.drawRect(r.left, r.top, r.right, r.bottom, paint);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(18);
            drawCentered(c, labels[i], r.centerX(), r.centerY() + 6);
            if (i == 0 && !g.offers.isEmpty()) {
                paint.setColor(COL_BAD);
                c.drawRect(r.right - 26, r.top - 4, r.right + 2, r.top + 20, paint);
                text.setTextSize(15);
                drawCentered(c, "" + g.offers.size(), r.right - 12, r.top + 13);
            }
            buttons.add(new Btn(r, A_OPEN, panels[i]));
            x += w + gap;
        }
    }

    // ---------------------------------------------------------------- panels

    private void drawPanel(Canvas c, Game g) {
        if (panel == P_NONE) return;
        paint.setColor(0x99000000);
        c.drawRect(0, 0, VW, VH, paint);
        paint.setColor(Sprites.OUTLINE);
        c.drawRect(PANEL_X - 3, PANEL_Y - 3, PANEL_X + PANEL_W + 3, PANEL_Y + PANEL_H + 3, paint);
        paint.setColor(COL_PANEL);
        c.drawRect(PANEL_X, PANEL_Y, PANEL_X + PANEL_W, PANEL_Y + PANEL_H, paint);
        paint.setColor(COL_HUD);
        c.drawRect(PANEL_X, PANEL_Y, PANEL_X + PANEL_W, PANEL_Y + 36, paint);

        String title;
        switch (panel) {
            case P_JOBS: title = "JOB OFFERS"; break;
            case P_PICK_MACHINE: title = "PICK A PRESS  -  " + pendingJob.tpl().name; break;
            case P_PICK_WORKER: title = "PICK A WORKER  -  " + pendingJob.tpl().name; break;
            case P_STAFF: title = "STAFF"; break;
            case P_HIRE: title = "HIRE  (fee = 2x salary)"; break;
            case P_SHOP: title = "EQUIPMENT SHOP  -  slots " + usedSlots(g) + "/" + Game.SLOTS; break;
            case P_MARKET: title = "MARKETS"; break;
            default: title = "HOW TO PLAY"; break;
        }
        text.setColor(0xFFFFFFFF);
        text.setTextSize(18);
        c.drawText(title, PANEL_X + 12, PANEL_Y + 25, text);

        Rect close = new Rect(PANEL_X + PANEL_W - 36, PANEL_Y + 2, PANEL_X + PANEL_W - 2, PANEL_Y + 34);
        paint.setColor(COL_BAD);
        c.drawRect(close.left, close.top, close.right, close.bottom, paint);
        drawCentered(c, "X", close.centerX(), close.centerY() + 6);
        buttons.add(new Btn(close, A_CLOSE, 0));

        if (panel == P_STAFF) {
            Rect hire = new Rect(PANEL_X + PANEL_W - 150, PANEL_Y + 4, PANEL_X + PANEL_W - 44, PANEL_Y + 32);
            paint.setColor(COL_BTN);
            c.drawRect(hire.left, hire.top, hire.right, hire.bottom, paint);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(16);
            drawCentered(c, "HIRE+", hire.centerX(), hire.centerY() + 5);
            buttons.add(new Btn(hire, A_HIREPANEL, 0));
        }

        switch (panel) {
            case P_JOBS: drawJobsPanel(c, g); break;
            case P_PICK_MACHINE: drawPickMachine(c, g); break;
            case P_PICK_WORKER: drawPickWorker(c, g); break;
            case P_STAFF: drawStaffPanel(c, g); break;
            case P_HIRE: drawHirePanel(c, g); break;
            case P_SHOP: drawShopPanel(c, g); break;
            case P_MARKET: drawMarketPanel(c, g); break;
            case P_HELP: drawHelpPanel(c, g); break;
        }
    }

    private int usedSlots(Game g) {
        int n = 0;
        for (Machine m : g.slots) if (m != null) n++;
        return n;
    }

    private int rowsTop() { return PANEL_Y + 44; }
    private int visibleRows() { return (PANEL_H - 52) / ROW_H; }

    /** Draws scroll arrows if needed and returns the clamped scroll offset. */
    private int scrolled(Canvas c, int totalRows) {
        int vis = visibleRows();
        if (totalRows <= vis) { scroll = 0; return 0; }
        scroll = Math.max(0, Math.min(scroll, totalRows - vis));
        Rect up = new Rect(PANEL_X + PANEL_W - 38, rowsTop(), PANEL_X + PANEL_W - 6, rowsTop() + 60);
        Rect dn = new Rect(PANEL_X + PANEL_W - 38, PANEL_Y + PANEL_H - 66, PANEL_X + PANEL_W - 6, PANEL_Y + PANEL_H - 6);
        paint.setColor(scroll > 0 ? COL_BTN : COL_BTN_DISABLED);
        c.drawRect(up.left, up.top, up.right, up.bottom, paint);
        paint.setColor(scroll < totalRows - vis ? COL_BTN : COL_BTN_DISABLED);
        c.drawRect(dn.left, dn.top, dn.right, dn.bottom, paint);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(18);
        drawCentered(c, "▲", up.centerX(), up.centerY() + 6);
        drawCentered(c, "▼", dn.centerX(), dn.centerY() + 6);
        buttons.add(new Btn(up, A_SCROLL, -1));
        buttons.add(new Btn(dn, A_SCROLL, 1));
        return scroll;
    }

    private Rect rowRect(int visIdx) {
        int y = rowsTop() + visIdx * ROW_H;
        return new Rect(PANEL_X + 8, y, PANEL_X + PANEL_W - 44, y + ROW_H - 6);
    }

    private void rowBg(Canvas c, Rect r, int visIdx) {
        paint.setColor(visIdx % 2 == 0 ? COL_PANEL_ROW : 0xFFEDE6D0);
        c.drawRect(r.left, r.top, r.right, r.bottom, paint);
    }

    private void actionBtn(Canvas c, Rect row, String label, boolean enabled, int action, int p1) {
        Rect b = new Rect(row.right - 130, row.top + 8, row.right - 8, row.bottom - 8);
        paint.setColor(enabled ? COL_BTN : COL_BTN_DISABLED);
        c.drawRect(b.left, b.top, b.right, b.bottom, paint);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(15);
        drawCentered(c, label, b.centerX(), b.centerY() + 5);
        if (enabled) buttons.add(new Btn(b, action, p1));
    }

    private void drawJobsPanel(Canvas c, Game g) {
        int off = scrolled(c, g.offers.size());
        if (g.offers.isEmpty()) {
            text.setColor(COL_TEXT_DARK);
            text.setTextSize(16);
            c.drawText("No offers right now - clients will call soon.", PANEL_X + 20, rowsTop() + 30, text);
            return;
        }
        for (int v = 0; v < visibleRows(); v++) {
            int i = off + v;
            if (i >= g.offers.size()) break;
            Job j = g.offers.get(i);
            Catalog.JobTemplate t = j.tpl();
            Rect r = rowRect(v);
            rowBg(c, r, v);
            text.setColor(COL_TEXT_DARK);
            text.setTextSize(16);
            c.drawText(t.name + "  [" + Catalog.MARKETS[t.market].name + "]", r.left + 8, r.top + 20, text);
            text.setTextSize(13);
            String needs = (t.catReq == Catalog.CAT_OFFSET ? "offset" : t.catReq == Catalog.CAT_DIGITAL ? "digital" : "any press")
                    + (t.finishLevel > 0 ? ", finish Lv" + t.finishLevel : "");
            c.drawText(Game.fmt(t.sheets) + " sheets  Q" + t.qReq + "  " + t.deadlineDays + "d  (" + needs + ")",
                    r.left + 8, r.top + 40, text);
            text.setColor(0xFF3E6E44);
            text.setTextSize(16);
            c.drawText("$" + Game.fmt(j.pay), r.right - 260, r.top + 20, text);
            double expDays = (j.offerExpiresHour - g.hours) / Game.HOURS_PER_DAY;
            text.setColor(0xFF8A6A3A);
            text.setTextSize(13);
            c.drawText("offer " + String.format("%.1fd", Math.max(0, expDays)), r.right - 260, r.top + 40, text);
            String blocker = g.acceptBlocker(j);
            if (blocker == null) {
                actionBtn(c, r, "ACCEPT", true, A_ACCEPT, i);
            } else {
                text.setColor(COL_BAD);
                text.setTextSize(12);
                c.drawText(blocker, r.right - 132, r.bottom - 12, text);
            }
        }
    }

    private void drawPickMachine(Canvas c, Game g) {
        List<Machine> list = g.compatiblePresses(pendingJob, true);
        int off = scrolled(c, list.size());
        if (list.isEmpty()) {
            text.setColor(COL_TEXT_DARK);
            text.setTextSize(16);
            c.drawText("No compatible press is idle right now.", PANEL_X + 20, rowsTop() + 30, text);
            c.drawText("Wait for a machine to free up, or buy one in SHOP.", PANEL_X + 20, rowsTop() + 56, text);
            return;
        }
        for (int v = 0; v < visibleRows(); v++) {
            int i = off + v;
            if (i >= list.size()) break;
            Machine m = list.get(i);
            Catalog.MachineType t = m.type();
            Rect r = rowRect(v);
            rowBg(c, r, v);
            text.setColor(COL_TEXT_DARK);
            text.setTextSize(16);
            c.drawText(t.name + "  T" + t.tier, r.left + 8, r.top + 20, text);
            text.setTextSize(13);
            c.drawText(Game.fmt(t.speed) + " sheets/h   quality " + t.quality + "   $" + t.runCost + "/h run",
                    r.left + 8, r.top + 40, text);
            actionBtn(c, r, "USE", true, A_PICK_M, m.slot);
        }
    }

    private void drawPickWorker(Canvas c, Game g) {
        List<Worker> list = g.idleWorkers();
        int off = scrolled(c, list.size());
        if (list.isEmpty()) {
            text.setColor(COL_TEXT_DARK);
            text.setTextSize(16);
            c.drawText("Everyone is busy. Wait or hire more staff.", PANEL_X + 20, rowsTop() + 30, text);
            return;
        }
        int cat = pendingMachine != null ? pendingMachine.type().cat : Catalog.CAT_DIGITAL;
        for (int v = 0; v < visibleRows(); v++) {
            int i = off + v;
            if (i >= list.size()) break;
            Worker w = list.get(i);
            Rect r = rowRect(v);
            rowBg(c, r, v);
            text.setColor(COL_TEXT_DARK);
            text.setTextSize(16);
            c.drawText(w.name + "  " + w.title() + " Lv" + w.level, r.left + 8, r.top + 20, text);
            text.setTextSize(13);
            c.drawText("press " + w.press + "  digital " + w.digital + "  prepress " + w.prepress
                    + "  finish " + w.finishing, r.left + 8, r.top + 40, text);
            text.setColor(0xFF3E6E44);
            c.drawText("machine skill: " + w.skillFor(cat), r.right - 300, r.top + 20, text);
            actionBtn(c, r, "ASSIGN", true, A_PICK_W, g.workers.indexOf(w));
        }
    }

    private void drawStaffPanel(Canvas c, Game g) {
        int off = scrolled(c, g.workers.size());
        for (int v = 0; v < visibleRows(); v++) {
            int i = off + v;
            if (i >= g.workers.size()) break;
            Worker w = g.workers.get(i);
            Rect r = rowRect(v);
            rowBg(c, r, v);
            text.setColor(COL_TEXT_DARK);
            text.setTextSize(15);
            c.drawText(w.name + "  " + w.title() + " Lv" + w.level
                    + (w.job != null ? "  [working]" : ""), r.left + 8, r.top + 18, text);
            text.setTextSize(13);
            c.drawText("press " + w.press + " dig " + w.digital + " pre " + w.prepress
                    + " fin " + w.finishing + "   $" + Game.fmt(w.salary) + "/mo", r.left + 8, r.top + 36, text);

            // train buttons: one per discipline
            String[] tl = {"D", "P", "C", "F"};
            int[] cats = {Catalog.CAT_DIGITAL, Catalog.CAT_OFFSET, Catalog.CAT_CTP, Catalog.CAT_FINISH};
            for (int b = 0; b < 4; b++) {
                Rect tb = new Rect(r.right - 340 + b * 44, r.top + 8, r.right - 340 + b * 44 + 38, r.bottom - 8);
                boolean en = w.job == null && g.money >= Game.TRAIN_COST;
                paint.setColor(en ? COL_BTN_DK : COL_BTN_DISABLED);
                c.drawRect(tb.left, tb.top, tb.right, tb.bottom, paint);
                text.setColor(0xFFFFFFFF);
                text.setTextSize(14);
                drawCentered(c, tl[b], tb.centerX(), tb.centerY() + 5);
                if (en) buttons.add(new Btn(tb, A_TRAIN, i * 4 + cats[b]));
            }
            text.setColor(COL_TEXT_DARK);
            text.setTextSize(11);
            c.drawText("train $2k:", r.right - 340, r.top + 6, text);
            actionBtn(c, r, "FIRE", w.job == null && g.workers.size() > 1, A_FIRE, i);
        }
    }

    private void drawHirePanel(Canvas c, Game g) {
        int off = scrolled(c, g.candidates.size());
        if (g.candidates.isEmpty()) {
            text.setColor(COL_TEXT_DARK);
            text.setTextSize(16);
            c.drawText("No candidates. New applicants arrive monthly.", PANEL_X + 20, rowsTop() + 30, text);
        }
        for (int v = 0; v < visibleRows(); v++) {
            int i = off + v;
            if (i >= g.candidates.size()) break;
            Worker w = g.candidates.get(i);
            Rect r = rowRect(v);
            rowBg(c, r, v);
            text.setColor(COL_TEXT_DARK);
            text.setTextSize(16);
            c.drawText(w.name + "  " + w.title() + " Lv" + w.level, r.left + 8, r.top + 20, text);
            text.setTextSize(13);
            c.drawText("press " + w.press + "  digital " + w.digital + "  prepress " + w.prepress
                    + "  finish " + w.finishing, r.left + 8, r.top + 40, text);
            text.setColor(0xFF3E6E44);
            c.drawText("$" + Game.fmt(w.salary) + "/mo  fee $" + Game.fmt(w.salary * 2), r.right - 330, r.top + 20, text);
            String blocker = g.hireBlocker(w);
            actionBtn(c, r, blocker == null ? "HIRE" : "----", blocker == null, A_HIRE, i);
            if (blocker != null) {
                text.setColor(COL_BAD);
                text.setTextSize(12);
                c.drawText(blocker, r.right - 132, r.bottom - 12, text);
            }
        }
    }

    private void drawShopPanel(Canvas c, Game g) {
        // rows: owned machines first, then the catalog
        List<Machine> owned = new ArrayList<>();
        for (Machine m : g.slots) if (m != null) owned.add(m);
        int total = owned.size() + Catalog.MACHINES.length;
        int off = scrolled(c, total);
        for (int v = 0; v < visibleRows(); v++) {
            int i = off + v;
            if (i >= total) break;
            Rect r = rowRect(v);
            rowBg(c, r, v);
            if (i < owned.size()) {
                Machine m = owned.get(i);
                Catalog.MachineType t = m.type();
                text.setColor(0xFF3E5244);
                text.setTextSize(15);
                c.drawText("[OWNED] " + t.name + "  T" + t.tier + (m.busy() ? "  (busy)" : ""),
                        r.left + 8, r.top + 20, text);
                text.setTextSize(13);
                c.drawText(machineStats(t) + "   sell $" + Game.fmt((int) (t.price * 0.4)),
                        r.left + 8, r.top + 40, text);
                actionBtn(c, r, "SELL", !m.busy(), A_SELL, m.slot);
            } else {
                Catalog.MachineType t = Catalog.MACHINES[i - owned.size()];
                boolean locked = g.rep < t.repReq;
                text.setColor(locked ? 0xFF9A9488 : COL_TEXT_DARK);
                text.setTextSize(15);
                c.drawText(t.name + "  [" + Catalog.CAT_NAMES[t.cat] + " T" + t.tier + "]",
                        r.left + 8, r.top + 20, text);
                text.setTextSize(13);
                c.drawText(locked ? "Unlocks at " + t.repReq + " rep" : t.desc, r.left + 8, r.top + 40, text);
                text.setColor(locked ? 0xFF9A9488 : 0xFF3E6E44);
                text.setTextSize(15);
                c.drawText("$" + Game.fmt(t.price), r.right - 270, r.top + 20, text);
                if (!locked) {
                    text.setTextSize(12);
                    text.setColor(0xFF6A6E62);
                    c.drawText(machineStats(t), r.right - 270, r.top + 40, text);
                }
                String blocker = g.buyBlocker(t);
                actionBtn(c, r, "BUY", blocker == null, A_BUY, i - owned.size());
                if (blocker != null && !locked) {
                    text.setColor(COL_BAD);
                    text.setTextSize(12);
                    c.drawText(blocker, r.right - 132, r.bottom - 12, text);
                }
            }
        }
    }

    private String machineStats(Catalog.MachineType t) {
        switch (t.cat) {
            case Catalog.CAT_CTP:
                return "-" + (t.tier * 18) + "% offset setup, +" + (t.tier * 3) + " quality";
            case Catalog.CAT_FINISH:
                return "finish Lv" + t.tier + "  " + Game.fmt(t.speed) + "/h  $" + t.runCost + "/h";
            default:
                return Game.fmt(t.speed) + "/h  quality " + t.quality + "  $" + t.runCost + "/h";
        }
    }

    private void drawMarketPanel(Canvas c, Game g) {
        for (int i = 0; i < Catalog.MARKETS.length; i++) {
            Catalog.Market m = Catalog.MARKETS[i];
            Rect r = new Rect(PANEL_X + 8, rowsTop() + i * 88, PANEL_X + PANEL_W - 8, rowsTop() + i * 88 + 80);
            rowBg(c, r, i);
            boolean open = g.marketUnlocked(m);
            text.setColor(open ? COL_TEXT_DARK : 0xFF9A9488);
            text.setTextSize(17);
            c.drawText(m.name + (open ? "" : "  [LOCKED - need " + m.repReq + " rep]"), r.left + 10, r.top + 24, text);
            if (open) {
                double d = g.demand(m);
                text.setTextSize(13);
                text.setColor(0xFF6A6E62);
                c.drawText("demand " + (d > 1.25 ? "HOT!" : d < 0.75 ? "slow" : "steady"), r.left + 10, r.top + 46, text);
                paint.setColor(0xFF20242A);
                c.drawRect(r.left + 10, r.top + 54, r.left + 410, r.top + 70, paint);
                paint.setColor(d > 1.25 ? COL_GOLD : d < 0.75 ? COL_BAD : COL_GOOD);
                c.drawRect(r.left + 12, r.top + 56, (float) (r.left + 12 + 396 * (d / 1.5)), r.top + 68, paint);
                // job types in this market
                StringBuilder sb = new StringBuilder();
                for (Catalog.JobTemplate t : Catalog.JOBS)
                    if (t.market == m.id) sb.append(t.name).append("  ");
                text.setColor(0xFF6A6E62);
                c.drawText(sb.toString(), r.left + 430, r.top + 46, text);
            }
        }
    }

    private void drawHelpPanel(Canvas c, Game g) {
        String[] lines = {
            "Run your print shop to fame and fortune!",
            "",
            "1. JOBS: accept client contracts before they expire.",
            "2. Tap a job in the right list to assign a press + worker.",
            "3. Offset presses need plate setup (CTP machines speed it up).",
            "4. Jobs with a finish level need a finishing machine installed.",
            "5. Deliver on time and above the quality bar to earn reputation.",
            "6. Reputation unlocks new machines and markets.",
            "7. Wages and rent are paid monthly. Don't go broke!",
            "",
            "Progress is saved automatically.",
        };
        text.setColor(COL_TEXT_DARK);
        text.setTextSize(16);
        for (int i = 0; i < lines.length; i++)
            c.drawText(lines[i], PANEL_X + 24, rowsTop() + 24 + i * 26, text);
        Rect ng = new Rect(PANEL_X + 24, PANEL_Y + PANEL_H - 56, PANEL_X + 220, PANEL_Y + PANEL_H - 16);
        paint.setColor(COL_BAD);
        c.drawRect(ng.left, ng.top, ng.right, ng.bottom, paint);
        text.setColor(0xFFFFFFFF);
        drawCentered(c, "NEW GAME", ng.centerX(), ng.centerY() + 6);
        buttons.add(new Btn(ng, A_NEWGAME, 0));
    }

    private void drawToasts(Canvas c, Game g) {
        text.setTextSize(15);
        if (panel != P_NONE) {
            // panels own the screen: show just the newest toast, up top
            if (g.toasts.isEmpty()) return;
            Game.Toast t = g.toasts.get(g.toasts.size() - 1);
            float w = text.measureText(t.text);
            float x = (VW - w) / 2;
            paint.setColor(0xE0202428);
            c.drawRect(x - 8, HUD_H + 2, x + w + 8, HUD_H + 26, paint);
            text.setColor(0xFFF1EBD8);
            c.drawText(t.text, x, HUD_H + 20, text);
            return;
        }
        int y = BAR_Y - 14;
        for (int i = g.toasts.size() - 1; i >= 0; i--) {
            Game.Toast t = g.toasts.get(i);
            float w = text.measureText(t.text);
            paint.setColor(0xD0202428);
            c.drawRect(8, y - 18, 24 + w, y + 6, paint);
            text.setColor(0xFFF1EBD8);
            c.drawText(t.text, 16, y, text);
            y -= 30;
        }
    }

    private void drawCentered(Canvas c, String s, float cx, float cy) {
        c.drawText(s, cx - text.measureText(s) / 2, cy, text);
    }

    // ================================================================ input

    public void tap(int x, int y, Game g) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Btn b = buttons.get(i);
            if (b.r.contains(x, y)) {
                act(b, g);
                return;
            }
        }
        if (panel != P_NONE) closePanel(); // tap outside panel closes it
    }

    private void act(Btn b, Game g) {
        switch (b.action) {
            case A_CLOSE:
                closePanel();
                break;
            case A_SPEED:
                g.speedIdx = (g.speedIdx + 1) % Game.SPEED_HOURS_PER_SEC.length;
                break;
            case A_OPEN:
                if (panel == b.p1) closePanel();
                else openPanel(b.p1);
                break;
            case A_ACCEPT:
                if (b.p1 < g.offers.size() && g.acceptOffer(g.offers.get(b.p1)))
                    g.toast("Job accepted! Tap it on the right to assign.");
                break;
            case A_SIDEBAR:
                if (b.p1 < g.active.size()) {
                    Job j = g.active.get(b.p1);
                    if (j.phase == Job.PH_UNASSIGNED) {
                        pendingJob = j;
                        openPanel(P_PICK_MACHINE);
                    } else {
                        g.toast(j.tpl().name + ": " + j.phaseName() + " on " + j.machine.type().name
                                + " (" + j.worker.name + ")");
                    }
                }
                break;
            case A_PICK_M: {
                Machine m = g.slots[b.p1];
                if (m != null && !m.busy() && pendingJob != null) {
                    pendingMachine = m;
                    openPanel(P_PICK_WORKER);
                }
                break;
            }
            case A_PICK_W: {
                if (pendingJob != null && pendingMachine != null && !pendingMachine.busy()
                        && b.p1 < g.workers.size()) {
                    Worker w = g.workers.get(b.p1);
                    if (w.job == null) {
                        g.startJob(pendingJob, pendingMachine, w);
                        g.toast(pendingJob.tpl().name + " started on " + pendingMachine.type().name);
                        pendingJob = null;
                        pendingMachine = null;
                        closePanel();
                    }
                }
                break;
            }
            case A_TRAIN:
                g.train(g.workers.get(b.p1 / 4), b.p1 % 4);
                break;
            case A_FIRE:
                if (b.p1 < g.workers.size()) g.fire(g.workers.get(b.p1));
                break;
            case A_HIRE:
                if (b.p1 < g.candidates.size()) g.hire(g.candidates.get(b.p1));
                break;
            case A_HIREPANEL:
                openPanel(P_HIRE);
                break;
            case A_BUY:
                g.buyMachine(Catalog.MACHINES[b.p1]);
                break;
            case A_SELL:
                if (g.slots[b.p1] != null) g.sellMachine(g.slots[b.p1]);
                break;
            case A_SCROLL:
                scroll += b.p1;
                break;
            case A_NEWGAME:
                resetGame(g);
                closePanel();
                break;
            case A_SLOT: {
                Machine m = g.slots[b.p1];
                if (m == null) g.toast("Empty slot - buy machines in SHOP");
                else if (m.busy()) g.toast(m.type().name + ": " + m.job.tpl().name + " (" + m.job.phaseName() + ")");
                else g.toast(m.type().name + " - idle");
                break;
            }
        }
    }

    private void resetGame(Game g) {
        for (int i = 0; i < Game.SLOTS; i++) g.slots[i] = null;
        g.workers.clear();
        g.offers.clear();
        g.active.clear();
        g.candidates.clear();
        g.toasts.clear();
        g.speedIdx = 1;
        g.nextOfferHour = 0;
        g.newGame();
        g.dirty = true;
    }

    private void openPanel(int p) {
        panel = p;
        scroll = 0;
        if (p != P_PICK_MACHINE && p != P_PICK_WORKER) {
            pendingJob = null;
            pendingMachine = null;
        }
    }

    private void closePanel() {
        panel = P_NONE;
        pendingJob = null;
        pendingMachine = null;
    }
}

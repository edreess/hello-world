package com.pixelpress.tycoon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** Render loop + input, mapping the 960x540 virtual canvas onto the screen. */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private Thread thread;
    private volatile boolean running;
    private volatile boolean surfaceReady;

    private final Game game;
    private final Ui ui = new Ui();
    private float scale = 1, offX = 0, offY = 0;
    private double lastSaveClock;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        game = SaveGame.loadOrNew(context);
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            if (!surfaceReady) {
                sleepQuiet();
                continue;
            }
            long now = System.nanoTime();
            double dt = Math.min(0.05, (now - last) / 1e9);
            last = now;

            synchronized (game) {
                game.update(dt);
            }

            Canvas c = getHolder().lockCanvas();
            if (c != null) {
                try {
                    c.drawColor(Color.BLACK);
                    c.save();
                    c.translate(offX, offY);
                    c.scale(scale, scale);
                    synchronized (game) {
                        ui.draw(c, game, dt);
                    }
                    c.restore();
                } finally {
                    getHolder().unlockCanvasAndPost(c);
                }
            }

            // periodic autosave when the sim flags changes
            lastSaveClock += dt;
            if (game.dirty && lastSaveClock > 5) {
                lastSaveClock = 0;
                saveNow();
            }
            sleepQuiet();
        }
    }

    private void sleepQuiet() {
        try { Thread.sleep(16); } catch (InterruptedException ignored) {}
    }

    private void saveNow() {
        synchronized (game) {
            SaveGame.save(getContext(), game);
            game.dirty = false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            int x = (int) ((e.getX() - offX) / scale);
            int y = (int) ((e.getY() - offY) / scale);
            synchronized (game) {
                ui.tap(x, y, game);
            }
        }
        return true;
    }

    public void onResume() {
        running = true;
        if (thread == null || !thread.isAlive()) {
            thread = new Thread(this, "game-loop");
            thread.start();
        }
    }

    public void onPause() {
        running = false;
        saveNow();
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) {}
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        scale = Math.min(w / (float) Ui.VW, h / (float) Ui.VH);
        offX = (w - Ui.VW * scale) / 2f;
        offY = (h - Ui.VH * scale) / 2f;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
    }
}

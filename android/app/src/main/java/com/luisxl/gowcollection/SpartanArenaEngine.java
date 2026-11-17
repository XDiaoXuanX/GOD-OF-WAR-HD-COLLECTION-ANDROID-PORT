package com.luisxl.gowcollection;

import android.graphics.*;
import android.view.SurfaceHolder;
import java.util.*;

public class SpartanArenaEngine implements Runnable {
    private SurfaceHolder holder;
    private volatile boolean running = false;
    private Thread thread;
    private int w = 1280, h = 720, btns = 0, lx = 128, ly = 128, prevBtns = 0;
    private float px, py, pvx, pvy, pAng = 0, hp = 100, rage = 30;
    private float dashT = 0, dashCd = 0, atkT = 0, jZ = 0, jVz = 0, invT = 0, shkT = 0;
    private int wave = 1, score = 0, combo = 0;
    private boolean gameOver = false;
    private final Random rng = new Random();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final List<Arc> arcs = new ArrayList<>();

    static class Enemy {
        float x, y, hp, maxHp, r, spd, cd;
        boolean boss;
        Enemy(float x, float y, float hp, float r, float spd, boolean boss) {
            this.x = x; this.y = y; this.hp = hp; this.maxHp = hp;
            this.r = r; this.spd = spd; this.boss = boss;
        }
    }
    static class Spark {
        float x, y, vx, vy, life;
        Spark(float x, float y, float vx, float vy, float life) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.life = life;
        }
    }
    static class Arc {
        float x, y, ang, rad, life;
        Arc(float x, float y, float ang, float rad, float life) {
            this.x = x; this.y = y; this.ang = ang; this.rad = rad; this.life = life;
        }
    }

    public void setHolder(SurfaceHolder h) { this.holder = h; }
    public void setSize(int w, int h) {
        if (w > 0 && h > 0) {
            boolean init = (px == 0 && py == 0);
            this.w = w; this.h = h;
            if (init) reset();
        }
    }
    public void updateInput(int b, int lx, int ly) { this.btns = b; this.lx = lx; this.ly = ly; }
    public synchronized void start() {
        if (!running) {
            running = true;
            if (px == 0 && py == 0) reset();
            thread = new Thread(this, "SpartanArena");
            thread.start();
        }
    }
    public synchronized void stop() { running = false; }

    private synchronized void reset() {
        px = w * 0.5f; py = h * 0.55f; hp = 100; rage = 30;
        wave = 1; score = 0; combo = 0; gameOver = false;
        enemies.clear(); sparks.clear(); arcs.clear();
        spawnWave(1);
    }

    private void spawnWave(int wv) {
        for (int i = 0; i < 3 + wv * 2; i++) {
            float a = rng.nextFloat() * 6.283f, d = Math.max(w, h) * 0.55f;
            enemies.add(new Enemy(w * 0.5f + (float)Math.cos(a)*d, h * 0.55f + (float)Math.sin(a)*d, 35 + wv*12, 24, 115 + wv*8, false));
        }
        if (wv % 2 == 0) enemies.add(new Enemy(w * 0.5f, 95, 170 + wv*45, 44, 85, true));
    }

    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            update(dt);
            render();
            try { Thread.sleep(14); } catch (Exception ignored) {}
        }
    }

    private synchronized void update(float dt) {
        int b = btns, pr = b & ~prevBtns; prevBtns = b;
        if (gameOver) {
            if ((pr & (ControlesView.CRUZ | ControlesView.QUADRADO | ControlesView.START)) != 0) reset();
            return;
        }
        if (dashCd > 0) dashCd -= dt;
        if (invT > 0) invT -= dt;
        if (shkT > 0) shkT -= dt;
        if (atkT > 0) atkT -= dt;

        float ax = (lx - 128) / 128f, ay = (ly - 128) / 128f;
        if ((b & ControlesView.ESQUERDA) != 0) ax -= 1;
        if ((b & ControlesView.DIREITA) != 0) ax += 1;
        if ((b & ControlesView.CIMA) != 0) ay -= 1;
        if ((b & ControlesView.BAIXO) != 0) ay += 1;
        float mag = (float)Math.hypot(ax, ay);
        if (mag > 0.18f) { ax /= Math.max(1, mag); ay /= Math.max(1, mag); pAng = (float)Math.atan2(ay, ax); }
        else { ax = 0; ay = 0; }

        if ((pr & ControlesView.CIRCULO) != 0 && dashCd <= 0) {
            dashT = 0.18f; dashCd = 0.6f; invT = 0.3f;
            pvx = (float)Math.cos(pAng) * 760; pvy = (float)Math.sin(pAng) * 760;
        }
        if ((pr & ControlesView.CRUZ) != 0 && jZ <= 0.1f) jVz = 430;
        if (jZ > 0 || jVz != 0) {
            jZ += jVz * dt; jVz -= 1150 * dt;
            if (jZ <= 0) { jZ = 0; jVz = 0; hitArea(130, 24); }
        }
        if ((pr & ControlesView.QUADRADO) != 0 && atkT <= 0.08f) {
            atkT = 0.22f; arcs.add(new Arc(px, py, pAng, 125, 0.2f)); hitArea(135, 28);
        }
        if ((pr & (ControlesView.TRIANGULO | ControlesView.R1)) != 0 && atkT <= 0.08f) {
            boolean full = rage >= 100; if (full) rage = 0;
            atkT = 0.35f; float rad = full ? 230 : 165;
            arcs.add(new Arc(px, py, pAng, rad, 0.3f)); shkT = 0.2f; hitArea(rad, full ? 90 : 48);
        }

        if (dashT > 0) { dashT -= dt; px += pvx * dt; py += pvy * dt; }
        else { px += ax * 290 * dt; py += ay * 290 * dt; }
        px = Math.max(65, Math.min(w - 65, px));
        py = Math.max(115, Math.min(h - 65, py));

        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            if (e.cd > 0) e.cd -= dt;
            float dx = px - e.x, dy = py - e.y, d = (float)Math.hypot(dx, dy);
            if (d > e.r + 18) { e.x += (dx / d) * e.spd * dt; e.y += (dy / d) * e.spd * dt; }
            else if (e.cd <= 0 && invT <= 0 && jZ < 28) {
                e.cd = 1.0f; invT = 0.5f; hp -= e.boss ? 18 : 10; shkT = 0.2f;
                if (hp <= 0) { hp = 0; gameOver = true; }
            }
            if (e.hp <= 0) {
                score += e.boss ? 500 : 100;
                rage = Math.min(100, rage + 15);
                hp = Math.min(100, hp + 5);
                it.remove();
            }
        }
        if (enemies.isEmpty()) { wave++; spawnWave(wave); }
        Iterator<Arc> ait = arcs.iterator();
        while (ait.hasNext()) { Arc a = ait.next(); a.life -= dt; if (a.life <= 0) ait.remove(); }
        Iterator<Spark> sit = sparks.iterator();
        while (sit.hasNext()) { Spark s = sit.next(); s.x += s.vx*dt; s.y += s.vy*dt; s.life -= dt; if (s.life <= 0) sit.remove(); }
    }

    private void hitArea(float rad, float dmg) {
        boolean hit = false;
        for (Enemy e : enemies) {
            float dx = e.x - px, dy = e.y - py, d = (float)Math.hypot(dx, dy);
            if (d <= rad + e.r) {
                e.hp -= dmg; hit = true;
                if (d > 1) { e.x += (dx / d) * 32; e.y += (dy / d) * 32; }
                for (int i = 0; i < 8; i++) {
                    float a = rng.nextFloat() * 6.283f;
                    sparks.add(new Spark(e.x, e.y, (float)Math.cos(a)*180, (float)Math.sin(a)*180, 0.3f));
                }
            }
        }
        if (hit) { combo++; rage = Math.min(100, rage + 6); }
    }

    private synchronized void render() {
        if (holder == null || !holder.getSurface().isValid()) return;
        Canvas c = holder.lockCanvas();
        if (c == null) return;
        try {
            w = c.getWidth(); h = c.getHeight();
            c.save();
            if (shkT > 0) c.translate((rng.nextFloat()-0.5f)*16, (rng.nextFloat()-0.5f)*16);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(w*0.5f, h*0.55f, Math.max(w, h)*0.75f,
                    Color.rgb(48, 22, 18), Color.rgb(12, 10, 14), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);

            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(Color.argb(100, 255, 110, 40));
            c.drawRoundRect(new RectF(55, 105, w - 55, h - 55), 26, 26, p);
            for (Arc a : arcs) {
                p.setStrokeWidth(16); p.setColor(Color.rgb(255, 130, 30));
                c.drawArc(new RectF(a.x - a.rad, a.y - a.rad, a.x + a.rad, a.y + a.rad),
                        (float)Math.toDegrees(a.ang) - 65, 130, false, p);
            }

            p.setStyle(Paint.Style.FILL);
            for (Enemy e : enemies) {
                p.setColor(e.boss ? Color.rgb(190, 30, 30) : Color.rgb(75, 145, 95));
                c.drawCircle(e.x, e.y, e.r, p);
                p.setColor(Color.RED);
                c.drawRect(e.x - 25, e.y - e.r - 12, e.x - 25 + 50 * Math.max(0, e.hp / e.maxHp), e.y - e.r - 6, p);
            }

            float dy = py - jZ;
            p.setColor(Color.rgb(235, 225, 215)); c.drawCircle(px, dy, 22, p);
            p.setColor(Color.rgb(255, 120, 20)); p.setStrokeWidth(5);
            c.drawLine(px, dy, px + (float)Math.cos(pAng+0.5f)*32, dy + (float)Math.sin(pAng+0.5f)*32, p);
            c.drawLine(px, dy, px + (float)Math.cos(pAng-0.5f)*32, dy + (float)Math.sin(pAng-0.5f)*32, p);

            for (Spark s : sparks) { p.setColor(Color.rgb(255, 150, 30)); c.drawCircle(s.x, s.y, 5, p); }
            c.restore();

            p.setColor(Color.rgb(50, 200, 80)); c.drawRoundRect(new RectF(36, 26, 36 + 320*(hp/100f), 50), 8, 8, p);
            p.setColor(rage >= 100 ? Color.rgb(255, 50, 20) : Color.rgb(255, 170, 30));
            c.drawRoundRect(new RectF(36, 56, 36 + 240*(rage/100f), 72), 6, 6, p);
            p.setColor(Color.WHITE); p.setTextSize(26); p.setFakeBoldText(true);
            c.drawText("WAVE " + wave + "  |  SCORE " + score + "  |  COMBO " + combo, 380, 50, p);

            if (gameOver) {
                p.setColor(Color.argb(185, 0, 0, 0)); c.drawRect(0, 0, w, h, p);
                p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.RED); p.setTextSize(64);
                c.drawText("YOU DIED - PRESS X / SQUARE", w * 0.5f, h * 0.5f, p);
            }
        } finally {
holder.unlockCanvasAndPost(c);

}

}

}

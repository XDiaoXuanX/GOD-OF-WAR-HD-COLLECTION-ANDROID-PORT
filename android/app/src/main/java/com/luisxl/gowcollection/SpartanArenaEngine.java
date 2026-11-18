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
    private float dashT = 0, dashCd = 0, atkT = 0, jZ = 0, jVz = 0, invT = 0, shkT = 0, animT = 0, lightningT = 0;
    private int wave = 1, score = 0, combo = 0, comboT = 0;
    private boolean gameOver = false;
    private final Random rng = new Random();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final List<Arc> arcs = new ArrayList<>();

    static class Enemy {
        float x, y, hp, maxHp, r, spd, cd, flash;
        boolean boss;
        Enemy(float x, float y, float hp, float r, float spd, boolean boss) {
            this.x = x; this.y = y; this.hp = hp; this.maxHp = hp;
            this.r = r; this.spd = spd; this.boss = boss;
        }
    }
    static class Spark {
        float x, y, vx, vy, life;
        int col;
        Spark(float x, float y, float vx, float vy, float life, int col) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.life = life; this.col = col;
        }
    }
    static class Arc {
        float x, y, ang, rad, life, maxLife;
        boolean heavy;
        Arc(float x, float y, float ang, float rad, float life, boolean heavy) {
            this.x = x; this.y = y; this.ang = ang; this.rad = rad; this.life = life; this.maxLife = life; this.heavy = heavy;
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
        px = w * 0.5f; py = h * 0.56f; hp = 100; rage = 35;
        wave = 1; score = 0; combo = 0; gameOver = false;
        enemies.clear(); sparks.clear(); arcs.clear();
        spawnWave(1);
    }

    private void spawnWave(int wv) {
        for (int i = 0; i < 3 + wv * 2; i++) {
            float a = rng.nextFloat() * 6.283f, d = Math.max(w, h) * 0.52f;
            enemies.add(new Enemy(w * 0.5f + (float)Math.cos(a)*d, h * 0.56f + (float)Math.sin(a)*d, 40 + wv*14, 26, 118 + wv*8, false));
        }
        if (wv % 2 == 0) enemies.add(new Enemy(w * 0.5f, 110, 190 + wv*50, 48, 88, true));
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
        animT += dt;
        if (lightningT > 0) lightningT -= dt;
        else if (rng.nextFloat() < 0.008f) lightningT = 0.14f;

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
            dashT = 0.18f; dashCd = 0.55f; invT = 0.32f;
            pvx = (float)Math.cos(pAng) * 780; pvy = (float)Math.sin(pAng) * 780;
        }
        if ((pr & ControlesView.CRUZ) != 0 && jZ <= 0.1f) jVz = 440;
        if (jZ > 0 || jVz != 0) {
            jZ += jVz * dt; jVz -= 1180 * dt;
            if (jZ <= 0) { jZ = 0; jVz = 0; shkT = 0.14f; hitArea(145, 28); }
        }
        if ((pr & ControlesView.QUADRADO) != 0 && atkT <= 0.06f) {
            atkT = 0.22f; arcs.add(new Arc(px, py, pAng, 145, 0.22f, false)); hitArea(150, 32);
        }
        if ((pr & (ControlesView.TRIANGULO | ControlesView.R1)) != 0 && atkT <= 0.06f) {
            boolean full = rage >= 100; if (full) rage = 0;
            atkT = 0.36f; float rad = full ? 250 : 180;
            arcs.add(new Arc(px, py, pAng, rad, 0.32f, true)); shkT = 0.22f; hitArea(rad, full ? 105 : 52);
        }

        if (dashT > 0) { dashT -= dt; px += pvx * dt; py += pvy * dt; }
        else { px += ax * 300 * dt; py += ay * 300 * dt; }
        px = Math.max(85, Math.min(w - 85, px));
        py = Math.max(135, Math.min(h - 75, py));

        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            if (e.cd > 0) e.cd -= dt;
            if (e.flash > 0) e.flash -= dt;
            float dx = px - e.x, dy = py - e.y, d = (float)Math.hypot(dx, dy);
            if (d > e.r + 20) { e.x += (dx / d) * e.spd * dt; e.y += (dy / d) * e.spd * dt; }
            else if (e.cd <= 0 && invT <= 0 && jZ < 28) {
                e.cd = 1.0f; invT = 0.5f; hp -= e.boss ? 18 : 9; shkT = 0.2f;
                if (hp <= 0) { hp = 0; gameOver = true; }
            }
            if (e.hp <= 0) {
                score += e.boss ? 500 : 100;
                rage = Math.min(100, rage + 16);
                hp = Math.min(100, hp + 6);
                for (int i = 0; i < 16; i++) {
                    float a = rng.nextFloat() * 6.283f;
                    sparks.add(new Spark(e.x, e.y, (float)Math.cos(a)*220, (float)Math.sin(a)*220, 0.4f, Color.rgb(255, 60, 20)));
                }
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
                e.hp -= dmg; e.flash = 0.14f; hit = true;
                if (d > 1) { e.x += (dx / d) * 36; e.y += (dy / d) * 36; }
                for (int i = 0; i < 9; i++) {
                    float a = rng.nextFloat() * 6.283f;
                    sparks.add(new Spark(e.x, e.y, (float)Math.cos(a)*190, (float)Math.sin(a)*190, 0.3f, Color.rgb(255, 160, 30)));
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
            // Langit badai & laut Aegean gelap
            int skyTop = lightningT > 0 ? Color.rgb(95, 105, 120) : Color.rgb(18, 22, 26);
            p.setShader(new LinearGradient(0, 0, 0, h, skyTop, Color.rgb(10, 12, 14), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);

            // Dek Kapal Kayu Aegean (papan kayu + paku perunggu)
            RectF deck = new RectF(68, 118, w - 68, h - 58);
            p.setShader(new RadialGradient(w*0.5f, h*0.56f, Math.max(w, h)*0.65f,
                    Color.rgb(66, 48, 34), Color.rgb(28, 20, 15), Shader.TileMode.CLAMP));
            c.drawRoundRect(deck, 24, 24, p);
            p.setShader(null);

            p.setColor(Color.argb(65, 15, 10, 8));
            p.setStrokeWidth(3);
            for (float y = 150; y < h - 70; y += 36) c.drawLine(76, y, w - 76, y, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(6); p.setColor(Color.rgb(135, 98, 46));
            c.drawRoundRect(deck, 24, 24, p);

            // Tebasan rantai api Blades of Chaos
            for (Arc a : arcs) {
                float t = a.life / a.maxLife;

p.setStrokeWidth(a.heavy ? 24 * t : 16 * t);
p.setColor(a.heavy ? Color.rgb(255, 70, 20) : Color.rgb(255, 155, 35));
float sweep = a.heavy ? 320 : 140;
float start = a.heavy ? (float)Math.toDegrees(a.ang) : (float)Math.toDegrees(a.ang) - 70;
c.drawArc(new RectF(a.x - a.rad, a.y - a.rad, a.x + a.rad, a.y + a.rad), start, sweep, false, p);
}
// Musuh (Undead Legionnaire & Cyclops Boss berzirah)
p.setStyle(Paint.Style.FILL);
for (Enemy e : enemies) {
p.setColor(Color.argb(120, 0, 0, 0));
c.drawOval(new RectF(e.x - e.r, e.y + e.r * 0.5f, e.x + e.r, e.y + e.r * 1.05f), p);
p.setColor(e.flash > 0 ? Color.WHITE : (e.boss ? Color.rgb(125, 38, 32) : Color.rgb(68, 82, 74)));
c.drawCircle(e.x, e.y, e.r, p);
// Helm & Zirah Perunggu
p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(e.boss ? 6 : 4);
p.setColor(e.boss ? Color.rgb(218, 165, 55) : Color.rgb(150, 140, 115));
c.drawCircle(e.x, e.y, e.r * 0.85f, p);
p.setStyle(Paint.Style.FILL);
// Mata menyala
p.setColor(e.boss ? Color.rgb(255, 60, 20) : Color.rgb(255, 190, 40));
c.drawCircle(e.x - e.r * 0.28f, e.y - e.r * 0.15f, e.boss ? 5 : 3.5f, p);
c.drawCircle(e.x + e.r * 0.28f, e.y - e.r * 0.15f, e.boss ? 5 : 3.5f, p);
// Bar darah musuh
p.setColor(Color.argb(180, 10, 10, 10));
c.drawRect(e.x - 28, e.y - e.r - 15, e.x + 28, e.y - e.r - 8, p);
p.setColor(Color.rgb(215, 35, 35));
c.drawRect(e.x - 28, e.y - e.r - 15, e.x - 28 + 56 * Math.max(0, e.hp / e.maxHp), e.y - e.r - 8, p);
}
// Karakter Kratos (Abu-abu pucat + Tato Merah + Rantai Blades of Chaos)
p.setColor(Color.argb(130, 0, 0, 0));
c.drawOval(new RectF(px - 26, py + 12, px + 26, py + 28), p);
float dy = py - jZ;
// Rantai & Bilah Blades of Chaos
float reach = atkT > 0 ? 68f : 36f;
for (int side = -1; side <= 1; side += 2) {
float ba = pAng + side * (atkT > 0 ? 0.35f : 0.65f);
float bx = px + (float)Math.cos(ba) * reach;
float by = dy + (float)Math.sin(ba) * reach;
p.setColor(Color.rgb(255, 125, 25)); p.setStrokeWidth(4);
c.drawLine(px, dy, bx, by, p);
p.setColor(Color.rgb(255, 210, 90)); p.setStrokeWidth(7);
c.drawLine(bx, by, bx + (float)Math.cos(ba)*16, by + (float)Math.sin(ba)*16, p);
}
// Tubuh & Tato Merah Kratos
p.setColor(invT > 0 && ((int)(invT*25)%2 == 0) ? Color.YELLOW : Color.rgb(225, 220, 215));
c.drawCircle(px, dy, 23, p);
p.setColor(Color.rgb(195, 22, 22)); p.setStrokeWidth(6);
c.drawLine(px - 11, dy - 13, px + 11, dy + 13, p);
p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.rgb(165, 120, 48));
c.drawCircle(px, dy, 23, p);
p.setStyle(Paint.Style.FILL);
for (Spark s : sparks) { p.setColor(s.col); c.drawCircle(s.x, s.y, 5, p); }
c.restore();
// HUD Bilah Pedang God of War (Pojok Kiri Atas)
Path hilt = new Path();
hilt.moveTo(28, 22); hilt.lineTo(68, 22); hilt.lineTo(82, 42); hilt.lineTo(68, 62); hilt.lineTo(28, 62); hilt.close();
p.setColor(Color.rgb(165, 125, 52)); c.drawPath(hilt, p);
p.setColor(Color.rgb(255, 175, 40)); c.drawCircle(52, 42, 11, p);
// Bilah Darah (HP) berbentuk Pedang
Path bladeBg = new Path();
bladeBg.moveTo(78, 28); bladeBg.lineTo(355, 28); bladeBg.lineTo(385, 41); bladeBg.lineTo(355, 54); bladeBg.lineTo(78, 54); bladeBg.close();
p.setColor(Color.argb(210, 22, 24, 28)); c.drawPath(bladeBg, p);
float hpW = 78 + 275 * (hp / 100f);
Path bladeHp = new Path();
bladeHp.moveTo(80, 31); bladeHp.lineTo(hpW, 31); bladeHp.lineTo(hpW + 18, 41); bladeHp.lineTo(hpW, 51); bladeHp.lineTo(80, 51); bladeHp.close();
p.setColor(Color.rgb(55, 195, 75)); c.drawPath(bladeHp, p);
// Bilah Sihir / Rage Biru-Emas di bawah pedang
p.setColor(Color.argb(200, 20, 25, 35)); c.drawRoundRect(new RectF(78, 58, 295, 73), 5, 5, p);
p.setColor(rage >= 100 ? Color.rgb(255, 70, 20) : Color.rgb(55, 155, 245));
c.drawRoundRect(new RectF(80, 60, 80 + 213 * (rage / 100f), 71), 4, 4, p);
p.setColor(Color.rgb(235, 210, 155)); p.setTextSize(24); p.setFakeBoldText(true);
c.drawText("WAVE " + wave + "   |   RED ORBS: " + score + "   |   " + combo + " HITS", 405, 48, p);
if (gameOver) {
p.setColor(Color.argb(190, 0, 0, 0)); c.drawRect(0, 0, w, h, p);
p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.rgb(215, 35, 35)); p.setTextSize(64);
c.drawText("YOU ARE DEAD", w * 0.5f, h * 0.46f, p);
p.setColor(Color.rgb(235, 210, 155)); p.setTextSize(26);
c.drawText("Press X or Square to Rise Again", w * 0.5f, h * 0.56f, p);
}
} finally {
holder.unlockCanvasAndPost(c);
}
}
}

package com.luisxl.gowcollection;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

// O visual "Minimalista" dos controles na tela: solto, só o traço fino branco e um véu quase transparente (não tapa o
// jogo); ao tocar, a peça vira uma máscara sólida em 3D: branca em relevo, sombra embaixo, chanfro (luz em cima,
// sombra dentro da borda de baixo) e o símbolo gravado nela, um pouco maior, como se saltasse da tela. Mesmas peças
// do Desenho (o ControlesView escolhe).
final class DesenhoMinimal {
    private static final int TRACO = 0xD9FFFFFF, VEU = 0x2E0A0C10, GRAVADO = 0xFF22262D;
    private static final float SALTO = 1.06f; // a máscara cresce um pouco ao tocar
    private static final Typeface FINA = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final Typeface MEDIA = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private static Paint p() { return new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); }

    private static Bitmap tela(float r) {
        float d = 2 * (r + Desenho.folga(r));
        return Desenho.novo(d, d);
    }

    private static Path escalar(Path forma, float k, float cx, float cy) {
        Path out = new Path(forma);
        Matrix m = new Matrix();
        m.setScale(k, k, cx, cy);
        out.transform(m);
        return out;
    }

    // ---------------------------------------------------------------- solto: traço fino
    private static void contorno(Canvas c, Path forma, float r) {
        Paint v = p();
        v.setColor(VEU);
        c.drawPath(forma, v);
        Paint t = p();
        t.setStyle(Paint.Style.STROKE);
        t.setStrokeWidth(Math.max(1.3f, r * 0.045f));
        t.setColor(TRACO);
        t.setShadowLayer(r * 0.08f, 0, 0, 0x66000000); // um halo escuro: o traço aparece no cenário claro
        c.drawPath(forma, t);
    }

    // ---------------------------------------------------------------- tocado: a máscara em 3D
    private static void mascara(Canvas c, Path forma, RectF bx, boolean esfera) {
        float r = Math.min(bx.width(), bx.height()) / 2, cx = bx.centerX(), cy = bx.centerY();
        Paint s = p(); // sombra projetada: a peça está acima da tela
        s.setColor(0x99000000);
        s.setMaskFilter(new BlurMaskFilter(r * 0.32f, BlurMaskFilter.Blur.NORMAL));
        c.save();
        c.translate(0, r * 0.2f);
        c.drawPath(forma, s);
        c.restore();
        Paint corpo = p();
        if (esfera) corpo.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.38f, r * 1.55f,
                new int[]{0xFFFFFFFF, 0xFFEEF1F5, 0xFFB9C1CC, 0xFF7E8794}, new float[]{0f, 0.3f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        else corpo.setShader(new LinearGradient(0, bx.top, 0, bx.bottom, new int[]{0xFFFFFFFF, 0xFFF0F2F6, 0xFFC4CBD5},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(forma, corpo);
        c.save();
        c.clipPath(forma);
        Paint sombraDentro = p(); // chanfro: sombra dentro da borda de baixo
        sombraDentro.setStyle(Paint.Style.STROKE);
        sombraDentro.setStrokeWidth(r * 0.24f);
        sombraDentro.setColor(0x59000000);
        sombraDentro.setMaskFilter(new BlurMaskFilter(r * 0.14f, BlurMaskFilter.Blur.NORMAL));
        c.save();
        c.translate(0, -r * 0.09f);
        c.drawPath(forma, sombraDentro);
        c.restore();
        Paint luz = p(); // e luz dentro da borda de cima
        luz.setStyle(Paint.Style.STROKE);
        luz.setStrokeWidth(r * 0.16f);
        luz.setColor(0xE6FFFFFF);
        luz.setMaskFilter(new BlurMaskFilter(r * 0.08f, BlurMaskFilter.Blur.NORMAL));
        c.save();
        c.translate(0, r * 0.07f);
        c.drawPath(forma, luz);
        c.restore();
        c.restore();
        Paint borda = p();
        borda.setStyle(Paint.Style.STROKE);
        borda.setStrokeWidth(Math.max(1f, r * 0.025f));
        borda.setColor(0x40000000);
        c.drawPath(forma, borda);
    }

    // o símbolo (traço) no estado solto ou gravado na máscara: escuro, com o fio de luz embaixo de quem foi entalhado
    private static void gravar(Canvas c, Path s, float k, boolean tocado, boolean traco) {
        Paint t = p();
        if (traco) {
            t.setStyle(Paint.Style.STROKE);
            t.setStrokeWidth(k * (tocado ? 0.2f : 0.16f));
            t.setStrokeCap(Paint.Cap.ROUND);
            t.setStrokeJoin(Paint.Join.ROUND);
        }
        if (!tocado) {
            t.setColor(0xF2FFFFFF);
            t.setShadowLayer(k * 0.2f, 0, 0, 0x80000000);
            c.drawPath(s, t);
            return;
        }
        Paint fio = new Paint(t);
        fio.setColor(0xCCFFFFFF);
        c.save();
        c.translate(0, k * 0.07f);
        c.drawPath(s, fio);
        c.restore();
        t.setColor(GRAVADO);
        c.drawPath(s, t);
    }

    private static Path simbolo(int ps, float cx, float cy, float k) {
        Path s = new Path();
        switch (ps) {
            case Desenho.TRIANGULO:
                s.moveTo(cx, cy - k * 0.9f);
                s.lineTo(cx + k * 0.96f, cy + k * 0.7f);
                s.lineTo(cx - k * 0.96f, cy + k * 0.7f);
                s.close();
                break;
            case Desenho.CIRCULO: s.addCircle(cx, cy, k * 0.84f, Path.Direction.CW); break;
            case Desenho.CRUZ:
                s.moveTo(cx - k * 0.76f, cy - k * 0.76f);
                s.lineTo(cx + k * 0.76f, cy + k * 0.76f);
                s.moveTo(cx + k * 0.76f, cy - k * 0.76f);
                s.lineTo(cx - k * 0.76f, cy + k * 0.76f);
                break;
            default: s.addRoundRect(new RectF(cx - k * 0.72f, cy - k * 0.72f, cx + k * 0.72f, cy + k * 0.72f), k * 0.06f, k * 0.06f, Path.Direction.CW);
        }
        return s;
    }

    private static void texto(Canvas c, String s, float cx, float cy, float alt, boolean tocado) {
        Paint t = p();
        t.setTypeface(tocado ? MEDIA : FINA);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(alt);
        if (tocado) {
            Paint fio = new Paint(t);
            fio.setColor(0xCCFFFFFF);
            c.drawText(s, cx, cy + alt * 0.36f + alt * 0.06f, fio);
            t.setColor(GRAVADO);
        } else {
            t.setColor(0xF2FFFFFF);
            t.setShadowLayer(alt * 0.2f, 0, 0, 0x80000000);
        }
        c.drawText(s, cx, cy + alt * 0.36f, t);
    }

    // a forma em volta (já com o salto quando tocado) desenhada solta ou como máscara
    private static void peca(Canvas c, Path forma, float cx, float cy, boolean tocado, boolean esfera, float r) {
        if (tocado) {
            Path f = escalar(forma, SALTO, cx, cy);
            RectF bx = new RectF();
            f.computeBounds(bx, true);
            mascara(c, f, bx, esfera);
        } else {
            contorno(c, forma, r);
        }
    }

    private static Path circulo(float cx, float cy, float r) {
        Path d = new Path();
        d.addCircle(cx, cy, r, Path.Direction.CW);
        return d;
    }

    // ---------------------------------------------------------------- peças (mesma interface do Desenho)
    static Bitmap acao(float r, int icone, int cor, String selo, boolean apertado) {
        Bitmap b = tela(r);
        Canvas c = new Canvas(b);
        float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f, k = apertado ? SALTO : 1f;
        int ps = "tri".equals(selo) ? Desenho.TRIANGULO : "cir".equals(selo) ? Desenho.CIRCULO : "cru".equals(selo) ? Desenho.CRUZ
                : "qua".equals(selo) ? Desenho.QUADRADO : -1;
        if (ps >= 0) {
            peca(c, circulo(cx, cy, r), cx, cy, apertado, false, r);
            gravar(c, simbolo(ps, cx, cy, r * 0.4f * k), r * 0.4f * k, apertado, true);
        } else if (selo != null) { // ombros e gatilhos: cápsula com o nome
            float w = r * 2.05f, h = r * 1.2f;
            Path pil = new Path();
            RectF bx = new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            if (selo.endsWith("2")) pil.addRoundRect(bx, new float[]{h * 0.62f, h * 0.62f, h * 0.62f, h * 0.62f, h * 0.36f, h * 0.36f, h * 0.36f, h * 0.36f}, Path.Direction.CW);
            else pil.addRoundRect(bx, h / 2, h / 2, Path.Direction.CW);
            peca(c, pil, cx, cy, apertado, false, h / 2);
            texto(c, selo, cx, cy, h * 0.46f * k, apertado);
        } else { // select / start
            peca(c, circulo(cx, cy, r), cx, cy, apertado, false, r);
            gravar(c, Icones.de(icone, cx, cy, r * 0.48f * k), r * 0.6f * k, apertado, Icones.traco(icone));
        }
        return b;
    }

    static Bitmap mini(float r, String nome, boolean apertado) {
        Bitmap b = tela(r);
        Canvas c = new Canvas(b);
        float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f;
        peca(c, circulo(cx, cy, r), cx, cy, apertado, false, r);
        texto(c, nome, cx, cy, r * 0.72f * (apertado ? SALTO : 1f), apertado);
        return b;
    }

    // quatro teclas separadas em traço; braco >= 0 desenha só a máscara daquele braço (por cima da base)
    static Bitmap direcional(float r, int braco) {
        Bitmap b = tela(r);
        Canvas c = new Canvas(b);
        float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f;
        for (int i = 0; i < 4; i++) {
            if (braco >= 0 && braco != i) continue;
            float w = r * 0.29f, fora = r * 0.98f, dentro = r * 0.2f;
            Path tecla = new Path();
            tecla.moveTo(cx - w, cy - fora);
            tecla.lineTo(cx + w, cy - fora);
            tecla.lineTo(cx + w, cy - dentro - w * 0.75f);
            tecla.lineTo(cx, cy - dentro);
            tecla.lineTo(cx - w, cy - dentro - w * 0.75f);
            tecla.close();
            Paint canto = p();
            canto.setPathEffect(new CornerPathEffect(w * 0.55f));
            Path redonda = new Path();
            canto.getFillPath(tecla, redonda);
            Matrix m = new Matrix();
            m.setRotate(90 * i, cx, cy);
            redonda.transform(m);
            RectF bx = new RectF();
            redonda.computeBounds(bx, true);
            peca(c, redonda, bx.centerX(), bx.centerY(), braco >= 0, false, w);
            float s = r * 0.1f, py = cy - r * 0.66f;
            Path seta = new Path();
            seta.moveTo(cx, py - s);
            seta.lineTo(cx + s * 1.05f, py + s * 0.62f);
            seta.lineTo(cx - s * 1.05f, py + s * 0.62f);
            seta.close();
            seta.transform(m);
            gravar(c, seta, s * 2.4f, braco >= 0, false);
        }
        return b;
    }

    // base do analógico: só o círculo fino, a trilha do alcance e quatro pontinhos
    static Bitmap baseAnalogico(float r, String legenda) {
        Bitmap b = tela(r);
        Canvas c = new Canvas(b);
        float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f;
        Paint v = p();
        v.setColor(0x1F0A0C10);
        c.drawCircle(cx, cy, r, v);
        Paint t = p();
        t.setStyle(Paint.Style.STROKE);
        t.setStrokeWidth(Math.max(1.3f, r * 0.022f));
        t.setColor(0xB3FFFFFF);
        t.setShadowLayer(r * 0.05f, 0, 0, 0x66000000);
        c.drawCircle(cx, cy, r, t);
        Paint trilha = p();
        trilha.setStyle(Paint.Style.STROKE);
        trilha.setStrokeWidth(Math.max(1f, r * 0.01f));
        trilha.setColor(0x33FFFFFF);
        c.drawCircle(cx, cy, r * 0.62f, trilha);
        Paint ponto = p();
        ponto.setColor(0x80FFFFFF);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i - Math.PI / 2;
            c.drawCircle(cx + (float) Math.cos(a) * r * 0.84f, cy + (float) Math.sin(a) * r * 0.84f, Math.max(1.5f, r * 0.022f), ponto);
        }
        return b;
    }

    // topo do analógico: solto, um disco de traço com véu claro; tocado, uma esfera branca em relevo
    static Bitmap topoAnalogico(float r, boolean apertado) {
        Bitmap b = tela(r);
        Canvas c = new Canvas(b);
        float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f;
        if (!apertado) {
            Paint v = p();
            v.setColor(0x40FFFFFF);
            c.drawCircle(cx, cy, r, v);
            contorno(c, circulo(cx, cy, r), r);
            Paint dentro = p();
            dentro.setStyle(Paint.Style.STROKE);
            dentro.setStrokeWidth(Math.max(1f, r * 0.03f));
            dentro.setColor(0x66FFFFFF);
            c.drawCircle(cx, cy, r * 0.62f, dentro);
            return b;
        }
        peca(c, circulo(cx, cy, r), cx, cy, true, true, r);
        Paint concha = p(); // a concha côncava do meio, como no DualSense
        concha.setStyle(Paint.Style.STROKE);
        concha.setStrokeWidth(r * 0.05f);
        concha.setShader(new LinearGradient(0, cy - r * 0.62f, 0, cy + r * 0.62f, 0x59000000, 0xCCFFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 0.64f, concha);
        return b;
    }

    static Bitmap menu(float r, boolean apertado) {
        Bitmap b = tela(r);
        Canvas c = new Canvas(b);
        float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f;
        peca(c, circulo(cx, cy, r), cx, cy, apertado, false, r);
        Paint t = p();
        t.setTypeface(FINA);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(r * 1.05f * (apertado ? SALTO : 1f));
        if (apertado) t.setColor(GRAVADO);
        else {
            t.setColor(0xF2FFFFFF);
            t.setShadowLayer(r * 0.2f, 0, 0, 0x80000000);
        }
        c.drawText("Ω", cx, cy + r * 0.37f, t);
        return b;
    }

    private DesenhoMinimal() {}
}

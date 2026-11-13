package com.luisxl.gowcollection;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

// Os controles na tela, desenhados uma vez em bitmap (canvas de software: brilho desfocado e sombra funcionam) e
// só colados a cada quadro. Disco de vidro escuro com o aro na cor do tipo da ação, o ícone do que o botão faz e um
// selinho com o botão do PlayStation correspondente.
final class Desenho {
    static final int TRIANGULO = 0, CIRCULO = 1, CRUZ = 2, QUADRADO = 3;
    // cores do PlayStation para os selos
    private static final int[] COR_PS = {0xFF3FD6B4, 0xFFFF5A52, 0xFF6EA6FF, 0xFFF18CD5};
    // aros por tipo de ação
    static final int VERMELHO = 0xFFD8392B, AZUL = 0xFF3D7BE0, DOURADO = 0xFFD4A24C, ROXO = 0xFF9B5BE0, CINZA = 0xFF8A8F99;
    private static final int CREME = 0xFFF4EBDA;
    private static final Typeface LETRA = Typeface.create("sans-serif-condensed", Typeface.BOLD);

    // folga em volta para a sombra, o brilho e o selo não cortarem
    static float folga(float r) { return r * 0.6f; }

    static Bitmap novo(float w, float h) {
        return Bitmap.createBitmap(Math.max(1, (int) Math.ceil(w)), Math.max(1, (int) Math.ceil(h)), Bitmap.Config.ARGB_8888);
    }

    private static Paint p() { return new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); }

    private static int clarear(int c, float t) {
        return Color.argb(Color.alpha(c), (int) (Color.red(c) + (255 - Color.red(c)) * t),
                (int) (Color.green(c) + (255 - Color.green(c)) * t), (int) (Color.blue(c) + (255 - Color.blue(c)) * t));
    }

    private static int escurecer(int c, float t) {
        return Color.argb(Color.alpha(c), (int) (Color.red(c) * (1 - t)), (int) (Color.green(c) * (1 - t)),
                (int) (Color.blue(c) * (1 - t)));
    }

    private static int alfa(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

    // disco de vidro: fundo escuro translúcido, aro em degradê da cor (claro em cima), fio de luz por fora
    private static void vidro(Canvas c, float cx, float cy, float r, int cor, boolean apertado) {
        Paint s = p(); // sombra
        s.setColor(0x90000000);
        s.setMaskFilter(new BlurMaskFilter(r * 0.16f, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(cx, cy + r * 0.06f, r * 1.02f, s);
        if (apertado) { // brilho da cor vazando
            Paint g = p();
            g.setShader(new RadialGradient(cx, cy, r * 1.5f, alfa(cor, 170), alfa(cor, 0), Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r * 1.5f, g);
        }
        Paint f = p();
        f.setShader(new RadialGradient(cx, cy - r * 0.35f, r * 1.25f,
                new int[]{apertado ? alfa(escurecer(cor, 0.35f), 235) : 0xD82A2E36, apertado ? alfa(escurecer(cor, 0.65f), 235) : 0xD0141619,
                        0xE00A0B0D},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, f);
        Paint dentro = p(); // a cor tingindo a borda de dentro
        dentro.setShader(new RadialGradient(cx, cy, r, new int[]{0x00000000, 0x00000000, alfa(cor, apertado ? 150 : 90)},
                new float[]{0f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, dentro);
        Paint aro = p();
        aro.setStyle(Paint.Style.STROKE);
        aro.setStrokeWidth(r * 0.11f);
        aro.setShader(new LinearGradient(cx, cy - r, cx, cy + r, clarear(cor, apertado ? 0.6f : 0.35f), escurecer(cor, 0.35f),
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 0.945f, aro);
        Paint luz = p();
        luz.setStyle(Paint.Style.STROKE);
        luz.setStrokeWidth(r * 0.025f);
        luz.setColor(apertado ? 0xCCFFFFFF : 0x66FFFFFF);
        c.drawCircle(cx, cy, r * 1.0f, luz);
        Paint reflexo = p(); // reflexo do vidro em cima
        reflexo.setShader(new LinearGradient(cx, cy - r, cx, cy, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 0.86f, reflexo);
    }

    // Ícone em relevo: o PNG renderizado (ouro com esmalte, sombra já nele) com o brilho da cor por trás; sem o
    // PNG, o vetor em metal. s = meia caixa do desenho.
    private static void icone(Canvas c, int qual, float cx, float cy, float s, int cor, boolean apertado) {
        Bitmap art = Arte.icones != null && qual < Arte.icones.length ? Arte.icones[qual] : null;
        if (art == null) {
            metal(c, Icones.de(qual, cx, cy, s), Icones.traco(qual), s, cor, apertado);
            return;
        }
        float lado = 2 * s / 0.80f; // a caixa -1..1 ocupa 80% do PNG
        android.graphics.RectF dst = new android.graphics.RectF(cx - lado / 2, cy - lado / 2, cx + lado / 2, cy + lado / 2);
        Bitmap alfa = art.extractAlpha();
        Paint brilho = p();
        brilho.setColor(apertado ? clarear(cor, 0.35f) : cor);
        brilho.setAlpha(apertado ? 255 : 120);
        brilho.setMaskFilter(new BlurMaskFilter(lado * (apertado ? 0.07f : 0.045f), BlurMaskFilter.Blur.OUTER));
        c.drawBitmap(alfa, null, dst, brilho);
        alfa.recycle();
        Paint pc = p();
        if (apertado) pc.setColorFilter(new android.graphics.LightingColorFilter(0xFFFFFFFF, 0xFF302010));
        c.drawBitmap(art, null, dst, pc);
    }

    // vetor em metal: corpo em degradê de ouro velho, borda escura, fio de luz em cima e sombra embaixo
    private static void metal(Canvas c, Path ic, boolean traco, float s, int cor, boolean apertado) {
        Paint brilho = p();
        brilho.setColor(apertado ? clarear(cor, 0.4f) : cor);
        brilho.setMaskFilter(new BlurMaskFilter(s * (apertado ? 0.45f : 0.3f), BlurMaskFilter.Blur.NORMAL));
        estilo(brilho, traco, s * 0.32f);
        c.drawPath(ic, brilho);
        Paint sombra = p();
        sombra.setColor(0xD0000000);
        sombra.setMaskFilter(new BlurMaskFilter(s * 0.08f, BlurMaskFilter.Blur.NORMAL));
        estilo(sombra, traco, s * 0.2f);
        c.save();
        c.translate(s * 0.03f, s * 0.08f);
        c.drawPath(ic, sombra);
        c.restore();
        android.graphics.RectF bx = new android.graphics.RectF();
        ic.computeBounds(bx, true);
        Paint corpo = p();
        corpo.setShader(new LinearGradient(bx.left, bx.top, bx.right * 0.4f + bx.left * 0.6f, bx.bottom,
                apertado ? new int[]{0xFFFFFFFF, 0xFFF6DE97, 0xFFB57F35} : new int[]{0xFFF8E7B8, 0xFFD5A957, 0xFF7A4E1E},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        estilo(corpo, traco, s * 0.17f);
        c.drawPath(ic, corpo);
        Paint borda = p();
        borda.setStyle(Paint.Style.STROKE);
        borda.setStrokeWidth(s * (traco ? 0.03f : 0.05f));
        borda.setColor(0xB03A2208);
        borda.setStrokeJoin(Paint.Join.ROUND);
        if (!traco) c.drawPath(ic, borda);
    }

    private static void estilo(Paint p, boolean traco, float largura) {
        if (!traco) return;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(largura);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    // selo no canto de baixo à direita: o símbolo do PlayStation na cor dele, ou o nome (L1, R2...)
    private static void selo(Canvas c, String qual, float cx, float cy, float r) {
        Paint f = p();
        f.setColor(0xF00C0D10);
        c.drawCircle(cx, cy, r, f);
        Paint a = p();
        a.setStyle(Paint.Style.STROKE);
        a.setStrokeWidth(r * 0.14f);
        a.setColor(0xFFD4A24C);
        c.drawCircle(cx, cy, r * 0.93f, a);
        int ps = "tri".equals(qual) ? TRIANGULO : "cir".equals(qual) ? CIRCULO : "cru".equals(qual) ? CRUZ
                : "qua".equals(qual) ? QUADRADO : -1;
        if (ps < 0) {
            Paint t = p();
            t.setTypeface(LETRA);
            t.setTextAlign(Paint.Align.CENTER);
            t.setTextSize(r * (qual.length() > 2 ? 0.78f : 0.95f));
            t.setColor(CREME);
            c.drawText(qual, cx, cy + r * 0.34f, t);
            return;
        }
        Path s = new Path();
        float k = r * 0.5f;
        switch (ps) {
            case TRIANGULO:
                s.moveTo(cx, cy - k);
                s.lineTo(cx + k * 0.95f, cy + k * 0.68f);
                s.lineTo(cx - k * 0.95f, cy + k * 0.68f);
                s.close();
                break;
            case CIRCULO: s.addCircle(cx, cy, k * 0.82f, Path.Direction.CW); break;
            case CRUZ:
                s.moveTo(cx - k * 0.75f, cy - k * 0.75f);
                s.lineTo(cx + k * 0.75f, cy + k * 0.75f);
                s.moveTo(cx + k * 0.75f, cy - k * 0.75f);
                s.lineTo(cx - k * 0.75f, cy + k * 0.75f);
                break;
            default: s.addRect(cx - k * 0.7f, cy - k * 0.7f, cx + k * 0.7f, cy + k * 0.7f, Path.Direction.CW);
        }
        Paint t = p();
        t.setStyle(Paint.Style.STROKE);
        t.setStrokeWidth(r * 0.2f);
        t.setStrokeCap(Paint.Cap.ROUND);
        t.setStrokeJoin(Paint.Join.ROUND);
        t.setColor(COR_PS[ps]);
        c.drawPath(s, t);
    }

    // ---------------------------------------------------------------- peças
    // botão de ação: vidro, ícone e selo (selo null = sem)
    static Bitmap acao(float r, int icone, int cor, String selo, boolean apertado) {
        float m = folga(r), d = 2 * (r + m);
        Bitmap b = novo(d, d);
        Canvas c = new Canvas(b);
        float cx = d / 2, cy = d / 2;
        Bitmap base = apertado ? Arte.baseApertada : Arte.baseSolta;
        if (base != null) { // bronze com meandro: o disco ocupa 90% da imagem
            float lado = 2 * r / 0.90f;
            c.drawBitmap(base, null, new android.graphics.RectF(cx - lado / 2, cy - lado / 2, cx + lado / 2, cy + lado / 2), p());
        } else {
            vidro(c, cx, cy, r, cor, apertado);
        }
        icone(c, icone, cx, cy, r * 0.62f, cor, apertado);
        if (selo != null) selo(c, selo, cx + r * 0.72f, cy + r * 0.72f, r * 0.34f);
        return b;
    }

    // botão pequeno só com o nome (L3, R3)
    static Bitmap mini(float r, String nome, boolean apertado) {
        float m = folga(r), d = 2 * (r + m);
        Bitmap b = novo(d, d);
        Canvas c = new Canvas(b);
        float cx = d / 2, cy = d / 2;
        vidro(c, cx, cy, r, CINZA, apertado);
        Paint t = p();
        t.setTypeface(LETRA);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(r * 0.82f);
        t.setColor(CREME);
        c.drawText(nome, cx, cy + r * 0.3f, t);
        return b;
    }

    // cruz direcional (a da primeira versão, que ele prefere): quatro setas de pedra em volta de um medalhão; braco >= 0 desenha só a brasa daquele braço
    static Bitmap direcional(float r, int braco) {
        float m = folga(r), d = 2 * (r + m);
        Bitmap b = novo(d, d);
        Canvas c = new Canvas(b);
        float cx = d / 2, cy = d / 2;
        for (int i = 0; i < 4; i++) {
            if (braco >= 0 && braco != i) continue;
            c.save();
            c.rotate(90 * i, cx, cy); // 0 cima, 1 direita, 2 baixo, 3 esquerda
            Path seta = new Path();
            float w = r * 0.36f, t = r * 0.98f, base = r * 0.30f;
            seta.moveTo(cx - w, cy - base);
            seta.lineTo(cx - w, cy - t + w * 0.55f);
            seta.lineTo(cx, cy - t);
            seta.lineTo(cx + w, cy - t + w * 0.55f);
            seta.lineTo(cx + w, cy - base);
            seta.lineTo(cx, cy - base * 0.35f);
            seta.close();
            if (braco >= 0) {
                Paint g = p();
                g.setShader(Arte.brasa(cx, cy - r * 0.62f, r * 0.75f, 230));
                c.drawCircle(cx, cy - r * 0.62f, r * 0.75f, g);
                Paint q = p();
                q.setColor(0x66FFC070);
                q.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
                c.drawPath(seta, q);
            } else {
                peca(c, seta, new RectF(cx - r, cy - r, cx + r, cy + r), r * 0.55f, false);
                Path ponta = new Path(); // seta gravada
                float s = r * 0.13f, py = cy - r * 0.64f;
                ponta.moveTo(cx, py - s);
                ponta.lineTo(cx + s, py + s * 0.6f);
                ponta.lineTo(cx - s, py + s * 0.6f);
                ponta.close();
                Paint o = p();
                o.setShader(Arte.ouroLinear(cx, py - s, cx, py + s));
                c.drawPath(ponta, o);
            }
            c.restore();
        }
        if (braco < 0) { // medalhão do meio
            Paint o = p();
            o.setShader(Arte.ouro(cx, cy));
            c.drawCircle(cx, cy, r * 0.2f, o);
            Paint f = p();
            f.setShader(new RadialGradient(cx, cy - r * 0.05f, r * 0.16f, 0xFF3A2C20, 0xFF0E0906, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r * 0.15f, f);
        }
        return b;
    }

    // corpo de pedra com o aro de ouro e o volume; brasa por dentro se apertado
    private static void peca(Canvas c, Path forma, RectF caixa, float r, boolean apertado) {
        float cx = caixa.centerX(), cy = caixa.centerY();
        sombra(c, forma, r);
        if (apertado) { // a brasa vaza para fora do aro
            Paint g = p();
            g.setShader(Arte.brasa(cx, cy, r * 1.45f, 200));
            c.drawCircle(cx, cy, r * 1.45f, g);
        }
        Paint corpo = p();
        Arte.pedra(corpo, cx, cy, r);
        c.drawPath(forma, corpo);
        Paint vol = p();
        vol.setShader(Arte.volume(cx, cy, r));
        c.drawPath(forma, vol);
        if (apertado) {
            Paint b = p();
            b.setShader(Arte.brasa(cx, cy + r * 0.1f, r * 0.95f, 150));
            b.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
            c.drawPath(forma, b);
        }
        // aro: ouro largo, um fio escuro por dentro e um reflexo fino por fora
        Paint aro = p();
        aro.setStyle(Paint.Style.STROKE);
        aro.setStrokeWidth(r * 0.13f);
        aro.setShader(Arte.ouro(cx, cy));
        if (apertado) aro.setColorFilter(Arte.tingir(0xFFFFE2B0));
        c.drawPath(forma, aro);
        Paint fio = p();
        fio.setStyle(Paint.Style.STROKE);
        fio.setStrokeWidth(r * 0.03f);
        fio.setColor(0xCC1A0E04);
        c.save();
        float k = 1f - 0.11f;
        c.scale(k, k, cx, cy);
        c.drawPath(forma, fio);
        c.restore();
    }

    // base do analógico: disco de pedra com o meandro grego em ouro na borda (o de antes, que ele preferiu)
    static Bitmap baseAnalogico(float r, String legenda) {
        float m = folga(r), d = 2 * (r + m);
        Bitmap b = novo(d, d);
        Canvas c = new Canvas(b);
        float cx = d / 2, cy = d / 2;
        Path disco = new Path();
        disco.addCircle(cx, cy, r, Path.Direction.CW);
        sombra(c, disco, r);
        Paint corpo = p();
        Arte.pedra(corpo, cx, cy, r);
        corpo.setAlpha(225);
        c.drawPath(disco, corpo);
        Paint vol = p();
        vol.setShader(new RadialGradient(cx, cy, r, new int[]{0x99000000, 0x33000000, 0x88000000},
                new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(disco, vol);
        meandro(c, cx, cy, r * 0.80f, r * 0.95f);
        Paint aro = p();
        aro.setStyle(Paint.Style.STROKE);
        aro.setStrokeWidth(r * 0.05f);
        aro.setShader(Arte.ouro(cx, cy));
        c.drawCircle(cx, cy, r * 0.985f, aro);
        c.drawCircle(cx, cy, r * 0.78f, aro);
        return b;
    }

    // o "key pattern" grego dobrado em volta do círculo: uma volta por gomo
    private static void meandro(Canvas c, float cx, float cy, float r0, float r1) {
        int gomos = 18;
        Paint o = p();
        o.setStyle(Paint.Style.STROKE);
        o.setStrokeWidth((r1 - r0) * 0.17f);
        o.setStrokeJoin(Paint.Join.MITER);
        o.setShader(Arte.ouro(cx, cy));
        Path pa = new Path();
        float h = r1 - r0;
        for (int i = 0; i < gomos; i++) {
            float a0 = (float) (2 * Math.PI * i / gomos), da = (float) (2 * Math.PI / gomos);
            // em coordenadas (ângulo, raio): sobe, avança, desce até o meio, volta, sobe um pouco, avança até o fim
            float[][] pts = {{0f, 0.1f}, {0f, 0.9f}, {0.75f, 0.9f}, {0.75f, 0.35f}, {0.35f, 0.35f}, {0.35f, 0.62f},
                    {0.52f, 0.62f}, {0.52f, 0.1f}, {1f, 0.1f}};
            for (int k = 0; k < pts.length; k++) {
                double ang = a0 + pts[k][0] * da;
                float rr = r0 + pts[k][1] * h;
                float x = cx + (float) Math.cos(ang) * rr, y = cy + (float) Math.sin(ang) * rr;
                if (i == 0 && k == 0) pa.moveTo(x, y);
                else pa.lineTo(x, y);
            }
        }
        Paint sulco = p();
        sulco.setStyle(Paint.Style.STROKE);
        sulco.setStrokeWidth(o.getStrokeWidth() * 1.8f);
        sulco.setColor(0xCC0C0603);
        c.drawPath(pa, sulco);
        c.drawPath(pa, o);
    }

    // topo do analógico: cúpula de bronze com o ômega gravado
    static Bitmap topoAnalogico(float r, boolean apertado) {
        float m = folga(r), d = 2 * (r + m);
        Bitmap b = novo(d, d);
        Canvas c = new Canvas(b);
        float cx = d / 2, cy = d / 2;
        Path disco = new Path();
        disco.addCircle(cx, cy, r, Path.Direction.CW);
        sombra(c, disco, r * 1.3f);
        if (apertado) {
            Paint g = p();
            g.setShader(Arte.brasa(cx, cy, r * 1.5f, 210));
            c.drawCircle(cx, cy, r * 1.5f, g);
        }
        Paint cup = p();
        cup.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.4f, r * 1.5f,
                new int[]{0xFFB98A4E, 0xFF6B4520, 0xFF2E1B0B, 0xFF120A04}, new float[]{0f, 0.35f, 0.75f, 1f},
                Shader.TileMode.CLAMP));
        c.drawPath(disco, cup);
        Paint t = p(); // Ω gravado: sulco escuro e o ouro um pouco acima
        t.setTypeface(Arte.SERIFA);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(r * 1.05f);
        t.setColor(0xE0140A04);
        c.drawText("Ω", cx, cy + r * 0.38f, t);
        t.setShader(Arte.ouroLinear(cx, cy - r * 0.4f, cx, cy + r * 0.4f));
        c.drawText("Ω", cx, cy + r * 0.34f, t);
        Paint aro = p();
        aro.setStyle(Paint.Style.STROKE);
        aro.setStrokeWidth(r * 0.12f);
        aro.setShader(Arte.ouro(cx, cy));
        if (apertado) aro.setColorFilter(Arte.tingir(0xFFFFE2B0));
        c.drawCircle(cx, cy, r * 0.94f, aro);
        Paint reflexo = p(); // brilho de cúpula
        reflexo.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.45f, r * 0.55f, 0x55FFF0D0, 0x00FFF0D0,
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 0.9f, reflexo);
        return b;
    }

    // sombra suave embaixo, como peça de metal em relevo
    private static void sombra(Canvas c, Path forma, float r) {
        Paint s = p();
        s.setColor(0xB0000000);
        s.setMaskFilter(new BlurMaskFilter(r * 0.18f, BlurMaskFilter.Blur.NORMAL));
        c.save();
        c.translate(0, r * 0.09f);
        c.drawPath(forma, s);
        c.restore();
    }

    // o botão do menu dos controles: o ômega em brasa do jogo dentro de um aro de ouro
    static Bitmap menu(float r, boolean apertado) {
        float m = folga(r), d = 2 * (r + m);
        Bitmap b = novo(d, d);
        Canvas c = new Canvas(b);
        float cx = d / 2, cy = d / 2;
        Path disco = new Path();
        disco.addCircle(cx, cy, r, Path.Direction.CW);
        Paint s = p();
        s.setColor(0x90000000);
        s.setMaskFilter(new BlurMaskFilter(r * 0.18f, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(cx, cy + r * 0.08f, r, s);
        if (Arte.omegaFogo != null) {
            Bitmap om = Arte.omegaFogo;
            BitmapShader bs = new BitmapShader(om, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix mx = new Matrix();
            float k = (r * 2.3f) / om.getWidth();
            mx.setScale(k, k);
            mx.postTranslate(cx - om.getWidth() * k / 2, cy - om.getHeight() * k / 2);
            bs.setLocalMatrix(mx);
            Paint op = p();
            op.setShader(bs);
            c.drawPath(disco, op);
        } else {
            Paint f = p();
            f.setColor(0xFF120C0A);
            c.drawPath(disco, f);
            Paint t = p();
            t.setTypeface(Arte.SERIFA);
            t.setTextAlign(Paint.Align.CENTER);
            t.setTextSize(r * 1.2f);
            t.setShader(new RadialGradient(cx, cy, r, 0xFFFFD27A, Arte.BRASA_FUNDA, Shader.TileMode.CLAMP));
            t.setShadowLayer(r * 0.25f, 0, 0, Arte.BRASA);
            c.drawText("Ω", cx, cy + r * 0.42f, t);
        }
        if (apertado) {
            Paint g = p();
            g.setShader(Arte.brasa(cx, cy, r, 120));
            g.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
            c.drawPath(disco, g);
        }
        Paint aro = p();
        aro.setStyle(Paint.Style.STROKE);
        aro.setStrokeWidth(r * 0.12f);
        aro.setShader(Arte.ouro(cx, cy));
        c.drawCircle(cx, cy, r * 0.95f, aro);
        return b;
    }

    private Desenho() {}
}

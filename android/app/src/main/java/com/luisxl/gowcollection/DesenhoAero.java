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

// O visual "Cristal" dos controles na tela: o desenho do DualSense (símbolos nas cores do PS5, cruz de quatro teclas,
// gatilhos em cápsula) em vidro fosco no jeito da Apple, com o brilho e o azul-água do Frutiger Aero. Mesmas peças e
// tamanhos do Desenho (o ControlesView escolhe um dos dois); tudo desenhado uma vez em bitmap.
final class DesenhoAero {
    // cores dos símbolos (as do PS5, um pouco mais claras para aparecer no vidro escuro)
    private static final int[] COR_PS = {0xFF4DE3CB, 0xFFFF6E6E, 0xFF86B6FF, 0xFFF7A1DC}; // triângulo, círculo, cruz, quadrado
    static final int AGUA = 0xFF5CD0FF;     // o azul-água do aperto
    private static final int BRANCO = 0xFFF7FAFF;
    static final Typeface SISTEMA = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface FINA = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private static Paint p() { return new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); }

    private static int alfa(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

    private static Bitmap tela(float r) {
        float d = 2 * (r + Desenho.folga(r));
        return Desenho.novo(d, d);
    }

    // ---------------------------------------------------------------- o vidro
    // Vidro de qualquer forma: sombra macia, corpo azul-marinho translúcido (azul-água quando apertado), a luz que
    // passa por baixo, o brilho elíptico em cima (o do Aero) e o aro claro em cima, apagado embaixo.
    private static void vidro(Canvas c, Path forma, RectF bx, boolean apertado, float forca, float brilhoK) {
        float r = Math.min(bx.width(), bx.height()) / 2, cx = bx.centerX();
        Paint s = p();
        s.setColor(0x66000000);
        s.setMaskFilter(new BlurMaskFilter(r * 0.28f, BlurMaskFilter.Blur.NORMAL));
        c.save();
        c.translate(0, r * 0.12f);
        c.drawPath(forma, s);
        c.restore();
        if (apertado) { // brilho azul-água vazando
            Paint g = p();
            g.setColor(alfa(AGUA, 200));
            g.setMaskFilter(new BlurMaskFilter(r * 0.45f, BlurMaskFilter.Blur.OUTER));
            c.drawPath(forma, g);
        }
        Paint corpo = p();
        corpo.setShader(new LinearGradient(0, bx.top, 0, bx.bottom,
                apertado ? new int[]{0xF07FDBFF, 0xE82E9EE0, 0xF0126AB0} : new int[]{alfa(0xFF4F8DD0, (int) (180 * forca)),
                        alfa(0xFF1C4677, (int) (190 * forca)), alfa(0xFF091A33, (int) (210 * forca))},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(forma, corpo);
        c.save();
        c.clipPath(forma);
        Paint baixo = p(); // a luz que atravessa o vidro e acende a borda de baixo
        baixo.setShader(new RadialGradient(cx, bx.bottom - r * 0.1f, r * 0.95f, apertado ? 0x99FFFFFF : 0x7068D8FF, 0x0068D8FF,
                Shader.TileMode.CLAMP));
        c.drawRect(bx.left, bx.top, bx.right, bx.bottom, baixo);
        Paint brilho = p(); // o brilho elíptico de cima
        RectF ov = new RectF(bx.left + bx.width() * 0.12f, bx.top + bx.height() * 0.04f, bx.right - bx.width() * 0.12f,
                bx.top + bx.height() * 0.5f);
        brilho.setShader(new LinearGradient(0, ov.top, 0, ov.bottom, new int[]{alfa(0xFFFFFFFF, (int) (235 * brilhoK)),
                alfa(0xFFFFFFFF, (int) (110 * brilhoK)), 0x0AFFFFFF}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawOval(ov, brilho);
        c.restore();
        Paint aro = p();
        aro.setStyle(Paint.Style.STROKE);
        aro.setStrokeWidth(Math.max(1.5f, r * 0.05f));
        aro.setShader(new LinearGradient(0, bx.top, 0, bx.bottom, apertado ? 0xFFFFFFFF : 0xE6FFFFFF, apertado ? 0x99FFFFFF : 0x40FFFFFF,
                Shader.TileMode.CLAMP));
        c.drawPath(forma, aro);
        Paint fio = p(); // fio escuro por fora: separa do cenário claro
        fio.setStyle(Paint.Style.STROKE);
        fio.setStrokeWidth(Math.max(1f, r * 0.02f));
        fio.setColor(0x38000000);
        c.save();
        float k = 1f + Math.max(1f, r * 0.04f) / r;
        c.scale(k, k, cx, bx.centerY());
        c.drawPath(forma, fio);
        c.restore();
    }

    private static void disco(Canvas c, float cx, float cy, float r, boolean apertado, float forca) {
        disco(c, cx, cy, r, apertado, forca, 1f);
    }

    private static void disco(Canvas c, float cx, float cy, float r, boolean apertado, float forca, float brilhoK) {
        Path d = new Path();
        d.addCircle(cx, cy, r, Path.Direction.CW);
        vidro(c, d, new RectF(cx - r, cy - r, cx + r, cy + r), apertado, forca, brilhoK);
    }

    // símbolo do PlayStation em traço, com o brilho da cor (branco com brilho forte quando apertado)
    private static void simbolo(Canvas c, int ps, float cx, float cy, float k, boolean apertado) {
        Path s = new Path();
        switch (ps) {
            case Desenho.TRIANGULO:
                s.moveTo(cx, cy - k * 0.92f);
                s.lineTo(cx + k * 0.98f, cy + k * 0.72f);
                s.lineTo(cx - k * 0.98f, cy + k * 0.72f);
                s.close();
                break;
            case Desenho.CIRCULO: s.addCircle(cx, cy, k * 0.86f, Path.Direction.CW); break;
            case Desenho.CRUZ:
                s.moveTo(cx - k * 0.78f, cy - k * 0.78f);
                s.lineTo(cx + k * 0.78f, cy + k * 0.78f);
                s.moveTo(cx + k * 0.78f, cy - k * 0.78f);
                s.lineTo(cx - k * 0.78f, cy + k * 0.78f);
                break;
            default: s.addRoundRect(new RectF(cx - k * 0.74f, cy - k * 0.74f, cx + k * 0.74f, cy + k * 0.74f), k * 0.08f, k * 0.08f, Path.Direction.CW);
        }
        int cor = COR_PS[ps];
        Paint g = p();
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeWidth(k * 0.3f);
        g.setStrokeCap(Paint.Cap.ROUND);
        g.setStrokeJoin(Paint.Join.ROUND);
        g.setColor(alfa(cor, apertado ? 255 : 150));
        g.setMaskFilter(new BlurMaskFilter(k * (apertado ? 0.42f : 0.26f), BlurMaskFilter.Blur.NORMAL));
        c.drawPath(s, g);
        Paint t = p();
        t.setStyle(Paint.Style.STROKE);
        t.setStrokeWidth(k * 0.2f);
        t.setStrokeCap(Paint.Cap.ROUND);
        t.setStrokeJoin(Paint.Join.ROUND);
        t.setColor(apertado ? BRANCO : cor);
        c.drawPath(s, t);
    }

    private static void rotulo(Canvas c, String s, float cx, float cy, float alt, boolean apertado) {
        Paint t = p();
        t.setTypeface(SISTEMA);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(alt);
        t.setColor(BRANCO);
        t.setShadowLayer(alt * 0.25f, 0, alt * 0.05f, apertado ? alfa(AGUA, 255) : 0x99000000);
        c.drawText(s, cx, cy + alt * 0.36f, t);
    }

    // ---------------------------------------------------------------- peças (mesma interface do Desenho)
    // botão de ação: face com o símbolo; L1/R1/L2/R2 em cápsula; select/start como botões pequenos de ícone
    static Bitmap acao(float r, int icone, int cor, String selo, boolean apertado) {
        Bitmap b = tela(r);
        Canvas c = new Canvas(b);
        float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f;
        int ps = "tri".equals(selo) ? Desenho.TRIANGULO : "cir".equals(selo) ? Desenho.CIRCULO : "cru".equals(selo) ? Desenho.CRUZ
                : "qua".equals(selo) ? Desenho.QUADRADO : -1;
        if (ps >= 0) {
            disco(c, cx, cy, r, apertado, 1f);
            simbolo(c, ps, cx, cy + r * 0.02f, r * 0.42f, apertado);
        } else if (selo != null) { // gatilho/ombro: cápsula deitada com o nome
            float w = r * 2.05f, h = r * 1.22f;
            RectF bx = new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            Path pil = new Path();
            boolean gatilho = selo.endsWith("2"); // L2/R2: canto de cima mais redondo, como o gatilho
            if (gatilho) pil.addRoundRect(bx, new float[]{h * 0.62f, h * 0.62f, h * 0.62f, h * 0.62f, h * 0.36f, h * 0.36f, h * 0.36f, h * 0.36f}, Path.Direction.CW);
            else pil.addRoundRect(bx, h / 2, h / 2, Path.Direction.CW);
            vidro(c, pil, bx, apertado, 1f, 1f);
            rotulo(c, selo, cx, cy, h * 0.46f, apertado);
        } else { // select / start: o ícone em branco num disco pequeno
            disco(c, cx, cy, r, apertado, 0.95f);
            Path ic = Icones.de(icone, cx, cy, r * 0.5f);
            Paint t = p();
            if (Icones.traco(icone)) {
                t.setStyle(Paint.Style.STROKE);
                t.setStrokeWidth(r * 0.12f);
                t.setStrokeCap(Paint.Cap.ROUND);
                t.setStrokeJoin(Paint.Join.ROUND);
            }
            t.setColor(BRANCO);
            t.setShadowLayer(r * 0.15f, 0, 0, apertado ? alfa(AGUA, 255) : 0x80000000);
            c.drawPath(ic, t);
        }
        return b;
    }

    // botão pequeno só com o nome (L3, R3)
    static Bitmap mini(float r, String nome, boolean apertado) {
        Bitmap b = tela(r);
        Canvas c = new Canvas(b);
        float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f;
        disco(c, cx, cy, r, apertado, 0.9f);
        rotulo(c, nome, cx, cy, r * 0.74f, apertado);
        return b;
    }

    // cruz direcional do DualSense: quatro teclas separadas, cada uma com a ponta para o meio; braco >= 0 desenha só a
    // tecla daquele braço apertada (por cima da base)
    static Bitmap direcional(float r, int braco) {
        Bitmap b = tela(r);
        Canvas c = new Canvas(b);
        float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f;
        for (int i = 0; i < 4; i++) {
            if (braco >= 0 && braco != i) continue;
            float w = r * 0.29f, fora = r * 0.98f, dentro = r * 0.2f;
            Path tecla = new Path(); // apontando para cima; gira em volta do centro
            tecla.moveTo(cx - w, cy - fora);
            tecla.lineTo(cx + w, cy - fora);
            tecla.lineTo(cx + w, cy - dentro - w * 0.75f);
            tecla.lineTo(cx, cy - dentro);
            tecla.lineTo(cx - w, cy - dentro - w * 0.75f);
            tecla.close();
            Paint canto = p(); // cantos arredondados: a forma passa pelo CornerPathEffect antes do vidro
            canto.setPathEffect(new CornerPathEffect(w * 0.55f));
            canto.setStyle(Paint.Style.FILL);
            Path redonda = new Path();
            canto.getFillPath(tecla, redonda);
            Matrix m = new Matrix();
            m.setRotate(90 * i, cx, cy); // 0 cima, 1 direita, 2 baixo, 3 esquerda
            redonda.transform(m);
            RectF bx = new RectF();
            redonda.computeBounds(bx, true);
            vidro(c, redonda, bx, braco >= 0, 1f, 1f);
            // setinha gravada perto da ponta de fora
            float s = r * 0.1f, py = cy - r * 0.66f;
            Path seta = new Path();
            seta.moveTo(cx, py - s);
            seta.lineTo(cx + s * 1.05f, py + s * 0.62f);
            seta.lineTo(cx - s * 1.05f, py + s * 0.62f);
            seta.close();
            seta.transform(m);
            Paint t = p();
            t.setColor(braco >= 0 ? BRANCO : 0xD9FFFFFF);
            t.setShadowLayer(s * 0.8f, 0, 0, braco >= 0 ? alfa(AGUA, 255) : 0x66000000);
            c.drawPath(seta, t);
        }
        return b;
    }

    // base do analógico: disco grande de vidro fosco mais leve, com a trilha do alcance e o aro em azul-água
    static Bitmap baseAnalogico(float r, String legenda) {
        Bitmap b = tela(r);
        Canvas c = new Canvas(b);
        float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f;
        disco(c, cx, cy, r, false, 0.62f, 0.4f);
        Paint trilha = p();
        trilha.setStyle(Paint.Style.STROKE);
        trilha.setStrokeWidth(Math.max(1f, r * 0.012f));
        trilha.setColor(0x33FFFFFF);
        c.drawCircle(cx, cy, r * 0.62f, trilha);
        Paint agua = p(); // halo azul-água por dentro do aro
        agua.setShader(new RadialGradient(cx, cy, r, new int[]{0x0000000, 0x00000000, alfa(AGUA, 70)}, new float[]{0f, 0.8f, 1f},
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, agua);
        Paint marca = p(); // quatro marquinhas nos pontos cardeais
        marca.setColor(0x55FFFFFF);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i - Math.PI / 2;
            c.drawCircle(cx + (float) Math.cos(a) * r * 0.84f, cy + (float) Math.sin(a) * r * 0.84f, Math.max(1.5f, r * 0.025f), marca);
        }
        return b;
    }

    // topo do analógico: cúpula perolada com a concha côncava do DualSense e o brilho de cima
    static Bitmap topoAnalogico(float r, boolean apertado) {
        Bitmap b = tela(r);
        Canvas c = new Canvas(b);
        float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f;
        Paint s = p();
        s.setColor(0x80000000);
        s.setMaskFilter(new BlurMaskFilter(r * 0.3f, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(cx, cy + r * 0.16f, r, s);
        if (apertado) {
            Paint g = p();
            g.setColor(alfa(AGUA, 220));
            g.setMaskFilter(new BlurMaskFilter(r * 0.5f, BlurMaskFilter.Blur.OUTER));
            c.drawCircle(cx, cy, r, g);
        }
        Paint cup = p(); // pérola: branco azulado em cima, cinza-azul embaixo
        cup.setShader(new RadialGradient(cx - r * 0.25f, cy - r * 0.45f, r * 1.6f,
                apertado ? new int[]{0xFFFFFFFF, 0xFFBDEBFF, 0xFF5AAFE0, 0xFF2C6E9E} : new int[]{0xFFFFFFFF, 0xFFE3EAF2, 0xFFA6B4C6, 0xFF5E6B7E},
                new float[]{0f, 0.35f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, cup);
        Paint concha = p(); // a concha côncava do meio
        concha.setShader(new RadialGradient(cx, cy + r * 0.12f, r * 0.7f, new int[]{0x22000000, 0x10000000, 0x30000000},
                new float[]{0f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 0.7f, concha);
        Paint borda = p();
        borda.setStyle(Paint.Style.STROKE);
        borda.setStrokeWidth(r * 0.045f);
        borda.setShader(new LinearGradient(0, cy - r * 0.7f, 0, cy + r * 0.7f, 0x55000000, 0x99FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 0.7f, borda);
        Paint brilho = p(); // o brilho de cima
        RectF ov = new RectF(cx - r * 0.62f, cy - r * 0.9f, cx + r * 0.62f, cy - r * 0.18f);
        brilho.setShader(new LinearGradient(0, ov.top, 0, ov.bottom, 0xCCFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawOval(ov, brilho);
        Paint aro = p();
        aro.setStyle(Paint.Style.STROKE);
        aro.setStrokeWidth(r * 0.05f);
        aro.setColor(apertado ? 0xFFFFFFFF : 0xB3FFFFFF);
        c.drawCircle(cx, cy, r * 0.975f, aro);
        return b;
    }

    // o botão do menu dos controles: o ômega fino em branco num disco de vidro
    static Bitmap menu(float r, boolean apertado) {
        Bitmap b = tela(r);
        Canvas c = new Canvas(b);
        float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f;
        disco(c, cx, cy, r, apertado, 1f);
        Paint t = p();
        t.setTypeface(FINA);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(r * 1.08f);
        t.setColor(BRANCO);
        t.setShadowLayer(r * 0.22f, 0, 0, apertado ? alfa(AGUA, 255) : alfa(AGUA, 150));
        c.drawText("Ω", cx, cy + r * 0.38f, t);
        return b;
    }

    private DesenhoAero() {}
}

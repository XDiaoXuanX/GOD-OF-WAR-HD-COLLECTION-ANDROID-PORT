package com.luisxl.gowcollection;

import android.graphics.Path;

// Ícones da ação de cada botão, em vetor numa caixa de -1 a 1 (centro 0,0, y para baixo). Traço = desenhar com
// contorno; o resto é preenchido.
final class Icones {
    static final int LAMINA = 0, PESADO = 1, MAO = 2, PULO = 3, ESCUDO = 4, CHAVE = 5, RAIO = 6, RUNA = 7, PAUSA = 8,
            EVOLUCAO = 9;

    // os que saem melhor só no contorno
    static boolean traco(int i) { return i == PULO || i == RUNA || i == EVOLUCAO; }

    static Path de(int i, float cx, float cy, float s) {
        Path p = new Path();
        switch (i) {
            case LAMINA: lamina(p, 0, 0, 1f, 45); break;
            case PESADO: // duas lâminas cruzadas
                lamina(p, 0, 0, 0.95f, 45);
                lamina(p, 0, 0, 0.95f, -45);
                break;
            case MAO: mao(p); break;
            case PULO: // duas setas para cima e o chão
                seta(p, -0.12f);
                seta(p, 0.32f);
                p.moveTo(-0.62f, 0.78f);
                p.lineTo(0.62f, 0.78f);
                break;
            case ESCUDO: // escudo de ponta, com a faixa do meio vazada
                p.moveTo(0, -0.88f);
                p.cubicTo(0.35f, -0.70f, 0.62f, -0.68f, 0.78f, -0.68f);
                p.cubicTo(0.80f, 0.05f, 0.58f, 0.55f, 0, 0.92f);
                p.cubicTo(-0.58f, 0.55f, -0.80f, 0.05f, -0.78f, -0.68f);
                p.cubicTo(-0.62f, -0.68f, -0.35f, -0.70f, 0, -0.88f);
                p.close();
                p.setFillType(Path.FillType.EVEN_ODD);
                p.addRect(-0.08f, -0.62f, 0.08f, 0.62f, Path.Direction.CW);
                break;
            case CHAVE: // argola, haste e dentes
                p.setFillType(Path.FillType.EVEN_ODD);
                p.addCircle(-0.42f, -0.42f, 0.36f, Path.Direction.CW);
                p.addCircle(-0.42f, -0.42f, 0.13f, Path.Direction.CW);
                p.moveTo(-0.18f, -0.20f);
                p.lineTo(0.82f, 0.80f);
                p.lineTo(0.62f, 1.0f);
                p.lineTo(-0.38f, 0.0f);
                p.close();
                p.addRect(0.30f, 0.38f, 0.44f, 0.66f, Path.Direction.CW);
                p.addRect(0.52f, 0.60f, 0.66f, 0.82f, Path.Direction.CW);
                break;
            case RAIO:
                p.moveTo(0.18f, -0.95f);
                p.lineTo(-0.52f, 0.12f);
                p.lineTo(-0.02f, 0.12f);
                p.lineTo(-0.22f, 0.95f);
                p.lineTo(0.55f, -0.18f);
                p.lineTo(0.05f, -0.18f);
                p.close();
                break;
            case RUNA: // losango com o traço do meio, sem ação fixa
                p.moveTo(0, -0.82f);
                p.lineTo(0.6f, 0);
                p.lineTo(0, 0.82f);
                p.lineTo(-0.6f, 0);
                p.close();
                p.moveTo(0, -0.42f);
                p.lineTo(0, 0.42f);
                break;
            case PAUSA:
                p.addRoundRect(-0.52f, -0.62f, -0.16f, 0.62f, 0.08f, 0.08f, Path.Direction.CW);
                p.addRoundRect(0.16f, -0.62f, 0.52f, 0.62f, 0.08f, 0.08f, Path.Direction.CW);
                break;
            case EVOLUCAO: // orbe com a seta de subir (o menu de Power Up)
                p.addCircle(0, 0, 0.78f, Path.Direction.CW);
                p.moveTo(0, 0.42f);
                p.lineTo(0, -0.40f);
                p.moveTo(-0.32f, -0.10f);
                p.lineTo(0, -0.42f);
                p.lineTo(0.32f, -0.10f);
                break;
        }
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(s, s);
        m.postTranslate(cx, cy);
        p.transform(m);
        return p;
    }

    // lâmina curva (a das Lâminas do Caos) com guarda e cabo, girada em graus
    private static void lamina(Path out, float cx, float cy, float k, float graus) {
        Path p = new Path();
        p.moveTo(0, -1.0f);                     // ponta
        p.cubicTo(0.40f, -0.55f, 0.32f, 0.05f, 0.17f, 0.38f);
        p.lineTo(-0.17f, 0.38f);
        p.cubicTo(-0.06f, 0.02f, -0.10f, -0.55f, 0, -1.0f);
        p.close();
        p.addRoundRect(-0.30f, 0.38f, 0.30f, 0.48f, 0.04f, 0.04f, Path.Direction.CW); // guarda
        p.addRect(-0.09f, 0.48f, 0.09f, 0.82f, Path.Direction.CW);                    // cabo
        p.addCircle(0, 0.88f, 0.08f, Path.Direction.CW);                              // pomo
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(k, k);
        m.postRotate(graus);
        m.postTranslate(cx, cy);
        p.transform(m);
        out.addPath(p);
    }

    // mão aberta: palma e quatro dedos para cima, polegar de lado
    private static void mao(Path p) {
        p.addRoundRect(-0.48f, -0.05f, 0.42f, 0.78f, 0.28f, 0.28f, Path.Direction.CW);
        float[] xs = {-0.40f, -0.17f, 0.06f, 0.28f}, topo = {-0.48f, -0.78f, -0.84f, -0.62f};
        for (int i = 0; i < 4; i++)
            p.addRoundRect(xs[i], topo[i], xs[i] + 0.17f, 0.2f, 0.085f, 0.085f, Path.Direction.CW);
        Path pol = new Path();
        pol.addRoundRect(-0.20f, -0.10f, -0.03f, 0.42f, 0.085f, 0.085f, Path.Direction.CW);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setRotate(-48, -0.12f, 0.36f);
        m.postTranslate(-0.42f, 0.02f);
        pol.transform(m);
        p.addPath(pol);
    }

    private static void seta(Path p, float y) {
        p.moveTo(-0.55f, y + 0.30f);
        p.lineTo(0, y - 0.25f);
        p.lineTo(0.55f, y + 0.30f);
    }

    private Icones() {}
}

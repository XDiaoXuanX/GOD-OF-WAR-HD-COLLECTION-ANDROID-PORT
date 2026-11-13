package com.luisxl.gowcollection;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

// A arte do próprio jogo que as ferramentas do PC tiram (data/ui-arte): fonte dos menus, pedra, ômega, friso,
// haste. Nada disso vai no APK; se a pasta não estiver no celular, tudo cai no desenho de reserva daqui.
final class Arte {
    static Bitmap rocha, omegaFogo, friso, haste, fonte;
    // os ícones dos botões, recortados até o que não é transparente
    // base dos botões (tools/render_botao.py, vai no APK)
    static Bitmap baseSolta, baseApertada;

    static synchronized void carregarBases(android.content.res.AssetManager am) {
        if (baseSolta != null) return;
        try (java.io.InputStream a = am.open("ui/base_solta.png"); java.io.InputStream b = am.open("ui/base_apertada.png")) {
            baseSolta = BitmapFactory.decodeStream(a);
            baseApertada = BitmapFactory.decodeStream(b);
        } catch (Exception e) {
            baseSolta = baseApertada = null;
        }
        // ícones de ação em relevo (tools/render_icones.py), na ordem de Icones
        icones = new Bitmap[Icones.EVOLUCAO + 1];
        for (int i = 0; i < icones.length; i++)
            try (java.io.InputStream a = am.open("ui/icone_" + i + ".png")) {
                icones[i] = BitmapFactory.decodeStream(a);
            } catch (Exception e) {
                icones[i] = null;
            }
    }
    static Bitmap[] icones;
    private static JSONObject glifos;
    private static float capital = 76, escala = 4, espaco = 32, entre = 4;
    private static boolean lida;

    // cores do ouro e da brasa (tiradas a olho da haste e do ômega do jogo)
    static final int OURO_ESCURO = 0xFF5A3A12, OURO = 0xFFD9AE5B, OURO_CLARO = 0xFFF6DE97, OURO_MEIO = 0xFF9A6A26;
    static final int BRASA = 0xFFFF7A1A, BRASA_FUNDA = 0xFFB3260C;
    static final Typeface SERIFA = Typeface.create("serif", Typeface.BOLD);

    static synchronized void carregar(File raiz) {
        if (lida) return;
        lida = true;
        File d = new File(raiz, "data/ui-arte");
        rocha = abrir(new File(d, "rocha.jpg"), 512);
        omegaFogo = abrir(new File(d, "icone.png"), 512);

        friso = abrir(new File(d, "friso.png"), 1024);
        haste = abrir(new File(d, "haste.png"), 512);
        try {
            File js = new File(d, "fonte.json");
            byte[] b = new byte[(int) js.length()];
            try (FileInputStream in = new FileInputStream(js)) {
                int n = 0;
                while (n < b.length) n += in.read(b, n, b.length - n);
            }
            JSONObject o = new JSONObject(new String(b, StandardCharsets.UTF_8));
            BitmapFactory.Options op = new BitmapFactory.Options();
            op.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap f = BitmapFactory.decodeFile(new File(d, "fonte.png").getPath(), op);
            if (f != null) {
                glifos = o.getJSONObject("glifos");
                fonte = reempacotar(f, glifos);
                f.recycle();
                capital = (float) o.getDouble("capital");
                escala = (float) o.getDouble("escala");
                espaco = (float) o.getDouble("espaco");
                entre = (float) o.getDouble("entre");
            }
        } catch (Exception ignorada) {
            fonte = null;
        }
    }

    // o atlas do jogo vem numa linha só de 11132 px, mais larga que a textura máxima de muita GPU (sai riscado ou
    // nem aparece): os glifos vão para prateleiras de até 4096 px e as coordenadas do JSON são trocadas
    private static Bitmap reempacotar(Bitmap f, JSONObject g) throws org.json.JSONException {
        final int larg = 4096, folga = 4;
        int alt = 0;
        for (java.util.Iterator<String> it = g.keys(); it.hasNext(); )
            alt = Math.max(alt, g.getJSONArray(it.next()).getInt(3));
        java.util.List<String> nomes = new java.util.ArrayList<>();
        for (java.util.Iterator<String> it = g.keys(); it.hasNext(); ) nomes.add(it.next());
        int x = 0, y = 0;
        java.util.Map<String, int[]> novo = new java.util.HashMap<>();
        for (String n : nomes) {
            JSONArray q = g.getJSONArray(n);
            int w = q.getInt(2);
            if (x + w > larg) { x = 0; y += alt + folga; }
            novo.put(n, new int[]{x, y});
            x += w + folga;
        }
        Bitmap a = Bitmap.createBitmap(larg, y + alt, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(a);
        Rect src = new Rect(), dst = new Rect();
        for (String n : nomes) {
            JSONArray q = g.getJSONArray(n);
            int[] p = novo.get(n);
            src.set(q.getInt(0), q.getInt(1), q.getInt(0) + q.getInt(2), q.getInt(1) + q.getInt(3));
            dst.set(p[0], p[1], p[0] + q.getInt(2), p[1] + q.getInt(3));
            c.drawBitmap(f, src, dst, null);
            q.put(0, p[0]);
            q.put(1, p[1]);
        }
        return a;
    }

    // corta as bordas transparentes (as artes vêm com folga grande e o botão quer o desenho inteiro no centro)
    private static Bitmap recortar(Bitmap b) {
        if (b == null) return null;
        int w = b.getWidth(), h = b.getHeight(), x0 = w, y0 = h, x1 = -1, y1 = -1;
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if ((px[y * w + x] >>> 24) > 24) {
                    if (x < x0) x0 = x;
                    if (x > x1) x1 = x;
                    if (y < y0) y0 = y;
                    if (y > y1) y1 = y;
                }
        if (x1 < x0 || y1 < y0) return b;
        Bitmap r = Bitmap.createBitmap(b, x0, y0, x1 - x0 + 1, y1 - y0 + 1);
        if (r != b) b.recycle();
        return r;
    }

    // o velo.png é uma folha de textura: a cabeça de carneiro (o Velo de Ouro) fica no alto, entre 42% e 86% da largura
    private static Bitmap cabecaDoVelo(Bitmap b) {
        if (b == null) return null;
        int w = b.getWidth(), h = b.getHeight();
        Bitmap r = Bitmap.createBitmap(b, (int) (w * 0.42f), 0, (int) (w * 0.44f), (int) (h * 0.42f));
        b.recycle();
        // recorta num círculo (o resto da folha encosta nas bordas)
        Bitmap o = Bitmap.createBitmap(r.getWidth(), r.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(o);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawCircle(o.getWidth() / 2f, o.getHeight() / 2f, Math.min(o.getWidth(), o.getHeight()) * 0.48f, p);
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        c.drawBitmap(r, 0, 0, p);
        r.recycle();
        return o;
    }

    // a águia vem num JPEG de fundo escuro, sem transparência: o brilho vira alfa (e a imagem é clareada)
    private static Bitmap chaveLuz(Bitmap b) {
        if (b == null) return null;
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int r = (px[i] >> 16) & 255, g = (px[i] >> 8) & 255, bl = px[i] & 255;
            int l = (r * 3 + g * 6 + bl) / 10;
            int a = Math.max(0, Math.min(255, (l - 34) * 6));
            int k = 3; // a asa é escura: clareia mantendo o tom de bronze
            px[i] = (a << 24) | (Math.min(255, r * k) << 16) | (Math.min(255, g * k) << 8) | Math.min(255, bl * k);
        }
        Bitmap r = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        b.recycle();
        return r;
    }

    private static Bitmap abrir(File f, int max) {
        if (!f.isFile()) return null;
        BitmapFactory.Options op = new BitmapFactory.Options();
        op.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), op);
        int s = 1;
        while (Math.max(op.outWidth, op.outHeight) / s > max) s *= 2;
        op = new BitmapFactory.Options();
        op.inSampleSize = s;
        return BitmapFactory.decodeFile(f.getPath(), op);
    }

    // ---------------------------------------------------------------- texto
    // Escreve na fonte do jogo, com a capital de altura alt e a linha de base em y; centralizado se centro.
    // Sem a fonte, vai de serifa em versalete (o jogo escreve assim). Devolve a largura.
    static float texto(Canvas c, String s, float x, float y, float alt, Paint tinta, boolean centro) {
        float w = largura(s, alt, tinta);
        if (centro) x -= w / 2;
        if (fonte == null || glifos == null) {
            Paint p = new Paint(tinta);
            p.setTypeface(SERIFA);
            p.setTextSize(alt * 1.38f);
            p.setLetterSpacing(0.06f);
            c.drawText(s.toUpperCase(), x, y, p);
            return w;
        }
        float r = tinta.getShadowLayerRadius();
        if (r < 0.5f) {
            glifos(c, s, x, y, alt, tinta);
            return w;
        }
        // Brilho: sombra em bitmap não funciona com aceleração por hardware (saíam letras-fantasma), então a frase
        // com o brilho é montada uma vez num bitmap de software e guardada
        int ri = Math.round(r);
        String chave = s + "|" + Math.round(alt) + "|" + tinta.getColor() + "|" + tinta.getShadowLayerColor() + "|" + ri;
        float folga = ri * 2 + 2, base = folga + alt * 1.4f;
        Bitmap b = brilhos.get(chave);
        if (b == null) {
            Bitmap t = Bitmap.createBitmap((int) Math.ceil(w + 2 * folga), (int) Math.ceil(alt * 1.85f + 2 * folga),
                    Bitmap.Config.ARGB_8888);
            glifos(new Canvas(t), s, folga, base, alt, tinta);
            Paint desfoque = new Paint();
            desfoque.setMaskFilter(new BlurMaskFilter(ri, BlurMaskFilter.Blur.NORMAL));
            int[] off = new int[2];
            Bitmap halo = t.extractAlpha(desfoque, off);
            b = Bitmap.createBitmap(t.getWidth(), t.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas cb = new Canvas(b);
            Paint cor = new Paint();
            cor.setColor(tinta.getShadowLayerColor());
            cb.drawBitmap(halo, off[0], off[1], cor);
            cb.drawBitmap(halo, off[0], off[1], cor); // duas passadas: o fogo do menu é forte
            cb.drawBitmap(t, 0, 0, null);
            halo.recycle();
            t.recycle();
            brilhos.put(chave, b);
        }
        c.drawBitmap(b, x - folga, y - base, COLAR);
        return w;
    }

    private static final android.util.LruCache<String, Bitmap> brilhos = new android.util.LruCache<>(64);
    private static final Paint COLAR = new Paint(Paint.FILTER_BITMAP_FLAG);

    private static void glifos(Canvas c, String s, float x, float y, float alt, Paint tinta) {
        float k = alt / capital;
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        p.setColorFilter(tinta.getColorFilter() != null ? tinta.getColorFilter() : tingir(tinta.getColor()));
        p.setAlpha(Color.alpha(tinta.getColor()));
        Rect src = new Rect();
        RectF dst = new RectF();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            JSONArray g = glifo(cp);
            if (g == null) {
                x += espaco * k;
                continue;
            }
            int gx = g.optInt(0), gy = g.optInt(1), gw = g.optInt(2), gh = g.optInt(3), base = g.optInt(4);
            src.set(gx, gy, gx + gw, gy + gh);
            dst.set(x, y - base * k, x + gw * k, y - base * k + gh * k);
            c.drawBitmap(fonte, src, dst, p);
            x += (gw + entre) * k;
        }
    }

    static float largura(String s, float alt, Paint tinta) {
        if (fonte == null || glifos == null) {
            Paint p = new Paint(tinta);
            p.setTypeface(SERIFA);
            p.setTextSize(alt * 1.38f);
            p.setLetterSpacing(0.06f);
            return p.measureText(s.toUpperCase());
        }
        float k = alt / capital, w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            JSONArray g = glifo(cp);
            w += g == null ? espaco * k : (g.optInt(2) + entre) * k;
        }
        return w;
    }

    // a fonte não tem tudo: sem o glifo, tenta a letra sem acento
    private static JSONArray glifo(int cp) {
        String c = new String(Character.toChars(cp));
        JSONArray g = glifos.optJSONArray(c);
        if (g != null) return g;
        String sem = Normalizer.normalize(c, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sem.isEmpty() ? null : glifos.optJSONArray(sem);
    }

    // os glifos são brancos: a cor da tinta multiplica
    static ColorMatrixColorFilter tingir(int cor) {
        float r = Color.red(cor) / 255f, g = Color.green(cor) / 255f, b = Color.blue(cor) / 255f;
        return new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                r, 0, 0, 0, 0,
                0, g, 0, 0, 0,
                0, 0, b, 0, 0,
                0, 0, 0, 1, 0}));
    }

    // ---------------------------------------------------------------- metal e pedra
    // aro de ouro: o brilho gira em volta (varredura), como metal polido sob uma luz de cima
    static Shader ouro(float cx, float cy) {
        return new SweepGradient(cx, cy,
                new int[]{OURO_MEIO, OURO_CLARO, OURO, OURO_ESCURO, OURO, OURO_CLARO, OURO_MEIO, OURO_ESCURO, OURO_MEIO},
                null);
    }

    static Shader ouroLinear(float x0, float y0, float x1, float y1) {
        return new LinearGradient(x0, y0, x1, y1, new int[]{OURO_CLARO, OURO, OURO_ESCURO, OURO_MEIO, OURO_CLARO},
                new float[]{0f, 0.3f, 0.55f, 0.8f, 1f}, Shader.TileMode.CLAMP);
    }

    // pedra escura: a rocha do jogo se tiver, senão um degradê de granito
    static void pedra(Paint p, float cx, float cy, float r) {
        if (rocha != null) {
            p.setShader(new android.graphics.BitmapShader(rocha, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR));
            p.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                    0.55f, 0, 0, 0, 6,
                    0, 0.5f, 0, 0, 4,
                    0, 0, 0.46f, 0, 2,
                    0, 0, 0, 1, 0})));
        } else {
            p.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.4f,
                    new int[]{0xFF4A443E, 0xFF2A2521, 0xFF141110}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            p.setColorFilter(null);
        }
    }

    // sombra de borda por cima da pedra: dá volume (centro claro, borda funda)
    static Shader volume(float cx, float cy, float r) {
        return new RadialGradient(cx, cy - r * 0.25f, r * 1.05f, new int[]{0x00000000, 0x22000000, 0xAA000000},
                new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP);
    }

    // brasa por trás do botão apertado
    static Shader brasa(float cx, float cy, float r, int alfa) {
        int a = Math.max(0, Math.min(255, alfa));
        return new RadialGradient(cx, cy, r, new int[]{(a << 24) | 0xFFB040, ((a * 2 / 3) << 24) | 0xFF5A10, 0x00B3260C},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
    }

    private Arte() {}
}

package com.luisxl.gowcollection;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Os troféus como o vitart os vê (hle_sys.cpp): a lista vem do TROPHY.TRP de game/PCSA00126/sce_sys/trophy/<NPWR>/
// (o TROP_<idioma>.SFM, senão o TROP.SFM), os ícones são os TROPnnn.PNG de dentro dele e os desbloqueados ficam em
// <pasta>/data/trophy/<NPWR>/unlocked.txt, um id por linha.
final class Trofeus {
    static final class Trofeu {
        int id;
        char tipo; // B, S, G, P
        boolean oculto, ganho;
        String nome = "", detalhe = "";
        Bitmap icone;
    }

    static final class Conjunto {
        String npwr, titulo = "";
        Bitmap icone;
        final List<Trofeu> lista = new ArrayList<>();

        int ganhos() { int n = 0; for (Trofeu t : lista) if (t.ganho) n++; return n; }

        int porcento() { return lista.isEmpty() ? 0 : Math.round(100f * ganhos() / lista.size()); }
    }

    // todos os conjuntos do jogo, no idioma (SceSystemParamLang) dado
    static List<Conjunto> ler(File raiz, int idioma) {
        List<Conjunto> out = new ArrayList<>();
        File[] ds = new File(Pastas.jogo(raiz), "sce_sys/trophy").listFiles(File::isDirectory);
        if (ds == null) return out;
        java.util.Arrays.sort(ds);
        for (File d : ds) {
            try {
                Conjunto c = lerConjunto(new File(d, "TROPHY.TRP"), d.getName(), idioma);
                if (c == null) continue;
                Set<Integer> ganhos = new HashSet<>();
                File u = desbloqueados(raiz, c.npwr);
                if (u.isFile())
                    for (String l : Files.readAllLines(u.toPath(), StandardCharsets.UTF_8))
                        try { ganhos.add(Integer.parseInt(l.trim())); } catch (NumberFormatException ignorada) { /* linha vazia */ }
                for (Trofeu t : c.lista) t.ganho = ganhos.contains(t.id);
                out.add(c);
            } catch (Exception ignorada) {
                // conjunto ilegível: fica de fora
            }
        }
        return out;
    }

    static File desbloqueados(File raiz, String npwr) { return new File(raiz, "data/trophy/" + npwr + "/unlocked.txt"); }

    // recomeça os troféus deste jogo neste aparelho (o arquivo antigo fica como unlocked.txt.bak)
    static boolean zerar(File raiz, String npwr) {
        File u = desbloqueados(raiz, npwr), bak = new File(u.getPath() + ".bak");
        if (!u.isFile()) return true;
        //noinspection ResultOfMethodCallIgnored
        bak.delete();
        return u.renameTo(bak);
    }

    // ---------------------------------------------------------------- o arquivo TRP
    private static Conjunto lerConjunto(File trp, String npwr, int idioma) throws Exception {
        if (!trp.isFile()) return null;
        try (RandomAccessFile f = new RandomAccessFile(trp, "r")) {
            byte[] h = new byte[0x40];
            f.readFully(h);
            if (be32(h, 0) != 0xDCA24D00) return null;
            int n = be32(h, 16), esz = be32(h, 20);
            List<String> nomes = new ArrayList<>();
            List<long[]> pos = new ArrayList<>();
            byte[] e = new byte[esz];
            for (int i = 0; i < n; i++) {
                f.seek(0x40L + (long) i * esz);
                f.readFully(e);
                int z = 0;
                while (z < 32 && e[z] != 0) z++;
                nomes.add(new String(e, 0, z, StandardCharsets.US_ASCII));
                pos.add(new long[]{be64(e, 32), be64(e, 40)});
            }
            String local = String.format(java.util.Locale.US, "TROP_%02d.SFM", idioma);
            int ix = nomes.indexOf(local);
            if (ix < 0) ix = nomes.indexOf("TROP.SFM");
            if (ix < 0) return null;
            String xml = new String(ler(f, pos.get(ix)), StandardCharsets.UTF_8);
            Conjunto c = new Conjunto();
            c.npwr = npwr;
            c.titulo = entre(xml, 0, "<title-name>", "</title-name>");
            int ic = nomes.indexOf("ICON0.PNG");
            if (ic >= 0) c.icone = png(ler(f, pos.get(ic)), 256);
            for (int p = xml.indexOf("<trophy id=\""); p >= 0; p = xml.indexOf("<trophy id=\"", p + 1)) {
                Trofeu t = new Trofeu();
                t.id = Integer.parseInt(xml.substring(p + 12, xml.indexOf('"', p + 12)).trim());
                int fim = xml.indexOf('>', p);
                String atr = xml.substring(p, fim);
                int ty = atr.indexOf("ttype=\"");
                t.tipo = ty >= 0 ? atr.charAt(ty + 7) : 'B';
                t.oculto = atr.contains("hidden=\"yes\"");
                t.nome = xmlTexto(entre(xml, p, "<name>", "</name>"));
                t.detalhe = xmlTexto(entre(xml, p, "<detail>", "</detail>"));
                int ii = nomes.indexOf(String.format(java.util.Locale.US, "TROP%03d.PNG", t.id));
                if (ii >= 0) t.icone = png(ler(f, pos.get(ii)), 128);
                c.lista.add(t);
            }
            return c;
        }
    }

    private static byte[] ler(RandomAccessFile f, long[] p) throws Exception {
        byte[] b = new byte[(int) p[1]];
        f.seek(p[0]);
        f.readFully(b);
        return b;
    }

    private static Bitmap png(byte[] b, int max) {
        Bitmap x = BitmapFactory.decodeByteArray(b, 0, b.length);
        if (x == null || Math.max(x.getWidth(), x.getHeight()) <= max) return x;
        return Bitmap.createScaledBitmap(x, max, max * x.getHeight() / x.getWidth(), true);
    }

    private static String entre(String s, int de, String a, String b) {
        int i = s.indexOf(a, de);
        if (i < 0) return "";
        int j = s.indexOf(b, i);
        return j < 0 ? "" : s.substring(i + a.length(), j).trim();
    }

    private static String xmlTexto(String s) {
        return s.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static int be32(byte[] b, int o) {
        return (b[o] & 0xff) << 24 | (b[o + 1] & 0xff) << 16 | (b[o + 2] & 0xff) << 8 | (b[o + 3] & 0xff);
    }

    private static long be64(byte[] b, int o) { return ((long) be32(b, o) << 32) | (be32(b, o + 4) & 0xffffffffL); }

    private Trofeus() {}
}

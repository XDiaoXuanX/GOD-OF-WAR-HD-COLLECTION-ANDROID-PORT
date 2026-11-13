package com.luisxl.gowcollection;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// A pasta do jogo no celular é a mesma do PC ("God of War Collection", com game, data e shaders), copiada para o
// armazenamento. A pasta bin do PC não serve aqui: as bibliotecas do celular vêm no APK.
// Onde procurar: a pasta escolhida à mão (lançador, "Escolher pasta"); senão a raiz do armazenamento, Download,
// Documents e cada cartão SD (/storage/XXXX-XXXX), com o nome em qualquer caixa e com sufixo ("God of War
// Collection (Android)"), e um nível abaixo (o descompactador que cria uma pasta a mais com o nome do zip).
final class Pastas {
    static final String NOME = "God of War Collection";
    private static String escolhida; // caminho escolhido no lançador (SharedPreferences "pasta")

    static void carregar(Context c) {
        escolhida = c.getSharedPreferences("pasta", Context.MODE_PRIVATE).getString("raiz", null);
    }

    static void guardar(Context c, String caminho) {
        escolhida = caminho;
        c.getSharedPreferences("pasta", Context.MODE_PRIVATE).edit().putString("raiz", caminho).apply();
    }

    static File raiz() {
        if (escolhida != null) {
            File e = new File(escolhida);
            if (completa(e)) return e;
        }
        File padrao = new File(Environment.getExternalStorageDirectory(), NOME);
        if (completa(padrao)) return padrao;
        File parcial = null; // a primeira que parece a pasta do jogo mas está incompleta (para dizer o que falta)
        for (File base : bases()) {
            File[] filhos = base.listFiles(File::isDirectory);
            if (filhos == null) continue;
            for (File d : filhos) {
                if (!pareceONome(d.getName())) continue;
                if (completa(d)) return d;
                File[] dentro = d.listFiles(File::isDirectory); // um nível abaixo: a pasta a mais do descompactador
                if (dentro != null)
                    for (File d2 : dentro) if (pareceONome(d2.getName()) && completa(d2)) return d2;
                if (parcial == null) parcial = d;
            }
        }
        if (escolhida != null) return new File(escolhida); // a escolhida, mesmo incompleta: o lançador diz o que falta
        return parcial != null ? parcial : padrao;
    }

    // onde a pasta costuma ficar: a raiz do armazenamento, Download, Documents e os cartões SD
    private static List<File> bases() {
        List<File> b = new ArrayList<>();
        b.add(Environment.getExternalStorageDirectory());
        b.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        b.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS));
        File[] vols = new File("/storage").listFiles(File::isDirectory);
        if (vols != null)
            for (File v : vols) {
                String n = v.getName();
                if (n.equals("emulated") || n.equals("self")) continue;
                b.add(v);
                b.add(new File(v, Environment.DIRECTORY_DOWNLOADS));
            }
        return b;
    }

    private static boolean pareceONome(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        return l.startsWith("god of war collection") || l.startsWith("god_of_war_collection") || l.startsWith("godofwarcollection");
    }

    static File jogo(File raiz) { return new File(raiz, "game/PCSA00126"); }

    static boolean completa(File raiz) { return faltando(raiz) == null; }

    // o que falta para jogar, em texto para a tela; null se está tudo lá
    static String faltando(File raiz) {
        if (!raiz.isDirectory()) return Tr.t("a pasta ") + NOME;
        File app = jogo(raiz);
        if (!new File(app, "eboot.bin").isFile() && !new File(app, "exec").isDirectory()) return "game/PCSA00126";
        if (!new File(raiz, "shaders").isDirectory()) return "shaders";
        if (!new File(raiz, "data").isDirectory()) return "data";
        return null;
    }

    // caminho de um documento de árvore do seletor de pastas ("primary:Pasta" ou "XXXX-XXXX:Pasta")
    static String caminhoDaArvore(String docId) {
        int i = docId.indexOf(':');
        String vol = i < 0 ? docId : docId.substring(0, i), resto = i < 0 ? "" : docId.substring(i + 1);
        String base = "primary".equalsIgnoreCase(vol) ? Environment.getExternalStorageDirectory().getPath() : "/storage/" + vol;
        return resto.isEmpty() ? base : base + "/" + resto;
    }

    private Pastas() {}
}

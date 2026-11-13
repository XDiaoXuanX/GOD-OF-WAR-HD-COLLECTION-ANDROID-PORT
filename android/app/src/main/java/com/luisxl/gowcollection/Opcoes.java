package com.luisxl.gowcollection;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

// Linhas do <pasta>/data/vitart_options.ini que o lançador mexe (o jogo lê ao abrir: games/gow/game_options.cpp).
final class Opcoes {
    // {nome no ini, rótulo, proporção (0 = a da tela)}; a ordem é a do kAspecto do game_options.cpp a partir do Auto
    static final Object[][] PROPORCOES = {
            {"auto", "Auto", 0f}, {"16:9", "16:9 (original)", 16f / 9f}, {"18:9", "18:9", 2f}, {"19.5:9", "19,5:9", 19.5f / 9f},
            {"20:9", "20:9", 20f / 9f}, {"21:9c", "21:9", 21f / 9f}, {"22:9", "22:9", 22f / 9f},
    };

    static File ini(File raiz) { return new File(raiz, "data/vitart_options.ini"); }

    static String ler(File raiz, String chave, String padrao) {
        try {
            for (String l : Files.readAllLines(ini(raiz).toPath(), StandardCharsets.UTF_8))
                if (l.startsWith(chave + "=")) return l.substring(chave.length() + 1).trim();
        } catch (Exception ignorada) {
            // sem ini: o padrão
        }
        return padrao;
    }

    static boolean gravar(File raiz, String chave, String valor) {
        try {
            File f = ini(raiz);
            List<String> ls = f.isFile() ? new ArrayList<>(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) : new ArrayList<>();
            boolean achou = false;
            for (int i = 0; i < ls.size(); i++)
                if (ls.get(i).startsWith(chave + "=")) { ls.set(i, chave + "=" + valor); achou = true; }
            if (!achou) ls.add(chave + "=" + valor);
            //noinspection ResultOfMethodCallIgnored
            f.getParentFile().mkdirs();
            DriverCatalog.escrever(f, String.join("\n", ls) + "\n");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Opcoes() {}
}

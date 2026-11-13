package com.luisxl.gowcollection;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

// O idioma que o jogo recebe (sceAppUtilSystemParamGetInt, SceSystemParamLang) e o dos textos dos troféus
// (TROP_<código>.SFM). Fica em files/idioma.txt; a GameActivity passa ao vitart como VITART_LANG.
final class Idioma {
    // {código do Vita, nome, se os troféus têm esse idioma}; o jogo usa o idioma se tiver os textos, senão o inglês
    static final Object[][] OPCOES = {
            {1, "English", true}, {17, "Português (Brasil)", false}, {3, "Español", true}, {2, "Français", true},
            {4, "Deutsch", true}, {5, "Italiano", true}, {8, "Русский", true}, {0, "日本語", true},
    };

    static int atual(Context c) {
        try {
            File f = new File(c.getFilesDir(), "idioma.txt");
            if (f.isFile()) return Integer.parseInt(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim());
        } catch (Exception ignorada) {
            // inválido: o padrão
        }
        return Tr.codigoVita(); // sem escolha: o idioma do Android
    }

    static void definir(Context c, int codigo) {
        try {
            DriverCatalog.escrever(new File(c.getFilesDir(), "idioma.txt"), String.valueOf(codigo));
        } catch (Exception ignorada) {
            // fica o anterior
        }
    }

    // a tradução do jogo para esse idioma (language= no vitart_options.ini, como o Settings do PC): o vitart aplica
    // data/traducoes/<código>; fr e es são os oficiais do próprio jogo; "" = o texto original (inglês)
    static String traducao(int codigo) {
        switch (codigo) {
            case 17: return "pt-BR";
            case 3: return "es";
            case 2: return "fr";
            case 4: return "de";
            case 5: return "it";
            default: return "";
        }
    }

    static String nome(int codigo) {
        for (Object[] o : OPCOES) if ((int) o[0] == codigo) return (String) o[1];
        return "English";
    }

    private Idioma() {}
}

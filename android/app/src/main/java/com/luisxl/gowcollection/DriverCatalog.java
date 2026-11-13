package com.luisxl.gowcollection;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// O catálogo de drivers do DroidDeck (gpu/TurnipReleases.kt, DriverPairs.kt e AutoPicks.kt, GPL-3.0): as mesmas fontes
// no GitHub, só arquivos com sha256 publicado, os mesmos pares e as mesmas regras de escolha automática (as embutidas
// ou o auto-drivers.json do Droid-Deck/Drivers-CI). Aqui só a metade Android (o driver da tela) é usada: o jogo é
// nativo, não tem a metade Linux do Proton.
final class DriverCatalog {
    private static final String TAG = "DriverCatalog";
    static final long INTERVALO_MS = 3_600_000L; // RELEASE_CHECK_INTERVAL_MS
    static final String REGRAS_URL = "https://raw.githubusercontent.com/Droid-Deck/Drivers-CI/main/auto-drivers.json";

    // chaves dos pares (DriverPairs)
    static final String BANNER = "banner", BANNER_A8XX = "banner-a8xx", BANNER_710 = "banner-710-720-test",
            BANNER_ONEUI = "banner-8g2-oneui", WN_BALANCED = "wn-b", WN_PERFORMANCE = "wn-p", DD_TURNIP = "dd-turnip",
            NON_ADRENO = "nad", NAD_RADV_XCLIPSE = "nad:radv-xclipse", NAD_RADV_XCLIPSE_UPSTREAM = "nad:radv-xclipse-upstream",
            NAD_MALI_JM = "nad:panvk-mali-jm", NAD_MALI_G710 = "nad:panvk-mali-g710", NAD_MALI_G615 = "nad:panvk-mali-g615",
            NAD_MALI_G720 = "nad:panvk-mali-g720", NAD_MALI_KBASE = "nad:panvk-mali-kbase", NAD_FAUX_RP4PRO = "nad:panvk-faux123-rp4pro",
            NAD_FAUX_ACE = "nad:panvk-faux123-ace", NAD_FAUX_RG556 = "nad:panvk-faux123-rg556";
    static final int MALI_KBASE_MIN_SDK = 35;
    static final String NEWEST_DD_TURNIP = DD_TURNIP;
    static final String NON_ADRENO_REPO = "The412Banner/droiddeck-nonadreno-drivers", NON_ADRENO_LABEL = "Non-Adreno";

    // ---------------------------------------------------------------- arquivos publicados
    static final class Asset {
        String fonte, tag, nome, url, sha256, par, atualizado;
        long tamanho, id;
        boolean linux, pacote;
        String rotulo;

        JSONObject json() throws Exception {
            return new JSONObject().put("source", fonte).put("tag", tag).put("name", nome).put("url", url).put("size", tamanho)
                    .put("linux", linux).put("label", rotulo).put("sha256", sha256).put("pair", par).put("bundle", pacote)
                    .put("id", id).put("updated_at", atualizado);
        }

        static Asset de(JSONObject a) {
            Asset x = new Asset();
            x.fonte = a.optString("source"); x.tag = a.optString("tag"); x.nome = a.optString("name"); x.url = a.optString("url");
            x.tamanho = a.optLong("size"); x.linux = a.optBoolean("linux"); x.rotulo = a.optString("label");
            x.sha256 = a.optString("sha256"); x.par = a.optString("pair"); x.pacote = a.optBoolean("bundle");
            x.id = a.optLong("id"); x.atualizado = a.optString("updated_at");
            return x;
        }
    }

    private static final class Tipo {
        final boolean linux, pacote;
        final String rotulo, par;

        Tipo(boolean linux, String rotulo, String par, boolean pacote) {
            this.linux = linux; this.rotulo = rotulo; this.par = par; this.pacote = pacote;
        }
    }

    private interface Classifica { Tipo de(String nome, String tag); }

    private static final class Fonte {
        final String rotulo, repo;
        final boolean prerelease;
        final Classifica classifica;

        Fonte(String rotulo, String repo, boolean prerelease, Classifica c) {
            this.rotulo = rotulo; this.repo = repo; this.prerelease = prerelease; this.classifica = c;
        }
    }

    private static final List<Fonte> FONTES = Arrays.asList(
            new Fonte("Banners-Turnip", "The412Banner/Banners-Turnip", false, (nome, tag) -> {
                String prefixo = "Turnip-" + tag;
                if (!nome.startsWith(prefixo) || !nome.endsWith(".zip")) return null;
                String v = nome.substring(prefixo.length(), nome.length() - 4);
                if (v.endsWith("-Wayland")) return null;
                boolean linux = v.endsWith("-Linux");
                if (linux) v = v.substring(0, v.length() - 6);
                String r;
                if (v.isEmpty()) r = "Adreno 6xx/7xx";
                else if (v.equals("-A8xx")) r = "Adreno 8xx";
                else if (v.startsWith("-710-720")) r = "Adreno 710/720" + (v.contains("Test") ? Tr.t(" (teste)") : "");
                else if (v.toLowerCase(Locale.US).contains("oneui")) r = Tr.t("8 Gen 2 no One UI");
                else r = v.replaceFirst("^-+", "");
                return new Tipo(linux, r, "banner" + v.toLowerCase(Locale.US), false);
            }),
            new Fonte("WinNative", "WinNative-Emu/Drivers", false, (nome, tag) -> {
                Matcher m = Pattern.compile("^WN-(Linux-)?Turnip-[^-]+-([a-z]+)_(\\w+)\\.zip$").matcher(nome);
                if (!m.find()) return null;
                String sabor = m.group(2).equals("b") ? "Balanced" : m.group(2).equals("p") ? "Performance" : m.group(2);
                String gpus = m.group(3).equals("Axxx") ? Tr.t("todas as Adreno") : m.group(3);
                return new Tipo(m.group(1) != null, gpus + " · " + sabor, "wn-" + m.group(2), false);
            }),
            new Fonte("Turnip CI", "Droid-Deck/Drivers-CI", false, (nome, tag) -> {
                if (!nome.matches("^DD-Turnip-v\\d+\\.\\d+\\.\\d+\\.zip$")) return null;
                return new Tipo(false, Tr.t("Android + Linux · todas as Adreno"), DD_TURNIP + ":" + tag, true);
            }),
            // <Id>-Pair.zip: drivers abertos de GPUs que não são Adreno (PanVK no Mali, RADV no Xclipse), as duas metades num zip
            new Fonte(NON_ADRENO_LABEL, NON_ADRENO_REPO, true, (nome, tag) -> {
                Matcher m = Pattern.compile("^(RADV-Xclipse(?:-[A-Za-z0-9]+)*?|PanVK-[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*?)-Pair\\.zip$").matcher(nome);
                if (!m.find()) return null;
                String par = naoAdreno(m.group(1));
                return new Tipo(false, "Android + Linux · " + nomeNaoAdreno(par), par, true);
            }));

    static String naoAdreno(String id) { return NON_ADRENO + ":" + id.toLowerCase(Locale.US); }

    static boolean ehNaoAdreno(String chave) {
        return chave != null && (chave.startsWith(NON_ADRENO + ":")
                || chave.matches("^(?i)(RADV-Xclipse|PanVK-[A-Za-z0-9]+)(-[A-Za-z0-9]+)*$"));
    }

    static String nomeNaoAdreno(String chave) {
        switch (chave) {
            case NAD_RADV_XCLIPSE: return "RADV · Samsung Xclipse";
            case NAD_RADV_XCLIPSE_UPSTREAM: return "RADV upstream · Samsung Xclipse";
            case NAD_MALI_JM: return "PanVK · Mali G52-G78";
            case NAD_MALI_G710: return "PanVK · Mali-G610/G710";
            case NAD_MALI_G615: return "PanVK · Mali-G615";
            case NAD_MALI_G720: return "PanVK · Mali-G720";
            case NAD_FAUX_RP4PRO: return "PanVK faux123 · Retroid Pocket 4 Pro (Mali-G77)";
            case NAD_FAUX_ACE: return "PanVK faux123 · GameForce Ace (Mali-G610)";
            case NAD_FAUX_RG556: return "PanVK faux123 · Anbernic RG556 (Mali-G57)";
            case NAD_MALI_KBASE: return "PanVK kbase · Mali G57-G925";
            default: return chave.substring(chave.indexOf(':') + 1);
        }
    }

    // ---------------------------------------------------------------- pares
    static final class Par {
        final String chave, nome;
        final Set<GpuInfo.Familia> familias;
        final Asset tela, linux, pacote;

        Par(String chave, String nome, Set<GpuInfo.Familia> familias, Asset tela, Asset linux, Asset pacote) {
            this.chave = chave; this.nome = nome; this.familias = familias; this.tela = tela; this.linux = linux; this.pacote = pacote;
        }

        boolean completo() { return pacote != null || (tela != null && linux != null); }

        // o que se baixa para a tela: o pacote inteiro ou o zip da metade Android
        Asset arquivo() { return pacote != null ? pacote : tela; }

        String versao() {
            List<String> v = new ArrayList<>();
            for (Asset a : new Asset[]{pacote, tela, linux})
                if (a != null) {
                    String t = a.tag.replaceFirst("^linux-", "").replaceFirst("^DD-Turnip-", "");
                    if (!v.contains(t)) v.add(t);
                }
            return String.join(" + ", v);
        }

        String liberacao() { return chave + "@" + versao(); } // AutoDriverTrial.release

        boolean serve(GpuInfo g) {
            if (chave.equals(NAD_RADV_XCLIPSE) || chave.equals(NAD_RADV_XCLIPSE_UPSTREAM))
                return g.familia == GpuInfo.Familia.POWERVR && g.nome.toLowerCase(Locale.US).contains("xclipse");
            if (chave.equals(NAD_MALI_KBASE)) return g.familia.mali() && GpuInfo.maliKbase(g.nome);
            Integer alvo = chave.equals(NAD_FAUX_RP4PRO) ? Integer.valueOf(77) : chave.equals(NAD_FAUX_ACE) ? Integer.valueOf(610)
                    : chave.equals(NAD_FAUX_RG556) ? Integer.valueOf(57) : null;
            if (alvo != null) return g.familia.mali() && alvo.equals(GpuInfo.numeroMali(g.nome));
            return familias.contains(g.familia) || (g.familia == GpuInfo.Familia.ADRENO_DESCONHECIDO
                    && (familias.contains(GpuInfo.Familia.A7XX) || familias.contains(GpuInfo.Familia.A8XX)));
        }
    }

    private static final Set<GpuInfo.Familia> TODAS_ADRENO = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            GpuInfo.Familia.A6XX, GpuInfo.Familia.A7XX, GpuInfo.Familia.A7XX_BAIXO, GpuInfo.Familia.A8XX, GpuInfo.Familia.ADRENO_DESCONHECIDO)));

    private static Set<GpuInfo.Familia> fs(GpuInfo.Familia... f) { return new HashSet<>(Arrays.asList(f)); }

    private static Object[] descrever(String chave) {
        if (chave.startsWith(DD_TURNIP)) return new Object[]{Tr.t("Turnip · todas as Adreno"), TODAS_ADRENO};
        if (chave.startsWith(NON_ADRENO + ":")) {
            Set<GpuInfo.Familia> f;
            switch (chave) {
                case NAD_RADV_XCLIPSE: case NAD_RADV_XCLIPSE_UPSTREAM: f = fs(GpuInfo.Familia.POWERVR); break;
                case NAD_MALI_JM: case NAD_FAUX_RP4PRO: case NAD_FAUX_RG556: f = fs(GpuInfo.Familia.MALI_JM); break;
                case NAD_MALI_G710: case NAD_FAUX_ACE: f = fs(GpuInfo.Familia.MALI_G610_G710); break;
                case NAD_MALI_G615: f = fs(GpuInfo.Familia.MALI_G615); break;
                case NAD_MALI_G720: f = fs(GpuInfo.Familia.MALI_G720); break;
                case NAD_MALI_KBASE: f = fs(GpuInfo.Familia.MALI_JM, GpuInfo.Familia.MALI_G610_G710, GpuInfo.Familia.MALI_G615,
                        GpuInfo.Familia.MALI_G720, GpuInfo.Familia.MALI_RECENTE); break;
                default: f = new HashSet<>();
            }
            return new Object[]{nomeNaoAdreno(chave), f};
        }
        switch (chave) {
            case BANNER: return new Object[]{"Banners-Turnip · Adreno 6xx/7xx", fs(GpuInfo.Familia.A6XX, GpuInfo.Familia.A7XX)};
            case BANNER_A8XX: return new Object[]{"Banners-Turnip · Adreno 8xx", fs(GpuInfo.Familia.A8XX)};
            case BANNER_710: return new Object[]{Tr.t("Banners-Turnip · Adreno 710/720/722 (teste)"), fs(GpuInfo.Familia.A7XX_BAIXO)};
            case BANNER_ONEUI: return new Object[]{Tr.t("Banners-Turnip · 8 Gen 2 no One UI"), fs(GpuInfo.Familia.A7XX)};
            case WN_BALANCED: return new Object[]{"WinNative · Balanced", TODAS_ADRENO};
            case WN_PERFORMANCE: return new Object[]{"WinNative · Performance", TODAS_ADRENO};
            default: return new Object[]{chave, TODAS_ADRENO};
        }
    }

    @SuppressWarnings("unchecked")
    static List<Par> pares(List<Asset> assets) {
        List<String> ordem = Arrays.asList(DD_TURNIP, BANNER, BANNER_A8XX, BANNER_ONEUI, BANNER_710, WN_BALANCED, WN_PERFORMANCE, NON_ADRENO);
        Map<String, List<Asset>> por = new LinkedHashMap<>();
        for (Asset a : assets) if (!a.par.isEmpty()) por.computeIfAbsent(a.par, k -> new ArrayList<>()).add(a);
        List<String> chaves = new ArrayList<>(por.keySet());
        chaves.sort((x, y) -> {
            int ix = ordem.indexOf(x.contains(":") ? x.substring(0, x.indexOf(':')) : x), iy = ordem.indexOf(y.contains(":") ? y.substring(0, y.indexOf(':')) : y);
            return Integer.compare(ix < 0 ? ordem.size() : ix, iy < 0 ? ordem.size() : iy);
        });
        List<Par> out = new ArrayList<>();
        for (String k : chaves) {
            Object[] d = descrever(k);
            List<Asset> metades = por.get(k);
            Asset pac = null, tela = null, linux = null;
            for (Asset a : metades) {
                if (a.pacote && pac == null) pac = a;
                if (!a.pacote && !a.linux && tela == null) tela = a;
                if (!a.pacote && a.linux && linux == null) linux = a;
            }
            if (pac != null) { out.add(new Par(k, (String) d[0], (Set<GpuInfo.Familia>) d[1], null, null, pac)); continue; }
            boolean mesma = !k.startsWith(BANNER) || (tela != null && linux != null && tela.tag.equals(linux.tag));
            out.add(new Par(k, (String) d[0], (Set<GpuInfo.Familia>) d[1], tela, mesma ? linux : null, null));
        }
        return out;
    }

    // ---------------------------------------------------------------- regras automáticas (AutoPicks)
    static final class Regra {
        final Set<GpuInfo.Familia> familias;
        final Set<Integer> modelos;
        final Boolean oneUi8Gen2;
        final int minSdk, blobMin, blobMax;
        final List<String> prefere;

        Regra(Set<GpuInfo.Familia> f, Set<Integer> m, Boolean o, int minSdk, int blobMin, int blobMax, List<String> p) {
            familias = f; modelos = m; oneUi8Gen2 = o; this.minSdk = minSdk; this.blobMin = blobMin; this.blobMax = blobMax; prefere = p;
        }

        Regra(Set<GpuInfo.Familia> f, Boolean o, int minSdk, String... p) { this(f, new HashSet<>(), o, minSdk, 0, Integer.MAX_VALUE, Arrays.asList(p)); }

        boolean casa(GpuInfo g, int sdk, Integer blob) {
            return familias.contains(g.familia) && (modelos.isEmpty() || modelos.contains(g.modelo))
                    && (oneUi8Gen2 == null || g.oneUi8Gen2 == oneUi8Gen2) && sdk >= minSdk
                    && ((blobMin == 0 && blobMax == Integer.MAX_VALUE) || (blob != null && blob >= blobMin && blob <= blobMax));
        }
    }

    static final List<Regra> EMBUTIDAS = Arrays.asList(
            new Regra(fs(GpuInfo.Familia.A8XX), null, 0, NEWEST_DD_TURNIP, WN_BALANCED),
            new Regra(fs(GpuInfo.Familia.A7XX_BAIXO), null, 0, NEWEST_DD_TURNIP, BANNER_710),
            new Regra(fs(GpuInfo.Familia.A7XX), Boolean.TRUE, 0, NEWEST_DD_TURNIP, BANNER_ONEUI),
            new Regra(fs(GpuInfo.Familia.A7XX), null, 0, NEWEST_DD_TURNIP, BANNER),
            new Regra(fs(GpuInfo.Familia.A6XX), null, 0, NEWEST_DD_TURNIP, BANNER),
            new Regra(fs(GpuInfo.Familia.ADRENO_DESCONHECIDO), null, 36, WN_BALANCED),
            new Regra(fs(GpuInfo.Familia.ADRENO_DESCONHECIDO), null, 0, BANNER));

    // nomes de família do DroidDeck (os do auto-drivers.json) -> as nossas
    private static GpuInfo.Familia familiaDD(String n) {
        switch (n) {
            case "A8XX": return GpuInfo.Familia.A8XX;
            case "A7XX_LOW": return GpuInfo.Familia.A7XX_BAIXO;
            case "A7XX": return GpuInfo.Familia.A7XX;
            case "A6XX": return GpuInfo.Familia.A6XX;
            case "ADRENO_UNKNOWN": return GpuInfo.Familia.ADRENO_DESCONHECIDO;
            case "POWERVR": return GpuInfo.Familia.POWERVR;
            case "MALI_JM": return GpuInfo.Familia.MALI_JM;
            case "MALI_G610_G710": return GpuInfo.Familia.MALI_G610_G710;
            case "MALI_G615": return GpuInfo.Familia.MALI_G615;
            case "MALI_G720": return GpuInfo.Familia.MALI_G720;
            case "MALI_LATEST": return GpuInfo.Familia.MALI_RECENTE;
            case "MALI_OTHER": return GpuInfo.Familia.MALI_OUTRO;
            case "NOT_ADRENO": return GpuInfo.Familia.NAO_ADRENO;
            default: return null;
        }
    }

    static List<Regra> lerRegras(String texto) {
        try {
            JSONObject j = new JSONObject(texto);
            if (j.optInt("schemaVersion") != 1) return null;
            JSONArray rs = j.getJSONArray("rules");
            List<Regra> out = new ArrayList<>();
            for (int i = 0; i < rs.length(); i++) {
                JSONObject o = rs.getJSONObject(i);
                Set<GpuInfo.Familia> f = new HashSet<>();
                JSONArray fa = o.optJSONArray("families");
                for (int k = 0; fa != null && k < fa.length(); k++) { GpuInfo.Familia x = familiaDD(fa.getString(k)); if (x != null) f.add(x); }
                List<String> p = new ArrayList<>();
                JSONArray pa = o.optJSONArray("prefer");
                for (int k = 0; pa != null && k < pa.length(); k++) p.add(pa.getString(k));
                if (f.isEmpty() || p.isEmpty()) continue;
                Set<Integer> m = new HashSet<>();
                JSONArray ma = o.optJSONArray("models");
                for (int k = 0; ma != null && k < ma.length(); k++) m.add(ma.getInt(k));
                out.add(new Regra(f, m, o.has("oneUi8Gen2") ? o.getBoolean("oneUi8Gen2") : null, o.optInt("minSdk", 0),
                        o.optInt("blobMin", 0), o.optInt("blobMax", Integer.MAX_VALUE), p));
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    static List<String> preferencias(GpuInfo g, List<Regra> regras, int sdk, Integer blob) {
        for (Regra r : regras) if (r.casa(g, sdk, blob)) return r.prefere;
        return Collections.emptyList();
    }

    // DriverPairs.recommendedKey, com os mesmos casos de Xclipse e Mali
    static String recomendado(GpuInfo g, List<Par> pares, List<Regra> regras, Set<String> ruins, int sdk, Integer blob) {
        switch (g.familia) {
            case POWERVR:
                if (!g.nome.toLowerCase(Locale.US).contains("xclipse")) return null;
                if (xclipseUpstream(g.nome) && temPar(pares, NAD_RADV_XCLIPSE_UPSTREAM, false)) return NAD_RADV_XCLIPSE_UPSTREAM;
                return NAD_RADV_XCLIPSE;
            case MALI_OUTRO: case NAO_ADRENO: return null;
            case MALI_JM: case MALI_G610_G710: case MALI_G615: case MALI_G720: case MALI_RECENTE: return chaveMali(g, pares, sdk);
            default: break;
        }
        List<String> chaves = preferencias(g, regras, sdk, blob);
        if (chaves.isEmpty()) chaves = preferencias(g, EMBUTIDAS, sdk, blob);
        List<String> res = new ArrayList<>();
        for (String k : chaves) {
            String r = NEWEST_DD_TURNIP.equals(k) ? ddTurnipMaisNovo(pares) : k;
            if (r != null) res.add(r);
        }
        for (String k : res)
            for (Par p : pares) if (p.chave.equals(k) && p.completo() && !ruins.contains(p.liberacao())) return k;
        return res.isEmpty() ? null : res.get(0);
    }

    private static boolean temPar(List<Par> pares, String chave, boolean completo) {
        for (Par p : pares) if (p.chave.equals(chave) && (!completo || p.completo())) return true;
        return false;
    }

    // 950 e 960 dão pane no build antigo; sem número no nome, vale o mesmo
    private static boolean xclipseUpstream(String nome) {
        Matcher m = Pattern.compile("(?i)xclipse\\s*(\\d{3})\\b").matcher(nome);
        if (!m.find()) return true;
        return m.group(1).equals("950") || m.group(1).equals("960");
    }

    private static String chaveMali(GpuInfo g, List<Par> pares, int sdk) {
        String fam;
        switch (g.familia) {
            case MALI_JM: fam = NAD_MALI_JM; break;
            case MALI_G610_G710: fam = NAD_MALI_G710; break;
            case MALI_G615: fam = NAD_MALI_G615; break;
            case MALI_G720: fam = NAD_MALI_G720; break;
            default: fam = null;
        }
        String kbase = sdk >= MALI_KBASE_MIN_SDK && GpuInfo.maliKbase(g.nome) ? NAD_MALI_KBASE : null;
        if (pares.isEmpty()) return kbase != null ? kbase : fam;
        if (kbase != null && temPar(pares, kbase, true)) return kbase;
        if (fam != null && temPar(pares, fam, true)) return fam;
        return kbase != null ? kbase : fam;
    }

    static String ddTurnipMaisNovo(List<Par> pares) {
        String melhor = null;
        long v = -1;
        for (Par p : pares) {
            if (!p.chave.startsWith(DD_TURNIP + ":") || !p.completo()) continue;
            Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)$").matcher(p.chave);
            long x = m.find() ? Long.parseLong(m.group(1)) * 1_000_000_000_000L + Long.parseLong(m.group(2)) * 1_000_000L + Long.parseLong(m.group(3)) : -1;
            if (x > v) { v = x; melhor = p.chave; }
        }
        return melhor;
    }

    // ---------------------------------------------------------------- consulta ao GitHub (TurnipReleases.refresh)
    static final class Consulta {
        final List<Asset> assets;
        final List<String> falharam;
        final long quando;

        Consulta(List<Asset> a, List<String> f, long q) { assets = a; falharam = f; quando = q; }
    }

    private static File arq(Context c, String n) {
        File d = Drivers.base(c);
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return new File(d, n);
    }

    static Consulta guardada(Context c) {
        try {
            JSONObject j = new JSONObject(new String(Files.readAllBytes(arq(c, ".catalogo.json").toPath()), StandardCharsets.UTF_8));
            List<Asset> a = new ArrayList<>();
            JSONArray l = j.getJSONArray("assets");
            for (int i = 0; i < l.length(); i++) a.add(Asset.de(l.getJSONObject(i)));
            List<String> f = new ArrayList<>();
            JSONArray fl = j.optJSONArray("failed");
            for (int i = 0; fl != null && i < fl.length(); i++) f.add(fl.getString(i));
            return new Consulta(a, f, j.optLong("checkedAt"));
        } catch (Exception e) {
            return null;
        }
    }

    static boolean velha(Context c, boolean adreno) {
        Consulta g = guardada(c);
        if (g == null || System.currentTimeMillis() - g.quando > INTERVALO_MS) return true;
        boolean algum = false, nad = false;
        for (Asset a : g.assets) { if (!a.par.isEmpty()) algum = true; if (ehNaoAdreno(a.par)) nad = true; }
        return !algum || (!adreno && !nad);
    }

    static List<Regra> regras(Context c) {
        try {
            List<Regra> r = lerRegras(new String(Files.readAllBytes(arq(c, ".regras.json").toPath()), StandardCharsets.UTF_8));
            if (r != null) return r;
        } catch (Exception ignorada) {
            // sem a lista remota, as embutidas
        }
        return EMBUTIDAS;
    }

    static Consulta atualizar(Context c, boolean adreno) throws IOException {
        JSONArray assets = new JSONArray();
        List<String> falharam = new ArrayList<>();
        IOException ultimo = null;
        int fontes = 0;
        for (Fonte f : FONTES) {
            if (adreno && f.repo.equals(NON_ADRENO_REPO)) continue;
            fontes++;
            try {
                int porPagina = f.repo.equals(NON_ADRENO_REPO) ? 100 : 15;
                JSONArray rels = new JSONArray(baixarTexto("https://api.github.com/repos/" + f.repo + "/releases?per_page=" + porPagina));
                Set<String> vistos = new HashSet<>();
                for (int i = 0; i < rels.length(); i++) {
                    JSONObject r = rels.getJSONObject(i);
                    if (r.optBoolean("draft")) continue;
                    String tag = r.getString("tag_name");
                    JSONArray lista = r.optJSONArray("assets");
                    for (int k = 0; lista != null && k < lista.length(); k++) {
                        JSONObject a = lista.getJSONObject(k);
                        String nome = a.getString("name");
                        Tipo t = f.classifica.de(nome, tag);
                        if (t == null) continue;
                        if (t.pacote && r.optBoolean("prerelease") && !f.prerelease) continue;
                        String sha = sha256GitHub(a.optString("digest"));
                        if (sha == null) continue; // sem soma publicada, fica de fora (como no DroidDeck)
                        if (!vistos.add(t.pacote ? t.par : t.linux + "|" + t.rotulo)) continue;
                        Asset x = new Asset();
                        x.fonte = f.rotulo; x.tag = tag; x.nome = nome; x.url = a.getString("browser_download_url");
                        x.tamanho = a.optLong("size"); x.linux = t.linux; x.rotulo = t.rotulo; x.sha256 = sha; x.par = t.par;
                        x.pacote = t.pacote; x.id = a.optLong("id"); x.atualizado = a.optString("updated_at");
                        assets.put(x.json());
                    }
                }
            } catch (Exception e) {
                falharam.add(f.rotulo);
                ultimo = e instanceof IOException ? (IOException) e : new IOException(e.getMessage(), e);
            }
        }
        if (falharam.size() == fontes) throw ultimo != null ? ultimo : new IOException(Tr.t("nenhuma fonte respondeu"));
        try {
            String r = baixarTexto(REGRAS_URL);
            if (lerRegras(r) != null) escrever(arq(c, ".regras.json"), r);
        } catch (Exception e) {
            Log.i(TAG, "regras automáticas: " + e.getMessage());
        }
        try {
            JSONObject j = new JSONObject().put("assets", assets).put("failed", new JSONArray(falharam)).put("checkedAt", System.currentTimeMillis());
            escrever(arq(c, ".catalogo.json"), j.toString());
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
        return guardada(c);
    }

    static String sha256GitHub(String digest) {
        if (digest == null || !digest.startsWith("sha256:")) return null;
        String h = digest.substring(7).toLowerCase(Locale.US);
        return h.matches("[0-9a-f]{64}") ? h : null;
    }

    static String baixarTexto(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "GowCollection-android");
        try {
            int cod = c.getResponseCode();
            if (cod == 403 || cod == 429) throw new IOException("o GitHub limitou as consultas; tente de novo em alguns minutos");
            if (cod != 200) throw new IOException("o GitHub respondeu " + cod);
            try (InputStream in = c.getInputStream()) {
                return new String(lerTudo(in), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    interface Progresso { void em(int pct); }

    // baixa o arquivo para o cache e confere o sha256 publicado (TurnipReleases.download)
    static File baixar(Context c, Asset a, Progresso pr) throws IOException {
        if (a.sha256.isEmpty()) throw new IOException("a lista é anterior às somas de verificação; procure atualizações");
        File alvo = new File(c.getCacheDir(), a.nome);
        HttpURLConnection h = (HttpURLConnection) new URL(a.url).openConnection();
        h.setConnectTimeout(15_000);
        h.setReadTimeout(30_000);
        h.setRequestProperty("User-Agent", "GowCollection-android");
        try {
            if (h.getResponseCode() != 200) throw new IOException("download respondeu " + h.getResponseCode());
            long total = h.getContentLengthLong() > 0 ? h.getContentLengthLong() : a.tamanho;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = h.getInputStream(); FileOutputStream out = new FileOutputStream(alvo)) {
                byte[] buf = new byte[1 << 16];
                long feito = 0;
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    feito += n;
                    if (pr != null && total > 0) pr.em((int) (feito * 100 / total));
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format(Locale.US, "%02x", b));
            if (!hex.toString().equalsIgnoreCase(a.sha256)) {
                //noinspection ResultOfMethodCallIgnored
                alvo.delete();
                throw new IOException("a soma de verificação não confere: download corrompido");
            }
            return alvo;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        } finally {
            h.disconnect();
        }
    }

    // ---------------------------------------------------------------- o que já foi baixado (nome do arquivo -> id instalado)
    static String instalado(Context c, Asset a) {
        JSONObject d = baixados(c);
        String id = d.optString(a.nome, "");
        if (ehNaoAdreno(a.par) && !a.sha256.isEmpty() && !a.sha256.equalsIgnoreCase(d.optString("sha256:" + a.nome, a.sha256))) return null;
        return !id.isEmpty() && Drivers.instalado(c, id) ? id : null;
    }

    static void registrar(Context c, Asset a, String id) {
        try {
            JSONObject d = baixados(c).put(a.nome, id);
            if (ehNaoAdreno(a.par) && !a.sha256.isEmpty()) d.put("sha256:" + a.nome, a.sha256);
            escrever(arq(c, ".baixados.json"), d.toString());
        } catch (Exception ignorada) {
            // baixa de novo na próxima
        }
    }

    static void esquecer(Context c, String id) {
        try {
            JSONObject d = baixados(c);
            List<String> ks = new ArrayList<>();
            for (java.util.Iterator<String> it = d.keys(); it.hasNext(); ) { String k = it.next(); if (id.equals(d.optString(k))) ks.add(k); }
            for (String k : ks) d.remove(k);
            escrever(arq(c, ".baixados.json"), d.toString());
        } catch (Exception ignorada) {
            // nada a esquecer
        }
    }

    private static JSONObject baixados(Context c) {
        try {
            return new JSONObject(new String(Files.readAllBytes(arq(c, ".baixados.json").toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static void escrever(File f, String s) throws IOException {
        File t = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(t)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        }
        if (!t.renameTo(f)) throw new IOException("não gravou " + f);
    }

    static byte[] lerTudo(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        return b.toByteArray();
    }

    private DriverCatalog() {}
}

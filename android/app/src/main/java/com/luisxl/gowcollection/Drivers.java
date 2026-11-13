package com.luisxl.gowcollection;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// O driver Vulkan da tela pelos critérios do DroidDeck (gpu/TurnipDriver.java, AutoDriverTrial.kt, DriverBundle.kt e o
// ensureAuto do DriverMenus.kt, GPL-3.0). Pacotes AdrenoTools (meta.json + .so) em files/drivers/<id>/ (pasta interna:
// o dlopen recusa .so do armazenamento comum); estado em files/drivers/.estado.json, que a GameActivity lê no outro
// processo antes de carregar o vitart. O libvulkan.so do APK (runtime/vkshim) abre o escolhido pela libadrenotools.
//
// Automático: no Adreno, o par recomendado pelas regras (o DD-Turnip mais novo antes) e, sem ele, o Turnip embutido do
// modelo; no Xclipse, o RADV-Xclipse (o upstream no 950/960), senão o driver do aparelho; PowerVR e Mali, sempre o do
// aparelho (o PanVK do lado Android trava o gralloc do Mali). Um driver novo do automático fica em teste: duas aberturas
// sem chegar a uma sessão pronta (Drivers.sessaoPronta, ~10 s de jogo) e ele volta ao anterior e é marcado ruim.
final class Drivers {
    private static final String TAG = "Drivers";
    static final String SISTEMA = "system", AUTO = "";
    static final String MODO_AUTO = "auto", MODO_MANUAL = "manual";
    static final int INICIOS_ANTES_DE_REVERTER = 2; // UNREADY_STARTS_BEFORE_ROLLBACK
    // os Turnip que o DroidDeck traz no APK (assets/drivers/adrenotools-<id>.zip, tools/prep_turnip_embutido.py)
    static final String EMB_A7XX = "turnip25.1.0", EMB_A8XX = "turnip-sdk36";
    static final List<String> EMBUTIDOS = Collections.unmodifiableList(Arrays.asList(EMB_A7XX, EMB_A8XX));

    static final class Driver {
        String pasta, nome, versao, descricao, biblioteca;
    }

    static File base(Context c) { return new File(c.getFilesDir(), "drivers"); }

    // ---------------------------------------------------------------- estado
    private static synchronized JSONObject estado(Context c) {
        try {
            return new JSONObject(new String(Files.readAllBytes(new File(base(c), ".estado.json").toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            JSONObject j = new JSONObject();
            try { j.put("modo", MODO_AUTO).put("android", AUTO); } catch (Exception ignorada) { /* fixo */ }
            return j;
        }
    }

    private static synchronized void gravar(Context c, JSONObject j) {
        try {
            //noinspection ResultOfMethodCallIgnored
            base(c).mkdirs();
            DriverCatalog.escrever(new File(base(c), ".estado.json"), j.toString());
        } catch (Exception e) {
            Log.w(TAG, "estado", e);
        }
    }

    private static Set<String> conjunto(JSONObject j, String k) {
        Set<String> s = new HashSet<>();
        JSONArray a = j.optJSONArray(k);
        for (int i = 0; a != null && i < a.length(); i++) s.add(a.optString(i));
        return s;
    }

    private static void por(JSONObject j, String k, Object v) {
        try { j.put(k, v instanceof Set ? new JSONArray((Set<?>) v) : v); } catch (Exception ignorada) { /* chave fixa */ }
    }

    static String modo(Context c) { return estado(c).optString("modo", MODO_AUTO); }

    static String android(Context c) { return estado(c).optString("android", AUTO); }

    static void definirAndroid(Context c, String id) { JSONObject j = estado(c); por(j, "android", id); gravar(c, j); }

    static void definirModo(Context c, String m) { JSONObject j = estado(c); por(j, "modo", m); gravar(c, j); }

    static Set<String> ruins(Context c) { return conjunto(estado(c), "ruins"); }

    // ---------------------------------------------------------------- drivers instalados
    private static JSONObject meta(Context c, String id) {
        if (id == null || id.isEmpty() || id.contains("/") || id.contains("..")) return null;
        try {
            return new JSONObject(new String(Files.readAllBytes(new File(new File(base(c), id), "meta.json").toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    static Driver ler(Context c, String id) {
        JSONObject m = meta(c, id);
        if (m == null) return null;
        Driver d = new Driver();
        d.pasta = id;
        d.nome = m.optString("name", "").isEmpty() ? id : m.optString("name");
        d.versao = m.optString("driverVersion", "");
        d.descricao = m.optString("description", "");
        d.biblioteca = m.optString("libraryName", "");
        if (d.biblioteca.isEmpty() || !new File(new File(base(c), id), d.biblioteca).isFile()) return null;
        return d;
    }

    static boolean instalado(Context c, String id) { return ler(c, id) != null; }

    static List<String> importados(Context c) {
        List<String> ids = new ArrayList<>();
        File[] ds = base(c).listFiles();
        if (ds != null)
            for (File d : ds) {
                String id = d.getName();
                if (d.isDirectory() && !EMBUTIDOS.contains(id) && !id.startsWith(".") && instalado(c, id)) ids.add(id);
            }
        Collections.sort(ids);
        return ids;
    }

    static String rotulo(Context c, String id) {
        if (SISTEMA.equals(id)) return Tr.t("Driver do aparelho");
        Driver d = ler(c, id);
        if (d == null) return EMBUTIDOS.contains(id) ? "Turnip " + id.replace("turnip", "") + Tr.t(" (embutido)") : id;
        String n = EMBUTIDOS.contains(id) ? "Turnip " + id.replace("turnip", "") + Tr.t(" (embutido)") : d.nome;
        return (n + " " + (EMBUTIDOS.contains(id) ? "" : d.versao)).trim();
    }

    static void remover(Context c, String id) {
        if (id == null || id.isEmpty() || EMBUTIDOS.contains(id) || id.contains("/") || id.contains("..")) return;
        apagar(new File(base(c), id));
        DriverCatalog.esquecer(c, id);
        if (id.equals(android(c))) definirAndroid(c, AUTO);
    }

    // ---------------------------------------------------------------- validação e instalação (TurnipDriver.adopt)
    // Só pacotes AdrenoTools do lado Android: driver de Linux (glibc) ou de Wayland é recusado
    static String recusa(File dir) {
        File mf = new File(dir, "meta.json");
        if (!mf.isFile()) return Tr.t("o pacote não tem meta.json");
        String lib, tipo;
        try {
            JSONObject m = new JSONObject(new String(Files.readAllBytes(mf.toPath()), StandardCharsets.UTF_8));
            lib = m.optString("libraryName", "");
            tipo = m.optString("kind", "");
        } catch (Exception e) {
            return Tr.t("meta.json ilegível (") + e.getMessage() + ")";
        }
        if ("linux-vulkan-icd".equals(tipo)) return Tr.t("é um driver de Linux (para o Proton), não do Android");
        if ("wayland-game-driver".equals(tipo)) return Tr.t("é um driver de Wayland, não do Android");
        if (lib.isEmpty()) return Tr.t("o meta.json não diz o libraryName");
        File so = new File(dir, lib);
        if (!so.isFile()) return Tr.t("o pacote não tem ") + lib;
        if (!elfAarch64(so)) return lib + Tr.t(" não é uma biblioteca ARM64");
        if (contemAscii(so, "libc.so.6")) return lib + Tr.t(" é de Linux (glibc), não do Android");
        return null;
    }

    static boolean elfAarch64(File f) {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            byte[] h = new byte[20];
            r.readFully(h);
            return h[0] == 0x7f && h[1] == 'E' && h[2] == 'L' && h[3] == 'F' && h[4] == 2 && ((h[18] & 0xff) | ((h[19] & 0xff) << 8)) == 183;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean contemAscii(File f, String s) {
        byte[] alvo = s.getBytes(StandardCharsets.US_ASCII);
        try (InputStream in = new java.io.BufferedInputStream(new FileInputStream(f), 1 << 16)) {
            int casou = 0;
            for (int b; (b = in.read()) >= 0; ) {
                if (b == alvo[casou]) { if (++casou == alvo.length) return true; }
                else casou = b == alvo[0] ? 1 : 0;
            }
        } catch (Exception ignorada) {
            // ilegível: a recusa sai pelo ELF
        }
        return false;
    }

    private static File palco(Context c) throws Exception {
        File d = new File(base(c), ".tmp-" + System.nanoTime());
        apagar(d);
        if (!d.mkdirs()) throw new Exception(Tr.t("não criou ") + d);
        return d;
    }

    private static String adotar(Context c, File tmp, String sugestao) throws Exception {
        String r = recusa(tmp);
        if (r != null) throw new IllegalArgumentException(Tr.t("Não é um driver AdrenoTools: ") + r);
        String nome = "";
        try {
            nome = new JSONObject(new String(Files.readAllBytes(new File(tmp, "meta.json").toPath()), StandardCharsets.UTF_8)).optString("name", "");
        } catch (Exception ignorada) {
            // usa o nome do arquivo
        }
        if (nome.isEmpty()) {
            nome = sugestao != null ? sugestao : "adrenotools-driver";
            if (nome.toLowerCase(Locale.US).endsWith(".zip")) nome = nome.substring(0, nome.length() - 4);
        }
        String id = nome.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (id.isEmpty() || id.startsWith(".")) id = "driver" + id;
        String unico = id;
        for (int n = 2; new File(base(c), unico).exists(); n++) unico = id + "-" + n;
        if (!tmp.renameTo(new File(base(c), unico))) throw new Exception(Tr.t("não moveu para ") + unico);
        Log.i(TAG, "driver AdrenoTools instalado: " + unico);
        return unico;
    }

    // Um .zip qualquer: pacote AdrenoTools (meta.json + .so) ou pacote de par (manifest.json do DroidDeck, com as metades
    // android e linux): deste só a metade android entra
    static String instalarZip(Context c, InputStream zip, String sugestao) throws Exception {
        File tmp = palco(c);
        boolean manter = false;
        try {
            File bruto = palco(c);
            try {
                desempacotar(zip, bruto, true);
                File dir = bruto;
                File man = new File(bruto, "manifest.json");
                if (man.isFile()) {
                    JSONObject m = new JSONObject(new String(Files.readAllBytes(man.toPath()), StandardCharsets.UTF_8));
                    JSONObject dr = m.optJSONObject("drivers");
                    String p = dr != null && dr.optJSONObject("android") != null ? dr.getJSONObject("android").optString("path") : "";
                    p = p.trim().replaceAll("^/+|/+$", "");
                    if (p.isEmpty() || p.contains("..")) throw new IllegalArgumentException(Tr.t("o manifest.json do pacote não tem a metade android"));
                    dir = new File(bruto, p);
                    if (sugestao == null || sugestao.isEmpty()) sugestao = m.optString("package", "pacote");
                }
                File[] fs = dir.listFiles();
                if (fs != null) for (File f : fs) if (f.isFile()) Files.copy(f.toPath(), new File(tmp, f.getName()).toPath());
            } finally {
                apagar(bruto);
            }
            String id = adotar(c, tmp, sugestao);
            manter = true;
            return id;
        } finally {
            if (!manter) apagar(tmp);
        }
    }

    // keepDirs: um pacote de par tem pastas (android/ linux/); o AdrenoTools é plano
    private static void desempacotar(InputStream zip, File dir, boolean keepDirs) throws Exception {
        long total = 0;
        try (ZipInputStream z = new ZipInputStream(zip)) {
            byte[] buf = new byte[1 << 16];
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                if (e.isDirectory()) continue;
                String n = e.getName().replace('\\', '/');
                if (n.contains("..") || n.startsWith("/")) continue;
                File alvo = keepDirs ? new File(dir, n) : new File(dir, new File(n).getName());
                File pai = alvo.getParentFile();
                //noinspection ResultOfMethodCallIgnored
                if (pai != null) pai.mkdirs();
                try (FileOutputStream o = new FileOutputStream(alvo)) {
                    for (int k; (k = z.read(buf)) > 0; ) {
                        total += k;
                        if (total > (512L << 20)) throw new Exception(Tr.t("pacote grande demais")); // DriverBundle.MAX_BYTES
                        o.write(buf, 0, k);
                    }
                }
            }
        }
    }

    static boolean garantirEmbutido(Context c, String id) {
        if (instalado(c, id)) return true;
        File dir = new File(base(c), id);
        apagar(dir);
        try (InputStream in = c.getAssets().open("drivers/adrenotools-" + id + ".zip")) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            desempacotar(in, dir, false);
        } catch (Exception e) {
            Log.e(TAG, "não abriu o embutido " + id, e);
            apagar(dir);
            return false;
        }
        return instalado(c, id);
    }

    // ---------------------------------------------------------------- a escolha para o jogo (TurnipDriver.choose)
    // null = driver do aparelho
    static Driver paraJogo(Context c) {
        String id = escolher(c);
        if (id == null) return null;
        if (EMBUTIDOS.contains(id) && !garantirEmbutido(c, id)) return null;
        return ler(c, id);
    }

    static String escolher(Context c) {
        JSONObject e = estado(c);
        String esc = e.optString("android", AUTO);
        GpuInfo g = GpuInfo.detectar(c);
        // um Mali no automático desenha a tela no driver do aparelho: a metade Android do PanVK falha no gralloc do Mali
        if (!esc.isEmpty() && g.familia.mali() && MODO_AUTO.equals(e.optString("modo")) && conjunto(e, "naoAdreno").contains(esc)) {
            Log.i(TAG, "Mali no automático: tela no driver do aparelho, não em " + esc);
            return null;
        }
        if (SISTEMA.equals(esc)) return null;
        if (!esc.isEmpty()) {
            if (EMBUTIDOS.contains(esc) || instalado(c, esc)) return esc;
            Log.w(TAG, "o driver escolhido \"" + esc + "\" sumiu; escolhendo pela GPU");
        }
        return pelaGpu(g);
    }

    static String pelaGpu(GpuInfo g) {
        if (GpuInfo.hal() != null) return null; // PowerVR / Xclipse sem par: o driver do aparelho
        if (g.familia == GpuInfo.Familia.NAO_ADRENO || g.familia.mali()) return null;
        return peloModelo(g.modelo, Build.VERSION.SDK_INT);
    }

    // TurnipDriver.chooseByModel; o embutido A6xx do DroidDeck não está no repositório dele: no A6xx, o do aparelho
    static String peloModelo(int modelo, int sdk) {
        if (modelo >= 800) return EMB_A8XX;
        if (modelo >= 700) return EMB_A7XX;
        if (modelo >= 600) return null;
        return sdk >= 36 ? EMB_A8XX : EMB_A7XX;
    }

    // ---------------------------------------------------------------- teste do driver novo (AutoDriverTrial)
    static String liberacaoEmTeste(Context c) {
        JSONObject t = estado(c).optJSONObject("teste");
        return t == null ? null : t.optString("release", null);
    }

    static void comecarTeste(Context c, String release, String nome, String tela, String anterior) {
        JSONObject e = estado(c);
        JSONObject t = new JSONObject();
        por(t, "release", release); por(t, "name", nome); por(t, "display", tela); por(t, "previous", anterior); por(t, "starts", 0);
        por(e, "teste", t);
        gravar(c, e);
    }

    // a GameActivity abriu com este driver (displayStarting/sessionStarting)
    static void jogoIniciando(Context c, String tela) {
        JSONObject e = estado(c);
        JSONObject t = e.optJSONObject("teste");
        if (t == null || !t.optString("display").equals(tela)) return;
        por(t, "starts", t.optInt("starts") + 1);
        por(e, "teste", t);
        gravar(c, e);
    }

    // o jogo apresentou quadros suficientes com o driver em teste: fica (AutoDriverTrial.sessionReady)
    static void sessaoPronta(Context c) {
        JSONObject e = estado(c);
        JSONObject t = e.optJSONObject("teste");
        if (t == null) return;
        String tela = t.optString("display");
        if (!tela.equals(e.optString("android"))) return;
        Log.i(TAG, t.optString("release") + " chegou a uma sessão pronta; fica");
        e.remove("teste");
        gravar(c, e);
        podar(c, new HashSet<>(Collections.singletonList(tela)));
    }

    // duas aberturas sem sessão pronta: volta ao anterior e marca a liberação como ruim (AutoDriverTrial.rollBack)
    static String reverterSeDevido(Context c) {
        JSONObject e = estado(c);
        JSONObject t = e.optJSONObject("teste");
        if (t == null || t.optInt("starts") < INICIOS_ANTES_DE_REVERTER) return null;
        Log.w(TAG, t.optString("release") + " não chegou a uma sessão pronta em " + t.optInt("starts") + " aberturas; volta o anterior");
        if (e.optString("android").equals(t.optString("display"))) por(e, "android", t.optString("previous"));
        Set<String> r = conjunto(e, "ruins");
        r.add(t.optString("release"));
        por(e, "ruins", r);
        e.remove("teste");
        gravar(c, e);
        Set<String> manter = new HashSet<>();
        manter.add(t.optString("previous"));
        manter.add(android(c));
        podar(c, manter);
        return t.optString("name");
    }

    // apaga os drivers que o automático baixou e não estão mais em uso (AutoDriverTrial.prune)
    static void podar(Context c, Set<String> manter) {
        JSONObject e = estado(c);
        Set<String> donos = conjunto(e, "autoInstalados");
        Set<String> fica = new HashSet<>();
        for (String id : donos) {
            if (manter.contains(id)) { fica.add(id); continue; }
            remover(c, id);
        }
        e = estado(c);
        por(e, "autoInstalados", fica);
        gravar(c, e);
    }

    // ---------------------------------------------------------------- automático e escolha à mão (DriverMenus)
    interface Aviso { void status(String texto); }

    // Roda numa thread: decide e, se preciso, baixa e instala. O texto vai para a tela de drivers.
    static void automatico(Context c, boolean forcar, Aviso av) {
        if (!MODO_AUTO.equals(modo(c))) return;
        GpuInfo g = GpuInfo.detectar(c);
        if (g.familia == GpuInfo.Familia.POWERVR && !g.xclipse() && !GpuInfo.xclipseHal()) {
            definirAndroid(c, AUTO);
            av.status(Tr.t("PowerVR: a tela usa o driver do aparelho"));
            return;
        }
        if (g.familia.mali()) {
            if (!AUTO.equals(android(c))) definirAndroid(c, AUTO);
            av.status(g.nome + Tr.t(": a tela usa o driver do aparelho (o PanVK do lado Android trava o gralloc do Mali)"));
            return;
        }
        String revertido = reverterSeDevido(c);
        if (revertido != null) {
            av.status(revertido + Tr.t(" não abriu o jogo em ") + INICIOS_ANTES_DE_REVERTER + Tr.t(" tentativas: voltou o driver anterior"));
            return;
        }
        int sdk = Build.VERSION.SDK_INT;
        Integer blob = GpuInfo.blob(c);
        Set<String> ruins = ruins(c);
        if (DriverCatalog.recomendado(g, new ArrayList<>(), DriverCatalog.regras(c), ruins, sdk, blob) == null) {
            av.status(Tr.t("Nenhum driver a definir para ") + g.nome + Tr.t(": a tela usa o driver do aparelho"));
            return;
        }
        String problema = null;
        if (forcar || DriverCatalog.velha(c, g.familia.adreno())) {
            av.status(Tr.t("Procurando os drivers mais novos…"));
            try {
                DriverCatalog.atualizar(c, g.familia.adreno());
            } catch (Exception ex) {
                problema = ex.getMessage();
            }
        }
        DriverCatalog.Consulta q = DriverCatalog.guardada(c);
        List<DriverCatalog.Par> pares = DriverCatalog.pares(q == null ? new ArrayList<>() : q.assets);
        String chave = DriverCatalog.recomendado(g, pares, DriverCatalog.regras(c), ruins, sdk, blob);
        DriverCatalog.Par par = null;
        for (DriverCatalog.Par p : pares) if (p.chave.equals(chave)) par = p;
        String usando = rotulo(c, escolher(c) == null ? SISTEMA : escolher(c));
        if (par != null && par.completo()) {
            String inst = DriverCatalog.instalado(c, par.arquivo());
            if (inst != null && inst.equals(android(c))) {
                av.status(problema != null ? Tr.t("Em dia (a consulta falhou: ") + problema + ")" : Tr.t("Em dia: ") + par.nome + " " + par.versao());
                return;
            }
        }
        if (g.familia == GpuInfo.Familia.POWERVR && (par == null || !par.completo())) {
            definirAndroid(c, AUTO);
            av.status(Tr.t("O RADV do Xclipse não está publicado agora: a tela usa o driver do aparelho"));
            return;
        }
        if (par == null || !par.completo()) {
            av.status((problema != null ? Tr.t("Não deu para consultar (") + problema + ")" : Tr.t("O driver recomendado ainda não foi publicado"))
                    + Tr.t("; usando ") + usando);
            return;
        }
        if (ruins.contains(par.liberacao())) {
            av.status(Tr.t("Pulando ") + par.nome + " " + par.versao() + Tr.t(", que falhou antes; usando ") + usando);
            return;
        }
        instalarPar(c, par, true, av);
    }

    // baixa (ou reaproveita) a metade Android do par e a põe em uso; no automático, em teste
    static boolean instalarPar(Context c, DriverCatalog.Par par, boolean auto, Aviso av) {
        DriverCatalog.Asset a = par.arquivo();
        if (a == null) return false;
        try {
            String id = DriverCatalog.instalado(c, a);
            boolean baixou = false;
            if (id == null) {
                av.status(Tr.t("Baixando ") + par.nome + " " + par.versao() + "…");
                File z = DriverCatalog.baixar(c, a, pct -> av.status(Tr.t("Baixando ") + par.nome + " " + par.versao() + "… " + pct + "%"));
                try (InputStream in = new FileInputStream(z)) {
                    id = instalarZip(c, in, par.nome + "-" + par.versao());
                } finally {
                    //noinspection ResultOfMethodCallIgnored
                    z.delete();
                }
                DriverCatalog.registrar(c, a, id);
                baixou = true;
            }
            JSONObject e = estado(c);
            String anterior = e.optString("android", AUTO);
            if (DriverCatalog.ehNaoAdreno(par.chave)) { Set<String> n = conjunto(e, "naoAdreno"); n.add(id); por(e, "naoAdreno", n); }
            por(e, "android", id);
            if (!auto) por(e, "modo", MODO_MANUAL);
            if (auto) { Set<String> d = conjunto(e, "autoInstalados"); d.add(id); por(e, "autoInstalados", d); }
            gravar(c, e);
            if (auto && !anterior.equals(id)) {
                JSONObject t = estado(c).optJSONObject("teste");
                String antes = t != null ? t.optString("previous") : anterior;
                comecarTeste(c, par.liberacao(), par.nome + " " + par.versao(), id, antes);
                Set<String> manter = new HashSet<>(Arrays.asList(antes, id));
                podar(c, manter);
            }
            av.status((baixou ? Tr.t("Atualizado para ") : Tr.t("Usando ")) + par.nome + " " + par.versao() + (auto ? Tr.t(" (em teste até o jogo abrir)") : ""));
            return true;
        } catch (Exception ex) {
            Log.w(TAG, "par " + par.chave, ex);
            av.status(ex instanceof IllegalArgumentException ? ex.getMessage() : Tr.t("Não baixou: ") + ex.getMessage());
            return false;
        }
    }

    // escolha à mão de um driver já instalado, embutido ou o do aparelho
    static void escolherManual(Context c, String id) {
        JSONObject e = estado(c);
        por(e, "modo", MODO_MANUAL);
        por(e, "android", id);
        e.remove("teste");
        gravar(c, e);
    }

    static void voltarAoAutomatico(Context c) {
        JSONObject e = estado(c);
        por(e, "modo", MODO_AUTO);
        por(e, "android", AUTO);
        gravar(c, e);
    }

    // ---------------------------------------------------------------- pacotes deixados em <pasta do jogo>/drivers/*.zip
    static int importarDaPasta(Context c, File raiz) {
        File[] zs = new File(raiz, "drivers").listFiles((d, n) -> n.toLowerCase(Locale.US).endsWith(".zip"));
        if (zs == null) return 0;
        File feitos = new File(base(c), ".importados");
        //noinspection ResultOfMethodCallIgnored
        base(c).mkdirs();
        String ja = "";
        try { if (feitos.isFile()) ja = new String(Files.readAllBytes(feitos.toPath()), StandardCharsets.UTF_8); } catch (Exception ignorada) { /* relê */ }
        StringBuilder novo = new StringBuilder(ja);
        int n = 0;
        for (File z : zs) {
            String chave = z.getName() + ":" + z.length() + "\n";
            if (ja.contains(chave)) continue;
            try (InputStream in = new FileInputStream(z)) {
                instalarZip(c, in, z.getName());
                n++;
            } catch (Exception ex) {
                Log.w(TAG, "pacote " + z, ex);
            }
            novo.append(chave);
        }
        try { DriverCatalog.escrever(feitos, novo.toString()); } catch (Exception ignorada) { /* tenta na próxima */ }
        return n;
    }

    static void apagar(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) apagar(x);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private Drivers() {}
}

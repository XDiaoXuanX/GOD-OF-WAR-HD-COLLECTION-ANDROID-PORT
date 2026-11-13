package com.luisxl.gowcollection;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A GPU do aparelho, pelos mesmos critérios do DroidDeck (Droid-Deck/DroidDeck, gpu/GpuInfo.kt e
// SystemVulkanDriver.java, GPL-3.0): o modelo vem do nome do kernel (kgsl), senão do nome que o Vulkan deu (o jogo grava
// files/vk_dispositivo.txt na primeira abertura), senão da plataforma do chip; a família decide os drivers.
final class GpuInfo {
    enum Familia {
        A8XX("Adreno 8xx"), A7XX_BAIXO("Adreno 710/720/722"), A7XX("Adreno 7xx"), A6XX("Adreno 6xx"), ADRENO_DESCONHECIDO("Adreno"),
        POWERVR("PowerVR"), MALI_JM("Mali (G52-G78)"), MALI_G610_G710("Mali-G610/G710"), MALI_G615("Mali-G615"),
        MALI_G720("Mali-G720"), MALI_RECENTE("Mali-G715/G725/G925"), MALI_OUTRO("Mali"), NAO_ADRENO(Tr.t("GPU que não é Adreno"));

        final String rotulo;

        Familia(String r) { rotulo = r; }

        boolean mali() { return name().startsWith("MALI_"); }

        boolean adreno() { return this == A8XX || this == A7XX_BAIXO || this == A7XX || this == A6XX || this == ADRENO_DESCONHECIDO; }
    }

    final String nome;
    final int modelo;
    final Familia familia;
    final boolean oneUi8Gen2;
    final String origem; // "kernel", "vulkan", "plataforma" ou ""
    final String nomeKernel;

    private GpuInfo(String nome, int modelo, Familia familia, boolean oneUi8Gen2, String origem, String nomeKernel) {
        this.nome = nome;
        this.modelo = modelo;
        this.familia = familia;
        this.oneUi8Gen2 = oneUi8Gen2;
        this.origem = origem;
        this.nomeKernel = nomeKernel;
    }

    boolean xclipse() { return familia == Familia.POWERVR && nome.toLowerCase(Locale.US).contains("xclipse"); }

    String rotulo() { return xclipse() ? "Samsung Xclipse" : familia.rotulo; }

    // ---------------------------------------------------------------- o que o Vulkan disse (files/vk_dispositivo.txt)
    static Map<String, String> vulkan(Context c) {
        Map<String, String> m = new HashMap<>();
        try {
            for (String l : Files.readAllLines(new File(c.getFilesDir(), "vk_dispositivo.txt").toPath(), StandardCharsets.UTF_8)) {
                int eq = l.indexOf('=');
                if (eq > 0) m.put(l.substring(0, eq), l.substring(eq + 1));
            }
        } catch (Exception ignorada) {
            // o jogo ainda não abriu neste aparelho
        }
        return m;
    }

    // versão do blob da Qualcomm (o meio de driverVersion), como AutoPicks.blobVersion
    static Integer blob(Context c) {
        Map<String, String> v = vulkan(c);
        String st = v.get("status");
        if (st != null && st.startsWith("driver próprio:")) return null; // o número é do Turnip, não do blob
        String dv = v.get("driver_version");
        if (dv == null) return null;
        String[] p = dv.split("\\.");
        try {
            return p.length > 1 ? Integer.parseInt(p[1]) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- detecção
    static GpuInfo detectar(Context c) {
        boolean adreno = new File("/sys/class/kgsl/kgsl-3d0").exists() || new File("/vendor/lib64/hw/vulkan.adreno.so").exists();
        String bruto = ler("/sys/class/kgsl/kgsl-3d0/gpu_model");
        String nomeVk = vulkan(c).get("device");
        int modelo = 0;
        String origem = "";
        if (adreno) {
            int k = tresDigitos(bruto);
            int v = tresDigitos(nomeVk);
            int p = modeloDaPlataforma();
            if (k > 0) { modelo = k; origem = "kernel"; }
            else if (v > 0) { modelo = v; origem = "vulkan"; }
            else if (p > 0) { modelo = p; origem = "plataforma"; }
        }
        String mali = adreno || !new File("/dev/mali0").exists() ? null : nomeMali(nomeVk);
        boolean powerVr = hal() != null;
        Familia f = familia(adreno, modelo, powerVr, mali);
        boolean samsung = "samsung".equalsIgnoreCase(Build.MANUFACTURER);
        String nome;
        if (f == Familia.POWERVR) {
            String g = xclipseHal() ? "Xclipse" : "PowerVR";
            nome = nomeVk != null && nomeVk.toLowerCase(Locale.US).contains(g.toLowerCase(Locale.US)) ? nomeVk : g;
        } else if (mali != null && !mali.isEmpty()) nome = mali;
        else if (!adreno) nome = Build.HARDWARE.isEmpty() ? Tr.t("esta GPU") : Build.HARDWARE;
        else nome = modelo > 0 ? "Adreno " + modelo : "Adreno";
        return new GpuInfo(nome, modelo, f, samsung && modelo == 740, origem, bruto == null ? "" : bruto);
    }

    static Familia familia(boolean adreno, int modelo, boolean powerVr, String mali) {
        if (!adreno && powerVr) return Familia.POWERVR;
        if (!adreno && mali != null) return familiaMali(mali);
        if (!adreno) return Familia.NAO_ADRENO;
        if (modelo == 0) return Familia.ADRENO_DESCONHECIDO;
        if (modelo >= 800) return Familia.A8XX;
        if (modelo == 710 || modelo == 720 || modelo == 722) return Familia.A7XX_BAIXO;
        if (modelo >= 700) return Familia.A7XX;
        if (modelo >= 600) return Familia.A6XX;
        return Familia.ADRENO_DESCONHECIDO;
    }

    private static final Pattern ADRENO = Pattern.compile("(?i)\\badreno\\s*(?:\\(TM\\)\\s*)?(\\d{3})(?!\\d)");

    static int tresDigitos(String s) {
        if (s == null) return 0;
        Matcher m = ADRENO.matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static final Map<String, Integer> PLATAFORMAS = new HashMap<>();
    static {
        PLATAFORMAS.put("msmnile", 640); PLATAFORMAS.put("kona", 650); PLATAFORMAS.put("lahaina", 660);
        PLATAFORMAS.put("taro", 730); PLATAFORMAS.put("cape", 730); PLATAFORMAS.put("kalama", 740);
        PLATAFORMAS.put("pineapple", 750); PLATAFORMAS.put("sun", 830);
    }

    static int modeloDaPlataforma() {
        String plat = prop("ro.board.platform").toLowerCase(Locale.US), qti = prop("ro.vendor.qti.soc_name");
        String soc = Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : "";
        if (normal(soc).equals("SM8975") || normal(qti).equals("SM8975")) return 850;
        Integer m = PLATAFORMAS.get(plat);
        if (m == null) m = PLATAFORMAS.get(qti.toLowerCase(Locale.US));
        return m == null ? 0 : m;
    }

    private static String normal(String s) { return s == null ? "" : s.trim().toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", ""); }

    // ---------------------------------------------------------------- PowerVR e Xclipse (SystemVulkanDriver.hal)
    private static final String[] HALS = {"/vendor/lib64/hw/vulkan.powervr.so", "/vendor/lib64/hw/vulkan.samsung.so"};

    // Um Exynos com Mali (Exynos 1280/1380: /dev/mali0) também chama o HAL de vulkan.samsung.so: esse é Mali, não Xclipse
    static String hal() {
        for (String p : HALS) {
            if (!new File(p).isFile()) continue;
            if (p.endsWith("/vulkan.samsung.so") && new File("/dev/mali0").exists()) continue;
            return p;
        }
        return null;
    }

    static boolean xclipseHal() {
        String h = hal();
        return h != null && h.endsWith("/vulkan.samsung.so");
    }

    // ---------------------------------------------------------------- Mali
    private static String nomeMali(String nomeVk) {
        if (nomeVk != null && nomeVk.toLowerCase(Locale.US).contains("mali")) return nomeVk;
        String g = ler("/sys/class/misc/mali0/device/gpuinfo");
        if (g != null && g.toLowerCase(Locale.US).contains("mali")) return g.split(" ")[0];
        String[] ids = {Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : "", prop("ro.board.platform"), prop("ro.soc.model"),
                Build.HARDWARE, Build.BOARD};
        for (String id : ids) {
            if (id == null || id.trim().isEmpty()) continue;
            String m = MALI_SOCS.get(id.trim().toLowerCase(Locale.US));
            if (m != null) return m + Tr.t(" (pelo chip ") + id.trim().toLowerCase(Locale.US) + ")";
        }
        return "";
    }

    private static final Map<String, String> MALI_SOCS = new HashMap<>();
    static {
        String[][] t = {{"mt6993", "Mali-G1-Ultra"}, {"mt6991", "Mali-G925"}, {"mt6989", "Mali-G720"}, {"mt6899", "Mali-G720"},
                {"mt6985", "Mali-G715"}, {"mt6983", "Mali-G710"}, {"mt6897", "Mali-G615"}, {"mt6878", "Mali-G615"},
                {"mt6895", "Mali-G610"}, {"mt6896", "Mali-G610"}, {"mt6886", "Mali-G610"}, {"mt6893", "Mali-G77"},
                {"mt6891", "Mali-G77"}, {"mt6889", "Mali-G77"}, {"mt6885", "Mali-G77"}, {"mt6877", "Mali-G68"},
                {"mt6853", "Mali-G57"}, {"mt6833", "Mali-G57"}, {"mt6835", "Mali-G57"}, {"mt6789", "Mali-G57"},
                {"mt6785", "Mali-G76"}, {"mt6781", "Mali-G52"}, {"mt6769", "Mali-G52"}, {"mt8192", "Mali-G57"},
                {"mt8195", "Mali-G57"}, {"gs101", "Mali-G78"}, {"gs201", "Mali-G710"}, {"zuma", "Mali-G715"},
                {"zumapro", "Mali-G715"}, {"s5e8835", "Mali-G68"}, {"s5e8825", "Mali-G68"}};
        for (String[] r : t) MALI_SOCS.put(r[0], r[1]);
    }

    static Integer numeroMali(String nome) {
        Matcher m = Pattern.compile("(?i)G(\\d{2,3})").matcher(nome);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    // os Mali que o PanVK kbase de um binário cobre (arch v9-v13)
    static boolean maliKbase(String nome) {
        Integer n = numeroMali(nome);
        if (n == null) return false;
        int[] ok = {57, 68, 77, 78, 610, 710, 615, 715, 720, 725, 925};
        for (int x : ok) if (x == n) return true;
        return false;
    }

    static Familia familiaMali(String nome) {
        Integer n = numeroMali(nome);
        if (n == null) return Familia.MALI_OUTRO;
        switch (n) {
            case 31: case 51: case 52: case 57: case 68: case 71: case 72: case 76: case 77: case 78: return Familia.MALI_JM;
            case 610: case 710: return Familia.MALI_G610_G710;
            case 615: return Familia.MALI_G615;
            case 720: return Familia.MALI_G720;
            case 715: case 725: case 925: return Familia.MALI_RECENTE;
            default: return Familia.MALI_OUTRO;
        }
    }

    // ---------------------------------------------------------------- utilidades
    static String ler(String caminho) {
        try {
            String s = new String(Files.readAllBytes(new File(caminho).toPath()), StandardCharsets.UTF_8).trim();
            return s.isEmpty() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    static String prop(String nome) {
        try {
            Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, nome);
            return v instanceof String ? (String) v : "";
        } catch (Exception e) {
            return "";
        }
    }
}

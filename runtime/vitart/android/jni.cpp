// a ponte com o Java (com.luisxl.gowcollection.GameActivity): o que a Activity manda fica guardado aqui, e o que o
// runtime precisa do Android (som, aviso, troféu, relançar) volta por métodos estáticos dela
#include "android/droid.h"
#include "gxm_overlay.h"
#include "gxm_window.h"
#include "host_audio.h"
#include "host_input.h"
#include "vitart.h"

#include <android/native_window_jni.h>
#include <jni.h>
#include <pthread.h>
#include <unistd.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdlib>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

int vitart_main(int argc, char **argv); // core.cpp

namespace {

JavaVM *g_vm = nullptr;
jclass g_cls = nullptr; // GameActivity (referência global: FindClass numa thread nossa não acha classe do app)

// janela
std::mutex g_wm;
std::condition_variable g_wcv;
ANativeWindow *g_win = nullptr;
uint32_t g_gen = 0, g_ack = 0;
bool g_holding = false; // o renderizador está com a janela da geração g_ack
std::atomic<bool> g_visible{false}, g_resized{false};
std::atomic<int> g_w{0}, g_h{0}, g_hz{60};

// entrada
std::atomic<uint32_t> g_buttons{0}, g_sticks{0x80808080u};
std::atomic<uint8_t> g_active{0};
std::atomic<bool> g_touch{false};
std::atomic<float> g_tx{0}, g_ty{0};
std::atomic<bool> g_keys[256];

// a thread pede JNIEnv uma vez e fica presa à VM até acabar (as threads do jogo nunca acabam antes do processo)
JNIEnv *env() {
    JNIEnv *e = nullptr;
    if (g_vm->GetEnv(reinterpret_cast<void **>(&e), JNI_VERSION_1_6) == JNI_OK) return e;
    static pthread_key_t key;
    static std::once_flag once;
    std::call_once(once, [] { pthread_key_create(&key, [](void *) { g_vm->DetachCurrentThread(); }); });
    if (g_vm->AttachCurrentThread(&e, nullptr) != JNI_OK) return nullptr;
    pthread_setspecific(key, e);
    return e;
}

jstring jstr(JNIEnv *e, const std::string &s) { return e->NewStringUTF(s.c_str()); }

void call_static_void(const char *name, const char *sig, jstring arg) {
    JNIEnv *e = env();
    if (!e || !g_cls) return;
    if (jmethodID m = e->GetStaticMethodID(g_cls, name, sig)) e->CallStaticVoidMethod(g_cls, m, arg);
    if (e->ExceptionCheck()) e->ExceptionClear();
    if (arg) e->DeleteLocalRef(arg);
}

} // namespace

// ============================================================= o que o runtime pede
namespace vt::droid {

void session_ready() { call_static_void("sessaoPronta", "(Ljava/lang/String;)V", nullptr); }
void sair_do_jogo() { call_static_void("sairDoJogo", "(Ljava/lang/String;)V", nullptr); }

ANativeWindow *window_take(uint32_t have, uint32_t &gen) {
    const std::lock_guard<std::mutex> lock(g_wm);
    gen = g_gen;
    if (g_gen == have || !g_win) return nullptr;
    ANativeWindow_acquire(g_win);
    return g_win;
}

void window_released(uint32_t gen) {
    {
        const std::lock_guard<std::mutex> lock(g_wm);
        g_ack = gen;
        g_holding = gen == g_gen && g_win;
    }
    g_wcv.notify_all();
}

bool window_visible() { return g_visible; }
void window_size(int &w, int &h) { w = g_w, h = g_h; }
int refresh_rate() { return g_hz; }

bool pointer(float &nx, float &ny) {
    if (!g_touch) return false;
    nx = g_tx, ny = g_ty;
    return true;
}

Pad pad() {
    Pad p;
    p.buttons = g_buttons;
    const uint32_t s = g_sticks;
    p.lx = (uint8_t)s, p.ly = (uint8_t)(s >> 8), p.rx = (uint8_t)(s >> 16), p.ry = (uint8_t)(s >> 24);
    p.active = g_active.exchange(0);
    return p;
}

bool key_down(int vk) { return vk > 0 && vk < 256 && g_keys[vk]; }

void play_sound(const std::string &wav) {
    JNIEnv *e = env();
    if (e) call_static_void("playSound", "(Ljava/lang/String;)V", jstr(e, wav));
}

void notify(const std::string &text) {
    JNIEnv *e = env();
    if (e) call_static_void("notifyText", "(Ljava/lang/String;)V", jstr(e, text));
}

bool render_banner(const std::vector<uint8_t> &icon_png, char tier, const std::string &name, const std::string &detail, uint32_t screen_h,
                   std::vector<uint32_t> &px, uint32_t &w, uint32_t &h, uint32_t &radius) {
    JNIEnv *e = env();
    if (!e || !g_cls) return false;
    jmethodID m = e->GetStaticMethodID(g_cls, "renderTrophy", "([BCLjava/lang/String;Ljava/lang/String;I)[I");
    if (!m) {
        e->ExceptionClear();
        return false;
    }
    jbyteArray icon = e->NewByteArray((jsize)icon_png.size());
    e->SetByteArrayRegion(icon, 0, (jsize)icon_png.size(), reinterpret_cast<const jbyte *>(icon_png.data()));
    jstring jn = jstr(e, name), jd = jstr(e, detail);
    auto arr = static_cast<jintArray>(e->CallStaticObjectMethod(g_cls, m, icon, (jchar)tier, jn, jd, (jint)screen_h));
    e->DeleteLocalRef(icon), e->DeleteLocalRef(jn), e->DeleteLocalRef(jd);
    if (e->ExceptionCheck() || !arr) {
        e->ExceptionClear();
        return false;
    }
    // [largura, altura, raio, pixels ARGB...]: o int ARGB do Java, na memória, já é BGRA
    const jsize n = e->GetArrayLength(arr);
    std::vector<jint> v((size_t)n);
    e->GetIntArrayRegion(arr, 0, n, v.data());
    e->DeleteLocalRef(arr);
    if (n < 3 || (size_t)n != 3 + (size_t)v[0] * (size_t)v[1]) return false;
    w = (uint32_t)v[0], h = (uint32_t)v[1], radius = (uint32_t)v[2];
    px.assign(reinterpret_cast<const uint32_t *>(v.data() + 3), reinterpret_cast<const uint32_t *>(v.data() + n));
    return true;
}

void relaunch(const std::string &main_lib) {
    if (JNIEnv *e = env()) call_static_void("relaunch", "(Ljava/lang/String;)V", jstr(e, main_lib));
    // o Java mata este processo depois de pedir o novo; se não matar em 5 s, sai assim mesmo
    std::this_thread::sleep_for(std::chrono::seconds(5));
    _exit(0);
}

} // namespace vt::droid

// a janela comum dos renderizadores (gxm_window.h), na versão do celular: não tem título, borda nem mouse
namespace gvk::win {
bool create(const wchar_t *) { return true; }
void *hwnd() { return nullptr; }
void pump() {}
bool take_resized() { return g_resized.exchange(false); }
bool take_capture() { return false; }
void mark_resized() { g_resized = true; }
void presented() {}
bool closed() { return false; }
// "Quit to Desktop" do menu de pausa: no celular, volta ao lançador (GameActivity.sairDoJogo)
void request_close() { vt::droid::sair_do_jogo(); }
bool rect(int &x, int &y, int &w, int &h) {
    x = y = 0, w = g_w, h = g_h;
    return w > 0 && h > 0;
}
bool pointer(float &nx, float &ny) { return vt::droid::pointer(nx, ny); }
bool focused() { return g_visible; }
void notify(const std::string &text) { vt::droid::notify(text); }
void request_borderless(bool) {}
bool borderless() { return true; }
} // namespace gvk::win

// controles (host_input.h): o Java já somou a tela e o controle físico
namespace hostin {
State poll() {
    const vt::droid::Pad p = vt::droid::pad();
    State s;
    s.buttons = p.buttons, s.lx = p.lx, s.ly = p.ly, s.rx = p.rx, s.ry = p.ry, s.active = p.active;
    return s;
}
} // namespace hostin

// ============================================================= o que o Java chama
#define JF(name) extern "C" JNIEXPORT void JNICALL Java_com_luisxl_gowcollection_GameActivity_##name

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

JF(nativeSetenv)(JNIEnv *e, jclass, jstring k, jstring v) {
    const char *ks = e->GetStringUTFChars(k, nullptr), *vs = e->GetStringUTFChars(v, nullptr);
    setenv(ks, vs, 1);
    e->ReleaseStringUTFChars(k, ks), e->ReleaseStringUTFChars(v, vs);
}

// argumentos como os do vitart.exe; cwd = pasta gravável (cache de pipelines, capturas)
JF(nativeStart)(JNIEnv *e, jclass cls, jobjectArray args, jstring cwd) {
    static bool started = false;
    if (started) return;
    started = true;
    g_cls = static_cast<jclass>(e->NewGlobalRef(cls));
    const char *c = e->GetStringUTFChars(cwd, nullptr);
    chdir(c);
    e->ReleaseStringUTFChars(cwd, c);
    auto *argv = new std::vector<std::string>{"vitart"};
    for (jsize i = 0, n = e->GetArrayLength(args); i < n; i++) {
        auto s = static_cast<jstring>(e->GetObjectArrayElement(args, i));
        const char *u = e->GetStringUTFChars(s, nullptr);
        argv->emplace_back(u);
        e->ReleaseStringUTFChars(s, u);
        e->DeleteLocalRef(s);
    }
    // o jogo cria threads com pilhas de host normais, mas a principal faz recursão funda: 16 MB, como no PC
    pthread_attr_t at;
    pthread_attr_init(&at);
    pthread_attr_setstacksize(&at, 16u << 20);
    pthread_t th;
    pthread_create(&th, &at, [](void *p) -> void * {
        auto *a = static_cast<std::vector<std::string> *>(p);
        std::vector<char *> av;
        for (std::string &s : *a) av.push_back(s.data());
        av.push_back(nullptr);
        const int r = vitart_main((int)a->size(), av.data());
        vt::logf("vitart: o jogo terminou (%d)", r);
        call_static_void("onGameExit", "(Ljava/lang/String;)V", nullptr);
        return nullptr;
    }, argv);
    pthread_attr_destroy(&at);
}

// Surface nova (ou null quando vai ser destruída: aí espera o renderizador soltar a swapchain, até 3 s)
JF(nativeSurface)(JNIEnv *e, jclass, jobject surface) {
    ANativeWindow *nw = surface ? ANativeWindow_fromSurface(e, surface) : nullptr;
    std::unique_lock<std::mutex> lock(g_wm);
    if (g_win) ANativeWindow_release(g_win);
    g_win = nw;
    g_gen++;
    if (nw) g_w = ANativeWindow_getWidth(nw), g_h = ANativeWindow_getHeight(nw);
    if (!nw && g_holding) g_wcv.wait_for(lock, std::chrono::seconds(3), [] { return g_ack >= g_gen; });
}

JF(nativeSurfaceSize)(JNIEnv *, jclass, jint w, jint h) {
    g_w = w, g_h = h;
    g_resized = true;
}

JF(nativeVisible)(JNIEnv *, jclass, jboolean on) {
    g_visible = on;
    hostaudio::pause(!on);
}

JF(nativeRefreshRate)(JNIEnv *, jclass, jint hz) { g_hz = hz > 0 ? hz : 60; }

// botões e analógicos já somados (tela + controle); active = 2 quando foi o controle físico
JF(nativePad)(JNIEnv *, jclass, jint buttons, jint lx, jint ly, jint rx, jint ry, jint active) {
    g_buttons = (uint32_t)buttons;
    g_sticks = (uint32_t)(lx & 255) | (uint32_t)(ly & 255) << 8 | (uint32_t)(rx & 255) << 16 | (uint32_t)(ry & 255) << 24;
    if (active) g_active = (uint8_t)active;
}

// dedo na imagem do jogo (fora dos botões), normalizado 0..1
JF(nativeTouch)(JNIEnv *, jclass, jboolean down, jfloat nx, jfloat ny) {
    g_tx = nx, g_ty = ny;
    g_touch = down;
}

// toque na tela (pixels da superfície): se caiu no aviso de troféu, ele abre com a descrição e o toque é dele
extern "C" JNIEXPORT jboolean JNICALL Java_com_luisxl_gowcollection_GameActivity_nativeTrophyTouch(JNIEnv *, jclass, jint x, jint y) {
    return gvk::ovl::trophy_touch(x, y) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL Java_com_luisxl_gowcollection_GameActivity_nativeTrophyTop(JNIEnv *, jclass, jfloat frac) {
    gvk::ovl::trophy_top(frac);
}

JF(nativeKey)(JNIEnv *, jclass, jint vk, jboolean down) {
    if (vk > 0 && vk < 256) g_keys[vk] = down;
}

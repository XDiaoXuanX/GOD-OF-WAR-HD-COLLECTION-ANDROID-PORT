// o que o runtime pede pro lado Android (jni.cpp guarda o estado que o Java manda e chama o Java quando precisa)
#pragma once
#include <cstdint>
#include <string>
#include <vector>

struct ANativeWindow;

namespace vt::droid {

// Superfície do SurfaceView. A thread que apresenta pergunta com a geração que está usando: se o Java trocou a
// janela, recebe a nova (com referência própria, ou nullptr se sumiu) e a geração nova; depois de largar a antiga
// avisa com window_released(gen), que é o que o surfaceDestroyed do Java espera para seguir.
ANativeWindow *window_take(uint32_t have, uint32_t &gen);
void window_released(uint32_t gen);
bool window_visible();              // app em primeiro plano com superfície
void window_size(int &w, int &h);
int refresh_rate(); // Hz da tela (Display.getRefreshRate)

// toque fora dos botões na tela: dedo no painel frontal do Vita, normalizado sobre a imagem do jogo
bool pointer(float &nx, float &ny);
// controles: botões da tela + controle físico, já somados pelo Java (bits do SceCtrlData)
struct Pad {
    uint32_t buttons = 0;
    uint8_t lx = 128, ly = 128, rx = 128, ry = 128;
    uint8_t active = 0; // 2 = controle físico mexeu (ícones do PlayStation)
};
Pad pad();
// teclado e mouse físicos, nos códigos VK_ do Windows (plat.h)
bool key_down(int vk);

void play_sound(const std::string &wav);
void notify(const std::string &text);
// o jogo apresentou quadros suficientes: o driver em teste serve (Drivers.sessaoPronta no Java)
void session_ready();
void sair_do_jogo(); // volta ao lançador (o "Quit to Desktop" do menu de pausa)
// aviso de troféu desenhado pelo Java (Canvas), em BGRA; false se não deu
bool render_banner(const std::vector<uint8_t> &icon_png, char tier, const std::string &name, const std::string &detail, uint32_t screen_h,
                   std::vector<uint32_t> &px, uint32_t &w, uint32_t &h, uint32_t &radius);
// processo novo da Activity com outro módulo principal (LoadExec do Vita, Apply dos mods)
[[noreturn]] void relaunch(const std::string &main_lib);

} // namespace vt::droid

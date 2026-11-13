// aviso de troféu no celular: o Java desenha no Canvas (fonte e cantos do Android) e aqui só se acha, linha a
// linha, o trecho opaco que o renderizador copia
#include "trophy_banner.h"
#include "android/droid.h"

namespace gvk {

bool render_trophy_banner(const TrophyBanner &t, uint32_t screen_h, BannerImage &out) {
    uint32_t w = 0, h = 0, radius = 0;
    if (!vt::droid::render_banner(t.icon_png, t.tier, t.name, t.expanded ? t.detail : std::string(), screen_h, out.px, w, h, radius) || !w || !h) return false;
    out.w = w, out.h = h, out.radius = radius;
    out.row_from.assign(h, 0);
    out.row_to.assign(h, w);
    for (uint32_t y = 0; y < h; y++) {
        const uint32_t *row = &out.px[(size_t)y * w];
        uint32_t a = 0, b = w;
        while (a < w && (row[a] >> 24) == 0) a++;
        while (b > a && (row[b - 1] >> 24) == 0) b--;
        out.row_from[y] = a, out.row_to[y] = b;
    }
    return true;
}

} // namespace gvk

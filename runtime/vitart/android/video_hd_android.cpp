// cenas remasterizadas: no PC vêm do ffmpeg.exe (video_hd.cpp); no celular ainda não tem decodificador, então o
// jogo toca o próprio Bink, como no Vita
#include "video_hd.h"

namespace vt {

void video_hd_open(const std::string &, uint32_t, uint32_t, uint32_t, uint32_t, uint32_t) {}
void video_hd_close() {}
bool video_hd_frame(int64_t, const uint8_t *&, uint32_t &, uint32_t &) { return false; }
bool video_hd_active() { return false; }

} // namespace vt

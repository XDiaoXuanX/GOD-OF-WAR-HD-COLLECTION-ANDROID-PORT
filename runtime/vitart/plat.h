// fora do Windows (Android): os nomes do CRT da Microsoft que o runtime usa, apontando pros do POSIX
#pragma once

#ifndef _WIN32
#include <cstdio>
#include <ctime>
#include <fcntl.h>
#include <strings.h>
#include <sys/stat.h>
#include <unistd.h>

#define _stricmp strcasecmp
#define _strnicmp strncasecmp
#define _fseeki64 fseeko
#define _ftelli64 ftello
#define _lseeki64 lseek
#define _open open
#define _read read
#define _write write
#define _close close
#define _O_BINARY 0
#define _O_RDONLY O_RDONLY
#define _O_WRONLY O_WRONLY
#define _O_RDWR O_RDWR
#define _O_APPEND O_APPEND
#define _O_CREAT O_CREAT
#define _O_TRUNC O_TRUNC
#define _O_EXCL O_EXCL
#define _S_IREAD S_IRUSR
#define _S_IWRITE (S_IWUSR | S_IRGRP | S_IROTH)

inline int localtime_s(struct tm *t, const time_t *tt) { return localtime_r(tt, t) ? 0 : 1; }
inline int gmtime_s(struct tm *t, const time_t *tt) { return gmtime_r(tt, t) ? 0 : 1; }

// códigos de tecla do Windows: o mapa de teclado do jogo (games/gow) é escrito com eles e o Java do Android
// traduz o teclado físico para esses mesmos números
enum : int {
    VK_LBUTTON = 0x01, VK_RBUTTON = 0x02, VK_MBUTTON = 0x04, VK_BACK = 0x08, VK_TAB = 0x09, VK_RETURN = 0x0D,
    VK_SHIFT = 0x10, VK_CONTROL = 0x11, VK_ESCAPE = 0x1B, VK_SPACE = 0x20, VK_LEFT = 0x25, VK_UP = 0x26,
    VK_RIGHT = 0x27, VK_DOWN = 0x28, VK_LSHIFT = 0xA0, VK_RSHIFT = 0xA1, VK_LCONTROL = 0xA2, VK_RCONTROL = 0xA3,
    // os do mapeamento de teclas (input_map.cpp), com os valores do Windows
    VK_XBUTTON1 = 0x05, VK_XBUTTON2 = 0x06, VK_MENU = 0x12, VK_CAPITAL = 0x14, VK_PRIOR = 0x21, VK_NEXT = 0x22,
    VK_END = 0x23, VK_HOME = 0x24, VK_INSERT = 0x2D, VK_DELETE = 0x2E, VK_NUMPAD0 = 0x60, VK_NUMPAD1, VK_NUMPAD2,
    VK_NUMPAD3, VK_NUMPAD4, VK_NUMPAD5, VK_NUMPAD6, VK_NUMPAD7, VK_NUMPAD8, VK_NUMPAD9, VK_F1 = 0x70,
    VK_LMENU = 0xA4, VK_RMENU = 0xA5, VK_OEM_1 = 0xBA, VK_OEM_PLUS = 0xBB, VK_OEM_COMMA = 0xBC, VK_OEM_MINUS = 0xBD,
    VK_OEM_PERIOD = 0xBE, VK_OEM_2 = 0xBF, VK_OEM_3 = 0xC0, VK_OEM_4 = 0xDB, VK_OEM_5 = 0xDC, VK_OEM_6 = 0xDD,
    VK_OEM_7 = 0xDE,
};
#define MAX_PATH 4096

// nanossegundos pelo contador da CPU: o steady_clock do Android passa pelo clock_gettime, e os temporizadores
// do renderizador chamam isso milhares de vezes por quadro
#if defined(__aarch64__)
inline unsigned long long vt_now_ns() {
    unsigned long long c, f;
    __asm__ volatile("mrs %0, cntvct_el0" : "=r"(c));
    __asm__("mrs %0, cntfrq_el0" : "=r"(f));
    return (unsigned long long)((unsigned __int128)c * 1000000000u / f);
}
#endif

// VirtualQuery só para a janela do guest, que é o único uso no runtime e no jogo (varreduras de memória).
// "Confirmada" = página de região mapeada que o jogo já tocou (mincore), como no Windows; core.cpp responde.
#include <cstddef>
#include <cstdint>
namespace vt {
extern uint8_t *g_guest; // vitart.h
bool query_guest(uint64_t addr, uint64_t &start, uint64_t &end, bool &committed);
}
#define MEM_COMMIT 0x1000u
#define MEM_RESERVE 0x2000u
#define PAGE_NOACCESS 0x01u
#define PAGE_READWRITE 0x04u
#define PAGE_GUARD 0x100u
struct MEMORY_BASIC_INFORMATION {
    void *BaseAddress;
    size_t RegionSize;
    uint32_t State, Protect;
};
inline size_t VirtualQuery(const void *p, MEMORY_BASIC_INFORMATION *mi, size_t) {
    uint8_t *b = vt::g_guest;
    const uint8_t *q = static_cast<const uint8_t *>(p);
    uint64_t s, e;
    bool c;
    if (!b || q < b || q >= b + (1ull << 32) || !vt::query_guest((uint64_t)(q - b), s, e, c)) return 0;
    mi->BaseAddress = b + s;
    mi->RegionSize = (size_t)(e - s);
    mi->State = c ? MEM_COMMIT : MEM_RESERVE;
    mi->Protect = c ? PAGE_READWRITE : PAGE_NOACCESS;
    return sizeof *mi;
}
#endif

// WinCryptoOperations.h no Android (gow-android): a mesma semântica do arquivo do Windows (vita-recomp-lab), sem AES-NI
// nem BCrypt. AES-128 pelas instruções de criptografia do ARMv8 (AESE/AESMC; todo celular arm64 atual tem) e, sem
// elas, em software; SHA-1, SHA-256 e HMAC em software. Tamanho da chave AES em bits (128), do HMAC em bytes; CBC
// devolve no iv o último bloco cifrado; CTR avança o contador em size / 16.
#include "WinCryptoOperations.h"

#include <cstdint>
#include <cstring>
#include <vector>

#if defined(__aarch64__)
#include <arm_neon.h>
#include <sys/auxv.h>
#ifndef HWCAP_AES
#define HWCAP_AES (1 << 3)
#endif
#endif

namespace {

const uint8_t kSbox[256] = {
    0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76, 0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59,
    0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0, 0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1,
    0x71, 0xd8, 0x31, 0x15, 0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75, 0x09, 0x83,
    0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84, 0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b,
    0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf, 0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c,
    0x9f, 0xa8, 0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2, 0xcd, 0x0c, 0x13, 0xec,
    0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73, 0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee,
    0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb, 0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
    0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08, 0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6,
    0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a, 0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9,
    0x86, 0xc1, 0x1d, 0x9e, 0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf, 0x8c, 0xa1,
    0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16};
uint8_t g_inv[256];
bool g_inv_pronto = false;

void inv_sbox() {
    if (g_inv_pronto) return;
    for (int i = 0; i < 256; i++) g_inv[kSbox[i]] = (uint8_t)i;
    g_inv_pronto = true;
}

inline uint8_t xt(uint8_t x) { return (uint8_t)((x << 1) ^ ((x & 0x80) ? 0x1b : 0)); }
inline uint8_t mul(uint8_t a, uint8_t b) {
    uint8_t r = 0;
    while (b) {
        if (b & 1) r ^= a;
        a = xt(a), b >>= 1;
    }
    return r;
}

bool tem_aes_arm() {
#if defined(__aarch64__)
    static const bool v = (getauxval(AT_HWCAP) & HWCAP_AES) != 0;
    return v;
#else
    return false;
#endif
}

// ---- AES em software (FIPS-197), com as chaves de rodada de cifrar
void sw_encrypt(const uint8_t k[11][16], const uint8_t *in, uint8_t *out) {
    uint8_t s[16];
    for (int i = 0; i < 16; i++) s[i] = in[i] ^ k[0][i];
    for (int r = 1; r <= 10; r++) {
        uint8_t t[16];
        for (int i = 0; i < 16; i++) t[i] = kSbox[s[i]];
        // ShiftRows (coluna-maior: byte i = linha i%4, coluna i/4)
        for (int c = 0; c < 4; c++)
            for (int l = 0; l < 4; l++) s[4 * c + l] = t[4 * ((c + l) % 4) + l];
        if (r != 10)
            for (int c = 0; c < 4; c++) {
                uint8_t *a = &s[4 * c];
                const uint8_t a0 = a[0], a1 = a[1], a2 = a[2], a3 = a[3];
                a[0] = (uint8_t)(xt(a0) ^ xt(a1) ^ a1 ^ a2 ^ a3);
                a[1] = (uint8_t)(a0 ^ xt(a1) ^ xt(a2) ^ a2 ^ a3);
                a[2] = (uint8_t)(a0 ^ a1 ^ xt(a2) ^ xt(a3) ^ a3);
                a[3] = (uint8_t)(xt(a0) ^ a0 ^ a1 ^ a2 ^ xt(a3));
            }
        for (int i = 0; i < 16; i++) s[i] ^= k[r][i];
    }
    std::memcpy(out, s, 16);
}

void sw_decrypt(const uint8_t k[11][16], const uint8_t *in, uint8_t *out) {
    inv_sbox();
    uint8_t s[16];
    for (int i = 0; i < 16; i++) s[i] = in[i] ^ k[10][i];
    for (int r = 9; r >= 0; r--) {
        uint8_t t[16];
        for (int c = 0; c < 4; c++) // InvShiftRows
            for (int l = 0; l < 4; l++) t[4 * ((c + l) % 4) + l] = s[4 * c + l];
        for (int i = 0; i < 16; i++) s[i] = g_inv[t[i]] ^ k[r][i];
        if (r != 0)
            for (int c = 0; c < 4; c++) {
                uint8_t *a = &s[4 * c];
                const uint8_t a0 = a[0], a1 = a[1], a2 = a[2], a3 = a[3];
                a[0] = (uint8_t)(mul(a0, 14) ^ mul(a1, 11) ^ mul(a2, 13) ^ mul(a3, 9));
                a[1] = (uint8_t)(mul(a0, 9) ^ mul(a1, 14) ^ mul(a2, 11) ^ mul(a3, 13));
                a[2] = (uint8_t)(mul(a0, 13) ^ mul(a1, 9) ^ mul(a2, 14) ^ mul(a3, 11));
                a[3] = (uint8_t)(mul(a0, 11) ^ mul(a1, 13) ^ mul(a2, 9) ^ mul(a3, 14));
            }
    }
    std::memcpy(out, s, 16);
}

void ctr_add(unsigned char *iv, uint64_t add) {
    for (int i = 15; i >= 0; i--) {
        add += iv[i];
        iv[i] = (unsigned char)add;
        add >>= 8;
    }
}

// ---- SHA-1 e SHA-256
inline uint32_t rol(uint32_t x, int n) { return (x << n) | (x >> (32 - n)); }
inline uint32_t ror(uint32_t x, int n) { return (x >> n) | (x << (32 - n)); }

std::vector<uint8_t> pad(const uint8_t *src, size_t n) {
    std::vector<uint8_t> m(src, src + n);
    m.push_back(0x80);
    while (m.size() % 64 != 56) m.push_back(0);
    const uint64_t bits = (uint64_t)n * 8;
    for (int i = 7; i >= 0; i--) m.push_back((uint8_t)(bits >> (8 * i)));
    return m;
}

void sha1_raw(const uint8_t *src, size_t n, uint8_t *dst) {
    uint32_t h[5] = {0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0};
    const std::vector<uint8_t> m = pad(src, n);
    for (size_t o = 0; o < m.size(); o += 64) {
        uint32_t w[80];
        for (int i = 0; i < 16; i++) w[i] = (uint32_t)m[o + 4 * i] << 24 | m[o + 4 * i + 1] << 16 | m[o + 4 * i + 2] << 8 | m[o + 4 * i + 3];
        for (int i = 16; i < 80; i++) w[i] = rol(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
        uint32_t a = h[0], b = h[1], c = h[2], d = h[3], e = h[4];
        for (int i = 0; i < 80; i++) {
            const uint32_t f = i < 20 ? (b & c) | (~b & d) : i < 40 ? b ^ c ^ d : i < 60 ? (b & c) | (b & d) | (c & d) : b ^ c ^ d;
            const uint32_t k = i < 20 ? 0x5A827999 : i < 40 ? 0x6ED9EBA1 : i < 60 ? 0x8F1BBCDC : 0xCA62C1D6;
            const uint32_t t = rol(a, 5) + f + e + k + w[i];
            e = d, d = c, c = rol(b, 30), b = a, a = t;
        }
        h[0] += a, h[1] += b, h[2] += c, h[3] += d, h[4] += e;
    }
    for (int i = 0; i < 5; i++)
        for (int j = 0; j < 4; j++) dst[4 * i + j] = (uint8_t)(h[i] >> (24 - 8 * j));
}

void sha256_raw(const uint8_t *src, size_t n, uint8_t *dst) {
    static const uint32_t K[64] = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be,
        0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa,
        0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85,
        0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
        0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f,
        0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};
    uint32_t h[8] = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};
    const std::vector<uint8_t> m = pad(src, n);
    for (size_t o = 0; o < m.size(); o += 64) {
        uint32_t w[64];
        for (int i = 0; i < 16; i++) w[i] = (uint32_t)m[o + 4 * i] << 24 | m[o + 4 * i + 1] << 16 | m[o + 4 * i + 2] << 8 | m[o + 4 * i + 3];
        for (int i = 16; i < 64; i++) {
            const uint32_t s0 = ror(w[i - 15], 7) ^ ror(w[i - 15], 18) ^ (w[i - 15] >> 3);
            const uint32_t s1 = ror(w[i - 2], 17) ^ ror(w[i - 2], 19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16] + s0 + w[i - 7] + s1;
        }
        uint32_t a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
        for (int i = 0; i < 64; i++) {
            const uint32_t S1 = ror(e, 6) ^ ror(e, 11) ^ ror(e, 25), ch = (e & f) ^ (~e & g);
            const uint32_t t1 = hh + S1 + ch + K[i] + w[i];
            const uint32_t S0 = ror(a, 2) ^ ror(a, 13) ^ ror(a, 22), mj = (a & b) ^ (a & c) ^ (b & c);
            const uint32_t t2 = S0 + mj;
            hh = g, g = f, f = e, e = d + t1, d = c, c = b, b = a, a = t1 + t2;
        }
        h[0] += a, h[1] += b, h[2] += c, h[3] += d, h[4] += e, h[5] += f, h[6] += g, h[7] += hh;
    }
    for (int i = 0; i < 8; i++)
        for (int j = 0; j < 4; j++) dst[4 * i + j] = (uint8_t)(h[i] >> (24 - 8 * j));
}

template <size_t N>
void hmac(void (*fn)(const uint8_t *, size_t, uint8_t *), const uint8_t *key, size_t kn, const uint8_t *src, size_t n, uint8_t *dst) {
    uint8_t k[64] = {};
    if (kn > 64) fn(key, kn, k);
    else std::memcpy(k, key, kn);
    std::vector<uint8_t> a(64 + n), b(64 + N);
    for (int i = 0; i < 64; i++) a[i] = k[i] ^ 0x36, b[i] = k[i] ^ 0x5c;
    std::memcpy(a.data() + 64, src, n);
    fn(a.data(), a.size(), b.data() + 64);
    fn(b.data(), b.size(), dst);
}

} // namespace

Aes128::Aes128(const unsigned char *key) {
    // expansão da chave (FIPS-197): 11 chaves de rodada, coluna-maior como no texto
    static const uint8_t rcon[10] = {0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36};
    std::memcpy(enc[0], key, 16);
    for (int r = 1; r <= 10; r++) {
        const uint8_t *p = enc[r - 1];
        uint8_t t[4] = {kSbox[p[13]], kSbox[p[14]], kSbox[p[15]], kSbox[p[12]]};
        t[0] ^= rcon[r - 1];
        for (int i = 0; i < 4; i++) enc[r][i] = p[i] ^ t[i];
        for (int i = 4; i < 16; i++) enc[r][i] = p[i] ^ enc[r][i - 4];
    }
    // chaves de decifrar no formato AESD/AESIMC (o mesmo arranjo do AES-NI do Windows)
    std::memcpy(dec[0], enc[10], 16);
    std::memcpy(dec[10], enc[0], 16);
    for (int i = 1; i < 10; i++) {
        const uint8_t *k = enc[10 - i];
        for (int c = 0; c < 4; c++) {
            const uint8_t a0 = k[4 * c], a1 = k[4 * c + 1], a2 = k[4 * c + 2], a3 = k[4 * c + 3];
            dec[i][4 * c] = (uint8_t)(mul(a0, 14) ^ mul(a1, 11) ^ mul(a2, 13) ^ mul(a3, 9));
            dec[i][4 * c + 1] = (uint8_t)(mul(a0, 9) ^ mul(a1, 14) ^ mul(a2, 11) ^ mul(a3, 13));
            dec[i][4 * c + 2] = (uint8_t)(mul(a0, 13) ^ mul(a1, 9) ^ mul(a2, 14) ^ mul(a3, 11));
            dec[i][4 * c + 3] = (uint8_t)(mul(a0, 11) ^ mul(a1, 13) ^ mul(a2, 9) ^ mul(a3, 14));
        }
    }
}

#if defined(__aarch64__)
__attribute__((target("aes"))) static void arm_encrypt(const uint8_t enc[11][16], const unsigned char *in, unsigned char *out, size_t blocks) {
    uint8x16_t k[11];
    for (int i = 0; i < 11; i++) k[i] = vld1q_u8(enc[i]);
    for (size_t b = 0; b < blocks; b++) {
        uint8x16_t x = vld1q_u8(in + 16 * b);
        for (int r = 0; r < 9; r++) x = vaesmcq_u8(vaeseq_u8(x, k[r]));
        x = veorq_u8(vaeseq_u8(x, k[9]), k[10]);
        vst1q_u8(out + 16 * b, x);
    }
}
__attribute__((target("aes"))) static void arm_decrypt(const uint8_t dec[11][16], const unsigned char *in, unsigned char *out, size_t blocks) {
    uint8x16_t k[11];
    for (int i = 0; i < 11; i++) k[i] = vld1q_u8(dec[i]);
    for (size_t b = 0; b < blocks; b++) {
        uint8x16_t x = vld1q_u8(in + 16 * b);
        for (int r = 0; r < 9; r++) x = vaesimcq_u8(vaesdq_u8(x, k[r]));
        x = veorq_u8(vaesdq_u8(x, k[9]), k[10]);
        vst1q_u8(out + 16 * b, x);
    }
}
#endif

void Aes128::encrypt(const unsigned char *in, unsigned char *out, size_t blocks) const {
#if defined(__aarch64__)
    if (tem_aes_arm()) return arm_encrypt(enc, in, out, blocks);
#endif
    for (size_t b = 0; b < blocks; b++) sw_encrypt(enc, in + 16 * b, out + 16 * b);
}

void Aes128::decrypt(const unsigned char *in, unsigned char *out, size_t blocks) const {
#if defined(__aarch64__)
    if (tem_aes_arm()) return arm_decrypt(dec, in, out, blocks);
#endif
    for (size_t b = 0; b < blocks; b++) sw_decrypt(enc, in + 16 * b, out + 16 * b);
}

int WinCryptoOperations::aes_cbc_encrypt(const unsigned char *src, unsigned char *dst, int size, const unsigned char *key, int key_size,
                                         unsigned char *iv) const {
    if (key_size != 128 || size % 16) return -1;
    const Aes128 a(key);
    unsigned char c[16];
    std::memcpy(c, iv, 16);
    for (int o = 0; o < size; o += 16) {
        unsigned char x[16];
        for (int i = 0; i < 16; i++) x[i] = src[o + i] ^ c[i];
        a.encrypt(x, c, 1);
        std::memcpy(dst + o, c, 16);
    }
    if (size) std::memcpy(iv, c, 16);
    return 0;
}

int WinCryptoOperations::aes_cbc_decrypt(const unsigned char *src, unsigned char *dst, int size, const unsigned char *key, int key_size,
                                         unsigned char *iv) const {
    if (key_size != 128 || size % 16) return -1;
    if (!size) return 0;
    const Aes128 a(key);
    std::vector<unsigned char> tmp(src, src + size); // src e dst podem ser o mesmo buffer
    a.decrypt(tmp.data(), dst, size / 16);
    for (int i = 0; i < 16; i++) dst[i] ^= iv[i];
    for (int o = 16; o < size; o++) dst[o] ^= tmp[o - 16];
    std::memcpy(iv, tmp.data() + size - 16, 16);
    return 0;
}

int WinCryptoOperations::aes_ctr_encrypt(const unsigned char *src, unsigned char *dst, int size, const unsigned char *key, int key_size,
                                         unsigned char *iv) {
    if (key_size != 128) return -1;
    const Aes128 a(key);
    unsigned char ctr[16], ks[16];
    std::memcpy(ctr, iv, 16);
    for (int o = 0; o < size; o += 16) {
        a.encrypt(ctr, ks, 1);
        const int n = size - o < 16 ? size - o : 16;
        for (int i = 0; i < n; i++) dst[o + i] = src[o + i] ^ ks[i];
        ctr_add(ctr, 1);
    }
    ctr_add(iv, (uint64_t)size / 16);
    return 0;
}

int WinCryptoOperations::aes_ctr_decrypt(const unsigned char *src, unsigned char *dst, int size, const unsigned char *key, int key_size,
                                         unsigned char *iv) {
    return aes_ctr_encrypt(src, dst, size, key, key_size, iv);
}

int WinCryptoOperations::aes_ecb_encrypt(const unsigned char *src, unsigned char *dst, int size, const unsigned char *key, int key_size) const {
    if (key_size != 128 || size % 16) return -1;
    Aes128(key).encrypt(src, dst, size / 16);
    return 0;
}

int WinCryptoOperations::aes_ecb_decrypt(const unsigned char *src, unsigned char *dst, int size, const unsigned char *key, int key_size) const {
    if (key_size != 128 || size % 16) return -1;
    Aes128(key).decrypt(src, dst, size / 16);
    return 0;
}

// AES-CMAC (RFC 4493)
int WinCryptoOperations::aes_cmac(const unsigned char *src, unsigned char *dst, int size, const unsigned char *key, int key_size) const {
    if (key_size != 128) return -1;
    const Aes128 a(key);
    unsigned char L[16] = {}, K1[16], K2[16];
    a.encrypt(L, L, 1);
    auto dbl = [](const unsigned char *in, unsigned char *out) {
        const unsigned char carry = in[0] & 0x80;
        for (int i = 0; i < 15; i++) out[i] = (unsigned char)(in[i] << 1 | in[i + 1] >> 7);
        out[15] = (unsigned char)(in[15] << 1);
        if (carry) out[15] ^= 0x87;
    };
    dbl(L, K1);
    dbl(K1, K2);
    const int n = size ? (size + 15) / 16 : 1;
    const bool full = size && size % 16 == 0;
    unsigned char x[16] = {}, y[16];
    for (int b = 0; b < n - 1; b++) {
        for (int i = 0; i < 16; i++) y[i] = x[i] ^ src[16 * b + i];
        a.encrypt(y, x, 1);
    }
    unsigned char last[16] = {};
    const int rem = size - 16 * (n - 1);
    std::memcpy(last, src + 16 * (n - 1), rem);
    if (full) {
        for (int i = 0; i < 16; i++) last[i] ^= K1[i];
    } else {
        last[rem] = 0x80;
        for (int i = 0; i < 16; i++) last[i] ^= K2[i];
    }
    for (int i = 0; i < 16; i++) y[i] = x[i] ^ last[i];
    a.encrypt(y, dst, 1);
    return 0;
}

int WinCryptoOperations::sha1(const unsigned char *src, unsigned char *dst, int size) const {
    sha1_raw(src, (size_t)size, dst);
    return 0;
}
int WinCryptoOperations::sha256(const unsigned char *src, unsigned char *dst, int size) const {
    sha256_raw(src, (size_t)size, dst);
    return 0;
}
int WinCryptoOperations::hmac_sha1(const unsigned char *src, unsigned char *dst, int size, const unsigned char *key, int key_size) const {
    hmac<20>(sha1_raw, key, (size_t)key_size, src, (size_t)size, dst);
    return 0;
}
int WinCryptoOperations::hmac_sha256(const unsigned char *src, unsigned char *dst, int size, const unsigned char *key, int key_size) const {
    hmac<32>(sha256_raw, key, (size_t)key_size, src, (size_t)size, dst);
    return 0;
}

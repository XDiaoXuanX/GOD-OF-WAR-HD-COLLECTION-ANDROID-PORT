"""Base dos botões da tela: disco de bronze com o meandro grego gravado no aro e o centro de obsidiana côncavo,
renderizado em relevo (mapa de altura -> normais -> luz de metal). Arte nossa, vai no APK.

    python tools/render_botao.py <pasta de saída>      (gera base_solta.png e base_apertada.png, 512x512)
"""
import sys
import numpy as np
from PIL import Image, ImageFilter

N = 512
SS = 2  # superamostragem: renderiza em 1024 e reduz (bordas lisas)
R = N * SS


def ruido(tam, escala, sem):
    rng = np.random.default_rng(sem)
    base = rng.random((tam // escala + 2, tam // escala + 2)).astype(np.float32)
    im = Image.fromarray((base * 255).astype(np.uint8)).resize((tam, tam), Image.BICUBIC)
    return np.asarray(im, np.float32) / 255.0


def meandro(ang, rad):
    """1 onde o traço do meandro passa, num gomo de (ângulo 0..1, raio 0..1); 24 gomos na volta."""
    gomos = 24
    u = (ang * gomos) % 1.0
    v = rad
    t = 0.13  # espessura do traço
    def h(u0, u1, v0):  # trecho horizontal (ao longo do ângulo)
        return (u >= u0) & (u <= u1) & (np.abs(v - v0) < t / 2)
    def vt(u0, v0, v1):  # trecho vertical (ao longo do raio)
        return (np.abs(u - u0) < t * 0.5 * 0.9) & (v >= v0) & (v <= v1)
    m = (vt(0.06, 0.1, 0.9) | h(0.06, 0.78, 0.9) | vt(0.78, 0.35, 0.9) | h(0.36, 0.78, 0.35) |
         vt(0.36, 0.35, 0.62) | h(0.36, 0.56, 0.62) | h(0.06, 1.0, 0.1))
    return m.astype(np.float32)


def render(apertado):
    y, x = np.mgrid[0:R, 0:R].astype(np.float32)
    cx = cy = (R - 1) / 2
    dx, dy = (x - cx) / (R * 0.5), (y - cy) / (R * 0.5)
    r = np.sqrt(dx * dx + dy * dy)
    ang = (np.arctan2(dy, dx) / (2 * np.pi)) % 1.0
    R_OUT, R_IN, R_BAND0, R_BAND1, R_DISH = 0.90, 0.70, 0.745, 0.865, 0.68

    # altura: aro arredondado, faixa do meandro rebaixada com o traço em relevo, lábio interno, centro côncavo
    h = np.zeros((R, R), np.float32)
    aro = (r >= R_IN) & (r <= R_OUT)
    perfil = np.clip(1 - ((r - (R_IN + R_OUT) / 2) / ((R_OUT - R_IN) / 2)) ** 2, 0, 1)
    h += np.where(aro, 0.55 + 0.45 * np.sqrt(perfil), 0)
    banda = (r >= R_BAND0) & (r <= R_BAND1)
    vb = (r - R_BAND0) / (R_BAND1 - R_BAND0)
    traco = meandro(ang, vb) * banda
    h -= banda * 0.28
    h += traco * 0.24
    centro = r < R_DISH
    h += np.where(centro, 0.10 + 0.32 * (r / R_DISH) ** 2, 0)  # côncavo: sobe para a borda
    # rebites nas diagonais
    reb = np.zeros_like(h)
    for a in (0.125, 0.375, 0.625, 0.875):
        rx, ry = np.cos(a * 2 * np.pi) * 0.885, np.sin(a * 2 * np.pi) * 0.885
        d = np.sqrt((dx - rx) ** 2 + (dy - ry) ** 2)
        reb = np.maximum(reb, np.clip(1 - (d / 0.032) ** 2, 0, 1))
    h += np.sqrt(reb) * 0.18
    gasto = ruido(R, 6, 3) * 0.035 + ruido(R, 40, 5) * 0.03  # metal batido
    h += np.where(aro, gasto, ruido(R, 18, 9) * 0.02 * centro)

    # normais
    gy, gx = np.gradient(h * R * 0.035)
    nz = np.ones_like(h)
    n = np.stack([-gx, -gy, nz], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    L = np.array([-0.55, -0.65, 0.52], np.float32)
    L /= np.linalg.norm(L)
    V = np.array([0, 0, 1], np.float32)
    H = (L + V) / np.linalg.norm(L + V)
    ndl = np.clip((n * L).sum(-1), 0, 1)
    ndh = np.clip((n * H).sum(-1), 0, 1)

    # material: bronze no aro (sulcos mais escuros), obsidiana no centro
    bronze = np.array([0.55, 0.34, 0.15], np.float32)
    ouro = np.array([1.0, 0.82, 0.48], np.float32)
    obs = np.array([0.045, 0.040, 0.050], np.float32)
    sulco = banda & (traco < 0.5)
    alb = np.where(aro[..., None], bronze, obs)
    alb = np.where(sulco[..., None], bronze * 0.45, alb)
    alb = alb * (0.85 + 0.3 * ruido(R, 24, 7)[..., None])
    spec_aro = ndh ** 28 * 1.1
    spec_obs = ndh ** 90 * 0.9
    spec = np.where(aro, spec_aro, spec_obs)[..., None]
    spec_cor = np.where(aro[..., None], ouro, np.array([0.9, 0.9, 1.0], np.float32))
    amb = 0.22 + 0.18 * n[..., 2:3]
    cor = alb * (amb + 0.95 * ndl[..., None]) + spec * spec_cor
    # reflexo de ambiente na obsidiana (faixa clara em cima)
    refl = np.clip(1 - ((dy + 0.38) / 0.16) ** 2, 0, 1) * np.clip(1 - (np.abs(dx) / 0.5) ** 2, 0, 1) * centro
    cor += refl[..., None] * 0.10
    # oclusão perto do lábio interno
    ao = np.clip(1 - np.exp(-((R_DISH - r) / 0.05) ** 2) * 0.55, 0, 1) * centro + (1 - centro)
    cor *= ao[..., None]

    if apertado:  # brasa: o meandro e a borda de dentro acendem
        brasa = np.array([1.0, 0.42, 0.08], np.float32)
        quente = np.array([1.0, 0.78, 0.35], np.float32)
        brilho_sulco = sulco * (0.7 + 0.3 * ruido(R, 10, 11))
        cor = cor + brilho_sulco[..., None] * brasa * 1.1
        borda = np.exp(-((R_DISH - r) / 0.12) ** 2) * centro
        cor = cor + borda[..., None] * brasa * 0.85 + (borda ** 3)[..., None] * quente * 0.5
        cor = cor * 1.08

    cor = np.clip(cor, 0, 1)
    alfa = np.clip((R_OUT + 0.004 - r) / 0.008, 0, 1)
    rgba = np.dstack([cor, alfa])
    im = Image.fromarray((rgba * 255).astype(np.uint8), 'RGBA')
    # sombra projetada embaixo (fora do disco)
    sombra = Image.new('L', (R, R), 0)
    sm = (np.clip((R_OUT - np.sqrt(dx ** 2 + (dy - 0.05) ** 2)) / 0.01, 0, 1) * 200).astype(np.uint8)
    sombra = Image.fromarray(sm).filter(ImageFilter.GaussianBlur(R * 0.02))
    fundo = Image.new('RGBA', (R, R), (0, 0, 0, 0))
    fundo.putalpha(sombra)
    if apertado:  # halo de brasa em volta
        halo = (np.clip(1 - (r - R_OUT) / 0.09, 0, 1) * (r > R_OUT * 0.95) * 150).astype(np.uint8)
        hl = Image.fromarray(halo).filter(ImageFilter.GaussianBlur(R * 0.025))
        laranja = Image.new('RGBA', (R, R), (255, 110, 30, 0))
        laranja.putalpha(hl)
        fundo = Image.alpha_composite(fundo, laranja)
    out = Image.alpha_composite(fundo, im)
    return out.resize((N, N), Image.LANCZOS)


if __name__ == '__main__':
    saida = sys.argv[1]
    render(False).save(saida + '/base_solta.png', optimize=True)
    render(True).save(saida + '/base_apertada.png', optimize=True)
    print('ok')

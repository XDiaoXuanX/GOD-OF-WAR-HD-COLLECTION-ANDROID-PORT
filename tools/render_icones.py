"""Ícones de ação dos botões da tela, em relevo: moldura de ouro chanfrada com esmalte colorido embutido
(cloisonné), luz de cima à esquerda como na base de bronze (render_botao.py). Desenhos nossos, vão no APK.

    python tools/render_icones.py <pasta de saída>     (gera icone_0.png .. icone_9.png, 256x256)

A ordem segue Icones.java: 0 golpe, 1 golpe forte, 2 agarrar, 3 pulo, 4 escudo, 5 chave, 6 magia, 7 runa,
8 pausa, 9 evolução.
"""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy.ndimage import distance_transform_edt, gaussian_filter

N = 256
SS = 4
R = N * SS
ESCALA = 0.80  # a caixa -1..1 do desenho ocupa 80% da imagem (folga para a sombra)

VERMELHO, AZUL, AMBAR, ROXO, CINZA = (0.80, 0.12, 0.08), (0.10, 0.32, 0.85), (0.85, 0.45, 0.06), (0.50, 0.20, 0.85), (0.40, 0.42, 0.46)


def px(x, y):
    return ((x * ESCALA + 1) * R / 2, (y * ESCALA + 1) * R / 2)


def poli(d, pts, cor=255):
    d.polygon([px(x, y) for x, y in pts], fill=cor)


def disco(d, cx, cy, r, cor=255):
    (x0, y0), (x1, y1) = px(cx - r, cy - r), px(cx + r, cy + r)
    d.ellipse([x0, y0, x1, y1], fill=cor)


def linha(d, pts, w, cor=255):
    pp = [px(x, y) for x, y in pts]
    lw = w * ESCALA * R / 2
    d.line(pp, fill=cor, width=int(lw), joint="curve")
    for x, y in pp:
        d.ellipse([x - lw / 2, y - lw / 2, x + lw / 2, y + lw / 2], fill=cor)


def arco(cx, cy, r, a0, a1, n=48):
    return [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))) for a in np.linspace(a0, a1, n)]


# ---- desenhos: máscara (onde há metal) e, opcional, sulcos (linhas gravadas por cima)
def golpe(d, g):
    # corte na diagonal: meia-lua que curva para baixo à esquerda, grossa no meio e fina nas pontas, com rastros
    n = 72
    fora, dentro = [], []
    for i in range(n + 1):
        t = i / n
        a = math.radians(80 + 110 * t)
        w = 0.40 * math.sin(math.pi * t) ** 0.9 + 0.012
        fora.append((0.64 + 1.18 * math.cos(a), -0.64 + 1.18 * math.sin(a)))
        dentro.append((0.64 + (1.18 - w) * math.cos(a), -0.64 + (1.18 - w) * math.sin(a)))
    poli(d, fora + dentro[::-1])
    for r, a0, a1, w in ((0.62, 108, 162, 0.08), (0.42, 118, 152, 0.06)):
        linha(d, arco(0.64, -0.64, r, a0, a1), w)


def golpe_forte(d, g):
    # estouro de impacto irregular (16 pontas desiguais) com o miolo redondo
    rng = np.random.default_rng(7)
    pts = []
    for i in range(16):
        a = math.radians(-90 + i * 22.5 + rng.uniform(-5, 5))
        r = (0.80 + rng.uniform(0, 0.18)) if i % 2 == 0 else (0.50 + rng.uniform(-0.04, 0.06))
        if i % 4 == 2: r *= 0.86
        pts.append((r * math.cos(a), r * math.sin(a)))
    poli(d, pts)
    disco(g, 0, 0, 0.30)
    disco(g, 0, 0, 0.24, 0)


def agarrar(d, g):
    # mão em garra: palma, quatro dedos curvados para dentro e o polegar
    (x0, y0), (x1, y1) = px(-0.46, 0.02), px(0.40, 0.82)
    d.rounded_rectangle([x0, y0, x1, y1], radius=0.26 * ESCALA * R / 2, fill=255)
    for x, topo, curva in ((-0.34, -0.38, 0.10), (-0.11, -0.66, 0.12), (0.12, -0.72, 0.12), (0.33, -0.52, 0.10)):
        linha(d, [(x, 0.15), (x, topo + 0.18), (x + curva * 0.3, topo + 0.02), (x + curva, topo)], 0.19)
    linha(d, [(-0.40, 0.45), (-0.66, 0.18), (-0.70, -0.06)], 0.19)
    linha(g, [(-0.30, 0.50), (0.24, 0.50)], 0.04)


def pulo(d, g):
    for y in (-0.42, 0.06):
        linha(d, [(-0.58, y + 0.40), (0, y - 0.16), (0.58, y + 0.40)], 0.22)
    linha(d, arco(0, 1.55, 0.82, 238, 302), 0.12)


def escudo(d, g):
    # escudo redondo (hoplita): aro largo de ouro, faixa de esmalte e a bossa do meio
    disco(d, 0, 0, 0.90)
    disco(d, 0, 0, 0.30, 0)
    disco(d, 0, 0, 0.22)
    disco(g, 0, 0, 0.64)
    disco(g, 0, 0, 0.60, 0)


def chave(d, g):
    disco(d, -0.40, -0.40, 0.40)
    disco(d, -0.40, -0.40, 0.17, 0)
    linha(d, [(-0.16, -0.16), (0.78, 0.78)], 0.20)
    linha(d, [(0.36, 0.36), (0.18, 0.56)], 0.17)
    linha(d, [(0.58, 0.58), (0.40, 0.78)], 0.17)


def magia(d, g):
    poli(d, [(0.20, -0.98), (-0.56, 0.12), (-0.04, 0.12), (-0.24, 0.98), (0.58, -0.20), (0.06, -0.20)])


def runa(d, g):
    poli(d, [(0, -0.90), (0.66, 0), (0, 0.90), (-0.66, 0)])
    poli(d, [(0, -0.56), (0.40, 0), (0, 0.56), (-0.40, 0)], 0)
    linha(d, [(0, -0.40), (0, 0.40)], 0.16)


def pausa(d, g):
    for x0 in (-0.56, 0.16):
        (a, b), (c, e) = px(x0, -0.66), px(x0 + 0.40, 0.66)
        d.rounded_rectangle([a, b, c, e], radius=0.10 * ESCALA * R / 2, fill=255)


def evolucao(d, g):
    disco(d, 0, 0, 0.88)
    disco(d, 0, 0, 0.70, 0)
    linha(d, [(0, 0.44), (0, -0.40)], 0.18)
    linha(d, [(-0.32, -0.08), (0, -0.42), (0.32, -0.08)], 0.18)


ICONES = [(golpe, VERMELHO), (golpe_forte, VERMELHO), (agarrar, AMBAR), (pulo, AZUL), (escudo, (0.62, 0.14, 0.06)),
          (chave, AMBAR), (magia, ROXO), (runa, CINZA), (pausa, CINZA), (evolucao, CINZA)]


def render(fn, esmalte):
    m = Image.new("L", (R, R), 0)
    sul = Image.new("L", (R, R), 0)
    fn(ImageDraw.Draw(m), ImageDraw.Draw(sul))
    mask = np.asarray(m, np.float32) / 255
    sulco = np.asarray(sul, np.float32) / 255 * mask
    dist = distance_transform_edt(mask > 0.5).astype(np.float32) / R  # distância à borda, fração da imagem
    BISEL, FIO = 0.026, 0.036  # largura do chanfro de ouro; onde começa o esmalte
    h = np.clip(dist / BISEL, 0, 1)
    h = np.sqrt(h) * 0.9
    esm = np.clip((dist - FIO) / 0.006, 0, 1)  # dentro do fio de ouro: esmalte, um degrau abaixo
    h -= esm * 0.18
    h -= sulco * 0.25 * (1 - esm * 0.5)
    h = gaussian_filter(h, R * 0.0025) * (mask > 0.02)

    gy, gx = np.gradient(h * R * 0.03)
    n = np.dstack([-gx, -gy, np.ones_like(h)])
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    L = np.array([-0.55, -0.65, 0.52], np.float32)
    L /= np.linalg.norm(L)
    H = L + np.array([0, 0, 1], np.float32)
    H /= np.linalg.norm(H)
    ndl = np.clip((n * L).sum(-1), 0, 1)[..., None]
    ndh = np.clip((n * H).sum(-1), 0, 1)[..., None]

    ouro = np.array([0.86, 0.62, 0.26], np.float32)
    ouro_luz = np.array([1.0, 0.92, 0.66], np.float32)
    e = np.array(esmalte, np.float32)
    # esmalte: mais claro no meio da peça (profundidade do vidro), quase preto rente ao ouro
    prof = np.clip((dist - FIO) / 0.09, 0, 1)[..., None]
    cor_esm = e * (0.35 + 0.75 * prof)
    alb = np.where(esm[..., None] > 0.5, cor_esm, ouro)
    spec = np.where(esm[..., None] > 0.5, ndh ** 120 * 1.2, ndh ** 26 * 1.0)
    amb = 0.25 + 0.15 * n[..., 2:3]
    cor = alb * (amb + 0.9 * ndl) + spec * np.where(esm[..., None] > 0.5, 1.0, ouro_luz)
    # reflexo do vidro do esmalte: faixa clara na parte de cima
    y, x = np.mgrid[0:R, 0:R].astype(np.float32) / R
    cor += (esm * np.clip(1 - y * 2.2, 0, 1) * 0.10)[..., None]
    cor = np.clip(cor, 0, 1)

    rgba = np.dstack([cor, mask])
    im = Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")
    sombra = Image.fromarray((mask * 210).astype(np.uint8)).filter(ImageFilter.GaussianBlur(R * 0.014))
    desloca = Image.new("L", (R, R), 0)
    desloca.paste(sombra, (int(R * 0.012), int(R * 0.03)))
    fundo = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    fundo.putalpha(desloca)
    return Image.alpha_composite(fundo, im).resize((N, N), Image.LANCZOS)


if __name__ == "__main__":
    saida = sys.argv[1]
    for i, (fn, esm) in enumerate(ICONES):
        render(fn, esm).save("%s/icone_%d.png" % (saida, i), optimize=True)
    print("ok")

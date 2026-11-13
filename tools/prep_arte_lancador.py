"""Arte do lançador que não vem na pasta do jogo: as artes do GoW1 e do GoW2 e o logo grande da Collection (da pasta
gow-dump/arte_web/x4 do PC). Saem em build/ui-arte-lancador para copiar para <pasta do jogo>/data/ui-arte no celular;
nada disso vai no APK. Sem elas o lançador usa a arte-chave (chave.jpg) e o logo da abertura do LiveArea.

    python tools/prep_arte_lancador.py [pasta x4]
"""
import os
import sys

import numpy as np
from PIL import Image

AQUI = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
X4 = sys.argv[1] if len(sys.argv) > 1 else os.path.join(AQUI, "..", "gow-dump", "arte_web", "x4")
OUT = os.path.join(AQUI, "build", "ui-arte-lancador")
os.makedirs(OUT, exist_ok=True)
# fundo2 = Kratos em Atenas (GoW1), fundo1 = Kratos em Rodes (GoW2)
for src, dst in (("fundo2.png", "heroi_gow1.jpg"), ("fundo1.png", "heroi_gow2.jpg")):
    Image.open(os.path.join(X4, src)).convert("RGB").save(os.path.join(OUT, dst), quality=90)
# o PNG do logo traz uma sombra própria semitransparente que vira caixa sobre a arte: fica só o desenho
logo = np.asarray(Image.open(os.path.join(X4, "logo.png")).convert("RGBA")).copy()
logo[..., 3] = np.clip((logo[..., 3].astype(np.float32) - 110) * 255 / 145, 0, 255).astype(np.uint8)
im = Image.fromarray(logo)
im.crop(im.getbbox()).save(os.path.join(OUT, "logo_collection.png"), optimize=True)
for f in sorted(os.listdir(OUT)):
    print(f, os.path.getsize(os.path.join(OUT, f)))

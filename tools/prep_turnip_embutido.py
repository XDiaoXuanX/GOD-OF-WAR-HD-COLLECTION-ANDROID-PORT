"""Turnip embutidos no APK, os mesmos do DroidDeck (app/src/main/assets/graphics_driver/adrenotools-<id>.tzst, Mesa, MIT):
o .tzst (tar + zstd) vira assets/drivers/adrenotools-<id>.zip, que o Java abre sem zstd.

    python tools/prep_turnip_embutido.py [pasta do DroidDeck]    (padrão: ../_referencias/DroidDeck)
"""
import io
import os
import sys
import tarfile
import zipfile
from compression import zstd  # Python 3.14

AQUI = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DD = sys.argv[1] if len(sys.argv) > 1 else os.path.join(AQUI, "..", "_referencias", "DroidDeck")
SRC = os.path.join(DD, "app", "src", "main", "assets", "graphics_driver")
OUT = os.path.join(AQUI, "android", "app", "src", "main", "assets", "drivers")
os.makedirs(OUT, exist_ok=True)
for nome in sorted(os.listdir(SRC)):
    if not (nome.startswith("adrenotools-") and nome.endswith(".tzst")):
        continue
    tar = tarfile.open(fileobj=io.BytesIO(zstd.decompress(open(os.path.join(SRC, nome), "rb").read())))
    destino = os.path.join(OUT, nome[:-5] + ".zip")
    with zipfile.ZipFile(destino, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for m in tar.getmembers():
            if m.isfile():
                z.writestr(os.path.basename(m.name), tar.extractfile(m).read())
    print(destino, os.path.getsize(destino))

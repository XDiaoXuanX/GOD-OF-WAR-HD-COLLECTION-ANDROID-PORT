"""Monta a pasta "God of War Collection" que vai na raiz do armazenamento do Android (/sdcard/God of War Collection).

Lê a instalação do PC (só lê) e grava em gow-android/dist/God of War Collection:
  game/      o app do Vita, inteiro
  shaders/   só os .spv (o celular usa Vulkan; os .dx11 e a pasta miss ficam de fora)
  data/      tudo menos mods_removidos/, videos/ (o vídeo HD está desligado no celular), texdump/ e savedata0/ (sem saves);
             troféus zerados (sem unlocked.txt); ui-arte com a arte do lançador (build/ui-arte-lancador);
             vitart_options.ini do celular (sem caminho de ISO do PC, cheats desligados, proporção 16:9)
  logs/      vazia

Uso: python tools/gerar_pasta_android.py [pasta do PC] [destino]
"""
import os
import shutil
import sys
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
PORTE = os.path.dirname(AQUI)
PC = sys.argv[1] if len(sys.argv) > 1 else r'C:\Users\Luis\Desktop\God of War Collection'
DEST = sys.argv[2] if len(sys.argv) > 2 else os.path.join(PORTE, 'dist', 'God of War Collection')

FORA_DO_DATA = {'videos', 'texdump', 'savedata0', 'vitart_options.ini', 'versao.txt', 'mods_removidos'}

INI = """# Opções do PC (mudam pelo menu de pausa do jogo: Options)
hd_textures=1
ps2_audio=1
resolution=4
fps=max
vsync=0
fps_counter=0
borderless=1
auto_qte=1
cheat_infinite_health=0
cheat_infinite_magic=0
cheat_infinite_rage=0
enemy_health_bars=1
aspect=16:9
renderer=vulkan
console=0
log=0
mod_gow3_kratos=0
mod_3=0
mod_traducao_ptbr=0
mod_4=0
mod_1=0
"""

copiados = 0
bytes_copiados = 0
inicio = time.time()


def copiar(src, dst):
    global copiados, bytes_copiados
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if os.path.exists(dst) and os.path.getsize(dst) == os.path.getsize(src) and \
            int(os.path.getmtime(dst)) >= int(os.path.getmtime(src)):
        return  # já copiado (rodar de novo só completa o que falta)
    shutil.copy2(src, dst)
    copiados += 1
    bytes_copiados += os.path.getsize(src)
    if copiados % 500 == 0:
        print(f'  {copiados} arquivos, {bytes_copiados / 2**30:.2f} GB, {time.time() - inicio:.0f} s', flush=True)


def arvore(src, dst, filtro=lambda rel: True):
    for raiz, pastas, arquivos in os.walk(src):
        rel_raiz = os.path.relpath(raiz, src)
        pastas[:] = [p for p in pastas if filtro(os.path.normpath(os.path.join(rel_raiz, p)))]
        for a in arquivos:
            rel = os.path.normpath(os.path.join(rel_raiz, a))
            if filtro(rel):
                copiar(os.path.join(raiz, a), os.path.join(dst, rel))


def main():
    if not os.path.isdir(os.path.join(PC, 'game')) or not os.path.isdir(os.path.join(PC, 'data')):
        sys.exit(f'não achei game/ e data/ em {PC}')
    print(f'origem {PC}\ndestino {DEST}', flush=True)
    print('game/', flush=True)
    arvore(os.path.join(PC, 'game'), os.path.join(DEST, 'game'))
    print('shaders/ (só .spv)', flush=True)
    arvore(os.path.join(PC, 'shaders'), os.path.join(DEST, 'shaders'),
           lambda rel: os.sep not in rel and (rel.endswith('.spv') or False))
    print('data/', flush=True)

    def filtro_data(rel):
        partes = rel.split(os.sep)
        if partes[0] in FORA_DO_DATA:
            return False
        if partes[0] == 'trophy' and partes[-1].startswith('unlocked.txt'):
            return False  # troféus zerados
        return True
    arvore(os.path.join(PC, 'data'), os.path.join(DEST, 'data'), filtro_data)
    os.makedirs(os.path.join(DEST, 'data', 'trophy'), exist_ok=True)
    arte = os.path.join(PORTE, 'build', 'ui-arte-lancador')
    if os.path.isdir(arte):
        print('data/ui-arte (arte do lançador)', flush=True)
        arvore(arte, os.path.join(DEST, 'data', 'ui-arte'))
    else:
        print('AVISO: build/ui-arte-lancador não existe (rode tools/prep_arte_lancador.py): o lançador fica sem a arte de fundo')
    with open(os.path.join(DEST, 'data', 'vitart_options.ini'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(INI)
    os.makedirs(os.path.join(DEST, 'logs'), exist_ok=True)
    # restos de execuções anteriores que não devem ir para o celular
    for sobra in ('vitart_env.txt',):
        p = os.path.join(DEST, sobra)
        if os.path.isfile(p):
            os.remove(p)

    total = 0
    n = 0
    for raiz, _, arquivos in os.walk(DEST):
        for a in arquivos:
            total += os.path.getsize(os.path.join(raiz, a))
            n += 1
    print(f'pronto: {n} arquivos, {total / 2**30:.2f} GB em {DEST} '
          f'({copiados} copiados agora, {time.time() - inicio:.0f} s)', flush=True)


if __name__ == '__main__':
    main()

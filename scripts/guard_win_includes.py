"""Põe os #include do Windows de um arquivo dentro de #ifdef _WIN32 (uma vez só por bloco contíguo).

    python scripts/guard_win_includes.py arquivo.cpp ...
"""
import re
import sys

WIN = re.compile(r'^\s*(#define\s+(WIN32_LEAN_AND_MEAN|NOMINMAX|VK_USE_PLATFORM_WIN32_KHR)\b|'
                 r'#include\s*<(windows|mmsystem|io|audioclient|avrt|mmdeviceapi|hidsdi|setupapi|wrl/client|d3d11_1|dxgi1_5)\.h>|'
                 r'#pragma\s+comment)')

for p in sys.argv[1:]:
    lines = open(p, encoding='utf-8').read().split('\n')
    out, i, depth = [], 0, 0
    while i < len(lines):
        l = lines[i]
        s = l.strip()
        if s.startswith('#if'):
            depth += 1
        elif s.startswith('#endif'):
            depth -= 1
        if depth == 0 and WIN.match(l):
            j = i
            while j < len(lines) and (WIN.match(lines[j]) or not lines[j].strip()) and j - i < 20:
                j += 1
            while not lines[j - 1].strip():
                j -= 1
            out += ['#ifdef _WIN32'] + lines[i:j] + ['#endif']
            i = j
            continue
        out.append(l)
        i += 1
    open(p, 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
    print(p)

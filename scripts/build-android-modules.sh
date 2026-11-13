#!/usr/bin/env bash
# Módulos do jogo para Android arm64: o mesmo ARM -> C do PC (build-gow-dlls.sh), só que compilado pelo NDK em
# lib<nome>.so, que é o único jeito de o Android deixar carregar código nosso (vai no jniLibs do APK).
#
#   scripts/build-android-modules.sh <pasta com elf/ e os *-plan.json> <saída>
#
# Saída: <saída>/lib/libvt_<módulo>.so e <saída>/link.hlevars.txt. Variáveis: ANDROID_NDK (padrão: NDK 29 do SDK),
# JOBS (padrão 4; a máquina tem 8 GB e o gow1 come memória), OPT (padrão -O2).
set -euo pipefail
[ $# -eq 2 ] || { sed -n 2,8p "$0"; exit 1; }
HERE="$(cd "$(dirname "$0")/.." && pwd)"
# o recompilador e os remendos do gow vêm do projeto do PC, que aqui só é lido
PC="$(cd "${VITA_RECOMP_LAB:-$HERE/../vita-recomp-lab}" && pwd)"
DUMP="$(cd "$1" && pwd)"
OUT="$(mkdir -p "$2" && cd "$2" && pwd)"
NDK="${ANDROID_NDK:-$LOCALAPPDATA/Android/Sdk/ndk/29.0.14206865}"
TC="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin"
[ -x "$TC/clang.exe" ] || TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
CC="$TC/clang"; STRIP="$TC/llvm-strip"
JOBS="${JOBS:-4}"
export PYTHONPATH="$PC/vitarecomp" PYTHONIOENCODING=utf-8 PYTHONDONTWRITEBYTECODE=1 CC
export CFLAGS="--target=aarch64-linux-android28 -c -fPIC ${OPT:--O2} -w -fwrapv -fno-strict-aliasing -ffp-contract=off -fno-math-errno -fvisibility=hidden -ffunction-sections -I $HERE/runtime/arm"
mkdir -p "$OUT/lib"

# só os pontos de entrada da cola ficam visíveis: sem isso dois módulos com o mesmo arc_mem/arc_import
# trocam de símbolo entre si no carregador do Android
cat > "$OUT/exports.map" <<'EOF'
{ global: arc_bind; arc_get_table; arc_get_module_name; arc_get_module; arc_get_segments; local: *; };
EOF

build_plan() {
  local plan="$1"; shift
  cp "$DUMP/$plan" "$OUT/$plan"
  [ -d "$OUT/elf" ] || cp -r "$DUMP/elf" "$OUT/elf"
  python -P -m vitarecomp link "$OUT/$plan" -o "$OUT/link.json"
  for m in "$@"; do
    local gen="$OUT/gen/$m" obj="$OUT/obj/$m" so="$OUT/lib/libvt_$m.so"
    if [ -f "$so" ] && [ -z "${REFAZ:-}" ]; then echo "== $m: já existe"; continue; fi
    rm -rf "$gen" "$obj"; mkdir -p "$obj"
    local t0=$(date +%s)
    python -P -m vitarecomp recomp --link "$OUT/link.json" --module "$m" -o "$gen" 2>&1 | tail -2
    case "$m" in
      gow1) python -P "$PC/tools/patch_gow1_dt.py" "$gen" ;;
      gow2) python -P "$PC/tools/patch_gow2_dt.py" "$gen" ;;
    esac
    cp "$HERE/runtime/arm/arc_runtime.c" "$HERE/runtime/arm/arc_dll.c" "$gen/"
    local t1=$(date +%s)
    ( cd "$gen" && ls *.c | xargs -P "$JOBS" -I{} sh -c \
        '"$CC" $CFLAGS {} -o "'"$obj"'/$(basename {} .c).o" 2> "'"$obj"'/$(basename {} .c).err" || { echo "   FALHOU {}"; head -5 "'"$obj"'/$(basename {} .c).err"; }' )
    # com -shared o lld aceita símbolo indefinido: um .o faltando só apareceria no celular
    [ "$(ls "$gen"/*.c | wc -l)" -eq "$(ls "$obj"/*.o 2>/dev/null | wc -l)" ] || { echo "== $m: faltam objetos"; exit 1; }
    "$CC" --target=aarch64-linux-android28 -shared -fPIC -o "$so" "$obj"/*.o \
      -Wl,--version-script="$OUT/exports.map" -Wl,-soname,"libvt_$m.so" -Wl,--gc-sections \
      -Wl,-z,max-page-size=16384 -Wl,-z,noexecstack -Wl,--no-undefined -lm
    "$STRIP" --strip-unneeded "$so"
    local t2=$(date +%s)
    echo "== $m: tradução $((t1-t0))s, compilação $((t2-t1))s, $(du -h "$so" | cut -f1)"
  done
}

build_plan gow1-plan.json gow1 libc libfios2
build_plan gow2-plan.json gow2
build_plan eboot-plan.json eboot
echo "pronto: $OUT/lib"

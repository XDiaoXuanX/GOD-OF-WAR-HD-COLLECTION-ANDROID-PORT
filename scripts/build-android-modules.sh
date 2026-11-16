#!/usr/bin/env bash
set -euo pipefail
[ $# -eq 2 ] || { sed -n 2,8p "$0"; exit 1; }
HERE="$(cd "$(dirname "$0")/.." && pwd)"
PC="$(cd "${VITA_RECOMP_LAB:-$HERE/../vita-recomp-lab}" && pwd)"
DUMP="$(cd "$1" && pwd)"
OUT="$(mkdir -p "$2" && cd "$2" && pwd)"
NDK="${ANDROID_NDK:-$LOCALAPPDATA/Android/Sdk/ndk/29.0.14206865}"
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
[ -x "$TC/clang" ] || TC="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin"
CC="$TC/clang"; STRIP="$TC/llvm-strip"
JOBS="${JOBS:-4}"
export PYTHONPATH="$PC/vitarecomp" PYTHONIOENCODING=utf-8 PYTHONDONTWRITEBYTECODE=1 CC
export CFLAGS="--target=armv7a-linux-androideabi28 -march=armv7-a -mfloat-abi=softfp -mfpu=neon -c -fPIC ${OPT:--O2} -w -fwrapv -fno-strict-aliasing -ffp-contract=off -fno-math-errno -fvisibility=hidden -ffunction-sections -I $HERE/runtime/arm"
mkdir -p "$OUT/lib"

cat > "$OUT/exports.map" << 'EOF'
{ global: arc_bind; arc_get_table; arc_get_module_name; arc_get_module; arc_get_segments; local: *; };
EOF

build_plan() {
  local plan="$1"; shift
  cp "$DUMP/$plan" "$OUT/$plan"
  [ -d "$OUT/elf" ] || cp -r "$DUMP/elf" "$OUT/elf"
  python -m vitarecomp link "$OUT/$plan" -o "$OUT/link.json"
  for m in "$@"; do
    local gen="$OUT/gen/$m" obj="$OUT/obj/$m" so="$OUT/lib/libvrt_$m.so"
    if [ -f "$so" ] && [ -z "${REFAZ:-}" ]; then echo "== $m: já existe"; continue; fi
    rm -rf "$gen" "$obj"; mkdir -p "$obj"
    local t0=$(date +%s)
    python -m vitarecomp recomp --link "$OUT/link.json" --module "$m" -o "$gen" 2>&1 | tail -2
    case "$m" in
      gow1) python -P "$PC/tools/patch_gow1_dt.py" "$gen" ;;
      gow2) python -P "$PC/tools/patch_gow2_dt.py" "$gen" ;;
    esac
    cp "$HERE/runtime/arm/arc_runtime.c" "$HERE/runtime/arm/arc_dll.c" "$gen/"
    local t1=$(date +%s)
    ( cd "$gen" && ls *.c | xargs -P "$JOBS" -I{} sh -c \
      '"$CC" $CFLAGS -o "'"$obj"'/$(basename {} .c).o" {} 2> "'"$obj"'/$(basename {} .c).err" || { echo "   FALHOU {}"; head -5 "'"$obj"'/$(basename {} .c).err"; }' )
    [ "$(ls "$gen/"*.c | wc -l)" -eq "$(ls "$obj/"*.o 2>/dev/null | wc -l)" ] || { echo "== $m: faltam objetos"; exit 1; }
    "$CC" --target=armv7a-linux-androideabi28 -shared -fPIC -o "$so" "$obj/"*.o \
      -Wl,--version-script="$OUT/exports.map" -Wl,-soname,"libvrt_$m.so" -Wl,--gc-sections \
      -Wl,-z,max-page-size=16384 -Wl,-z,noexecstack -Wl,--no-undefined -lm
    "$STRIP" --strip-unneeded "$so"
    local t2=$(date +%s)
    echo "== $m: tradução $((t1-t0))s, compilação $((t2-t1))s, $(du -h "$so" | cut -f1)"
  done
}

build_plan gow1-plan.json gow1 libc libfios2
build_plan gow2-plan.json gow2
build_plan eboot-plan.json eboot
echo "Pronto: $OUT/lib"

#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"
NDK="${ANDROID_NDK:-$LOCALAPPDATA/Android/Sdk/ndk/29.0.14206865}"
TC="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin"
[ -x "$TC/clang.exe" ] || TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
T="--target=armv7a-linux-androideabi28 -march=armv7-a -mfloat-abi=softfp -mfpu=neon"
AT="${ADRENOTOOLS:-third_party/adrenotools}"
[ -f "$AT/src/driver.cpp" ] || { echo "libadrenotools não encontrada em $AT"; exit 1; }
[ -f build/lib/libvitart.so ] || { echo "falta build/lib/libvitart.so (rode o build-android-runtime.sh)"; exit 1; }
O=build/vkshim; mkdir -p "$O" build/lib
LD="-Wl,-z,max-page-size=16384 -Wl,--no-undefined"
CXXF="$T -std=c++20 -O2 -fPIC -w -Wno-c++11-narrowing -I$AT/include -I$AT -I$AT/lib/linkernsbypass"

# 1. as funções: as que o libvitart.so importa + as de entrada do loader
{ "$TC/llvm-nm" -D --undefined-only build/lib/libvitart.so | grep -o '\bvk[A-Za-z0-9_]*'
  printf '%s\n' vkGetInstanceProcAddr vkGetDeviceProcAddr vkEnumerateInstanceExtensionProperties \
    vkEnumerateInstanceLayerProperties vkEnumerateInstanceVersion vkEnumerateDeviceExtensionProperties \
    vkGetPhysicalDeviceFeatures2 vkGetPhysicalDeviceProperties2; } | sort -u > "$O/nomes.txt"
N=$(wc -l < "$O/nomes.txt")
{ echo "// gerado por scripts/build-android-vkshim.sh"; echo "enum { kVkshimCount = $N };"
  echo "static const char *const kVkshimNames[kVkshimCount] = {"; sed 's/.*/  "&",/' "$O/nomes.txt"; echo "};"; } > "$O/vkshim_names.h"
{ echo "// gerado: cada função salta pela tabela g_vkshim"
  echo ".syntax unified"
  echo ".arm"
  echo ".text"; i=0
  while read -r f; do
    echo ".globl $f"; echo ".type $f,%function"; echo ".p2align 2"
    echo "$f:"
    echo "  ldr r12, 1f"
    echo "0: add r12, pc, r12"
    echo "  ldr r12, [r12]"
    echo "  ldr pc, [r12, #$((i*4))]"
    echo ".p2align 2"
    echo "1: .word g_vkshim(GOT) - (0b + 8)"
    echo ".size $f, .-$f"; i=$((i+1))
  done < "$O/nomes.txt"; } > "$O/vkshim_stubs.s"
  
{ echo "{ global:"; sed 's/.*/  &;/' "$O/nomes.txt"; echo "  vitart_vk_driver_status;"; echo "  local: *; };"; } > "$O/vkshim.map"

# 2. libadrenotools e o contorno de namespace (estáticos, dentro do shim e do hook_impl)
for f in "$AT/src/driver.cpp" "$AT/src/bcenabler.cpp" "$AT/lib/linkernsbypass/android_linker_ns.cpp" \
         "$AT/lib/linkernsbypass/elf_soname_patcher.cpp"; do
  "$TC/clang++" $CXXF -c "$f" -o "$O/$(basename "${f%.cpp}").o"
done
NS="$O/android_linker_ns.o $O/elf_soname_patcher.o"

# 3. ganchos (vão para o nativeLibraryDir: a libadrenotools os carrega de lá)
"$TC/clang++" $CXXF -fvisibility=hidden -shared -static-libstdc++ -o build/lib/libhook_impl.so "$AT/src/hook/hook_impl.cpp" $NS \
  $LD -Wl,-soname,libhook_impl.so -llog -landroid -ldl
for h in main_hook file_redirect_hook gsl_alloc_hook; do
  "$TC/clang" $T -O2 -fPIC -w -I"$AT/include" -shared -o "build/lib/lib$h.so" "$AT/src/hook/$h.c" \
    $LD -Wl,-z,global -Wl,-soname,lib$h.so -Lbuild/lib -lhook_impl
done

# 4. o shim
"$TC/clang" $T -O2 -fPIC -w -I"$AT/include" -I"$O" -c runtime/vkshim/vkshim.c -o "$O/vkshim.o"
"$TC/clang" $T -c "$O/vkshim_stubs.s" -o "$O/vkshim_stubs.o"
"$TC/clang++" $T -shared -fPIC -static-libstdc++ -o build/lib/libvulkan.so "$O/vkshim.o" "$O/vkshim_stubs.o" \
  "$O/driver.o" "$O/bcenabler.o" $NS $LD -Wl,-soname,libvulkan.so -Wl,--version-script="$O/vkshim.map" -landroid -llog -ldl
for l in libvulkan libhook_impl libmain_hook libfile_redirect_hook libgsl_alloc_hook; do "$TC/llvm-strip" --strip-unneeded build/lib/$l.so; done
echo "pronto: build/lib/libvulkan.so ($N funções + ganchos)"

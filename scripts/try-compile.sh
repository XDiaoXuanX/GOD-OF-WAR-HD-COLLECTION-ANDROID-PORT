#!/usr/bin/env bash
# compila cada fonte do runtime para arm64 e mostra só os erros (diagnóstico do porte)
cd "$(dirname "$0")/.."
NDK="$LOCALAPPDATA/Android/Sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/windows-x86_64/bin"
mkdir -p build/rt_obj
for f in "${@:-runtime/vitart/*.cpp runtime/vitart/android/*.cpp games/gow/*.cpp}"; do for g in $f; do
  case $g in *gxm_d3d11*|*gxm_window.cpp|*host_input.cpp|*trophy_banner.cpp) continue;; esac
  o=build/rt_obj/$(basename $g .cpp)
  if "$NDK/clang++" --target=aarch64-linux-android28 -std=c++20 -O2 -fPIC -w -include runtime/vitart/plat.h \
      -Iruntime/arm -Iruntime/vitart -Ithird_party/libatrac9/src -Ithird_party/stb -Ithird_party/font8x8 -c $g -o $o.o 2> $o.err
  then echo "OK $g"; else echo "FALHA $g: $(grep -c error: $o.err) erros"; grep error: $o.err | head -${N:-6}; fi
done; done

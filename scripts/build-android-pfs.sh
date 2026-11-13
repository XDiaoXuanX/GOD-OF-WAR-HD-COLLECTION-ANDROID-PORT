#!/usr/bin/env bash
# O extrator do app do PS Vita (PKG + work.bin -> game/PCSA00126) para o instalador do lançador: tools/pfs_extrair_android.cpp
# + third_party/psvpfsparser (com a ArmCryptoOperations.cpp) -> build/lib/libpfs_extrair.so, um EXECUTÁVEL com nome de
# biblioteca (o Android só deixa rodar binário que veio na pasta das bibliotecas do APK). Rodar antes do Gradle.
#   scripts/build-android-pfs.sh        (TESTE=1 também gera build/pfs_teste_cripto, para rodar no celular pelo adb)
set -e
cd "$(dirname "$0")/.."
NDK="${ANDROID_NDK:-$LOCALAPPDATA/Android/Sdk/ndk/29.0.14206865}"
TC="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin"
[ -x "$TC/clang.exe" ] || TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
T=--target=aarch64-linux-android28
PP=third_party/psvpfsparser
mkdir -p build/lib
"$TC/clang++" $T -std=c++20 -O2 -w -fPIE -pie -I $PP tools/pfs_extrair_android.cpp $PP/*.cpp -static-libstdc++ \
  -Wl,-z,max-page-size=16384 -o build/lib/libpfs_extrair.so
"$TC/llvm-strip" --strip-unneeded build/lib/libpfs_extrair.so
echo "pronto: build/lib/libpfs_extrair.so ($(du -h build/lib/libpfs_extrair.so | cut -f1))"
if [ -n "${TESTE:-}" ]; then
  "$TC/clang++" $T -std=c++20 -O2 -w -fPIE -pie -I $PP "${TESTE_SRC:?TESTE_SRC = o .cpp do teste}" $PP/ArmCryptoOperations.cpp \
    -static-libstdc++ -o build/pfs_teste_cripto
  echo "teste: build/pfs_teste_cripto"
fi

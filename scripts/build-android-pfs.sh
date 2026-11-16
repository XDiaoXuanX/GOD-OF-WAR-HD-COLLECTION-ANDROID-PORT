#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
NDK="${ANDROID_NDK:-$LOCALAPPDATA/Android/Sdk/ndk/29.0.14206865}"
TC="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin"
[ -x "$TC/clang.exe" ] || TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
T="--target=armv7a-linux-androideabi28 -march=armv7-a -mfloat-abi=softfp -mfpu=neon -mno-unaligned-access -D_FILE_OFFSET_BITS=64 -D_LARGEFILE64_SOURCE"
PP=third_party/psvpfsparser
mkdir -p build/lib
"$TC/clang++" $T -std=c++20 -O2 -w -fPIE -pie -I $PP tools/pfs_extrair_android.cpp $PP/*.cpp -static-libstdc++ \
  -Wl,-z,max-page-size=16384 -o build/lib/libpfs_extrair.so
"$TC/llvm-strip" --strip-unneeded build/lib/libpfs_extrair.so
echo "pronto: build/lib/libpfs_extrair.so ($(du -h build/lib/libpfs_extrair.so | cut -f1))"

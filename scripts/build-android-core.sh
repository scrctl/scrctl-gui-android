#!/usr/bin/env bash
set -euo pipefail

if (($# > 1)) || [[ -n "${1:-}" && "$1" != --xdb ]]; then
  echo "Usage: $0 [--xdb]" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE="$ROOT/vendor/scrctl/core"
APP="$ROOT/apps/android/app"
test -f "$CORE/Cargo.toml" || { echo "Run git submodule update --init" >&2; exit 1; }
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT

if command -v rustup >/dev/null 2>&1; then
  export PATH="$(dirname "$(rustup which rustc)"):$PATH"
fi

abis=(arm64-v8a armeabi-v7a x86_64)
build=(build --locked -p rfb-ffi --release)
jni_dir="$APP/src/main/jniLibs"
binding_dir="$APP/src/vnc/java"
if [[ "${1:-}" == --xdb ]]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  export ANDROID_NDK_HOME="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-$sdk/ndk/29.0.14206865}}"
  export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
  python3 "$ROOT/tools/build_native.py"
  export XDB_LIB_DIR="$ROOT/app/build/native-libs/arm64-v8a"
  abis=(arm64-v8a)
  build+=(--features xdb)
  jni_dir="$APP/build/xdb/jniLibs"
  binding_dir="$APP/build/xdb/bindings"
fi

targets=()
for abi in "${abis[@]}"; do
  targets+=(-t "$abi")
done
cd "$CORE"
cargo ndk -P 26 "${targets[@]}" -o "$stage/jniLibs" "${build[@]}"
cargo run --locked -p rfb-ffi-bindgen -- generate \
  --library "$stage/jniLibs/arm64-v8a/librfb_ffi.so" --language kotlin \
  --out-dir "$stage/bindings" --no-format

for abi in "${abis[@]}"; do
  test -f "$stage/jniLibs/$abi/librfb_ffi.so"
done
rm -rf "$jni_dir"
mkdir -p "$(dirname "$jni_dir")" "$binding_dir/uniffi/rfb_ffi"
mv "$stage/jniLibs" "$jni_dir"
binding="$binding_dir/uniffi/rfb_ffi/rfb_ffi.kt"
generated="$stage/bindings/uniffi/rfb_ffi/rfb_ffi.kt"
# UniFFI can vary trailing whitespace without changing the binding.
if [[ ! -f "$binding" ]] || ! cmp -s \
  <(sed 's/[[:blank:]]*$//' "$generated") \
  <(sed 's/[[:blank:]]*$//' "$binding"); then
  cp "$generated" "$binding"
fi

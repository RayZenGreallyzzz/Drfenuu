#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
WORK="${1:?Usage: build.sh /absolute/workdir [40-character Chromium commit]}"
[[ "$WORK" = /* && "$WORK" != *" "* ]] || { echo "Absolute path without spaces required"; exit 2; }
[[ "$(uname -s)" == Linux && "$(uname -m)" == x86_64 ]] || { echo "Linux x86_64 host required"; exit 2; }
mkdir -p "$WORK"
free_kb=$(df -Pk "$WORK" | awk 'NR==2{print $4}')
ram_kb=$(awk '/MemTotal/{print $2}' /proc/meminfo)
if [[ ! -d "$WORK/checkout/src/.git" ]] && (( free_kb < 150*1024*1024 )); then
  echo "Need at least 150 GiB free for a new checkout (more recommended)."; exit 3
fi
(( ram_kb >= 8*1024*1024-262144 )) || { echo "Need at least 8 GiB RAM; 32+ recommended"; exit 3; }
command -v git >/dev/null
command -v python3 >/dev/null
if [[ ! -d "$WORK/depot_tools/.git" ]]; then
  git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git "$WORK/depot_tools"
fi
export PATH="$WORK/depot_tools:$PATH"
export DEPOT_TOOLS_UPDATE=0
mkdir -p "$WORK/checkout"
cd "$WORK/checkout"
REV="${2:-$(git ls-remote https://chromium.googlesource.com/chromium/src.git HEAD | cut -f1)}"
[[ "$REV" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid Chromium revision"; exit 2; }
if [[ ! -f .gclient ]]; then
  gclient config --name src https://chromium.googlesource.com/chromium/src.git
  printf "\ntarget_os = ['android']\n" >> .gclient
fi
gclient sync --nohooks --no-history --revision "src@$REV"
cd src
[[ "$(git rev-parse HEAD)" == "$REV" ]] || { echo "Revision mismatch"; exit 4; }
echo "Chromium $REV"
# Dependencies are installed by the host administrator, never automatically with sudo.
if [[ "${AETHER_INSTALL_DEPS:-0}" == 1 ]]; then build/install-build-deps.sh; fi
gclient runhooks
python3 "$ROOT/brand.py" "$PWD"
mkdir -p out/AetherBolt
cp "$ROOT/args.gn" out/AetherBolt/args.gn
gn gen out/AetherBolt --fail-on-unused-args
autoninja -C out/AetherBolt chrome_public_apk
APK=out/AetherBolt/apks/ChromePublic.apk
test -s "$APK"
mkdir -p "$WORK/artifacts"
cp "$APK" "$WORK/artifacts/AetherBolt-Chromium-experimental-arm64.apk"
printf '%s\n' "$REV" > "$WORK/artifacts/chromium-revision.txt"
git -C "$WORK/depot_tools" rev-parse HEAD > "$WORK/artifacts/depot-tools-revision.txt"
cp out/AetherBolt/args.gn "$WORK/artifacts/args.gn"
(cd "$WORK/artifacts" && sha256sum *.apk > SHA256SUMS)

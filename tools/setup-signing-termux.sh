#!/usr/bin/env bash
set -euo pipefail
umask 077
repo=RayZenGreallyzzz/Drfenuu
dir="$HOME/aetherbolt-release-key"
for cmd in gh keytool base64; do command -v "$cmd" >/dev/null || { echo "Missing: $cmd"; exit 1; }; done
if ! gh auth status --hostname github.com >/dev/null 2>&1; then
  gh auth login --hostname github.com --git-protocol https --web
fi
echo "Checking access to $repo..."
existing="$(gh secret list --repo "$repo" --json name --jq '.[].name')"
if printf '%s\n' "$existing" | grep -Eq '^AETHER_KEYSTORE_(B64|PASSWORD)$'; then
  echo "Signing secrets already exist (or are partially configured). Refusing to replace them."
  exit 1
fi
mkdir -p "$dir"
chmod 700 "$dir"
key="$dir/aetherbolt.p12"
if [[ -e "$key" ]]; then
  echo "Existing local key found. Enter its original password."
else
  echo "Choose a new strong password for your signing key (12+ characters)."
fi
read -r -s -p 'Keystore password: ' AETHER_KEYSTORE_PASSWORD
echo
if [[ -z "$AETHER_KEYSTORE_PASSWORD" ]]; then exit 1; fi
if [[ ! -e "$key" ]]; then
  if [[ ${#AETHER_KEYSTORE_PASSWORD} -lt 12 ]]; then echo "Password too short"; exit 1; fi
  read -r -s -p 'Repeat password: ' confirmation; echo
  [[ "$confirmation" == "$AETHER_KEYSTORE_PASSWORD" ]] || { echo "Passwords differ"; exit 1; }
  unset confirmation
  export AETHER_KEYSTORE_PASSWORD
  keytool -genkeypair -alias aetherbolt -keyalg RSA -keysize 3072 -validity 10000 -storetype PKCS12 -keystore "$key" -storepass:env AETHER_KEYSTORE_PASSWORD -dname "CN=AetherBolt Browser"
fi
export AETHER_KEYSTORE_PASSWORD
keytool -list -alias aetherbolt -storetype PKCS12 -keystore "$key" -storepass:env AETHER_KEYSTORE_PASSWORD >/dev/null
base64 "$key" | tr -d '\n' | env -u AETHER_KEYSTORE_PASSWORD gh secret set AETHER_KEYSTORE_B64 --repo "$repo"
printf '%s' "$AETHER_KEYSTORE_PASSWORD" | env -u AETHER_KEYSTORE_PASSWORD gh secret set AETHER_KEYSTORE_PASSWORD --repo "$repo"
unset AETHER_KEYSTORE_PASSWORD
echo "Secrets saved. Back up $key and its password privately; do not upload them to GitHub files or chat."
gh workflow run android.yml --repo "$repo" --ref gecko-edition
echo "Build requested. Open https://github.com/$repo/actions"

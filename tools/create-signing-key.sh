#!/usr/bin/env bash
set -euo pipefail
umask 077
DIR="${1:-$HOME/aetherbolt-signing}"
mkdir -p "$DIR"
chmod 700 "$DIR"
if [[ -e "$DIR/aetherbolt.p12" || -e "$DIR/keystore-base64.txt" ]]; then
  echo "Signing files already exist. Refusing to overwrite your key." >&2
  exit 1
fi
keytool -genkeypair -alias aetherbolt -keyalg RSA -keysize 3072 -validity 10000 -storetype PKCS12 -keystore "$DIR/aetherbolt.p12" -dname "CN=AetherBolt Browser"
base64 "$DIR/aetherbolt.p12" | tr -d '\n' > "$DIR/keystore-base64.txt"
echo "Private key generated in $DIR. Keep an offline backup. Never commit these files."

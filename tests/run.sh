#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=$(mktemp -d); trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -d "$out" app/src/main/java/com/aetherbolt/browser/Router.java app/src/main/java/com/aetherbolt/browser/ScriptRules.java app/src/main/java/com/aetherbolt/browser/SearchLanguage.java app/src/main/java/com/aetherbolt/browser/DnsSettings.java app/src/main/java/com/aetherbolt/browser/SecurityCodec.java app/src/main/java/com/aetherbolt/browser/BackupCrypto.java tests/BackupCryptoTest.java tests/SecurityCodecTest.java tests/DnsSettingsTest.java tests/SearchLanguageTest.java tests/RouterTest.java tests/ScriptRulesTest.java app/src/main/java/com/aetherbolt/browser/ScriptImport.java tests/ScriptImportTest.java app/src/main/java/com/aetherbolt/browser/ExtensionPort.java tests/ExtensionPortTest.java
java -cp "$out" RouterTest

java -cp "$out" ScriptRulesTest

java -cp "$out" SearchLanguageTest

java -cp "$out" DnsSettingsTest

java -cp "$out" SecurityCodecTest

java -cp "$out" BackupCryptoTest

java -cp "$out" ScriptImportTest
java -cp "$out" com.aetherbolt.browser.ExtensionPortTest

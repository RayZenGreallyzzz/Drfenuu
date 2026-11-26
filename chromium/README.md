# AetherBolt Chromium Edition — milestone 0

This branch preserves the previous Android app as a UI reference. The new native browser is a separate build target and package. The old Gradle APK is not Chromium Edition.

## Build
Use a dedicated Linux x86_64 builder, recommended 32+ GiB RAM and 200+ GiB free SSD space. Install Chromium Linux/Android build dependencies following the official docs (below). Run:
```bash
bash chromium/build.sh /absolute/path/to/build-work [CHROMIUM_COMMIT_SHA]
```
Omit the optional commit SHA to resolve upstream HEAD once and record it in the artifacts. Use a currently security-maintained revision for any distributed build; HEAD is experimental. Set AETHER_INSTALL_DEPS=1 only when intentionally installing upstream dependencies on a machine you control. No Google API keys or production signing keys are included. Native APK build is not yet tested. Initial native build uses upstream Chromium interface with partial product-name branding; no false claim that the old Java UI has been ported.

## Acceptance checklist
1. Verify APK package com.aetherbolt.chromium and ARM64 native libchrome.
2. Install alongside com.aetherbolt.browser; verify launch and chrome://version.
3. Open chrome://extensions; check developer mode and installation of the supplied MV3 smoke test. If unpacked installation is missing, implement Android file-picker import in Chromium, not in WebView. ZIP/CRX install and signature validation are separate work.
4. Open https://example.com/ and check the test badge; verify popup, enable/disable and uninstall. This does not imply Chrome Web Store support.
5. Port AetherBolt colors, splash, home, tabs, settings and search providers using Chromium native UI/resources. Preserve privacy and sandbox defaults.
6. Add safe extension permission UI, signed CRX verification and update handling. MV2 is not a supported assumption for upstream desktop Android.
7. Tor: separate network context, fail closed on onion requests, isolate ordinary/Tor cookies and prevent DNS/WebRTC bypass; test redirects/subresources and mixed tabs. Until implemented, do not use this edition for Tor anonymity.
8. Verify security updates and distribution signing before any production release.

Official references:
- https://chromium.googlesource.com/chromium/src/+/HEAD/docs/android_build_instructions.md
- https://chromium.googlesource.com/chromium/src/+/HEAD/extensions/docs/buildflags.md
- https://chromium.googlesource.com/chromium/src/+/HEAD/build/config/chrome_build.gni
- https://chromium.googlesource.com/chromium/src/+/HEAD/extensions/buildflags/buildflags.gni

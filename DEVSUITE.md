# AetherBolt DevSuite 0.7.6 (experimental)
- Phone: bottom navigation, new tab, tab switcher, extensions and menu. Tablet: desktop tab strip.
- Search tab cards, undo last closed HTTP(S) tab, close other tabs, restore ordinary URLs on restart when history is enabled. Visible-page thumbnails are bounded to 320×200 pixels and kept only in RAM; title/favicon fallback if capture fails. Restored tabs load only when selected.
- Gecko tracker protection, native DevSuite bridge; no Chromium or Chrome extensions.
- Build requires Python 3, Node/npm and network: pinned upstream Eruda 3.4.3, xterm 6.0.0, v86 0.5.476, exact CyberChef ZIP SHA256 from supplied archive. BIOS and Linux ISO come from upstream copy.sh, not user-provided ISO. VGA and serial input modes support touch keyboards; users can also select their own ISO (up to 256 MB).
- Local tools run inside an unprivileged separate extension origin. No native messaging permission there. CyberChef inline scripts are externalized for extension CSP. Device compatibility of workers/WASM still requires testing.
- JS tools run only after a user command on an ordinary web page; page CSP or privileged sites may restrict execution. Proxy is runtime-wide, session-only, no auth, not a VPN; verify IP/DNS before relying on it.
- APK builds through existing CI; branch debug signing does not match stable key. No signing secrets are included.

- CyberChef gzip duplicates are validated and normalized before Android asset merging. The English OCR dictionary is unpacked and its worker is configured to load raw traineddata.

## 0.7.7
Native AetherBolt DevSuite and proxy panels. Gecko file prompt connects to Android Storage Access Framework for single and multiple files; folder and direct camera capture not yet implemented. Download streams use MediaStore Download/AetherBolt on Android 10+, app Downloads on Android 8/9 with export/share. Same-origin blob navigation allowed. Successful compile does not replace on-device upload/download testing.

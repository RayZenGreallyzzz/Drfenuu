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

### Browser download center
Dedicated bottom navigation button, active transfers with byte/percentage progress (when Content-Length is available), cancellation, errors, searchable persistent completed-file list including older app downloads, sorting by date, and open/share/export/delete actions. Downloads survive ordinary tab switching; Android process termination may interrupt active transfers. No resume or background service yet.

### Smooth scrolling
Gecko owns touch dispatch; native code observes gesture boundaries without intercepting input. Page-title updates are coalesced and postponed during a gesture and the initial fling window. No automatic page-load screenshots or snapshots on ordinary tab switches; capture is limited to explicit tab overview and bitmap downscaling is off the UI thread. The already attached GeckoView is reused when navigating within the same tab. No forced GPU/APZ prefs or refresh-rate override. Device frame timing is still unmeasured.

## 0.7.8 — Web fullscreen
Gecko ContentDelegate handles HTML Fullscreen API requests for the selected tab. Hides tab strip, address bar, progress, bottom controls and Android system bars; screen stays awake while fullscreen. Android back exits fullscreen without navigating away. Restores chrome on exit, navigation, closing or switching the fullscreen tab. Rotation respects the device setting; no video cropping or forced landscape. Sites must expose a working fullscreen request. Test on device with HTML5 video, embedded video and WebGL game fullscreen.

## 0.7.9 — Local Media Center
LibVLC 3.7.5 private fullscreen activity for user-selected local files and downloads. Lazy native initialization on playback only; seek, pause, audio and subtitle tracks, external player fallback. Gecko still handles web video, DRM and authenticated web streams. The list of extensions is a routing hint, not a guarantee that every file decodes. Before public release, review LibVLC and bundled codec licensing and source-distribution obligations. No background playback or DRM bypass. Device testing required.

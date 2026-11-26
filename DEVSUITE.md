# AetherBolt DevSuite 0.7.6 (experimental)
- Phone: bottom navigation, new tab, tab switcher, extensions and menu. Tablet: desktop tab strip.
- Search tab cards, undo last closed HTTP(S) tab, close other tabs, restore ordinary URLs on restart when history is enabled. Cards currently show title/favicon fallback; not live screenshots.
- Gecko tracker protection, native DevSuite bridge; no Chromium or Chrome extensions.
- Build requires Python 3, Node/npm and network: pinned upstream Eruda 3.4.3, xterm 6.0.0, v86 0.5.476, exact CyberChef ZIP SHA256 from supplied archive. BIOS and Linux ISO come from upstream copy.sh, not user-provided ISO.
- Local tools run inside an unprivileged separate extension origin. No native messaging permission there. CyberChef inline scripts are externalized for extension CSP. Device compatibility of workers/WASM still requires testing.
- JS tools run only after a user command on an ordinary web page; page CSP or privileged sites may restrict execution. Proxy is runtime-wide, session-only, no auth, not a VPN; verify IP/DNS before relying on it.
- APK builds through existing CI; branch debug signing does not match stable key. No signing secrets are included.

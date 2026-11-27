# AetherBolt 0.9.7.7 — Script and Extension Integration

* Added quick access to official signed Violentmonkey XPI through GeckoView's standard installation and permissions prompts, plus status and extension options.
* Wired `WebExtension.DownloadDelegate` into the existing native download queue with explicit user confirmation, sanitized filename, HTTP(S) GET only, native GeckoWebExecutor fetch, per-download extension status updates. Blob/data URLs, retries and resumability in this adapter are not supported.
* Added a safe, opt-in JS DOM probe in Script Manager. This tests the existing isolated-world manual injection, NOT page MAIN world or document-start.
* Existing user scripts and permissions preserved, no site-tracking disable, no unsigned XPI bypass, no protected-asset extraction.
* Firefox's native Violentmonkey addon manages userscripts; its GM features and site behavior still require on-device validation. Build success is not runtime compatibility proof.

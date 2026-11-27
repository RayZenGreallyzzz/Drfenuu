# DevSuite — future backlog (draft, not implemented)

User-proposed research topics for authorized testing on owned or explicitly permitted systems.

## 1. Testing tools
- HackBar (Firefox / Chrome): encoding and decoding Base64, URL and Hex; HTTP request editor, POST and referrer inspection; study safe, local lab exercises for SQL injection and XSS testing.
- FoxyProxy: user-controlled proxy profiles for Burp Suite / OWASP ZAP; switching direct and test connections.
- EditThisCookie / Cookie-Editor: inspect, add, edit and remove cookies in the user's own test sessions; session management testing. Never export or expose authentication secrets automatically.

## 2. Recon
- Wappalyzer: identify CMS, frameworks, servers, languages and analytics; version inventory and CVE references.
- Shodan: optional provider integration for public infrastructure metadata, ports and service inventory; user-provided API access, no automated scanning by default.
- DotGit: investigate exposure checks for owned or explicitly authorized sites; no automatic source-code retrieval.

## Implementation gates
Verify licensing, Firefox Android/GeckoView compatibility and permissions. Prefer established signed add-ons or local lab tools. Optional modules, off by default; no automatic scans or request modification. Protect credentials and tokens. Review and test each module separately. This is a backlog, not an included feature.

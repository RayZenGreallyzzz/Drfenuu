# DevSuite release signing

DevSuite uses a dedicated RSA 3072 PKCS#12 signing key (alias `aetherbolt-devsuite`).
Certificate SHA-256: `b2e85ad16066e3fb82e7ab68796b8a3f883510b37e51ae40f10cc3eb2d95bd43`.

Configure the repository Actions secrets `DEV_KEYSTORE_B64` and `DEV_KEYSTORE_PASSWORD` using the private backup supplied separately to the owner. Never commit the key or password. If credentials are missing, the workflow exports a clearly marked unsigned aligned APK and the SDK signing utility for offline signing; it never publishes a debug-signed APK. If credentials are configured but the certificate differs, the signed publication fails closed. This branch retains application ID `com.aetherbolt.gecko`; users with a different signing key must back up data and uninstall before installing the first release signed by this key. Subsequent upgrades require the same application ID, certificate and a nondecreasing version code; increment versionCode on new releases. The original gecko-edition branch is unchanged.

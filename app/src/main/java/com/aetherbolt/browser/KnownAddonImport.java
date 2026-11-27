package com.aetherbolt.browser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;

/** Recognize reviewed source archives by content, even when Android renames them. */
final class KnownAddonImport {
    enum Package { MODEL_DOWNLOADER, MESHY_FIREFOX, OTHER }

    private static final String MODEL_ARCHIVE = "39c2c6e3ddb509cae6eef2b7a8121d661e89ba45c80f3a33f827d95aae7fdc44";
    private static final String MESHY_ARCHIVE = "90415ba46a8d8a251e8b46413d292bdc839502a9158f662e0f529aac6a157fdf";
    private static final String MIXAMO_SCRIPT = "457a1dcef4b576e7883b7f288617c1904e6765b8c173d77586897f017a907f78";

    static Package identify(InputStream input) throws IOException {
        if (input == null) return Package.OTHER;
        MessageDigest hash = sha256();
        try (InputStream source = input) {
            byte[] buffer = new byte[8192];
            long total = 0;
            for (int n; (n = source.read(buffer)) != -1; ) {
                total += n;
                if (total > 2 * 1024 * 1024) return Package.OTHER;
                hash.update(buffer, 0, n);
            }
        }
        String digest = hex(hash.digest());
        if (MODEL_ARCHIVE.equals(digest)) return Package.MODEL_DOWNLOADER;
        if (MESHY_ARCHIVE.equals(digest)) return Package.MESHY_FIREFOX;
        return Package.OTHER;
    }

    static boolean isMixamoScript(String code) {
        return MIXAMO_SCRIPT.equals(hex(sha256().digest(code.getBytes(StandardCharsets.UTF_8))));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static String hex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        String digits = "0123456789abcdef";
        for (int i = 0; i < bytes.length; i++) {
            hex[i * 2] = digits.charAt((bytes[i] & 0xff) >>> 4);
            hex[i * 2 + 1] = digits.charAt(bytes[i] & 15);
        }
        return new String(hex);
    }
}

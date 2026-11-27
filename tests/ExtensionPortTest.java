package com.aetherbolt.browser;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

public final class ExtensionPortTest {
    private static final String ORIGINAL = "{\"manifest_version\":3,\"name\":\"Sample\",\"version\":\"1\"}";
    private static final String ADAPTED = "{\"manifest_version\":3,\"name\":\"Sample\",\"version\":\"1\",\"browser_specific_settings\":{\"gecko\":{\"id\":\"@test\"}}}";

    private static ByteArrayInputStream input(byte[] bytes) { return new ByteArrayInputStream(bytes); }

    private static byte[] zip(String... entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write((entry.endsWith("manifest.json") ? ORIGINAL : "sample").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] crx(int version, byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write("Cr24".getBytes(StandardCharsets.US_ASCII));
        ByteBuffer header = ByteBuffer.allocate(version == 2 ? 12 : 8).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(version).putInt(1);
        if (version == 2) header.putInt(1);
        bytes.write(header.array());
        bytes.write(version == 2 ? new byte[]{1, 2} : new byte[]{1});
        bytes.write(payload);
        return bytes.toByteArray();
    }

    private static void check(boolean passed) { if (!passed) throw new AssertionError(); }
    private interface Checked { void run() throws IOException; }
    private static void reject(Checked run) throws IOException {
        try { run.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Rejected input was accepted");
    }

    public static void main(String[] args) throws IOException {
        File payload = Files.createTempFile("payload", ".zip").toFile();
        File output = Files.createTempFile("converted", ".zip").toFile();
        try {
            for (int version : new int[]{2, 3}) {
                byte[] packed = crx(version, zip("manifest.json", "main.js", "_metadata/old.json"));
                ScriptImport.Result report = ScriptImport.read(input(packed), "renamed.js");
                check(report.kind == ScriptImport.Kind.EXTENSION && report.crxVersion == version);
                check(report.scripts.isEmpty() && report.manifestText.equals(ORIGINAL));
                ExtensionPort.stage(input(packed), payload, version);
                ExtensionPort.export(payload, output, report, ADAPTED, Collections.singletonList("main.js"));
                try (ZipFile saved = new ZipFile(output)) {
                    check(saved.size() == 2 && saved.getEntry("main.js") != null);
                    check(new String(saved.getInputStream(saved.getEntry("manifest.json")).readAllBytes(), StandardCharsets.UTF_8).equals(ADAPTED));
                }
                reject(() -> ExtensionPort.export(payload, output, report, ADAPTED, Collections.singletonList("missing.js")));
                reject(() -> ExtensionPort.stage(input(packed), payload, 0));
            }
            byte[] wrapped = zip("extension/manifest.json", "extension/main.js", "README.md");
            ScriptImport.Result report = ScriptImport.read(input(wrapped), "test.zip");
            ExtensionPort.stage(input(wrapped), payload, 0);
            ExtensionPort.export(payload, output, report, ADAPTED, Collections.singletonList("main.js"));
            try (ZipFile saved = new ZipFile(output)) {
                check(saved.size() == 2 && saved.getEntry("main.js") != null);
            }
            byte[] changed = zip("extension/manifest.json", "extension/main.js", "extension/META-INF/mozilla.rsa");
            ExtensionPort.stage(input(changed), payload, 0);
            reject(() -> ExtensionPort.export(payload, output, report, ADAPTED, Collections.emptyList()));
            for (String bad : new String[]{"../extra.js", "MAIN.js", "a/../b", "CON.txt"}) {
                byte[] unsafe = zip("manifest.json", "main.js", bad);
                ScriptImport.Result reviewed = ScriptImport.read(input(unsafe), "test.zip");
                ExtensionPort.stage(input(unsafe), payload, 0);
                reject(() -> ExtensionPort.export(payload, output, reviewed, ADAPTED, Collections.emptyList()));
            }
            byte[] raw = zip("manifest.json", "a.js");
            ScriptImport.Result reviewed = ScriptImport.read(input(raw), "test.zip");
            ExtensionPort.stage(input(Arrays.copyOf(raw, raw.length - 22)), payload, 0);
            reject(() -> ExtensionPort.export(payload, output, reviewed, ADAPTED, Collections.emptyList()));
            reject(() -> ScriptImport.read(input(crx(3, zip("a.js"))), "test.crx"));
            reject(() -> ScriptImport.read(input(new byte[]{'C', 'r', '2', '4', 3}), "test.crx"));
            reject(() -> ScriptImport.read(input(raw), "test.crx"));
        } finally {
            Files.deleteIfExists(payload.toPath());
            Files.deleteIfExists(output.toPath());
        }
        System.out.println("PASS: CRX2/3, wrapped ZIP export and damaged package handling");
    }
}

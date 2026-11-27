package com.aetherbolt.browser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;

/** Repackage an extension as source files without extracting paths or running code. */
final class ExtensionPort {
    static final long MAX_PACKAGE = 32L * 1024 * 1024;
    private static final int MAX_ENTRIES = 512;
    private static final Pattern DEVICE_NAME = Pattern.compile("(?:AUX|CON|NUL|PRN|COM[1-9¹²³]|LPT[1-9¹²³])(?:\\..*)?", Pattern.CASE_INSENSITIVE);

    static void stage(InputStream input, File payload, int expectedCrxVersion) throws IOException {
        if (input == null) throw new IOException("Файл недоступен");
        try (BufferedInputStream raw = new BufferedInputStream(input);
             FileOutputStream output = new FileOutputStream(payload)) {
            if (ScriptImport.seekZipPayload(raw) != expectedCrxVersion) {
                throw new IOException("Формат изменился. Откройте файл заново.");
            }
            ScriptImport.copyBounded(raw, output, MAX_PACKAGE);
        }
    }

    static void export(File payload, File output, ScriptImport.Result reviewed,
                       String manifest, List<String> backgroundFiles) throws IOException {
        String manifestPath = reviewed.manifestPath;
        String root = manifestPath.substring(0, manifestPath.lastIndexOf('/') + 1);
        try (ZipFile source = new ZipFile(payload)) {
            Map<String, ZipEntry> files = readEntries(source, root);
            ZipEntry oldManifest = files.get("manifest.json");
            if (oldManifest == null) throw new IOException("В архиве отсутствует manifest.json");
            ByteArrayOutputStream original = new ByteArrayOutputStream();
            copy(source, oldManifest, original, 128 * 1024);
            if (!reviewed.manifestText.equals(ScriptImport.decode(original.toByteArray()))) {
                throw new IOException("Манифест изменился. Откройте файл заново.");
            }
            for (String path : backgroundFiles) {
                String relative = path.startsWith("/") ? path.substring(1) : path;
                if (!files.containsKey(relative)) throw new IOException("Фоновый файл отсутствует: " + path);
            }
            byte[] rewritten = manifest.getBytes(StandardCharsets.UTF_8);
            try (ZipOutputStream dest = new ZipOutputStream(new FileOutputStream(output))) {
                dest.putNextEntry(new ZipEntry("manifest.json"));
                dest.write(rewritten);
                dest.closeEntry();
                long remaining = MAX_PACKAGE - rewritten.length;
                for (Map.Entry<String, ZipEntry> file : files.entrySet()) {
                    if (file.getKey().equals("manifest.json")) continue;
                    dest.putNextEntry(new ZipEntry(file.getKey()));
                    remaining -= copy(source, file.getValue(), dest, remaining);
                    dest.closeEntry();
                }
            }
        }
    }

    private static Map<String, ZipEntry> readEntries(ZipFile source, String root) throws IOException {
        if (source.size() > MAX_ENTRIES) throw new IOException("ZIP: не более 512 файлов");
        Map<String, ZipEntry> files = new LinkedHashMap<>();
        Map<String, String> paths = new HashMap<>();
        Set<String> names = new HashSet<>();
        long remaining = MAX_PACKAGE;
        Enumeration<? extends ZipEntry> entries = source.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            long size = entry.getSize();
            if (size < 0 || size > remaining) throw new IOException("ZIP: лимит распаковки 32 МБ");
            remaining -= size;
            String path = entry.getName();
            checkPath(path, paths, names);
            if (!path.startsWith(root)) continue;
            String relative = path.substring(root.length());
            String lower = relative.toLowerCase(Locale.ROOT);
            if (lower.equals("meta-inf") || lower.startsWith("meta-inf/")) {
                throw new IOException("Пакет содержит META-INF. Выберите архив исходников.");
            }
            if (!entry.isDirectory() && !lower.startsWith("_metadata/") && !lower.equals("_metadata")) files.put(relative, entry);
        }
        return files;
    }

    private static void checkPath(String path, Map<String, String> paths, Set<String> names) throws IOException {
        if (path.length() > 1024 || path.chars().anyMatch(c -> c < 32 || "\\:<>\"|?*".indexOf(c) >= 0)) {
            throw new IOException("Недопустимое имя файла в ZIP");
        }
        boolean directory = path.endsWith("/");
        String name = directory ? path.substring(0, path.length() - 1) : path;
        String[] parts = name.split("/", -1);
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.endsWith(".") || part.endsWith(" ") || DEVICE_NAME.matcher(part).matches()) {
                throw new IOException("Недопустимый путь в ZIP");
            }
            prefix.append(part);
            String key = Normalizer.normalize(prefix, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
            if (i < parts.length - 1 || directory) prefix.append('/');
            String exact = prefix.toString();
            String previous = paths.putIfAbsent(key, exact);
            if (previous != null && !previous.equals(exact)) throw new IOException("Конфликт имён в ZIP");
        }
        if (!names.add(name)) throw new IOException("Повторяющаяся запись в ZIP");
    }

    private static long copy(ZipFile source, ZipEntry entry, OutputStream out, long limit) throws IOException {
        CRC32 crc = new CRC32();
        long count = 0;
        byte[] bytes = new byte[8192];
        try (InputStream input = source.getInputStream(entry)) {
            int n;
            while ((n = input.read(bytes)) != -1) {
                if (n > limit - count || n > entry.getSize() - count) throw new IOException("Превышен размер записи ZIP");
                crc.update(bytes, 0, n);
                out.write(bytes, 0, n);
                count += n;
            }
        }
        if (count != entry.getSize() || crc.getValue() != entry.getCrc()) throw new IOException("Повреждена запись ZIP");
        return count;
    }
}

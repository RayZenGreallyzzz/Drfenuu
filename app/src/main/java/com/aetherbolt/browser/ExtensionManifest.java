package com.aetherbolt.browser;

import java.util.*;
import java.util.regex.Pattern;
import org.json.*;

/** Convert the common WebExtension manifest fields while preserving requested permissions. */
final class ExtensionManifest {
    private static final Pattern GECKO_ID = Pattern.compile(
        "(?:[a-z0-9_.-]*@[a-z0-9_.-]+|\\{[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\})",
        Pattern.CASE_INSENSITIVE);
    private static final String[] MANUAL_KEYS = {"side_panel", "externally_connectable", "oauth2", "devtools_page"};
    private static final Set<String> MANUAL_PERMISSIONS = new HashSet<>(Arrays.asList(
        "debugger", "desktopCapture", "geckoViewAddons", "nativeMessaging", "nativeMessagingFromContent",
        "offscreen", "proxy", "sidePanel", "tabCapture"));

    final JSONObject manifest;
    final List<String> blockers = new ArrayList<>();
    final List<String> backgroundFiles = new ArrayList<>();
    final boolean reviewWorker;
    final String geckoId;
    final boolean needsDataDeclaration;

    ExtensionManifest(JSONObject manifest) throws JSONException {
        this.manifest = manifest;
        Object version = manifest.opt("manifest_version");
        if (!Integer.valueOf(2).equals(version) && !Integer.valueOf(3).equals(version)) {
            throw new JSONException("Нужен Manifest V2 или V3");
        }
        requiredString(manifest, "name");
        requiredString(manifest, "version");
        for (String key : MANUAL_KEYS) if (manifest.has(key)) blockers.add(key);
        if ("split".equals(manifest.opt("incognito"))) blockers.add("incognito: split");
        for (String key : new String[]{"permissions", "optional_permissions", "host_permissions", "optional_host_permissions"}) {
            if (!manifest.has(key)) continue;
            JSONArray permissions = manifest.getJSONArray(key);
            for (int i = 0; i < permissions.length(); i++) {
                Object permission = permissions.get(i);
                if (!(permission instanceof String)) throw new JSONException("Ожидаются строки в " + key);
                if (MANUAL_PERMISSIONS.contains(permission) && !blockers.contains(permission)) blockers.add((String) permission);
            }
        }

        if (manifest.has("applications") && manifest.has("browser_specific_settings")) {
            throw new JSONException("Объедините applications и browser_specific_settings");
        }
        JSONObject settings = getOrCreate(manifest, manifest.has("applications") ? "applications" : "browser_specific_settings");
        JSONObject gecko = getOrCreate(settings, "gecko");
        getOrCreate(settings, "gecko_android");
        geckoId = gecko.has("id") ? requiredString(gecko, "id") : "";
        if (!geckoId.isEmpty() && !validId(geckoId)) throw new JSONException("Недопустимый идентификатор Gecko");
        needsDataDeclaration = !gecko.has("data_collection_permissions");

        JSONObject background = manifest.has("background") ? manifest.getJSONObject("background") : new JSONObject();
        if (background.has("type")) {
            String type = requiredString(background, "type");
            if (!type.equals("classic") && !type.equals("module")) throw new JSONException("Неизвестный background.type");
        }
        if (background.has("scripts")) {
            JSONArray scripts = background.getJSONArray("scripts");
            for (int i = 0; i < scripts.length(); i++) {
                Object script = scripts.get(i);
                if (!(script instanceof String) || ((String) script).trim().isEmpty()) {
                    throw new JSONException("Недопустимый файл в background.scripts");
                }
                backgroundFiles.add((String) script);
            }
        }
        if (background.has("page")) backgroundFiles.add(requiredString(background, "page"));
        reviewWorker = background.has("service_worker") && backgroundFiles.isEmpty();
        if (background.has("service_worker")) {
            if (!Integer.valueOf(3).equals(version)) throw new JSONException("service_worker требует Manifest V3");
            String worker = requiredString(background, "service_worker");
            if (reviewWorker) backgroundFiles.add(worker);
        }
    }

    String convert(String id, boolean workerReviewed) throws JSONException {
        if (!blockers.isEmpty()) throw new JSONException("Требуется ручной перенос: " + String.join(", ", blockers));
        if (reviewWorker && !workerReviewed) throw new JSONException("Проверьте совместимость фонового кода");
        if (!validId(id) || (!geckoId.isEmpty() && !geckoId.equals(id))) throw new JSONException("Недопустимый идентификатор Gecko");

        if (manifest.has("applications")) manifest.put("browser_specific_settings", manifest.remove("applications"));
        JSONObject gecko = manifest.getJSONObject("browser_specific_settings").getJSONObject("gecko");
        gecko.put("id", id);
        JSONObject background = manifest.optJSONObject("background");
        if (background != null && background.has("service_worker")) {
            if (reviewWorker) {
                background.put("scripts", new JSONArray().put(background.get("service_worker")));
            }
            background.remove("service_worker");
            background.remove("preferred_environment");
        }
        for (String key : new String[]{"key", "minimum_chrome_version", "update_url"}) manifest.remove(key);
        return manifest.toString(2);
    }

    static boolean validId(String id) {
        return id != null && !id.isEmpty() && id.length() <= 255 && GECKO_ID.matcher(id).matches();
    }

    private static JSONObject getOrCreate(JSONObject owner, String key) throws JSONException {
        if (!owner.has(key)) owner.put(key, new JSONObject());
        return owner.getJSONObject(key);
    }

    private static String requiredString(JSONObject owner, String key) throws JSONException {
        Object value = owner.opt(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) throw new JSONException("Ожидается строка " + key);
        return (String) value;
    }
}

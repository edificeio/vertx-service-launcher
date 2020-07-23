package com.opendigitaleducation.launcher.resolvers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.vertx.core.json.JsonObject;

public class ExtensionRegistry {
    private static Map<String, String> extensionForTypes = new HashMap<>();
    private static Map<String, String> extensionForIds = new HashMap<>();
    static {
        extensionForTypes.put("theme", ".tar.gz");
        extensionForTypes.put("themes", ".tar.gz");
        extensionForTypes.put("js", ".tar.gz");
        extensionForTypes.put("assets", ".tar.gz");
    }
    private static final String DEFAULT = "-fat.jar";

    public static void register(String id, JsonObject service) {
        extensionForIds.put(id, getExtensionForService(service));
    }

    public static Set<String> getKnownExtensions() {
        Set<String> set = new HashSet<>();
        set.addAll(extensionForTypes.values());
        set.add(DEFAULT);
        return set;
    }

    public static String getExtensionForId(String id) {
        return extensionForIds.getOrDefault(id, DEFAULT);
    }

    public static String getExtensionForService(JsonObject service) {
        if (service.containsKey("extension")) {
            final String ext = service.getString("extension");
            return ext;
        } else if (service.containsKey("type")) {
            final String type = service.getString("type");
            return extensionForTypes.getOrDefault(type, DEFAULT);
        } else {
            return DEFAULT;
        }
    }
}
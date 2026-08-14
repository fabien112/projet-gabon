package com.company.dss.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Charge un fichier {@code .env} du répertoire de travail (installateur / start.bat).
 */
public final class DotEnvLoader {

    private DotEnvLoader() {
    }

    public static Map<String, Object> load() {
        Path file = Path.of(System.getProperty("user.dir", "."), ".env");
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!key.isEmpty()) {
                    values.put(key, value);
                }
            }
        } catch (IOException ignored) {
            return Map.of();
        }
        return values;
    }
}

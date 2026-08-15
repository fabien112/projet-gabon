package com.company.dss.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Charge un fichier {@code .env} en propriétés JVM <em>avant</em> le démarrage Spring.
 * Les clés du fichier l'emportent (config site dans {@code .env}).
 */
public final class DotenvLoader {

    private DotenvLoader() {}

    public static void load() {
        Path envFile = resolveEnvFile();
        if (envFile == null) {
            System.out.println(">>> [ENV] Aucun fichier .env (copiez .env.example vers .env et renseignez DSS_HOST / user / mdp)");
            return;
        }
        loadFrom(envFile);
        applyAppHomeDefaults(envFile.getParent());
    }

    static void loadFrom(Path envFile) {
        List<String> lines;
        try {
            lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println(">>> [ENV] Impossible de lire " + envFile + " : " + e.getMessage());
            return;
        }
        int applied = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = unquote(line.substring(eq + 1).trim());
            if (key.isEmpty()) {
                continue;
            }
            System.setProperty(key, value);
            applied++;
        }
        System.out.println(">>> [ENV] " + applied + " paramètre(s) chargés depuis " + envFile.toAbsolutePath());
    }

    private static void applyAppHomeDefaults(Path appHome) {
        if (appHome == null) {
            return;
        }
        Path web = appHome.resolve("web");
        if (Files.isDirectory(web) && blankProperty("WEB_ROOT")) {
            System.setProperty("WEB_ROOT", web.toAbsolutePath().toString());
        }
        if (blankProperty("DB_URL")) {
            String dbFile = appHome.resolve("data").resolve("dss").toAbsolutePath().toString().replace('\\', '/');
            System.setProperty("DB_URL", "jdbc:h2:file:" + dbFile + ";MODE=MySQL;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE");
        }
        if (System.getProperty("jpackage.app-path") != null
                && System.getProperty("logging.file.name") == null) {
            System.setProperty(
                    "logging.file.name",
                    appHome.resolve("logs").resolve("dataexpert.log").toAbsolutePath().toString());
        }
        System.setProperty("user.dir", appHome.toAbsolutePath().toString());
    }

    static Path resolveEnvFile() {
        String home = System.getenv("DATAEXPERT_HOME");
        if (home != null && !home.isBlank()) {
            Path candidate = Path.of(home).resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        String jpackage = System.getProperty("jpackage.app-path");
        if (jpackage != null && !jpackage.isBlank()) {
            Path candidate = Path.of(jpackage).toAbsolutePath().getParent().resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        Path cwd = Path.of(System.getProperty("user.dir", ".")).resolve(".env");
        if (Files.isRegularFile(cwd)) {
            return cwd;
        }
        return null;
    }

    private static boolean blankProperty(String key) {
        String prop = System.getProperty(key);
        return prop == null || prop.isBlank();
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}

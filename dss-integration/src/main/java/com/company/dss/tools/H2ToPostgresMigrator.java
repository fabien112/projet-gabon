package com.company.dss.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Copie les données H2 (dev / dist-client) vers PostgreSQL (prod Docker).
 * Prérequis : schéma PG créé par Flyway (démarrer {@code db} ou {@code api} une fois).
 *
 * Usage :
 * <pre>
 *   java ... H2ToPostgresMigrator \
 *     --h2-path C:/DataExpert/data/dss \
 *     --pg-url jdbc:postgresql://localhost:5432/dss \
 *     --pg-user dss_user --pg-password secret
 * </pre>
 */
public final class H2ToPostgresMigrator {

    private static final int BATCH_SIZE = 2_000;

    private record Config(
            String h2Url,
            String pgUrl,
            String pgUser,
            String pgPassword,
            boolean dryRun,
            boolean skipTruncate) {
    }

    public static void main(String[] args) throws Exception {
        Config config = parseArgs(args);
        printBanner(config);

        try (Connection h2 = openH2(config.h2Url());
                Connection pg = openPostgres(config)) {
            pg.setAutoCommit(false);

            ensurePostgresSchema(pg, config.dryRun());

            if (!config.skipTruncate() && !config.dryRun()) {
                truncateTarget(pg);
            }

            Map<String, Long> counts = new LinkedHashMap<>();
            counts.put("camera", copyCamera(h2, pg, config.dryRun()));
            counts.put("app_user", copyAppUser(h2, pg, config.dryRun()));
            counts.put("people_counting_hourly", copyPeopleCounting(h2, pg, config.dryRun()));
            counts.put("sync_meta", copySyncMeta(h2, pg, config.dryRun()));

            if (!config.dryRun()) {
                resetSequences(pg);
                pg.commit();
            } else {
                pg.rollback();
            }

            printSummary(h2, pg, counts, config.dryRun());
        }
    }

    private static void printBanner(Config config) {
        System.out.println("=== DataExpert : migration H2 -> PostgreSQL ===");
        System.out.println("Source H2 : " + maskPassword(config.h2Url()));
        System.out.println("Cible PG  : " + maskPassword(config.pgUrl()) + " (user=" + config.pgUser() + ")");
        System.out.println("Mode      : " + (config.dryRun() ? "DRY-RUN (aucune écriture)" : "ÉCRITURE"));
        if (config.skipTruncate()) {
            System.out.println("Attention : --skip-truncate (fusion / upsert partiel non géré — tables non vidées)");
        }
        System.out.println();
    }

    private static Config parseArgs(String[] args) {
        String h2Path = null;
        String h2Url = null;
        String pgUrl = System.getenv("DB_URL");
        String pgUser = envOr("DB_USER", "dss_user");
        String pgPassword = envOr("DB_PASSWORD", envOr("POSTGRES_PASSWORD", null));
        boolean dryRun = false;
        boolean skipTruncate = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--h2-path" -> h2Path = requireValue(args, ++i, arg);
                case "--h2-url" -> h2Url = requireValue(args, ++i, arg);
                case "--pg-url" -> pgUrl = requireValue(args, ++i, arg);
                case "--pg-user" -> pgUser = requireValue(args, ++i, arg);
                case "--pg-password" -> pgPassword = requireValue(args, ++i, arg);
                case "--dry-run" -> dryRun = true;
                case "--skip-truncate" -> skipTruncate = true;
                case "--help", "-h" -> {
                    printHelp();
                    System.exit(0);
                }
                default -> throw new IllegalArgumentException("Argument inconnu : " + arg);
            }
        }

        if (h2Url == null) {
            if (h2Path == null) {
                h2Path = "dist-client/data/dss";
            }
            String normalized = Path.of(h2Path).toAbsolutePath().normalize().toString().replace('\\', '/');
            h2Url = "jdbc:h2:file:" + normalized + ";MODE=MySQL;AUTO_SERVER=TRUE";
        }
        if (pgUrl == null || pgUrl.isBlank()) {
            pgUrl = "jdbc:postgresql://localhost:5432/dss";
        }
        if (pgPassword == null || pgPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "Mot de passe PostgreSQL requis (--pg-password ou DB_PASSWORD / POSTGRES_PASSWORD)");
        }
        return new Config(h2Url, pgUrl, pgUser, pgPassword, dryRun, skipTruncate);
    }

    private static void printHelp() {
        System.out.println("""
                Migration H2 -> PostgreSQL (DataExpert)

                Options :
                  --h2-path <dir>       Dossier base H2 sans extension (défaut: dist-client/data/dss)
                  --h2-url <jdbc>       URL JDBC H2 complète
                  --pg-url <jdbc>       URL PostgreSQL (défaut: jdbc:postgresql://localhost:5432/dss)
                  --pg-user <user>      Utilisateur PG (défaut: dss_user)
                  --pg-password <pwd>   Mot de passe PG
                  --dry-run             Compte les lignes sans écrire
                  --skip-truncate       Ne vide pas les tables PG avant import
                  --help                Cette aide

                Variables d'environnement : DB_URL, DB_USER, DB_PASSWORD, POSTGRES_PASSWORD
                """);
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Valeur manquante pour " + flag);
        }
        return args[index];
    }

    private static String envOr(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    private static Connection openH2(String url) throws SQLException {
        return DriverManager.getConnection(url, "SA", "");
    }

    private static Connection openPostgres(Config config) throws SQLException {
        return DriverManager.getConnection(config.pgUrl(), config.pgUser(), config.pgPassword());
    }

    private static void ensurePostgresSchema(Connection pg, boolean dryRun) throws Exception {
        if (tableExists(pg, "camera")) {
            System.out.println("Schéma PostgreSQL déjà présent (table camera).");
            return;
        }
        if (dryRun) {
            System.out.println("Schéma PostgreSQL absent — serait créé via V1__init.sql (hors dry-run).");
            return;
        }
        System.out.println("Schéma PostgreSQL absent — exécution V1__init.sql...");
        runClasspathSql(pg, "/db/migration/V1__init.sql");
        System.out.println("Schéma créé. Flyway fera un baseline au prochain démarrage de l'API.");
    }

    private static void runClasspathSql(Connection pg, String resourcePath) throws Exception {
        try (var in = H2ToPostgresMigrator.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Ressource introuvable : " + resourcePath);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement st = pg.createStatement()) {
                for (String chunk : sql.split(";")) {
                    String statement = chunk.trim();
                    if (statement.isEmpty() || statement.startsWith("--")) {
                        continue;
                    }
                    st.execute(statement);
                }
            }
        }
    }

    private static void truncateTarget(Connection pg) throws SQLException {
        System.out.println("Vidage des tables PostgreSQL...");
        try (Statement st = pg.createStatement()) {
            st.execute("""
                    TRUNCATE TABLE
                        people_counting_hourly,
                        app_user,
                        camera,
                        sync_meta
                    RESTART IDENTITY CASCADE
                    """);
        }
    }

    private static long copyCamera(Connection h2, Connection pg, boolean dryRun) throws SQLException {
        String select = """
                SELECT id, channel_id, name, site, active, manual, created_at, updated_at
                FROM camera ORDER BY id
                """;
        String insert = """
                INSERT INTO camera (id, channel_id, name, site, active, manual, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return copyRows(h2, pg, dryRun, "camera", select, insert, (rs, ps) -> {
            ps.setLong(1, rs.getLong("id"));
            ps.setString(2, rs.getString("channel_id"));
            ps.setString(3, rs.getString("name"));
            ps.setString(4, rs.getString("site"));
            ps.setBoolean(5, rs.getBoolean("active"));
            ps.setBoolean(6, rs.getBoolean("manual"));
            ps.setTimestamp(7, rs.getTimestamp("created_at"));
            ps.setTimestamp(8, rs.getTimestamp("updated_at"));
        });
    }

    private static long copyAppUser(Connection h2, Connection pg, boolean dryRun) throws SQLException {
        String select = """
                SELECT id, username, password_hash, role, enabled, is_system, created_at
                FROM app_user ORDER BY id
                """;
        String insert = """
                INSERT INTO app_user (id, username, password_hash, role, enabled, is_system, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        return copyRows(h2, pg, dryRun, "app_user", select, insert, (rs, ps) -> {
            ps.setLong(1, rs.getLong("id"));
            ps.setString(2, rs.getString("username"));
            ps.setString(3, rs.getString("password_hash"));
            ps.setString(4, rs.getString("role"));
            ps.setBoolean(5, rs.getBoolean("enabled"));
            ps.setBoolean(6, rs.getBoolean("is_system"));
            ps.setTimestamp(7, rs.getTimestamp("created_at"));
        });
    }

    private static long copyPeopleCounting(Connection h2, Connection pg, boolean dryRun) throws SQLException {
        String select = """
                SELECT id, camera_id, slot_date, hour_start, hour_end,
                       entries, exits, occupancy, synced_at,
                       row_hash, last_source_timestamp, finalized
                FROM people_counting_hourly ORDER BY id
                """;
        String insert = """
                INSERT INTO people_counting_hourly (
                    id, camera_id, slot_date, hour_start, hour_end,
                    entries, exits, occupancy, synced_at,
                    row_hash, last_source_timestamp, finalized
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        long total = 0;
        System.out.print("Copie people_counting_hourly...");
        try (Statement h2St = h2.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                ResultSet rs = h2St.executeQuery(select)) {
            h2St.setFetchSize(BATCH_SIZE);
            if (dryRun) {
                while (rs.next()) {
                    total++;
                }
                System.out.println(" " + total + " ligne(s) (dry-run)");
                return total;
            }
            try (PreparedStatement ps = pg.prepareStatement(insert)) {
                while (rs.next()) {
                    ps.setLong(1, rs.getLong("id"));
                    ps.setLong(2, rs.getLong("camera_id"));
                    ps.setObject(3, readLocalDate(rs, "slot_date"));
                    ps.setObject(4, readLocalTime(rs, "hour_start"));
                    ps.setObject(5, readLocalTime(rs, "hour_end"));
                    ps.setInt(6, rs.getInt("entries"));
                    ps.setInt(7, rs.getInt("exits"));
                    ps.setInt(8, rs.getInt("occupancy"));
                    ps.setTimestamp(9, rs.getTimestamp("synced_at"));
                    ps.setString(10, rs.getString("row_hash"));
                    setInstant(ps, 11, rs.getTimestamp("last_source_timestamp"));
                    setBooleanNullable(ps, 12, rs, "finalized");
                    ps.addBatch();
                    total++;
                    if (total % BATCH_SIZE == 0) {
                        ps.executeBatch();
                        System.out.print(".");
                    }
                }
                ps.executeBatch();
            }
        }
        System.out.println(" " + total + " ligne(s)");
        return total;
    }

    private static long copySyncMeta(Connection h2, Connection pg, boolean dryRun) throws SQLException {
        if (!tableExists(h2, "SYNC_META") && !tableExists(h2, "sync_meta")) {
            System.out.println("Table sync_meta absente en H2 — ligne par défaut conservée côté PG");
            return 0;
        }
        String select = """
                SELECT id, last_started_at, last_finished_at, last_from_date, last_to_date,
                       last_status, last_message, last_days_processed, last_rows_upserted,
                       last_poll_at, last_poll_date
                FROM sync_meta ORDER BY id
                """;
        String insert = """
                INSERT INTO sync_meta (
                    id, last_started_at, last_finished_at, last_from_date, last_to_date,
                    last_status, last_message, last_days_processed, last_rows_upserted,
                    last_poll_at, last_poll_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    last_started_at = EXCLUDED.last_started_at,
                    last_finished_at = EXCLUDED.last_finished_at,
                    last_from_date = EXCLUDED.last_from_date,
                    last_to_date = EXCLUDED.last_to_date,
                    last_status = EXCLUDED.last_status,
                    last_message = EXCLUDED.last_message,
                    last_days_processed = EXCLUDED.last_days_processed,
                    last_rows_upserted = EXCLUDED.last_rows_upserted,
                    last_poll_at = EXCLUDED.last_poll_at,
                    last_poll_date = EXCLUDED.last_poll_date
                """;
        return copyRows(h2, pg, dryRun, "sync_meta", select, insert, (rs, ps) -> {
            ps.setLong(1, rs.getLong("id"));
            ps.setTimestamp(2, rs.getTimestamp("last_started_at"));
            ps.setTimestamp(3, rs.getTimestamp("last_finished_at"));
            ps.setObject(4, readLocalDate(rs, "last_from_date"));
            ps.setObject(5, readLocalDate(rs, "last_to_date"));
            ps.setString(6, rs.getString("last_status"));
            ps.setString(7, rs.getString("last_message"));
            ps.setInt(8, rs.getInt("last_days_processed"));
            ps.setInt(9, rs.getInt("last_rows_upserted"));
            ps.setTimestamp(10, rs.getTimestamp("last_poll_at"));
            ps.setObject(11, readLocalDate(rs, "last_poll_date"));
        });
    }

    @FunctionalInterface
    private interface RowBinder {
        void bind(ResultSet rs, PreparedStatement ps) throws SQLException;
    }

    private static long copyRows(
            Connection h2,
            Connection pg,
            boolean dryRun,
            String label,
            String select,
            String insert,
            RowBinder binder) throws SQLException {
        long total = 0;
        System.out.print("Copie " + label + "...");
        try (Statement h2St = h2.createStatement();
                ResultSet rs = h2St.executeQuery(select)) {
            if (dryRun) {
                while (rs.next()) {
                    total++;
                }
                System.out.println(" " + total + " ligne(s) (dry-run)");
                return total;
            }
            try (PreparedStatement ps = pg.prepareStatement(insert)) {
                while (rs.next()) {
                    binder.bind(rs, ps);
                    ps.addBatch();
                    total++;
                }
                ps.executeBatch();
            }
        }
        System.out.println(" " + total + " ligne(s)");
        return total;
    }

    private static void resetSequences(Connection pg) throws SQLException {
        System.out.println("Réinitialisation des séquences PostgreSQL...");
        try (Statement st = pg.createStatement()) {
            for (String table : new String[] { "camera", "app_user", "people_counting_hourly" }) {
                st.execute("""
                        SELECT setval(
                            pg_get_serial_sequence('%s', 'id'),
                            COALESCE((SELECT MAX(id) FROM %s), 1)
                        )
                        """.formatted(table, table));
            }
        }
    }

    private static void printSummary(Connection h2, Connection pg, Map<String, Long> migrated, boolean dryRun)
            throws SQLException {
        System.out.println();
        System.out.println("=== Résumé ===");
        for (Map.Entry<String, Long> entry : migrated.entrySet()) {
            System.out.printf("  %-24s %d ligne(s) migrée(s)%n", entry.getKey() + " :", entry.getValue());
        }
        if (dryRun) {
            System.out.println();
            System.out.println("Dry-run terminé — relancez sans --dry-run pour écrire en PostgreSQL.");
            return;
        }
        System.out.println();
        System.out.println("Contrôles PostgreSQL :");
        try (Statement st = pg.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*), MIN(slot_date), MAX(slot_date) FROM people_counting_hourly")) {
                if (rs.next()) {
                    System.out.printf(
                            "  people_counting_hourly : count=%d min=%s max=%s%n",
                            rs.getLong(1),
                            rs.getObject(2),
                            rs.getObject(3));
                }
            }
        }
        System.out.println();
        System.out.println("Migration terminée. Lancez : docker compose up -d");
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, new String[] { "TABLE" })) {
            if (rs.next()) {
                return true;
            }
        }
        String other = table.equals(table.toUpperCase()) ? table.toLowerCase() : table.toUpperCase();
        try (ResultSet rs = conn.getMetaData().getTables(null, null, other, new String[] { "TABLE" })) {
            return rs.next();
        }
    }

    private static LocalDate readLocalDate(ResultSet rs, String column) throws SQLException {
        var date = rs.getObject(column, LocalDate.class);
        if (date != null) {
            return date;
        }
        var ts = rs.getTimestamp(column);
        return ts != null ? ts.toLocalDateTime().toLocalDate() : null;
    }

    private static LocalTime readLocalTime(ResultSet rs, String column) throws SQLException {
        var time = rs.getObject(column, LocalTime.class);
        if (time != null) {
            return time;
        }
        var ts = rs.getTimestamp(column);
        return ts != null ? ts.toLocalDateTime().toLocalTime() : null;
    }

    private static void setInstant(PreparedStatement ps, int index, Timestamp ts) throws SQLException {
        if (ts == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, ts);
        }
    }

    private static void setBooleanNullable(PreparedStatement ps, int index, ResultSet rs, String column)
            throws SQLException {
        boolean wasNull = rs.getObject(column) == null;
        if (wasNull) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, rs.getBoolean(column));
        }
    }

    private static String maskPassword(String jdbcUrl) {
        return jdbcUrl.replaceAll("(?i)(password=)[^&;]+", "$1***");
    }
}

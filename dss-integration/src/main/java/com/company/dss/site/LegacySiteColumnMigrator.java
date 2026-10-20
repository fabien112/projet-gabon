package com.company.dss.site;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.company.dss.persistence.entity.SiteEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reprise de l'ancienne colonne texte {@code camera.site} vers la table {@code site}.
 * <p>
 * H2 (ddl-auto=update) : Hibernate crée {@code site} et {@code camera.site_id} mais ne supprime
 * pas l'ancienne colonne ; ce runner relie les caméras puis la supprime.
 * PostgreSQL : déjà fait par Flyway (V2) — la colonne n'existe plus, rien à faire.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class LegacySiteColumnMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final SiteService siteService;

    @Override
    public void run(ApplicationArguments args) {
        if (!legacyColumnExists()) {
            return;
        }
        List<String> names = jdbcTemplate.queryForList(
                "SELECT DISTINCT TRIM(site) FROM camera WHERE site IS NOT NULL AND TRIM(site) <> ''",
                String.class
        );
        int linked = 0;
        for (String name : names) {
            SiteEntity site = siteService.findOrCreate(name);
            linked += jdbcTemplate.update(
                    "UPDATE camera SET site_id = ? WHERE site_id IS NULL AND LOWER(TRIM(site)) = LOWER(?)",
                    site.getId(),
                    name
            );
        }
        jdbcTemplate.execute("ALTER TABLE camera DROP COLUMN site");
        log.info(">>> [SITE] Reprise de l'ancienne colonne camera.site : {} site(s), {} caméra(s) reliée(s)",
                names.size(), linked);
    }

    private boolean legacyColumnExists() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE LOWER(table_schema) = 'public'
                  AND LOWER(table_name) = 'camera'
                  AND LOWER(column_name) = 'site'
                """,
                Integer.class
        );
        return count != null && count > 0;
    }
}

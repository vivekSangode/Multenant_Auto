package com.nyaalkaran.erp.poc.provisioning;

import com.nyaalkaran.erp.poc.tenant.TenantDataSourceFactory;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Liquibase per tenant (master §16.5): runs the module's changelog inside one company module database, then
 * validates it. Replaces "hbm2ddl=update while empty", which has no migration path for populated databases.
 */
@Component
@RequiredArgsConstructor
public class TenantSchemaMigrator {

    private static final String DEFAULT_CHANGELOG = "_default";

    private final TenantDataSourceFactory dataSourceFactory;
    private final ResourceLoader resourceLoader;

    /** @return the id of the last applied changeset (stored as tbl_company_resource.schema_version) */
    public String migrate(String databaseName, String dbToken, String moduleCode) throws Exception {
        String changelog = changelogFor(dbToken);
        try (HikariDataSource ds = dataSourceFactory.create(databaseName, 1)) {
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setDataSource(ds);
            liquibase.setChangeLog(changelog);
            liquibase.setResourceLoader(resourceLoader);
            liquibase.afterPropertiesSet();

            JdbcTemplate jdbc = new JdbcTemplate(ds);
            if (changelog.endsWith(DEFAULT_CHANGELOG + ".sql")) {
                jdbc.update("INSERT IGNORE INTO module_info (id, module_code, bootstrapped_at) VALUES (1, ?, NOW(6))",
                        moduleCode);
            }
            return jdbc.queryForObject(
                    "SELECT id FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED DESC LIMIT 1", String.class);
        }
    }

    private String changelogFor(String dbToken) {
        String specific = "classpath:db/tenant/" + dbToken + ".sql";
        return resourceLoader.getResource(specific).exists()
                ? specific : "classpath:db/tenant/" + DEFAULT_CHANGELOG + ".sql";
    }
}

package com.nyaalkaran.erp.poc.tenant;

import com.nyaalkaran.erp.poc.common.PocProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Builds a pool for one company database with the ENVIRONMENT credentials (CD-03): same login for every
 * company, no per-company MySQL user; the database name comes only from the CP registry.
 */
@Component
@RequiredArgsConstructor
public class TenantDataSourceFactory {

    private static final Pattern COMPANY_DB = Pattern.compile("^cmp_[0-9]{1,8}_[a-z0-9_]{1,40}_db$");

    private final PocProperties props;

    public HikariDataSource create(String databaseName, int maxPoolSize) {
        if (!COMPANY_DB.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("Not a company database name: " + databaseName);
        }
        PocProperties.TenantDb db = props.tenantDb();
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("tenant-" + databaseName);
        cfg.setJdbcUrl("jdbc:mysql://" + db.host() + ":" + db.port() + "/" + databaseName
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        cfg.setUsername(db.username());
        cfg.setPassword(db.password());
        cfg.setMaximumPoolSize(maxPoolSize);
        cfg.setMinimumIdle(0);
        cfg.setIdleTimeout(60_000);
        cfg.setInitializationFailTimeout(-1);
        return new HikariDataSource(cfg);
    }
}

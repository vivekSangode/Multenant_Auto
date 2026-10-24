package com.nyaalkaran.erp.tenant.routing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.nyaalkaran.erp.tenant.TenantProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One small pool per company database, built with the ENVIRONMENT credentials (CD-03): same login for every
 * company, the database name comes only from the registry. Idle pools are closed.
 */
public class TenantDataSourceRegistry {

    private static final Pattern COMPANY_DB = Pattern.compile("^cmp_[0-9]{1,8}_[a-z0-9_]{1,40}_db$");

    private final TenantProperties.Db db;
    private final Cache<String, HikariDataSource> pools = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30)).maximumSize(500)
            .removalListener((String key, HikariDataSource ds, RemovalCause cause) -> {
                if (ds != null) {
                    ds.close();
                }
            })
            .build();

    public TenantDataSourceRegistry(TenantProperties props) {
        this.db = props.db();
    }

    /** @throws IllegalStateException if the registry answer does not belong to the company (defence in depth) */
    public DataSource dataSource(String companyCode, DatabaseLocation location) {
        String name = location.databaseName();
        if (!COMPANY_DB.matcher(name).matches() || !name.startsWith(companyCode.toLowerCase(Locale.ROOT) + "_")) {
            throw new IllegalStateException("Database " + name + " is not a database of " + companyCode);
        }
        String key = location.host() + ":" + location.port() + "/" + name;
        return pools.get(key, k -> create(location));
    }

    private HikariDataSource create(DatabaseLocation location) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("tenant-" + location.databaseName());
        cfg.setJdbcUrl("jdbc:mysql://" + location.host() + ":" + location.port() + "/" + location.databaseName()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        cfg.setUsername(db.username());
        cfg.setPassword(db.password());
        cfg.setMaximumPoolSize(db.maxPoolSize());
        cfg.setMinimumIdle(0);
        cfg.setIdleTimeout(60_000);
        cfg.setInitializationFailTimeout(-1);
        return new HikariDataSource(cfg);
    }

    @PreDestroy
    void shutdown() {
        pools.invalidateAll();
    }
}

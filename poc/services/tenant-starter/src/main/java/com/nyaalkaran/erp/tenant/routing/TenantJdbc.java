package com.nyaalkaran.erp.tenant.routing;

import com.nyaalkaran.erp.tenant.context.TenantContext;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The only way a repository reaches data: a JdbcClient bound to the CURRENT company's own database for this
 * service's module. There is no static datasource and no company column (AD-03, master §15.3).
 */
public class TenantJdbc {

    private final TenantDatabaseLocator locator;
    private final TenantDataSourceRegistry dataSources;

    public TenantJdbc(TenantDatabaseLocator locator, TenantDataSourceRegistry dataSources) {
        this.locator = locator;
        this.dataSources = dataSources;
    }

    public JdbcClient current() {
        String companyCode = TenantContext.require().companyCode();
        return JdbcClient.create(dataSources.dataSource(companyCode, locator.locate(companyCode)));
    }

    /** For diagnostics: which physical database serves the current company. */
    public String currentDatabaseName() {
        return locator.locate(TenantContext.require().companyCode()).databaseName();
    }
}

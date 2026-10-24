package com.nyaalkaran.erp.tenant.web;

import com.nyaalkaran.erp.tenant.TenantProperties;
import com.nyaalkaran.erp.tenant.context.TenantContext;
import com.nyaalkaran.erp.tenant.routing.TenantJdbc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** GET /api/v1/{module}/_routing on every service: which company the token resolved to and which DB served it. */
@RestController
public class TenantRoutingInfoController {

    private final TenantJdbc jdbc;
    private final TenantProperties props;

    public TenantRoutingInfoController(TenantJdbc jdbc, TenantProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @GetMapping("/api/v1/${erp.tenant.module-code}/_routing")
    public Map<String, String> routing() {
        TenantContext.CurrentTenant t = TenantContext.require();
        return Map.of("companyCode", t.companyCode(),
                "user", t.username(),
                "module", props.moduleCode(),
                "database", jdbc.currentDatabaseName(),
                "connectedTo", jdbc.current().sql("SELECT DATABASE()").query(String.class).single());
    }
}

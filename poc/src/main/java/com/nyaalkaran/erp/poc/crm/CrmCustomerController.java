package com.nyaalkaran.erp.poc.crm;

import com.nyaalkaran.erp.poc.tenant.TenantContext;
import com.nyaalkaran.erp.poc.tenant.TenantRoutingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A sample domain-service endpoint (stand-in for CRM-SERVICE). It contains no tenant logic at all: the
 * interceptor verified company + entitlement, and the routing service hands it the company's own
 * cmp_n_crm_db. There is no company column - isolation is physical (AD-03).
 */
@RestController
@RequestMapping("/api/v1/tenant/crm")
@RequiredArgsConstructor
public class CrmCustomerController {

    private final TenantRoutingService routing;

    public record CreateCustomer(@NotBlank @Size(max = 40) String customerCode, @NotBlank @Size(max = 150) String name,
                                 @Email @Size(max = 320) String email, @Size(max = 30) String phone) {
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody CreateCustomer req) {
        JdbcTemplate jdbc = routing.currentJdbc();
        jdbc.update("INSERT INTO crm_customer (customer_code, name, email, phone, created_at, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?)", req.customerCode(), req.name(), req.email(), req.phone(),
                LocalDateTime.now(), "api");
        return jdbc.queryForMap("SELECT * FROM crm_customer WHERE customer_code = ?", req.customerCode());
    }

    @GetMapping("/customers")
    public List<Map<String, Object>> list() {
        return routing.currentJdbc().queryForList("SELECT * FROM crm_customer ORDER BY id");
    }

    /** Shows which physical database served this request (demo of the routing). */
    @GetMapping("/_routing")
    public Map<String, String> routingInfo() {
        TenantContext.Tenant t = TenantContext.require();
        return Map.of("companyCode", t.companyCode(), "module", t.moduleCode(),
                "database", routing.resolveDatabase(t.companyCode(), t.moduleCode()),
                "connectedTo", routing.currentJdbc().queryForObject("SELECT DATABASE()", String.class));
    }
}

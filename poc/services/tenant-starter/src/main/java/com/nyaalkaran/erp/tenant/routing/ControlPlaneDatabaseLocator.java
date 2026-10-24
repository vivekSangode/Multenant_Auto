package com.nyaalkaran.erp.tenant.routing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nyaalkaran.erp.tenant.TenantProperties;
import com.nyaalkaran.erp.tenant.controlplane.ControlPlaneClient;
import com.nyaalkaran.erp.tenant.web.ServiceException;
import org.springframework.web.client.ResourceAccessException;

/** Registry lookup through the control plane, cached per company (PHASE_01 §6.6.1 "metadata(CMP_n, …), cached"). */
public class ControlPlaneDatabaseLocator implements TenantDatabaseLocator {

    private final ControlPlaneClient controlPlane;
    private final String moduleCode;
    private final Cache<String, DatabaseLocation> cache;

    public ControlPlaneDatabaseLocator(ControlPlaneClient controlPlane, TenantProperties props) {
        this.controlPlane = controlPlane;
        this.moduleCode = props.moduleCode();
        this.cache = Caffeine.newBuilder().expireAfterWrite(props.metadataTtl()).maximumSize(10_000).build();
    }

    @Override
    public DatabaseLocation locate(String companyCode) {
        DatabaseLocation cached = cache.getIfPresent(companyCode);
        if (cached != null) {
            return cached;
        }
        try {
            DatabaseLocation location = controlPlane.database(companyCode, moduleCode);
            cache.put(companyCode, location);
            return location;
        } catch (ResourceAccessException ex) {
            throw ServiceException.unavailable("CONTROL_PLANE_UNAVAILABLE", "The company registry cannot be reached");
        }
    }
}

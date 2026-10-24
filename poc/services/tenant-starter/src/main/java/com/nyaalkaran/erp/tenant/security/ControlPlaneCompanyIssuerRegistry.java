package com.nyaalkaran.erp.tenant.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nyaalkaran.erp.tenant.TenantProperties;
import com.nyaalkaran.erp.tenant.controlplane.ControlPlaneClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Asks the control plane (registry of IDP_REALM rows). Only known issuers are cached, so a newly provisioned
 * company is accepted at once; an unreachable control plane means "unknown" (fail-closed -> 401).
 */
public class ControlPlaneCompanyIssuerRegistry implements CompanyIssuerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneCompanyIssuerRegistry.class);

    private final ControlPlaneClient controlPlane;
    private final Cache<String, String> cache;

    public ControlPlaneCompanyIssuerRegistry(ControlPlaneClient controlPlane, TenantProperties props) {
        this.controlPlane = controlPlane;
        this.cache = Caffeine.newBuilder().expireAfterWrite(props.metadataTtl()).maximumSize(10_000).build();
    }

    @Override
    public Optional<String> companyFor(String issuer) {
        String cached = cache.getIfPresent(issuer);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            Optional<String> company = controlPlane.companyOfIssuer(issuer);
            company.ifPresent(c -> cache.put(issuer, c));
            return company;
        } catch (RuntimeException ex) {
            log.warn("Issuer lookup for {} failed, treating it as unknown: {}", issuer, ex.getMessage());
            return Optional.empty();
        }
    }
}

package com.nyaalkaran.erp.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration of one domain service. The service declares only WHICH module it is; everything about WHICH
 * company is decided per request from the verified token (master §14.3 rule 1).
 *
 * @param moduleCode   catalog code of the module this service serves, e.g. {@code vehicle}
 * @param gatewayToken entitlement token in COMPANY_MODULES, e.g. {@code VEHICLE}
 * @param metadataTtl  how long registry answers of the control plane are cached
 */
@ConfigurationProperties(prefix = "erp.tenant")
public record TenantProperties(
        String moduleCode,
        String gatewayToken,
        @DefaultValue Keycloak keycloak,
        @DefaultValue ControlPlane controlPlane,
        @DefaultValue Db db,
        @DefaultValue("30s") Duration metadataTtl,
        @DefaultValue("http://localhost:4200") java.util.List<String> corsAllowedOrigins) {

    /** Issuer base + this service's M2M client in the platform realm (master §15.2). */
    public record Keycloak(@DefaultValue("http://localhost:8180") String url,
                           @DefaultValue("erp-platform") String platformRealm,
                           @DefaultValue("nyaalkaran-erp") String audience,
                           String clientId,
                           String clientSecret) {

        public String issuer(String realm) {
            return url + "/realms/" + realm;
        }
    }

    public record ControlPlane(@DefaultValue("http://localhost:8080") String url) {
    }

    /** CD-03: the same environment credentials for every company database. */
    public record Db(@DefaultValue("erp_app") String username,
                     @DefaultValue("erp_app_pass") String password,
                     @DefaultValue("5") int maxPoolSize) {
    }
}

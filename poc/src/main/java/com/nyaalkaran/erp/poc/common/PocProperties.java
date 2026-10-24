package com.nyaalkaran.erp.poc.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "poc")
public record PocProperties(
        TenantDb tenantDb,
        Storage storage,
        Provisioning provisioning,
        Cache cache,
        Billing billing,
        Keycloak keycloak,
        Internal internal,
        Cors cors) {

    /** Browser origins of the portals (poc/frontend). */
    public record Cors(java.util.List<String> allowedOrigins) {
    }

    /** master §15.2 rule 3: internal registry APIs only for M2M clients (azp) on this allow-list. */
    public record Internal(java.util.List<String> allowedClients) {
    }

    /** Tax values are PENDING (Q-04, CD-25); 0 means "no approved tax rule" and adds a warning. */
    public record Billing(java.math.BigDecimal taxRatePercent, int quoteValidityDays) {
    }

    /** CD-03: environment-level DB credentials, identical for every company database. */
    public record TenantDb(String host, int port, String username, String password, int maxPoolSize) {
    }

    public record Storage(boolean enabled, String endpoint, String accessKey, String secretKey) {
    }

    public record Provisioning(boolean workerEnabled, long pollIntervalMs, int leaseSeconds, int maxAttempts,
                               int backoffBaseSeconds, int backoffMaxSeconds) {
    }

    public record Cache(int ttlSeconds) {
    }

    /** Keycloak server, platform realm and the realm-provisioning service account (CD-06, CD-17). */
    public record Keycloak(String url, String platformRealm, String audience, String provisionerClientId,
                           String provisionerClientSecret, boolean sendInviteEmail, String webRedirectUri) {

        /** The exact `iss` of the realm's tokens. */
        public String issuer(String realm) {
            return url + "/realms/" + realm;
        }
    }
}

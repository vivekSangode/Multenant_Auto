package com.nyaalkaran.erp.poc.provisioning;

import com.nyaalkaran.erp.poc.common.CompanyRoles;
import com.nyaalkaran.erp.poc.common.PocProperties;
import com.nyaalkaran.erp.poc.tenant.CompanyRealmIssuers;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The company realm "template as code" of the POC (PHASE_01 §6.5.3, §6.5.8, §6.5.9), applied through the Keycloak
 * admin API with the `erp-realm-provisioner` service account of `master` (CD-17). Every method is
 * "create if missing, repair if drifted", never "create or fail" (master §7.3), and never deletes anything.
 */
@Slf4j
@Component
public class RealmProvisioner {

    public static final String WEB_CLIENT = "erp-web";
    private static final String TENANT_MAPPER = "tenant_id (hard-coded)";
    private static final String AUDIENCE_MAPPER = "audience nyaalkaran-erp";
    private static final int INVITE_LIFESPAN_SECONDS = 7 * 24 * 3600;

    private final PocProperties.Keycloak cfg;
    private final Keycloak keycloak;

    public RealmProvisioner(PocProperties props) {
        this.cfg = props.keycloak();
        // Lazy: no call to Keycloak until the first provisioning step needs it.
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(cfg.url())
                .realm("master")
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(cfg.provisionerClientId())
                .clientSecret(cfg.provisionerClientSecret())
                .build();
    }

    @PreDestroy
    void close() {
        keycloak.close();
    }

    /** CREATE_REALM: realm co-<n> + client erp-web (tenant_id and audience mappers) + the predefined realm roles. */
    public void ensureRealm(String realmName, String companyCode, String displayName, Collection<String> roles) {
        if (!realmExists(realmName)) {
            RealmRepresentation rep = new RealmRepresentation();
            rep.setRealm(realmName);
            rep.setDisplayName(displayName);
            rep.setEnabled(true);
            rep.setAccessTokenLifespan(300);          // master §14.1: access tokens live <= 5 minutes
            rep.setBruteForceProtected(true);
            rep.setSslRequired("external");
            try {
                keycloak.realms().create(rep);
                log.info("Created realm {} for {}", realmName, companyCode);
            } catch (ClientErrorException ex) {
                if (ex.getResponse().getStatus() != 409) {
                    throw ex;
                }
            }
        }
        RealmResource realm = keycloak.realm(realmName);
        ensureWebClient(realm, companyCode);
        ensureRoles(realm, roles);
    }

    /** Re-reads the realm and fails unless it matches the template; only then is IDP_REALM VERIFIED. */
    public void verify(String realmName, String companyCode, Collection<String> roles) {
        RealmResource realm = keycloak.realm(realmName);
        if (!Boolean.TRUE.equals(realm.toRepresentation().isEnabled())) {
            throw new IllegalStateException("Realm " + realmName + " is disabled");
        }
        ClientRepresentation client = realm.clients().findByClientId(WEB_CLIENT).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(WEB_CLIENT + " missing in " + realmName));
        boolean tenantClaimOk = realm.clients().get(client.getId()).getProtocolMappers().getMappers().stream()
                .anyMatch(m -> TENANT_MAPPER.equals(m.getName())
                        && companyCode.equals(m.getConfig().get("claim.value")));
        if (!tenantClaimOk) {
            throw new IllegalStateException("tenant_id mapper of " + realmName + " is not " + companyCode);
        }
        Set<String> present = realmRoleNames(realm);
        List<String> missing = roles.stream().filter(r -> !present.contains(r)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Roles missing in " + realmName + ": " + missing);
        }
    }

    /**
     * INVITE_COMPANY_SUPER_ADMIN: the first user of the company realm, username = e-mail, realm role
     * COMPANY_SUPER_ADMIN, required actions UPDATE_PASSWORD + VERIFY_EMAIL (no password is ever set by the CP).
     * The execute-actions e-mail is sent only when SMTP is configured (CD-19; off in the POC).
     *
     * @return the Keycloak user id
     */
    public String inviteCompanySuperAdmin(String realmName, String email) {
        RealmResource realm = keycloak.realm(realmName);
        UsersResource users = realm.users();
        String userId = findUserId(users, email);
        if (userId == null) {
            UserRepresentation u = new UserRepresentation();
            u.setUsername(email);
            u.setEmail(email);
            u.setFirstName("Company");
            u.setLastName("Super Admin");
            u.setEnabled(true);
            u.setEmailVerified(false);
            u.setRequiredActions(List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
            try (Response r = users.create(u)) {
                if (r.getStatus() == 201) {
                    userId = CreatedResponseUtil.getCreatedId(r);
                } else if (r.getStatus() == 409) {
                    userId = findUserId(users, email);
                } else {
                    throw new IllegalStateException("Creating the Company Super Admin in " + realmName
                            + " returned HTTP " + r.getStatus());
                }
            }
        }
        RoleRepresentation role = realm.roles().get(CompanyRoles.COMPANY_SUPER_ADMIN).toRepresentation();
        users.get(userId).roles().realmLevel().add(List.of(role));
        if (cfg.sendInviteEmail()) {
            users.get(userId).executeActionsEmail(List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"), INVITE_LIFESPAN_SECONDS);
        } else {
            log.info("Invitation e-mail for the Company Super Admin of {} not sent (poc.keycloak.send-invite-email=false)",
                    realmName);
        }
        return userId;
    }

    /**
     * POC stand-in for the invitation e-mail (no SMTP, CD-19): a random one-time password that Keycloak forces the
     * user to replace at the first login (UPDATE_PASSWORD). It is returned once and never stored or logged.
     */
    public String issueTemporaryPassword(String realmName, String email) {
        UsersResource users = keycloak.realm(realmName).users();
        String userId = findUserId(users, email);
        if (userId == null) {
            throw new IllegalStateException("No user " + email + " in " + realmName);
        }
        String password = randomPassword();
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(true);
        users.get(userId).resetPassword(credential);
        UserRepresentation u = users.get(userId).toRepresentation();
        u.setEmailVerified(true);                       // nothing could verify it without SMTP
        u.setRequiredActions(List.of("UPDATE_PASSWORD"));
        users.get(userId).update(u);
        return password;
    }

    private static String randomPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder("Tmp-");
        for (int i = 0; i < 12; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private boolean realmExists(String realmName) {
        try {
            keycloak.realm(realmName).toRepresentation();
            return true;
        } catch (NotFoundException ex) {
            return false;
        }
    }

    private void ensureWebClient(RealmResource realm, String companyCode) {
        var existing = realm.clients().findByClientId(WEB_CLIENT).stream().findFirst();
        if (existing.isEmpty()) {
            ClientRepresentation c = new ClientRepresentation();
            c.setClientId(WEB_CLIENT);
            c.setName("Nyaalkaran ERP web");
            c.setPublicClient(true);
            c.setStandardFlowEnabled(true);
            // POC only: password grant so that tests and http/demo-flow.http can obtain tokens without a browser
            c.setDirectAccessGrantsEnabled(true);
            c.setFullScopeAllowed(true);             // CD-14: the realm holds only company roles
            c.setRedirectUris(List.of(cfg.webRedirectUri()));
            c.setWebOrigins(List.of("+"));
            c.setAttributes(Map.of("pkce.code.challenge.method", "S256"));
            c.setProtocolMappers(List.of(tenantMapper(companyCode), audienceMapper()));
            try (Response r = realm.clients().create(c)) {
                if (r.getStatus() != 201 && r.getStatus() != 409) {
                    throw new IllegalStateException("Creating " + WEB_CLIENT + " returned HTTP " + r.getStatus());
                }
            }
            return;
        }
        ProtocolMappersResource mappers = realm.clients().get(existing.get().getId()).getProtocolMappers();
        ensureMapper(mappers, tenantMapper(companyCode));
        ensureMapper(mappers, audienceMapper());
    }

    private static void ensureMapper(ProtocolMappersResource mappers, ProtocolMapperRepresentation wanted) {
        var current = mappers.getMappers().stream().filter(m -> wanted.getName().equals(m.getName())).findFirst();
        if (current.isEmpty()) {
            try (Response r = mappers.createMapper(wanted)) {
                if (r.getStatus() != 201 && r.getStatus() != 409) {
                    throw new IllegalStateException("Creating mapper " + wanted.getName() + " returned HTTP "
                            + r.getStatus());
                }
            }
        } else if (!wanted.getConfig().entrySet().stream()
                .allMatch(e -> Objects.equals(e.getValue(), current.get().getConfig().get(e.getKey())))) {
            wanted.setId(current.get().getId());
            mappers.update(current.get().getId(), wanted);
        }
    }

    private static void ensureRoles(RealmResource realm, Collection<String> roles) {
        Set<String> present = realmRoleNames(realm);
        for (String role : roles) {
            if (!present.contains(role)) {
                try {
                    realm.roles().create(new RoleRepresentation(role, "Predefined company role (CD-14)", false));
                } catch (ClientErrorException ex) {
                    if (ex.getResponse().getStatus() != 409) {
                        throw ex;
                    }
                }
            }
        }
    }

    private static Set<String> realmRoleNames(RealmResource realm) {
        return realm.roles().list().stream().map(RoleRepresentation::getName).collect(Collectors.toSet());
    }

    private static String findUserId(UsersResource users, String email) {
        return users.searchByEmail(email, true).stream().findFirst().map(UserRepresentation::getId).orElse(null);
    }

    /** The realm itself stamps the company: a hard-coded claim, never a user attribute (PHASE_01 §6.5.1 rule 1). */
    private static ProtocolMapperRepresentation tenantMapper(String companyCode) {
        return mapper(TENANT_MAPPER, "oidc-hardcoded-claim-mapper", Map.of(
                "claim.name", CompanyRealmIssuers.TENANT_CLAIM,
                "claim.value", companyCode,
                "jsonType.label", "String",
                "access.token.claim", "true",
                "id.token.claim", "true",
                "userinfo.token.claim", "true",
                "introspection.token.claim", "true"));
    }

    private ProtocolMapperRepresentation audienceMapper() {
        return mapper(AUDIENCE_MAPPER, "oidc-audience-mapper", Map.of(
                "included.custom.audience", cfg.audience(),
                "access.token.claim", "true",
                "id.token.claim", "false",
                "introspection.token.claim", "true"));
    }

    private static ProtocolMapperRepresentation mapper(String name, String type, Map<String, String> config) {
        ProtocolMapperRepresentation m = new ProtocolMapperRepresentation();
        m.setName(name);
        m.setProtocol("openid-connect");
        m.setProtocolMapper(type);
        m.setConfig(config);
        return m;
    }
}

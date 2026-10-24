package com.nyaalkaran.erp.poc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.MountableFile;

import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole R1 admin flow against real MySQL, Redis, MinIO and Keycloak (Testcontainers):
 * company -> quote -> confirm -> full manual payment -> provisioning (realm co-n + Company Super Admin, DBs,
 * buckets) -> tenant routing with company-realm tokens -> upgrade -> suspension.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "poc.provisioning.poll-interval-ms=300",
        "poc.cache.resync-interval-ms=600000"
})
class EndToEndFlowTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("control_panel_db")
            .withUsername("erp_app")
            .withPassword("erp_app_pass")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/mysql-init/01-users.sql"),
                    "/docker-entrypoint-initdb.d/01-users.sql");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4").withExposedPorts(6379);
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2025-09-07T16-13-09Z-cpuv1");
    /** Same image, realm import and service account as docker-compose.yml. */
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.3.5")
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_CLIENT_ID", "erp-realm-provisioner")
            .withEnv("KC_BOOTSTRAP_ADMIN_CLIENT_SECRET", "erp-realm-provisioner-secret")
            .withCopyFileToContainer(MountableFile.forHostPath("keycloak/erp-platform-realm.json"),
                    "/opt/keycloak/data/import/erp-platform-realm.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/erp-platform/.well-known/openid-configuration").forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    static final String KEYCLOAK_URL;
    /** The operator's Keycloak console login: simulates what users do in their browser (accept an invitation). */
    static final Keycloak OPERATOR;

    static {
        Startables.deepStart(MYSQL, REDIS, MINIO, KEYCLOAK).join();
        KEYCLOAK_URL = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
        OPERATOR = Keycloak.getInstance(KEYCLOAK_URL, "master", "admin", "admin", "admin-cli");
    }

    @AfterAll
    static void closeOperator() {
        OPERATOR.close();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true");
        r.add("poc.tenant-db.host", MYSQL::getHost);
        r.add("poc.tenant-db.port", () -> MYSQL.getMappedPort(3306));
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("poc.storage.endpoint", MINIO::getS3URL);
        r.add("poc.storage.access-key", MINIO::getUserName);
        r.add("poc.storage.secret-key", MINIO::getPassword);
        r.add("poc.keycloak.url", () -> KEYCLOAK_URL);
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;

    private final RestTemplate keycloakHttp = new RestTemplate();
    private final Map<String, CachedToken> tokens = new HashMap<>();

    private record CachedToken(String value, long fetchedAt) {
    }

    @Test
    void fullCompanyLifecycle() throws Exception {
        // 1. Two companies in DRAFT with race-safe CMP_n codes
        String acme = createCompany("Acme Minerals Pvt Ltd", "owner@acme.test");
        String beta = createCompany("Beta Mining Co", "owner@beta.test");
        assertThat(acme).matches("CMP_\\d+");
        assertThat(beta).isNotEqualTo(acme);

        // 2. Quote: crm + store (store pulls its mandatory account-book dependency), 8 users
        JsonNode order = post("/api/v1/platform/companies/" + acme + "/orders", Map.of(
                "billingPeriod", "MONTHLY", "modules", List.of("crm", "store"), "users", 8, "storageGb", 10));
        long orderId = order.at("/order/id").asLong();
        assertThat(order.at("/order/grandTotal").decimalValue()).isEqualByComparingTo("760"); // 300+150+250+3x20
        assertThat(order.at("/order/status").asText()).isEqualTo("QUOTED");
        post("/api/v1/platform/orders/" + orderId + "/confirm", Map.of());

        // 3. Full manual payment: cash now + cheque that needs clearance (CD-07, CD-20)
        post("/api/v1/platform/orders/" + orderId + "/payments", Map.of("mode", "CASH", "amount", 400));
        JsonNode afterCheque = post("/api/v1/platform/orders/" + orderId + "/payments",
                Map.of("mode", "CHEQUE", "amount", 360, "instrumentRef", "CHQ-000123", "bankName", "SBI"));
        assertThat(afterCheque.at("/order/paymentStatus").asText()).isEqualTo("PENDING_VERIFICATION");
        // over-payment is refused
        assertThat(call(HttpMethod.POST, "/api/v1/platform/orders/" + orderId + "/payments",
                Map.of("mode", "UPI", "amount", 1), platformToken()).getStatusCode().value()).isEqualTo(409);
        // no provisioning before 100 % is verified
        ResponseEntity<String> early = call(HttpMethod.POST, "/api/v1/platform/companies/" + acme + "/provision",
                null, platformToken());
        assertThat(early.getStatusCode().value()).isEqualTo(409);
        assertThat(early.getBody()).contains("SUBSCRIPTION_NOT_ACTIVE");

        long chequeId = afterCheque.at("/payments/1/id").asLong();
        post("/api/v1/platform/payments/" + chequeId + "/verify", Map.of());
        JsonNode paid = get("/api/v1/platform/orders/" + orderId);
        assertThat(paid.at("/order/status").asText()).isEqualTo("FULFILLED");
        assertThat(paid.at("/invoice/status").asText()).isEqualTo("ISSUED_PAID");
        assertThat(paid.at("/invoice/balanceDue").decimalValue()).isEqualByComparingTo("0");

        // Overlapping bundles (CD-24): both sold at full price with BUNDLE_OVERLAP; paying must not hit the charged
        // guard, and the shared module (production) is granted by both bundles but charged once.
        String gamma = createCompany("Gamma Overlap Ltd", "owner@gamma.test");
        JsonNode overlap = post("/api/v1/platform/companies/" + gamma + "/orders", Map.of(
                "billingPeriod", "MONTHLY", "bundles", List.of("SALES_DISPATCH", "PLANT_OPERATIONS"), "users", 5, "storageGb", 10));
        assertThat(overlap.at("/warnings").toString()).contains("BUNDLE_OVERLAP");
        long overlapId = overlap.at("/order/id").asLong();
        post("/api/v1/platform/orders/" + overlapId + "/confirm", Map.of());
        post("/api/v1/platform/orders/" + overlapId + "/payments",
                Map.of("mode", "UPI", "amount", overlap.at("/order/grandTotal").decimalValue()));
        assertThat(get("/api/v1/platform/orders/" + overlapId).at("/order/status").asText()).isEqualTo("FULFILLED");
        List<JsonNode> production = StreamSupport.stream(get("/api/v1/platform/companies/" + gamma + "/overview")
                .get("entitlements").spliterator(), false).filter(e -> "production".equals(e.get("module").asText())).toList();
        assertThat(production).hasSize(2);
        assertThat(production.stream().filter(e -> e.get("charged").asBoolean())).hasSize(1);

        // 4. Provision: realm co-n + Company Super Admin, DB per entitled module + attachment + notification,
        //    Liquibase per tenant, buckets
        post("/api/v1/platform/companies/" + acme + "/provision", null);
        awaitStatus(acme, "ACTIVE");
        JsonNode overview = get("/api/v1/platform/companies/" + acme + "/overview");
        String n = acme.substring(4);
        String acmeRealm = "co-" + n;
        assertThat(resourceNames(overview)).contains("cmp_" + n + "_crm_db", "cmp_" + n + "_store_db",
                "cmp_" + n + "_account_book_db", "cmp_" + n + "_attachment_db", "cmp_" + n + "_notification_db",
                "cmp-" + n + "-crm-bucket", "cmp-" + n + "-control-panel-bucket", acmeRealm);
        assertThat(resourceStatus(overview, acmeRealm)).isEqualTo("IDP_REALM:VERIFIED");
        assertThat(overview.at("/runtimeCache/modules").toString()).contains("CRM", "STORE", "ACCOUNT-BOOK");
        assertThat(overview.at("/allowances").toString()).contains("\"effectiveLimit\":8");
        assertThat(jdbc.queryForList("SHOW DATABASES LIKE 'cmp\\_" + n + "\\_%'", String.class)).hasSize(5);

        // the realm follows the template: company roles + module roles of the purchased modules only
        assertThat(realmRoles(acmeRealm)).contains("COMPANY_SUPER_ADMIN", "COMPANY_ADMIN", "CRM_USER",
                "CRM_SUPER_ADMIN", "STORE_MANAGER", "ACCOUNT_BOOK_USER").doesNotContain("FILE_USER", "ASSET_USER");
        // the Company Super Admin was invited: no password, must set one and verify the e-mail
        UserRepresentation owner = OPERATOR.realm(acmeRealm).users().searchByEmail("owner@acme.test", true).get(0);
        assertThat(owner.getRequiredActions()).contains("UPDATE_PASSWORD", "VERIFY_EMAIL");
        assertThat(OPERATOR.realm(acmeRealm).users().get(owner.getId()).roles().realmLevel().listEffective())
                .extracting(RoleRepresentation::getName).contains("COMPANY_SUPER_ADMIN");

        acceptInvitation(acmeRealm, "owner@acme.test", "Acme-Owner-1");
        JsonNode claims = claims(companyToken(acmeRealm, "owner@acme.test", "Acme-Owner-1"));
        assertThat(claims.get("tenant_id").asText()).isEqualTo(acme);
        assertThat(claims.get("iss").asText()).isEqualTo(KEYCLOAK_URL + "/realms/" + acmeRealm);
        assertThat(claims.get("aud").toString()).contains("nyaalkaran-erp");

        // internal registry API for the domain services: M2M token of an allow-listed svc-* client only
        String svc = serviceToken("svc-vehicle", "svc-vehicle-secret");
        assertThat(call(HttpMethod.GET, "/api/v1/internal/realms/company?issuer=" + claims.get("iss").asText(),
                null, svc).getBody()).contains(acme);
        assertThat(call(HttpMethod.GET, "/api/v1/internal/companies/" + acme + "/modules/crm/database", null, svc)
                .getBody()).contains("cmp_" + n + "_crm_db");
        assertThat(call(HttpMethod.GET, "/api/v1/internal/realms/company?issuer=" + KEYCLOAK_URL + "/realms/co-999",
                null, svc).getStatusCode().value()).isEqualTo(404);
        assertThat(call(HttpMethod.GET, "/api/v1/internal/companies/" + acme + "/modules/crm/database", null,
                platformToken()).getStatusCode().value()).isEqualTo(403);

        // 5. Beta buys only ASSET (UPI, immediate) and is provisioned
        long betaOrder = post("/api/v1/platform/companies/" + beta + "/orders", Map.of(
                "billingPeriod", "YEARLY", "modules", List.of("asset"), "users", 3, "storageGb", 5))
                .at("/order/id").asLong();
        post("/api/v1/platform/orders/" + betaOrder + "/confirm", Map.of());
        post("/api/v1/platform/orders/" + betaOrder + "/payments", Map.of("mode", "UPI", "amount", 1000));
        post("/api/v1/platform/companies/" + beta + "/provision", null);
        awaitStatus(beta, "ACTIVE");
        String betaRealm = "co-" + beta.substring(4);
        acceptInvitation(betaRealm, "owner@beta.test", "Beta-Owner-1");

        // 6. Tenant business API: company from the verified token, routed to the company's own database
        String acmeOwner = companyToken(acmeRealm, "owner@acme.test", "Acme-Owner-1");
        String betaOwner = companyToken(betaRealm, "owner@beta.test", "Beta-Owner-1");
        ResponseEntity<String> created = call(HttpMethod.POST, "/api/v1/tenant/crm/customers",
                Map.of("customerCode", "C-001", "name", "Rajesh Traders"), acmeOwner);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(tenantGet("/api/v1/tenant/crm/_routing", acmeOwner).getBody()).contains("cmp_" + n + "_crm_db");
        assertThat(tenantGet("/api/v1/tenant/crm/customers", betaOwner).getBody()).contains("MODULE_NOT_ENTITLED");

        // a client-supplied company header is not trusted any more
        HttpHeaders spoof = new HttpHeaders();
        spoof.setBearerAuth(acmeOwner);
        spoof.set("X-Company-Code", beta);
        assertThat(rest.exchange("/api/v1/tenant/crm/_routing", HttpMethod.GET, new HttpEntity<>(spoof), String.class)
                .getBody()).contains(acme, "cmp_" + n + "_crm_db");

        // no token, a garbage token, a platform token, or an unregistered realm -> 401
        assertThat(tenantGet("/api/v1/tenant/crm/customers", null).getStatusCode().value()).isEqualTo(401);
        assertThat(tenantGet("/api/v1/tenant/crm/customers", "not-a-jwt").getStatusCode().value()).isEqualTo(401);
        assertThat(tenantGet("/api/v1/tenant/crm/customers", platformToken()).getStatusCode().value()).isEqualTo(401);
        // ... and a company token cannot call the platform API
        assertThat(call(HttpMethod.GET, "/api/v1/platform/companies", null, acmeOwner).getStatusCode().value())
                .isEqualTo(401);

        // issuer company must equal tenant_id: a realm whose mapper stamps another company's code is refused
        try (MapperDrift drift = new MapperDrift(betaRealm, acme)) {
            String forged = fetchToken(betaRealm, "erp-web", "owner@beta.test", "Beta-Owner-1");
            assertThat(claims(forged).get("tenant_id").asText()).isEqualTo(acme);
            assertThat(tenantGet("/api/v1/tenant/crm/customers", forged).getStatusCode().value()).isEqualTo(401);
        }

        // module role required: a user without CRM role is refused, CRM_USER is enough
        createUser(acmeRealm, "clerk@acme.test", "Acme-Clerk-1");
        assertThat(tenantGet("/api/v1/tenant/crm/customers", companyToken(acmeRealm, "clerk@acme.test", "Acme-Clerk-1"))
                .getBody()).contains("MODULE_ACCESS_DENIED");
        grantRealmRole(acmeRealm, "clerk@acme.test", "CRM_USER");
        tokens.clear();
        assertThat(tenantGet("/api/v1/tenant/crm/customers", companyToken(acmeRealm, "clerk@acme.test", "Acme-Clerk-1"))
                .getBody()).contains("Rajesh Traders");

        // 7. Upgrade Beta with CRM: ADD_MODULE job while Beta stays ACTIVE; Beta sees only its own (empty) data
        JsonNode upgrade = post("/api/v1/platform/companies/" + beta + "/orders", Map.of(
                "billingPeriod", "YEARLY", "modules", List.of("crm"), "users", 3, "storageGb", 5));
        assertThat(upgrade.at("/order/orderKind").asText()).isEqualTo("UPGRADE");
        long upId = upgrade.at("/order/id").asLong();
        post("/api/v1/platform/orders/" + upId + "/confirm", Map.of());
        post("/api/v1/platform/orders/" + upId + "/payments", Map.of("mode", "CASH", "amount", 3000));
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(500)).until(() ->
                tenantGet("/api/v1/tenant/crm/customers", companyToken(betaRealm, "owner@beta.test", "Beta-Owner-1"))
                        .getStatusCode().value() == 200);
        assertThat(tenantGet("/api/v1/tenant/crm/customers", companyToken(betaRealm, "owner@beta.test", "Beta-Owner-1"))
                .getBody()).isEqualTo("[]");
        assertThat(tenantGet("/api/v1/tenant/crm/customers", companyToken(acmeRealm, "owner@acme.test", "Acme-Owner-1"))
                .getBody()).contains("Rajesh Traders");
        // ADD_MODULE re-applied the realm template: the new module's roles exist in Beta's realm
        assertThat(realmRoles(betaRealm)).contains("CRM_USER", "ASSET_USER");

        // 8. Suspension is enforced on the backend; reactivation restores access; data retained
        post("/api/v1/platform/companies/" + acme + "/status", Map.of("status", "SUSPENDED", "reason", "test"));
        await().atMost(Duration.ofSeconds(10)).until(() -> tenantGet("/api/v1/tenant/crm/customers",
                companyToken(acmeRealm, "owner@acme.test", "Acme-Owner-1")).getBody().contains("COMPANY_SUSPENDED"));
        post("/api/v1/platform/companies/" + acme + "/status", Map.of("status", "ACTIVE", "reason", "paid"));
        await().atMost(Duration.ofSeconds(10)).until(() -> tenantGet("/api/v1/tenant/crm/customers",
                companyToken(acmeRealm, "owner@acme.test", "Acme-Owner-1")).getBody().contains("Rajesh Traders"));

        // 9. Platform API requires a Platform Super Admin token
        assertThat(rest.getForEntity("/api/v1/platform/companies", String.class).getStatusCode().value())
                .isEqualTo(401);
    }

    // ---------------------------------------------------------------- platform API helpers

    private String createCompany(String legalName, String email) throws Exception {
        return post("/api/v1/platform/companies", Map.of("legalName", legalName, "superAdminEmail", email))
                .get("companyCode").asText();
    }

    private void awaitStatus(String companyCode, String status) {
        await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofMillis(500)).until(() ->
                get("/api/v1/platform/companies/" + companyCode).get("status").asText().equals(status));
    }

    private static List<String> resourceNames(JsonNode overview) {
        return overview.get("resources").findValuesAsText("name");
    }

    private static String resourceStatus(JsonNode overview, String name) {
        return StreamSupport.stream(overview.get("resources").spliterator(), false)
                .filter(r -> name.equals(r.get("name").asText()))
                .map(r -> r.get("type").asText() + ":" + r.get("status").asText())
                .findFirst().orElse(null);
    }

    private JsonNode post(String path, Object body) throws Exception {
        ResponseEntity<String> r = call(HttpMethod.POST, path, body, platformToken());
        assertThat(r.getStatusCode().is2xxSuccessful()).as(path + " -> " + r.getBody()).isTrue();
        return json.readTree(r.getBody());
    }

    private JsonNode get(String path) throws Exception {
        ResponseEntity<String> r = call(HttpMethod.GET, path, null, platformToken());
        assertThat(r.getStatusCode().is2xxSuccessful()).as(path + " -> " + r.getBody()).isTrue();
        return json.readTree(r.getBody());
    }

    private ResponseEntity<String> tenantGet(String path, String bearer) {
        return call(HttpMethod.GET, path, null, bearer);
    }

    private ResponseEntity<String> call(HttpMethod method, String path, Object body, String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            h.setBearerAuth(bearer);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, h), String.class);
    }

    // ---------------------------------------------------------------- Keycloak helpers

    private String platformToken() throws Exception {
        return cachedToken("erp-platform", "platform-console", "superadmin", "change-me");
    }

    private String companyToken(String realm, String username, String password) throws Exception {
        return cachedToken(realm, "erp-web", username, password);
    }

    /** Access tokens live 5 minutes (master §14.1); refresh after one. */
    private String cachedToken(String realm, String clientId, String username, String password) throws Exception {
        String key = realm + "|" + username;
        CachedToken t = tokens.get(key);
        if (t == null || System.currentTimeMillis() - t.fetchedAt() > 60_000) {
            t = new CachedToken(fetchToken(realm, clientId, username, password), System.currentTimeMillis());
            tokens.put(key, t);
        }
        return t.value();
    }

    /** Password grant: POC-only stand-in for the browser login (PKCE) of the real clients. */
    private String fetchToken(String realm, String clientId, String username, String password) throws Exception {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("username", username);
        form.add("password", password);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String body = keycloakHttp.postForObject(KEYCLOAK_URL + "/realms/" + realm + "/protocol/openid-connect/token",
                new HttpEntity<>(form, h), String.class);
        return json.readTree(body).get("access_token").asText();
    }

    /** client_credentials of a domain service's M2M client in erp-platform (master §15.2). */
    private String serviceToken(String clientId, String secret) throws Exception {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String body = keycloakHttp.postForObject(KEYCLOAK_URL + "/realms/erp-platform/protocol/openid-connect/token",
                new HttpEntity<>(form, h), String.class);
        return json.readTree(body).get("access_token").asText();
    }

    private JsonNode claims(String jwt) throws Exception {
        return json.readTree(new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8));
    }

    private static List<String> realmRoles(String realm) {
        return OPERATOR.realm(realm).roles().list().stream().map(RoleRepresentation::getName).toList();
    }

    /** What the invited user does through the invitation link: sets a password and verifies the e-mail. */
    private static void acceptInvitation(String realm, String email, String password) {
        UsersResource users = OPERATOR.realm(realm).users();
        UserRepresentation u = users.searchByEmail(email, true).get(0);
        users.get(u.getId()).resetPassword(password(password));
        u.setRequiredActions(List.of());
        u.setEmailVerified(true);
        users.get(u.getId()).update(u);
    }

    /** A user created by the Company Super Admin through the ERP (CD-13), without any role yet. */
    private static void createUser(String realm, String email, String password) {
        UserRepresentation u = new UserRepresentation();
        u.setUsername(email);
        u.setEmail(email);
        u.setFirstName("Test");
        u.setLastName("User");
        u.setEnabled(true);
        u.setEmailVerified(true);
        u.setCredentials(List.of(password(password)));
        try (Response r = OPERATOR.realm(realm).users().create(u)) {
            CreatedResponseUtil.getCreatedId(r);
        }
    }

    private static void grantRealmRole(String realm, String email, String role) {
        RealmResource r = OPERATOR.realm(realm);
        String id = r.users().searchByEmail(email, true).get(0).getId();
        r.users().get(id).roles().realmLevel().add(List.of(r.roles().get(role).toRepresentation()));
    }

    private static CredentialRepresentation password(String value) {
        CredentialRepresentation c = new CredentialRepresentation();
        c.setType(CredentialRepresentation.PASSWORD);
        c.setValue(value);
        c.setTemporary(false);
        return c;
    }

    /** Temporarily makes a realm stamp another company's tenant_id (a hand-edited realm), then restores it. */
    private static final class MapperDrift implements AutoCloseable {
        private final ProtocolMappersResource mappers;
        private final ProtocolMapperRepresentation original;

        MapperDrift(String realm, String foreignCompanyCode) {
            RealmResource r = OPERATOR.realm(realm);
            ClientRepresentation web = r.clients().findByClientId("erp-web").get(0);
            this.mappers = r.clients().get(web.getId()).getProtocolMappers();
            this.original = mappers.getMappers().stream()
                    .filter(x -> "tenant_id (hard-coded)".equals(x.getName())).findFirst().orElseThrow();
            ProtocolMapperRepresentation drifted = copy(original);
            drifted.getConfig().put("claim.value", foreignCompanyCode);
            mappers.update(original.getId(), drifted);
        }

        @Override
        public void close() {
            mappers.update(original.getId(), original);
        }

        private static ProtocolMapperRepresentation copy(ProtocolMapperRepresentation m) {
            ProtocolMapperRepresentation c = new ProtocolMapperRepresentation();
            c.setId(m.getId());
            c.setName(m.getName());
            c.setProtocol(m.getProtocol());
            c.setProtocolMapper(m.getProtocolMapper());
            c.setConfig(new HashMap<>(m.getConfig()));
            return c;
        }
    }
}

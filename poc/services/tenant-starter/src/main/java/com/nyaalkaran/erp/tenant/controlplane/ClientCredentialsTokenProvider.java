package com.nyaalkaran.erp.tenant.controlplane;

import com.nyaalkaran.erp.tenant.TenantProperties;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/** client_credentials grant of `svc-<service>` in `erp-platform`; the token is reused until shortly before expiry. */
public class ClientCredentialsTokenProvider implements ServiceTokenProvider {

    private static final long EXPIRY_MARGIN_SECONDS = 30;

    private final RestClient http;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    private String token;
    private Instant refreshAt = Instant.EPOCH;

    public ClientCredentialsTokenProvider(TenantProperties.Keycloak keycloak, RestClient.Builder builder) {
        if (keycloak.clientId() == null || keycloak.clientSecret() == null) {
            throw new IllegalStateException("erp.tenant.keycloak.client-id / client-secret are required (master §15.2)");
        }
        this.http = builder.build();
        this.tokenUri = keycloak.issuer(keycloak.platformRealm()) + "/protocol/openid-connect/token";
        this.clientId = keycloak.clientId();
        this.clientSecret = keycloak.clientSecret();
    }

    @Override
    public synchronized String token() {
        if (token == null || Instant.now().isAfter(refreshAt)) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            Map<?, ?> body = http.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
            if (body == null || body.get("access_token") == null) {
                throw new IllegalStateException("No access_token from " + tokenUri);
            }
            token = (String) body.get("access_token");
            long expiresIn = ((Number) body.get("expires_in")).longValue();
            refreshAt = Instant.now().plusSeconds(Math.max(0, expiresIn - EXPIRY_MARGIN_SECONDS));
        }
        return token;
    }
}

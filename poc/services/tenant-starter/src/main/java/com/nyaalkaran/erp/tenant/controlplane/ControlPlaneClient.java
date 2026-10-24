package com.nyaalkaran.erp.tenant.controlplane;

import com.nyaalkaran.erp.tenant.TenantProperties;
import com.nyaalkaran.erp.tenant.routing.DatabaseLocation;
import com.nyaalkaran.erp.tenant.web.ProblemResponseErrorHandler;
import com.nyaalkaran.erp.tenant.web.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/** The control plane's internal registry API (/api/v1/internal/**), called with this service's M2M token. */
public class ControlPlaneClient {

    record IssuerView(String issuer, String companyCode) {
    }

    record DatabaseView(String companyCode, String moduleCode, String databaseName, String host, Integer port) {
    }

    private final RestClient http;

    public ControlPlaneClient(TenantProperties props, ServiceTokenProvider tokens, RestClient.Builder builder) {
        this.http = builder
                .baseUrl(props.controlPlane().url())
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokens.token());
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::isError, new ProblemResponseErrorHandler())
                .build();
    }

    /** @return the company of a registered, VERIFIED company realm, or empty for any other issuer */
    public Optional<String> companyOfIssuer(String issuer) {
        try {
            IssuerView view = http.get()
                    .uri(b -> b.path("/api/v1/internal/realms/company").queryParam("issuer", issuer).build())
                    .retrieve().body(IssuerView.class);
            return Optional.ofNullable(view).map(IssuerView::companyCode);
        } catch (ServiceException ex) {
            if (ex.status() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    /** The VERIFIED database of (company, module); CP errors such as COMPANY_NOT_READY are passed through. */
    public DatabaseLocation database(String companyCode, String moduleCode) {
        DatabaseView view = http.get()
                .uri("/api/v1/internal/companies/{c}/modules/{m}/database", companyCode, moduleCode)
                .retrieve().body(DatabaseView.class);
        if (view == null) {
            throw ServiceException.unavailable("COMPANY_NOT_READY", "No database registered for " + moduleCode);
        }
        return new DatabaseLocation(view.databaseName(), view.host(), view.port());
    }
}

package com.nyaalkaran.erp.tenant.outbound;

import com.nyaalkaran.erp.tenant.context.TenantContext;
import com.nyaalkaran.erp.tenant.web.ProblemResponseErrorHandler;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

/**
 * RestClients for business calls to other domain services (PHASE_01 §6.6.3 "Service -> service"): the caller's
 * verified user token is relayed, so the callee re-verifies company, entitlement and roles itself. Error
 * responses of the callee come back as ServiceException with the callee's status and code.
 */
public class TenantRestClients {

    private final RestClient.Builder builder;

    public TenantRestClients(RestClient.Builder builder) {
        this.builder = builder;
    }

    public RestClient forService(String baseUrl) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(TenantContext.require().userToken());
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::isError, new ProblemResponseErrorHandler())
                .build();
    }
}

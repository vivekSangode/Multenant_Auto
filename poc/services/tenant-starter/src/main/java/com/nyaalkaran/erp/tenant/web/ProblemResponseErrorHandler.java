package com.nyaalkaran.erp.tenant.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;

/**
 * Turns an error response of another ERP service (problem details with "code") into a ServiceException with the
 * same status and code, so that e.g. MODULE_NOT_ENTITLED from a callee reaches the client unchanged.
 */
public final class ProblemResponseErrorHandler implements RestClient.ResponseSpec.ErrorHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
        String code = "DOWNSTREAM_" + status.name();
        String detail = request.getMethod() + " " + request.getURI().getPath() + " returned " + status.value();
        byte[] body = response.getBody().readAllBytes();
        if (body.length > 0) {
            try {
                JsonNode node = JSON.readTree(body);
                code = node.path("code").asText(code);
                detail = node.path("detail").asText(detail);
            } catch (IOException ignored) {
                // not a problem document: keep the generic code
            }
        }
        throw new ServiceException(status, code, detail);
    }
}

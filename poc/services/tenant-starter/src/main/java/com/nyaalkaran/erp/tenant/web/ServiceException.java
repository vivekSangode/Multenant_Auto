package com.nyaalkaran.erp.tenant.web;

import org.springframework.http.HttpStatus;

/** A business or access error with a stable machine-readable code, rendered as RFC 9457 problem details. */
public class ServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ServiceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ServiceException unauthorized(String code, String message) {
        return new ServiceException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ServiceException forbidden(String code, String message) {
        return new ServiceException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ServiceException notFound(String code, String message) {
        return new ServiceException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ServiceException conflict(String code, String message) {
        return new ServiceException(HttpStatus.CONFLICT, code, message);
    }

    public static ServiceException unprocessable(String code, String message) {
        return new ServiceException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static ServiceException unavailable(String code, String message) {
        return new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}

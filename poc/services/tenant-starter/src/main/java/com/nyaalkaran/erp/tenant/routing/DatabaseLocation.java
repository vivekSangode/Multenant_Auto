package com.nyaalkaran.erp.tenant.routing;

/** Where a company's module database lives. Credentials are never part of it (CD-03). */
public record DatabaseLocation(String databaseName, String host, int port) {
}

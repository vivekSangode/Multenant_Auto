package com.nyaalkaran.erp.tenant.controlplane;

/** The M2M access token of THIS service (platform realm, client credentials; master §15.2). */
public interface ServiceTokenProvider {

    String token();
}

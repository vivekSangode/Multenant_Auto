package com.nyaalkaran.erp.tenant.access;

import com.nyaalkaran.erp.tenant.web.ServiceException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

/** Reads the shared Redis keys. Fail-closed: a Redis error is 503, never "allow" (master §10.6). */
public class RedisRuntimeStateReader implements RuntimeStateReader {

    static final String STATUS_KEY = "TENANT_STATUS:";
    static final String MODULES_KEY = "COMPANY_MODULES:";

    private final StringRedisTemplate redis;

    public RedisRuntimeStateReader(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> companyStatus(String companyCode) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(STATUS_KEY + companyCode));
        } catch (DataAccessException ex) {
            throw unavailable();
        }
    }

    @Override
    public boolean isEntitled(String companyCode, String gatewayToken) {
        try {
            return Boolean.TRUE.equals(redis.opsForSet().isMember(MODULES_KEY + companyCode, gatewayToken));
        } catch (DataAccessException ex) {
            throw unavailable();
        }
    }

    private static ServiceException unavailable() {
        return ServiceException.unavailable("ENTITLEMENT_CACHE_UNAVAILABLE", "Access cannot be verified right now");
    }
}

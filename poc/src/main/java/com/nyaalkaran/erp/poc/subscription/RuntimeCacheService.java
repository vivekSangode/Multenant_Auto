package com.nyaalkaran.erp.poc.subscription;

import com.nyaalkaran.erp.poc.catalog.ErpModule;
import com.nyaalkaran.erp.poc.catalog.ErpModuleRepository;
import com.nyaalkaran.erp.poc.common.PocProperties;
import com.nyaalkaran.erp.poc.company.Company;
import com.nyaalkaran.erp.poc.company.CompanyRepository;
import com.nyaalkaran.erp.poc.company.CompanyStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.EntitlementStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.CompanyEntitlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Writes the runtime caches read by the gateway and the services (master §10.6):
 * COMPANY_MODULES:&lt;CMP_n&gt; (set of gateway tokens of ACTIVE entitlements) and TENANT_STATUS:&lt;CMP_n&gt;.
 * The control plane DB stays the source of truth; Redis is a projection, rewritten after every commit and by
 * the resync scheduler.
 */
@Service
@RequiredArgsConstructor
public class RuntimeCacheService {

    public static final String MODULES_KEY = "COMPANY_MODULES:";
    public static final String STATUS_KEY = "TENANT_STATUS:";

    private final StringRedisTemplate redis;
    private final CompanyRepository companyRepository;
    private final CompanyEntitlementRepository entitlementRepository;
    private final ErpModuleRepository moduleRepository;
    private final PocProperties props;

    public record Projection(String companyCode, CompanyStatus status, Set<String> modules) {
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Projection project(Long companyId) {
        Company c = companyRepository.findById(companyId).orElseThrow();
        Map<Long, ErpModule> modules = moduleRepository.findAll().stream()
                .collect(Collectors.toMap(ErpModule::getId, Function.identity()));
        Set<String> tokens = entitlementRepository.findByCompanyIdAndStatus(companyId, EntitlementStatus.ACTIVE)
                .stream().map(e -> modules.get(e.getModuleId()).getGatewayToken())
                .collect(Collectors.toCollection(TreeSet::new));
        return new Projection(c.getCompanyCode(), c.getStatus(), tokens);
    }

    /** Atomically replaces both keys of one company (MULTI/EXEC). An empty module set deletes the key: deny. */
    public void write(Projection p) {
        Duration ttl = Duration.ofSeconds(props.cache().ttlSeconds());
        redis.execute(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations ops) throws DataAccessException {
                ops.multi();
                ops.opsForValue().set(STATUS_KEY + p.companyCode(), p.status().name(), ttl);
                ops.delete(MODULES_KEY + p.companyCode());
                if (!p.modules().isEmpty()) {
                    ops.opsForSet().add(MODULES_KEY + p.companyCode(), p.modules().toArray(new String[0]));
                    ops.expire(MODULES_KEY + p.companyCode(), ttl);
                }
                return ops.exec();
            }
        });
    }

    public void publish(Long companyId) {
        write(project(companyId));
    }
}

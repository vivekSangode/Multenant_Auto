package com.nyaalkaran.erp.poc.subscription;

import com.nyaalkaran.erp.poc.company.Company;
import com.nyaalkaran.erp.poc.company.CompanyRepository;
import com.nyaalkaran.erp.poc.company.CompanyStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes Redis after commit (never inside the DB transaction, master §19.3) and resyncs every company
 * periodically, so a missed publish (e.g. Redis briefly down) heals itself. The resync is ON by default.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeCachePublisher {

    private final RuntimeCacheService cacheService;
    private final CompanyRepository companyRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(CompanyStatusChangedEvent e) {
        safePublish(e.companyId(), e.companyCode());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEntitlementsChanged(EntitlementsChangedEvent e) {
        safePublish(e.companyId(), e.companyCode());
    }

    @Scheduled(fixedDelayString = "${poc.cache.resync-interval-ms:60000}", initialDelay = 5000)
    public void resync() {
        for (Company c : companyRepository.findAll()) {
            safePublish(c.getId(), c.getCompanyCode());
        }
    }

    private void safePublish(Long companyId, String companyCode) {
        try {
            cacheService.publish(companyId);
        } catch (RuntimeException ex) {
            log.warn("Runtime cache publish failed for {} (resync will retry): {}", companyCode, ex.getMessage());
        }
    }
}

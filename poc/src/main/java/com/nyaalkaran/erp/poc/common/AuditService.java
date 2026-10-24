package com.nyaalkaran.erp.poc.common;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final PlatformAuditLogRepository repository;

    /** Writes an audit row in the caller's transaction, so it commits or rolls back with the change. */
    public void record(String action, String companyCode, String targetType, Object targetId, String detail) {
        PlatformAuditLog log = new PlatformAuditLog();
        log.setActor(Actors.current());
        log.setAction(action);
        log.setCompanyCode(companyCode);
        log.setTargetType(targetType);
        log.setTargetId(targetId == null ? null : String.valueOf(targetId));
        log.setOutcome("SUCCESS");
        log.setDetail(detail);
        log.setOccurredAt(LocalDateTime.now());
        repository.save(log);
    }
}

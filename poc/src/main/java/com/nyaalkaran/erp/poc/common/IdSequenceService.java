package com.nyaalkaran.erp.poc.common;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates identifiers with SELECT ... FOR UPDATE + increment (master §6.2). Replaces the race-prone
 * "max + 1 over all ids loaded in memory". The unique constraint on each identifier is the backstop.
 */
@Service
@RequiredArgsConstructor
public class IdSequenceService {

    private final IdSequenceRepository repository;

    /** Runs in the caller's transaction so a rolled-back company creation also rolls back the number. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long next(String sequenceName) {
        IdSequence seq = repository.lockByName(sequenceName)
                .orElseThrow(() -> new IllegalStateException("Missing sequence " + sequenceName));
        long value = seq.getNextValue();
        seq.setNextValue(value + 1);
        return value;
    }
}

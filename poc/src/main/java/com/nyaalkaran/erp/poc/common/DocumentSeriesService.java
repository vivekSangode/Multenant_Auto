package com.nyaalkaran.erp.poc.common;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Atomic document numbers in the document's own transaction, e.g. ORD/2026-27/00001 (PHASE_01 §6.2 D,
 * CD-33). The counter resets per Indian financial year (April - March).
 */
@Service
@RequiredArgsConstructor
public class DocumentSeriesService {

    public enum Series {
        ORDER("ORD"), PAYMENT("PAY"), RECEIPT("RCT"), INVOICE("INV"), SUBSCRIPTION("SUB");

        final String prefix;

        Series(String prefix) {
            this.prefix = prefix;
        }
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final DocumentSeriesRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Series series) {
        String fy = financialYear(LocalDate.now(ZONE));
        // Create the counter row if missing; INSERT IGNORE keeps concurrent first allocations safe.
        repository.insertIfMissing(series.name(), fy, series.prefix);
        DocumentSeries row = repository.lock(series.name(), fy).orElseThrow();
        long value = row.getNextValue();
        if (value > 99_999) {
            throw new IllegalStateException("Document series overflow: " + series);
        }
        row.setNextValue(value + 1);
        String pattern = "%s/%s/%0" + row.getPadding() + "d";
        return pattern.formatted(row.getPrefix(), fy, value);
    }

    static String financialYear(LocalDate date) {
        int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return start + "-" + String.format("%02d", (start + 1) % 100);
    }
}

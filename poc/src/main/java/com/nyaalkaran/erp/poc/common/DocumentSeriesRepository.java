package com.nyaalkaran.erp.poc.common;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DocumentSeriesRepository extends JpaRepository<DocumentSeries, Long> {

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO tbl_document_series
                (series_code, period_key, prefix, next_value, padding, created_at, created_by, version)
            VALUES (:code, :period, :prefix, 1, 5, NOW(6), 'system', 0)
            """, nativeQuery = true)
    void insertIfMissing(String code, String period, String prefix);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSeries s where s.seriesCode = :code and s.periodKey = :period")
    Optional<DocumentSeries> lock(String code, String period);
}

package com.nyaalkaran.erp.poc.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** tbl_document_series: document numbering per series and financial year (PHASE_01 §6.2 D). */
@Getter
@Setter
@Entity
@Table(name = "tbl_document_series")
public class DocumentSeries extends BaseEntity {

    @Column(name = "series_code", nullable = false, length = 30)
    private String seriesCode;

    @Column(name = "period_key", nullable = false, length = 20)
    private String periodKey;

    @Column(name = "prefix", nullable = false, length = 20)
    private String prefix;

    @Column(name = "next_value", nullable = false)
    private Long nextValue;

    @Column(name = "padding", nullable = false)
    private Integer padding;
}

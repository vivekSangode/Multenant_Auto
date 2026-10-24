package com.nyaalkaran.erp.poc.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** tbl_id_sequence: race-safe identifier allocation (master §6.2). */
@Getter
@Setter
@Entity
@Table(name = "tbl_id_sequence")
public class IdSequence {

    @Id
    @Column(name = "sequence_name", length = 30)
    private String sequenceName;

    @Column(name = "next_value", nullable = false)
    private Long nextValue;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}

package com.nyaalkaran.erp.poc.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ErpModuleRepository extends JpaRepository<ErpModule, Long> {

    Optional<ErpModule> findByCode(String code);

    List<ErpModule> findAllByOrderBySortOrderAsc();

    List<ErpModule> findByCodeIn(java.util.Collection<String> codes);
}

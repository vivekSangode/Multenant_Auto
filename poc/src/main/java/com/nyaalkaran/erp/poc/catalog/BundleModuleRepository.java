package com.nyaalkaran.erp.poc.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BundleModuleRepository extends JpaRepository<BundleModule, Long> {

    @Query("select bm from BundleModule bm join fetch bm.bundle join fetch bm.module")
    List<BundleModule> findAllWithRefs();
}

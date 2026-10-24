package com.nyaalkaran.erp.poc.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ModuleDependencyRepository extends JpaRepository<ModuleDependency, Long> {

    @Query("""
            select d from ModuleDependency d join fetch d.module join fetch d.dependsOn
            where d.status = com.nyaalkaran.erp.poc.catalog.CatalogEnums.CatalogStatus.ACTIVE
            """)
    List<ModuleDependency> findActiveWithModules();
}

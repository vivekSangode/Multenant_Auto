package com.nyaalkaran.erp.poc.catalog;

/** Catalog enum values (PHASE_01 §6.2 C/D). Stored as VARCHAR(30), never native MySQL ENUM. */
public final class CatalogEnums {

    private CatalogEnums() {
    }

    public enum ModuleType { BUSINESS, PLATFORM_CORE, ADDON }

    public enum CatalogStatus { DRAFT, ACTIVE, RETIRED }

    public enum DependencyType { MANDATORY, RECOMMENDED, CONDITIONAL }

    public enum ChargeMode { CHARGED, INCLUDED }

    public enum PlanType { MODULE, BUNDLE, USER_SEAT, STORAGE }

    public enum BillingPeriod { MONTHLY, YEARLY }
}

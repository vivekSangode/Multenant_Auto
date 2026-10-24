package com.nyaalkaran.erp.poc.subscription;

/** Subscription and entitlement enum values (master §13, PHASE_01 §6.2 D/E). */
public final class SubscriptionEnums {

    private SubscriptionEnums() {
    }

    public enum SubscriptionStatus { PENDING, TRIAL, ACTIVE, PAST_DUE, SUSPENDED, CANCELLED, EXPIRED }

    public enum ItemType { PLAN, DEPENDENCY, ADDITIONAL_USERS, ADDITIONAL_STORAGE }

    public enum ItemStatus { SCHEDULED, ACTIVE, ENDED }

    public enum EntitlementSource { PLAN_MODULE, PLAN_BUNDLE, DEPENDENCY, PLATFORM_CORE, MANUAL_GRANT }

    /** PENDING = not yet provisioned. */
    public enum EntitlementStatus { PENDING, ACTIVE, DEGRADED, SUSPENDED, ENDED }

    public enum AllowanceType { USERS, STORAGE_BYTES }
}

package com.nyaalkaran.erp.poc.billing;

/** Billing enum values (PHASE_01 §6.2 D and §I.1, CD-07). */
public final class BillingEnums {

    private BillingEnums() {
    }

    public enum OrderKind { NEW, UPGRADE, DOWNGRADE, RENEWAL }

    public enum OrderChannel { ADMIN, SYSTEM, WEBSITE }

    /** No partial state: full payment only (CD-07). */
    public enum OrderPaymentStatus { UNPAID, PENDING_VERIFICATION, PAID }

    public enum OrderStatus { DRAFT, QUOTED, AWAITING_PAYMENT, PAID, FULFILLED, CANCELLED, EXPIRED }

    public enum PaymentMode {
        CASH(false), CHEQUE(true), DEMAND_DRAFT(true), UPI(false), BANK_TRANSFER(true), OTHER(true);

        /** Non-immediate methods stay PENDING_VERIFICATION until cleared (PHASE_01 §6.9.2 step 5). */
        public final boolean verificationRequired;

        PaymentMode(boolean verificationRequired) {
            this.verificationRequired = verificationRequired;
        }
    }

    public enum PaymentStatus { RECORDED, PENDING_VERIFICATION, CONFIRMED, REJECTED, REVERSED }

    public enum InvoiceStatus { ISSUED_UNPAID, ISSUED_PAID, CREDITED, VOID }
}

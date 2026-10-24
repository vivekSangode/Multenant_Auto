package com.nyaalkaran.erp.poc.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Master §6.3 frozen naming examples. */
class TenantResourceNamingTest {

    @Test
    void namesFollowTheFrozenRules() {
        assertThat(TenantResourceNaming.databaseName("CMP_101", "crm")).isEqualTo("cmp_101_crm_db");
        assertThat(TenantResourceNaming.databaseName("CMP_101", "account_book")).isEqualTo("cmp_101_account_book_db");
        assertThat(TenantResourceNaming.bucketName("CMP_101", "crm")).isEqualTo("cmp-101-crm-bucket");
        assertThat(TenantResourceNaming.bucketName("CMP_101", "control-panel")).isEqualTo("cmp-101-control-panel-bucket");
        assertThat(TenantResourceNaming.realmName("CMP_101")).isEqualTo("co-101");
    }

    @Test
    void onlyCompanyCodesAreAccepted() {
        assertThatThrownBy(() -> TenantResourceNaming.databaseName("ORG_101", "crm")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> TenantResourceNaming.databaseName("CMP_1; DROP DATABASE x", "crm"))
                .isInstanceOf(ApiException.class);
    }
}

package com.nyaalkaran.erp.poc.common;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The single implementation of resource naming (master §6.3, frozen). Every name derives ONLY from the
 * company code (CMP_n, CD-26) and catalog tokens, never from user input.
 */
public final class TenantResourceNaming {

    public static final Pattern COMPANY_CODE = Pattern.compile("^CMP_[0-9]{1,8}$");
    private static final Pattern DB_NAME = Pattern.compile("^[a-z0-9_]{1,64}$");
    private static final Pattern BUCKET_NAME = Pattern.compile("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$");

    private TenantResourceNaming() {
    }

    public static boolean isCompanyCode(String value) {
        return value != null && COMPANY_CODE.matcher(value).matches();
    }

    public static String requireCompanyCode(String companyCode) {
        if (!isCompanyCode(companyCode)) {
            throw ApiException.badRequest("INVALID_COMPANY_CODE", "Invalid company code");
        }
        return companyCode;
    }

    public static String companyCode(long n) {
        return "CMP_" + n;
    }

    /** cmp_101_crm_db */
    public static String databaseName(String companyCode, String dbToken) {
        String name = requireCompanyCode(companyCode).toLowerCase(Locale.ROOT) + "_" + dbToken + "_db";
        if (!DB_NAME.matcher(name).matches()) {
            throw new IllegalStateException("Invalid database name: " + name);
        }
        return name;
    }

    /** cmp-101-crm-bucket */
    public static String bucketName(String companyCode, String moduleCode) {
        String name = requireCompanyCode(companyCode).toLowerCase(Locale.ROOT).replace('_', '-')
                + "-" + moduleCode.toLowerCase(Locale.ROOT).replace('_', '-') + "-bucket";
        if (name.length() > 63 || !BUCKET_NAME.matcher(name).matches()) {
            throw new IllegalStateException("Invalid bucket name: " + name);
        }
        return name;
    }

    /** co-101: the company's Keycloak realm (CD-06, CD-16). */
    public static String realmName(String companyCode) {
        return "co-" + requireCompanyCode(companyCode).substring(4);
    }
}

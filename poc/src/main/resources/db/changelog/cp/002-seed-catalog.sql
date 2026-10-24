--liquibase formatted sql

-- Initial catalog (master §11.1), dependency set (§11.2), bundle candidates (§11.3)
-- and ILLUSTRATIVE prices (§12.3, IU). No real commercial prices are defined here.

--changeset poc:010-seed-sequences
INSERT INTO tbl_id_sequence (sequence_name, next_value, version) VALUES ('COMPANY', 101, 0);

--changeset poc:011-seed-modules
INSERT INTO tbl_module (code, gateway_token, db_token, service_name, module_type, requires_database, requires_bootstrap, standalone_allowed, name, category, sort_order, status, created_at, created_by) VALUES
('crm',                      'CRM',                      'crm',          'CRM-SERVICE',          'BUSINESS',      1, 1, 1, 'CRM',                        'Sales',      10, 'ACTIVE', NOW(6), 'seed'),
('hrms',                     'HRMS',                     'hrms',         'HRMS-SERVICE',         'BUSINESS',      1, 1, 1, 'HRMS',                       'People',     20, 'ACTIVE', NOW(6), 'seed'),
('attendance',               'ATTENDANCE',               'attendance',   'ATTENDANCE-SERVICE',   'BUSINESS',      1, 1, 0, 'Attendance',                 'People',     21, 'ACTIVE', NOW(6), 'seed'),
('payroll',                  'PAYROLL',                  'payroll',      'PAYROLL-SERVICE',      'BUSINESS',      1, 1, 0, 'Payroll',                    'People',     22, 'ACTIVE', NOW(6), 'seed'),
('store',                    'STORE',                    'store',        'STORE-SERVICE',        'BUSINESS',      1, 1, 0, 'Store',                      'Finance',    30, 'ACTIVE', NOW(6), 'seed'),
('account-book',             'ACCOUNT-BOOK',             'account_book', 'ACCOUNT-BOOK-SERVICE', 'BUSINESS',      1, 1, 1, 'Account Book',               'Finance',    31, 'ACTIVE', NOW(6), 'seed'),
('production',               'PRODUCTION',               'production',   'PRODUCTION-SERVICE',   'BUSINESS',      1, 1, 1, 'Production',                 'Operations', 40, 'ACTIVE', NOW(6), 'seed'),
('mining',                   'MINING',                   'mining',       'MINING-SERVICE',       'BUSINESS',      1, 1, 1, 'Mining',                     'Operations', 41, 'ACTIVE', NOW(6), 'seed'),
('qc',                       'QC',                       'qc',           'QC-SERVICE',           'BUSINESS',      1, 1, 0, 'Quality Control',            'Operations', 42, 'ACTIVE', NOW(6), 'seed'),
('research-and-development', 'RESEARCH-AND-DEVELOPMENT', 'rnd',          'RND-SERVICE',          'BUSINESS',      1, 1, 0, 'Research and Development',   'Operations', 43, 'ACTIVE', NOW(6), 'seed'),
('logistics',                'LOGISTICS',                'logistics',    'LOGISTICS-SERVICE',    'BUSINESS',      1, 1, 0, 'Logistics',                  'Sales',      11, 'ACTIVE', NOW(6), 'seed'),
('compliance',               'COMPLIANCE',               'compliance',   'COMPLIANCE-SERVICE',   'BUSINESS',      1, 1, 1, 'Compliance',                 'Governance', 50, 'ACTIVE', NOW(6), 'seed'),
('asset',                    'ASSET',                    'asset',        'ASSET-SERVICE',        'BUSINESS',      1, 1, 1, 'Asset',                      'Operations', 51, 'ACTIVE', NOW(6), 'seed'),
('chat',                     'CHAT',                     'chat',         'CHAT-SERVICE',         'BUSINESS',      1, 1, 1, 'Chat',                       'Collaboration', 60, 'ACTIVE', NOW(6), 'seed'),
('portal',                   'PORTAL',                   NULL,           'CUSTOMER-PORTAL',      'ADDON',         0, 0, 0, 'Customer Portal',            'Sales',      12, 'ACTIVE', NOW(6), 'seed'),
('file',                     'FILE',                     'attachment',   'FILE-SERVICE',         'PLATFORM_CORE', 1, 1, 0, 'Files and Attachments',      'Platform',   90, 'ACTIVE', NOW(6), 'seed'),
('notification',             'NOTIFICATION',             'notification', 'NOTIFICATION-SERVICE', 'PLATFORM_CORE', 1, 1, 0, 'Notifications',              'Platform',   91, 'ACTIVE', NOW(6), 'seed');

--changeset poc:012-seed-dependencies
INSERT INTO tbl_module_dependency (module_id, depends_on_module_id, dependency_type, charge_mode, reason, status, created_at, created_by)
SELECT m.id, d.id, x.dependency_type, x.charge_mode, x.reason, 'ACTIVE', NOW(6), 'seed'
FROM (
    SELECT 'store' AS m, 'account-book' AS d, 'MANDATORY' AS dependency_type, 'CHARGED' AS charge_mode, 'Store inward/outward call ACCOUNT-BOOK unconditionally' AS reason
    UNION ALL SELECT 'logistics', 'crm', 'MANDATORY', 'CHARGED', 'Dispatches are built on CRM booking orders'
    UNION ALL SELECT 'portal', 'crm', 'MANDATORY', 'CHARGED', 'CUSTOMER-PORTAL calls CRM without fallback'
    UNION ALL SELECT 'portal', 'logistics', 'MANDATORY', 'CHARGED', 'CUSTOMER-PORTAL calls LOGISTICS tracking without fallback'
    UNION ALL SELECT 'payroll', 'hrms', 'MANDATORY', 'CHARGED', 'Salary assignment validates employees in HRMS'
    UNION ALL SELECT 'attendance', 'hrms', 'MANDATORY', 'CHARGED', 'Employee master, shifts and calendar live in HRMS'
    UNION ALL SELECT 'payroll', 'attendance', 'RECOMMENDED', 'CHARGED', 'Without ATTENDANCE there is no attendance intake'
    UNION ALL SELECT 'qc', 'research-and-development', 'MANDATORY', 'CHARGED', 'QcCustomerOrderProxyController forwards to R&D'
    UNION ALL SELECT 'research-and-development', 'mining', 'RECOMMENDED', 'CHARGED', 'Master display names come from MINING'
) x
JOIN tbl_module m ON m.code = x.m
JOIN tbl_module d ON d.code = x.d;

--changeset poc:013-seed-bundles
INSERT INTO tbl_bundle (code, name, description, status, sort_order, created_at, created_by) VALUES
('HR_SUITE',        'HR Suite',              'hrms, attendance, payroll',                    'ACTIVE', 10, NOW(6), 'seed'),
('SALES_DISPATCH',  'Sales & Dispatch',      'crm, production, logistics',                   'ACTIVE', 20, NOW(6), 'seed'),
('FINANCE_INVENTORY','Finance & Inventory',  'account-book, store',                          'ACTIVE', 30, NOW(6), 'seed'),
('PLANT_OPERATIONS','Plant Operations',      'production, mining, qc, research-and-development', 'ACTIVE', 40, NOW(6), 'seed');

INSERT INTO tbl_bundle_module (bundle_id, module_id, created_at, created_by)
SELECT b.id, m.id, NOW(6), 'seed'
FROM (
    SELECT 'HR_SUITE' AS b, 'hrms' AS m
    UNION ALL SELECT 'HR_SUITE', 'attendance'
    UNION ALL SELECT 'HR_SUITE', 'payroll'
    UNION ALL SELECT 'SALES_DISPATCH', 'crm'
    UNION ALL SELECT 'SALES_DISPATCH', 'production'
    UNION ALL SELECT 'SALES_DISPATCH', 'logistics'
    UNION ALL SELECT 'FINANCE_INVENTORY', 'account-book'
    UNION ALL SELECT 'FINANCE_INVENTORY', 'store'
    UNION ALL SELECT 'PLANT_OPERATIONS', 'production'
    UNION ALL SELECT 'PLANT_OPERATIONS', 'mining'
    UNION ALL SELECT 'PLANT_OPERATIONS', 'qc'
    UNION ALL SELECT 'PLANT_OPERATIONS', 'research-and-development'
) x
JOIN tbl_bundle b ON b.code = x.b
JOIN tbl_module m ON m.code = x.m;

--changeset poc:014-seed-plans
-- One MODULE plan per sellable module, one BUNDLE plan per bundle, plus USER_SEAT and STORAGE.
INSERT INTO tbl_plan (code, name, plan_type, module_id, bundle_id, is_public, status, sort_order, created_at, created_by)
SELECT CONCAT('MOD_', UPPER(REPLACE(m.code, '-', '_'))), m.name, 'MODULE', m.id, NULL, 0, 'ACTIVE', m.sort_order, NOW(6), 'seed'
FROM tbl_module m WHERE m.module_type IN ('BUSINESS', 'ADDON');

INSERT INTO tbl_plan (code, name, plan_type, module_id, bundle_id, is_public, status, sort_order, created_at, created_by)
SELECT CONCAT('BUN_', b.code), b.name, 'BUNDLE', NULL, b.id, 0, 'ACTIVE', b.sort_order, NOW(6), 'seed'
FROM tbl_bundle b;

INSERT INTO tbl_plan (code, name, plan_type, module_id, bundle_id, is_public, status, sort_order, created_at, created_by) VALUES
('USER_SEAT', 'Additional users',   'USER_SEAT', NULL, NULL, 0, 'ACTIVE', 900, NOW(6), 'seed'),
('STORAGE',   'Additional storage', 'STORAGE',   NULL, NULL, 0, 'ACTIVE', 910, NOW(6), 'seed');

INSERT INTO tbl_plan_version (plan_id, version_number, status, is_custom, effective_from, published_at, published_by, created_at, created_by)
SELECT p.id, 1, 'ACTIVE', 0, NOW(6), NOW(6), 'seed', NOW(6), 'seed' FROM tbl_plan p;

--changeset poc:015-seed-prices
-- Monthly prices (IU, master §12.3). Yearly = 10 x monthly (illustrative).
INSERT INTO tbl_plan_price (plan_version_id, billing_period, currency, unit_price, unit_quantity, included_users, included_storage_gb, min_quantity, max_quantity, created_at, created_by)
SELECT pv.id, periods.period, 'INR', periods.mult * x.monthly, x.unit_quantity, x.included_users, x.included_storage_gb, 1, x.max_quantity, NOW(6), 'seed'
FROM (
              SELECT 'MOD_ASSET' AS plan, 100 AS monthly, 1 AS unit_quantity, 0 AS included_users, 0 AS included_storage_gb, NULL AS max_quantity
    UNION ALL SELECT 'MOD_CRM', 300, 1, 0, 0, NULL
    UNION ALL SELECT 'MOD_PRODUCTION', 250, 1, 0, 0, NULL
    UNION ALL SELECT 'MOD_LOGISTICS', 200, 1, 0, 0, NULL
    UNION ALL SELECT 'MOD_HRMS', 200, 1, 0, 0, NULL
    UNION ALL SELECT 'MOD_ATTENDANCE', 80, 1, 0, 0, NULL
    UNION ALL SELECT 'MOD_PAYROLL', 150, 1, 0, 0, NULL
    UNION ALL SELECT 'MOD_ACCOUNT_BOOK', 250, 1, 0, 0, NULL
    UNION ALL SELECT 'MOD_STORE', 150, 1, 0, 0, NULL
    UNION ALL SELECT 'MOD_MINING', 300, 1, 0, 0, NULL
    UNION ALL SELECT 'MOD_QC', 120, 1, 0, 0, NULL
    UNION ALL SELECT 'MOD_RESEARCH_AND_DEVELOPMENT', 180, 1, 0, 0, NULL
    UNION ALL SELECT 'MOD_COMPLIANCE', 90, 1, 0, 0, NULL
    UNION ALL SELECT 'MOD_CHAT', 50, 1, 0, 0, NULL
    UNION ALL SELECT 'MOD_PORTAL', 120, 1, 0, 0, NULL
    UNION ALL SELECT 'BUN_HR_SUITE', 380, 1, 0, 0, NULL
    UNION ALL SELECT 'BUN_SALES_DISPATCH', 650, 1, 0, 0, NULL
    UNION ALL SELECT 'BUN_FINANCE_INVENTORY', 350, 1, 0, 0, NULL
    UNION ALL SELECT 'BUN_PLANT_OPERATIONS', 750, 1, 0, 0, NULL
    -- 5 users included, then 20 per additional user; at most 500 users
    UNION ALL SELECT 'USER_SEAT', 20, 1, 5, 0, 500
    -- 10 GiB included, then 15 per 10 GiB block; at most 1000 GiB
    UNION ALL SELECT 'STORAGE', 15, 10, 0, 10, 1000
) x
JOIN tbl_plan p ON p.code = x.plan
JOIN tbl_plan_version pv ON pv.plan_id = p.id AND pv.version_number = 1
CROSS JOIN (SELECT 'MONTHLY' AS period, 1 AS mult UNION ALL SELECT 'YEARLY', 10) periods;

--liquibase formatted sql

-- Automotive (car dealer) modules served by the sample microservices in poc/services/ (VEHICLE-SERVICE,
-- INVENTORY-SERVICE, BOOKING-SERVICE). Same model as 002: one DB per entitled module per company, ILLUSTRATIVE prices.

--changeset poc:020-seed-automotive-modules
INSERT INTO tbl_module (code, gateway_token, db_token, service_name, module_type, requires_database, requires_bootstrap, standalone_allowed, name, category, sort_order, status, created_at, created_by) VALUES
('vehicle',   'VEHICLE',   'vehicle',   'VEHICLE-SERVICE',   'BUSINESS', 1, 1, 1, 'Vehicle Catalog',   'Automotive', 70, 'ACTIVE', NOW(6), 'seed'),
('inventory', 'INVENTORY', 'inventory', 'INVENTORY-SERVICE', 'BUSINESS', 1, 1, 0, 'Vehicle Inventory', 'Automotive', 71, 'ACTIVE', NOW(6), 'seed'),
('booking',   'BOOKING',   'booking',   'BOOKING-SERVICE',   'BUSINESS', 1, 1, 0, 'Vehicle Booking',   'Automotive', 72, 'ACTIVE', NOW(6), 'seed');

--changeset poc:021-seed-automotive-dependencies
INSERT INTO tbl_module_dependency (module_id, depends_on_module_id, dependency_type, charge_mode, reason, status, created_at, created_by)
SELECT m.id, d.id, x.dependency_type, x.charge_mode, x.reason, 'ACTIVE', NOW(6), 'seed'
FROM (
    SELECT 'inventory' AS m, 'vehicle' AS d, 'MANDATORY' AS dependency_type, 'CHARGED' AS charge_mode, 'Stock units are validated against VEHICLE-SERVICE models' AS reason
    UNION ALL SELECT 'booking', 'inventory', 'MANDATORY', 'CHARGED', 'A booking reserves a stock unit in INVENTORY-SERVICE'
    UNION ALL SELECT 'booking', 'vehicle', 'MANDATORY', 'CHARGED', 'The booking price comes from VEHICLE-SERVICE'
) x
JOIN tbl_module m ON m.code = x.m
JOIN tbl_module d ON d.code = x.d;

--changeset poc:022-seed-automotive-bundle
INSERT INTO tbl_bundle (code, name, description, status, sort_order, created_at, created_by) VALUES
('AUTO_DEALER', 'Auto Dealer Suite', 'vehicle, inventory, booking', 'ACTIVE', 50, NOW(6), 'seed');

INSERT INTO tbl_bundle_module (bundle_id, module_id, created_at, created_by)
SELECT b.id, m.id, NOW(6), 'seed'
FROM tbl_bundle b JOIN tbl_module m ON m.code IN ('vehicle', 'inventory', 'booking')
WHERE b.code = 'AUTO_DEALER';

--changeset poc:023-seed-automotive-plans
INSERT INTO tbl_plan (code, name, plan_type, module_id, bundle_id, is_public, status, sort_order, created_at, created_by)
SELECT CONCAT('MOD_', UPPER(m.code)), m.name, 'MODULE', m.id, NULL, 0, 'ACTIVE', m.sort_order, NOW(6), 'seed'
FROM tbl_module m WHERE m.code IN ('vehicle', 'inventory', 'booking');

INSERT INTO tbl_plan (code, name, plan_type, module_id, bundle_id, is_public, status, sort_order, created_at, created_by)
SELECT 'BUN_AUTO_DEALER', b.name, 'BUNDLE', NULL, b.id, 0, 'ACTIVE', b.sort_order, NOW(6), 'seed'
FROM tbl_bundle b WHERE b.code = 'AUTO_DEALER';

INSERT INTO tbl_plan_version (plan_id, version_number, status, is_custom, effective_from, published_at, published_by, created_at, created_by)
SELECT p.id, 1, 'ACTIVE', 0, NOW(6), NOW(6), 'seed', NOW(6), 'seed' FROM tbl_plan p
WHERE p.code IN ('MOD_VEHICLE', 'MOD_INVENTORY', 'MOD_BOOKING', 'BUN_AUTO_DEALER');

--changeset poc:024-seed-automotive-prices
INSERT INTO tbl_plan_price (plan_version_id, billing_period, currency, unit_price, unit_quantity, included_users, included_storage_gb, min_quantity, max_quantity, created_at, created_by)
SELECT pv.id, periods.period, 'INR', periods.mult * x.monthly, 1, 0, 0, 1, NULL, NOW(6), 'seed'
FROM (
              SELECT 'MOD_VEHICLE' AS plan, 150 AS monthly
    UNION ALL SELECT 'MOD_INVENTORY', 200
    UNION ALL SELECT 'MOD_BOOKING', 250
    UNION ALL SELECT 'BUN_AUTO_DEALER', 500
) x
JOIN tbl_plan p ON p.code = x.plan
JOIN tbl_plan_version pv ON pv.plan_id = p.id AND pv.version_number = 1
CROSS JOIN (SELECT 'MONTHLY' AS period, 1 AS mult UNION ALL SELECT 'YEARLY', 10) periods;

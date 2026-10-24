-- CD-03: one environment-level DB login for all company databases, no per-company MySQL users.
-- Least privilege (master §6.6): only control_panel_db and the cmp_% company schemas, no admin rights.
CREATE DATABASE IF NOT EXISTS control_panel_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'erp_app'@'%' IDENTIFIED BY 'erp_app_pass';
GRANT ALL PRIVILEGES ON `control_panel_db`.* TO 'erp_app'@'%';
GRANT ALL PRIVILEGES ON `cmp\_%`.* TO 'erp_app'@'%';
FLUSH PRIVILEGES;

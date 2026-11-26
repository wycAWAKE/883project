-- SmartCampus M1 FAQ Assistance, MySQL 8.
-- Select the existing target database before executing this file manually.
-- This script creates only the FAQ table. It does not modify legacy data or seed campus policies.
-- IF NOT EXISTS does not upgrade an incompatible pre-existing faq_entry table; inspect its schema first.
-- Rollback: leave the unused table in place; export its data before any separately authorized removal.

CREATE TABLE IF NOT EXISTS faq_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question VARCHAR(1000) NOT NULL,
    answer TEXT NOT NULL,
    category VARCHAR(100) DEFAULT NULL,
    keywords VARCHAR(1000) DEFAULT NULL,
    source_title VARCHAR(255) NOT NULL,
    source_url VARCHAR(2048) DEFAULT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

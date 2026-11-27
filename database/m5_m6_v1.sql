-- M5/M6 v1, MySQL 8.x. Select the intended database before importing.
-- Adds only sc_application, sc_attachment, sc_history, sc_notification.
-- Does not create users, change legacy tables, drop records or select a database.
-- Account key = canonical Session role + ':' + canonical username from the existing account table.
-- Usernames must remain stable while business records refer to them.
-- IF NOT EXISTS supports repeat installation of v1, not upgrades from a different schema.
CREATE TABLE IF NOT EXISTS sc_application (
 id VARCHAR(36) PRIMARY KEY, reference_no VARCHAR(50) NOT NULL UNIQUE, applicant_id VARCHAR(80) COLLATE utf8mb4_bin NOT NULL,
 applicant_name VARCHAR(100) NOT NULL, student_number VARCHAR(30) NOT NULL, programme VARCHAR(100) NOT NULL,
 email VARCHAR(150) NOT NULL, application_type VARCHAR(40) NOT NULL, purpose VARCHAR(1000) NOT NULL,
 document_language VARCHAR(20) NOT NULL, delivery_method VARCHAR(20) NOT NULL, notes VARCHAR(1000),
 status VARCHAR(20) NOT NULL, latest_reason VARCHAR(1000), version INT NOT NULL, revision INT NOT NULL,
 request_key VARCHAR(36) NOT NULL UNIQUE, request_digest VARCHAR(64) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 INDEX idx_app_owner (applicant_id,updated_at),
 INDEX idx_app_status (status,updated_at),
 CONSTRAINT ck_admin_status CHECK (status IN ('PENDING','RETURNED','APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS sc_attachment (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(80) COLLATE utf8mb4_bin NOT NULL, application_id VARCHAR(36), filename VARCHAR(160) NOT NULL,
 content_type VARCHAR(80) NOT NULL, file_size BIGINT NOT NULL, file_data MEDIUMBLOB NOT NULL, active BOOLEAN NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, FOREIGN KEY (application_id) REFERENCES sc_application(id),
 INDEX idx_attachment_app (application_id),
 INDEX idx_attachment_owner (owner_id,application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS sc_history (
 id VARCHAR(36) PRIMARY KEY, application_id VARCHAR(36) NOT NULL, actor_id VARCHAR(80) COLLATE utf8mb4_bin NOT NULL,
 actor_name VARCHAR(100) NOT NULL, action VARCHAR(30) NOT NULL, from_status VARCHAR(20), to_status VARCHAR(20) NOT NULL,
 reason VARCHAR(1000), revision INT NOT NULL, snapshot_json MEDIUMTEXT, created_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY (application_id) REFERENCES sc_application(id),
 INDEX idx_history_app (application_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS sc_notification (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(80) COLLATE utf8mb4_bin NOT NULL, application_id VARCHAR(36) NOT NULL,
 title VARCHAR(150) NOT NULL, content VARCHAR(1500) NOT NULL, is_read BOOLEAN NOT NULL, created_at TIMESTAMP(6) NOT NULL, FOREIGN KEY (application_id) REFERENCES sc_application(id),
 INDEX idx_notice_owner (user_id,is_read,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

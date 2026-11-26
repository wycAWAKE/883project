-- M4 schema v1. Select an explicitly chosen database before running this file.
-- Additive only: no USE, DROP, DELETE, or modification of legacy tables.
-- Run once; existing table names intentionally fail rather than hide schema drift.
-- Requires MySQL 8.0.16+ (enforced CHECK constraints).
CREATE TABLE m4_repair_ticket (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requester_username VARCHAR(64) NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    location_type VARCHAR(30) NOT NULL,
    location_text VARCHAR(200) NOT NULL,
    assignee_username VARCHAR(64) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    resolved_at DATETIME NULL,
    closed_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_m4_requester_created (requester_username, created_at, id),
    KEY idx_m4_assignee_status (assignee_username, status),
    KEY idx_m4_status_updated (status, updated_at),
    CONSTRAINT chk_m4_ticket_status CHECK
        (status IN ('SUBMITTED','ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED')),
    CONSTRAINT chk_m4_location CHECK
        (location_type IN ('DORM','CLASSROOM','PUBLIC_AREA','OTHER')),
    CONSTRAINT chk_m4_ticket_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE m4_repair_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    actor_username VARCHAR(64) NOT NULL,
    note VARCHAR(2000) NOT NULL,
    created_at DATETIME NOT NULL,
    ticket_version INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_m4_history_version (ticket_id, ticket_version),
    KEY idx_m4_history_order (ticket_id, id),
    CONSTRAINT fk_m4_history_ticket FOREIGN KEY (ticket_id)
        REFERENCES m4_repair_ticket (id) ON DELETE RESTRICT,
    CONSTRAINT chk_m4_history_version CHECK (ticket_version >= 0),
    CONSTRAINT chk_m4_history_role CHECK (actor_role IN ('stu','dormManager','admin'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

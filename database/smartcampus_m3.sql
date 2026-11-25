-- M3: Additive room-booking schema. Apply to the database used by the backend.
CREATE TABLE IF NOT EXISTS campus_room (
  room_id BIGINT NOT NULL AUTO_INCREMENT,
  room_code VARCHAR(30) NOT NULL,
  room_name VARCHAR(100) NOT NULL,
  capacity INT NOT NULL DEFAULT 1,
  active TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (room_id),
  UNIQUE KEY uk_campus_room_code (room_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS room_booking (
  booking_id BIGINT NOT NULL AUTO_INCREMENT,
  room_id BIGINT NOT NULL,
  username VARCHAR(50) NOT NULL,
  booking_date DATE NOT NULL,
  time_slot VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  cancelled_at TIMESTAMP NULL,
  occupied_slot VARCHAR(100) GENERATED ALWAYS AS (
    CASE WHEN status = 'BOOKED' THEN CONCAT(room_id, ':', booking_date, ':', time_slot) ELSE NULL END
  ) STORED,
  PRIMARY KEY (booking_id),
  UNIQUE KEY uk_room_booking_occupied_slot (occupied_slot),
  KEY idx_room_booking_owner (username, booking_date),
  KEY idx_room_booking_room_date (room_id, booking_date),
  CONSTRAINT fk_room_booking_room FOREIGN KEY (room_id) REFERENCES campus_room(room_id),
  CONSTRAINT ck_room_booking_status CHECK (status IN ('BOOKED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO campus_room (room_code, room_name, capacity) VALUES
('LIB-101', 'Library Study Room 101', 6),
('LIB-102', 'Library Study Room 102', 8),
('ENG-201', 'Engineering Room 201', 12)
ON DUPLICATE KEY UPDATE room_name = VALUES(room_name), capacity = VALUES(capacity), active = 1;

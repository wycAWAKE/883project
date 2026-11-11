-- Additive M2 schema. Select the intended isolated/team database explicitly.
-- Never run an original destructive dormitory initialization as a migration.
CREATE TABLE IF NOT EXISTS m2_course (
  code VARCHAR(32) PRIMARY KEY,
  source_course_code VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  topics TEXT NOT NULL,
  source_year INT NOT NULL,
  source_url VARCHAR(512) NOT NULL,
  syllabus_url VARCHAR(512) NOT NULL,
  source_sha256 CHAR(64) NOT NULL,
  license_url VARCHAR(255) NOT NULL,
  instructors_json TEXT NOT NULL,
  prerequisite_status VARCHAR(32) NOT NULL,
  prerequisite_evidence TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS m2_prerequisite (
  course_code VARCHAR(32) NOT NULL,
  group_no INT NOT NULL,
  prerequisite_code VARCHAR(32) NOT NULL,
  PRIMARY KEY (course_code,group_no,prerequisite_code),
  CONSTRAINT fk_m2_prerequisite_course FOREIGN KEY (course_code) REFERENCES m2_course(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS m2_completed (
  student_username VARCHAR(50) NOT NULL,
  course_code VARCHAR(32) NOT NULL,
  provenance VARCHAR(32) NOT NULL,
  PRIMARY KEY (student_username,course_code),
  CONSTRAINT fk_m2_completed_student FOREIGN KEY (student_username) REFERENCES student(username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS m2_profile (
  student_username VARCHAR(50) PRIMARY KEY,
  interests VARCHAR(500) NOT NULL,
  goals VARCHAR(300) NOT NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_m2_profile_student FOREIGN KEY (student_username) REFERENCES student(username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS m2_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_username VARCHAR(50) NOT NULL,
  query_text VARCHAR(801) NOT NULL,
  results_json LONGTEXT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_m2_history_student (student_username,id),
  CONSTRAINT fk_m2_history_student FOREIGN KEY (student_username) REFERENCES student(username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

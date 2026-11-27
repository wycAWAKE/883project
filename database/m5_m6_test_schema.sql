-- NEW disposable test database only. Fails if the database exists.
CREATE DATABASE smartcampus_group_m5m6_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE smartcampus_group_m5m6_test;
CREATE TABLE admin (
  username VARCHAR(50) PRIMARY KEY,
  password VARCHAR(100) NOT NULL,
  name VARCHAR(50) NOT NULL,
  gender VARCHAR(10),
  age INT DEFAULT 0,
  phone_num VARCHAR(30),
  email VARCHAR(100),
  avatar VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 答辩链路-学生表结构：Student.java 通过 @TableName("student") 对应这张表；下一步看 StudentMapper/StudentServiceImpl 如何插入和查询。
CREATE TABLE student (
  username VARCHAR(50) PRIMARY KEY,
  password VARCHAR(100) NOT NULL,
  name VARCHAR(50) NOT NULL,
  age INT DEFAULT 0,
  gender VARCHAR(10),
  phone_num VARCHAR(30),
  email VARCHAR(100),
  avatar VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dorm_manager (
  username VARCHAR(50) PRIMARY KEY,
  password VARCHAR(100) NOT NULL,
  dormbuild_id INT NOT NULL,
  name VARCHAR(50) NOT NULL,
  gender VARCHAR(10),
  age INT DEFAULT 0,
  phone_num VARCHAR(30),
  email VARCHAR(100),
  avatar VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dorm_build (
  id INT PRIMARY KEY AUTO_INCREMENT,
  dormbuild_id INT NOT NULL UNIQUE,
  dormbuild_name VARCHAR(100) NOT NULL,
  dormbuild_detail VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dorm_room (
  dormroom_id INT PRIMARY KEY,
  dormbuild_id INT NOT NULL,
  floor_num INT NOT NULL,
  max_capacity INT NOT NULL DEFAULT 4,
  current_capacity INT NOT NULL DEFAULT 0,
  first_bed VARCHAR(50),
  second_bed VARCHAR(50),
  third_bed VARCHAR(50),
  fourth_bed VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notice (
  id INT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(100) NOT NULL,
  content TEXT,
  author VARCHAR(50),
  release_time VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE repair (
  id INT PRIMARY KEY AUTO_INCREMENT,
  repairer VARCHAR(50),
  dormbuild_id INT,
  dormroom_id INT,
  title VARCHAR(100) NOT NULL,
  content TEXT,
  state VARCHAR(30),
  order_buildtime VARCHAR(50),
  order_finishtime VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE visitor (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  gender VARCHAR(10),
  phone_num VARCHAR(30),
  origin_city VARCHAR(100),
  visit_time VARCHAR(50),
  content TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE adjust_room (
  id INT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  name VARCHAR(50),
  currentroom_id INT,
  currentbed_id INT,
  towardsroom_id INT,
  towardsbed_id INT,
  state VARCHAR(30),
  apply_time VARCHAR(50),
  finish_time VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dorm_water (
  id INT PRIMARY KEY AUTO_INCREMENT,
  operatorName VARCHAR(50),
  roomid INT,
  type VARCHAR(20),
  money DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  date VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dorm_electric (
  id INT PRIMARY KEY AUTO_INCREMENT,
  operatorName VARCHAR(50),
  roomid INT,
  type VARCHAR(20),
  money DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  date VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dorm_check (
  id INT PRIMARY KEY AUTO_INCREMENT,
  operatorName VARCHAR(50),
  roomid INT,
  grade VARCHAR(20),
  reason VARCHAR(255),
  date VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role (
  id INT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(50) NOT NULL UNIQUE,
  role_name VARCHAR(50) NOT NULL,
  description VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_menu (
  id INT PRIMARY KEY AUTO_INCREMENT,
  parent_id INT NOT NULL DEFAULT 0,
  menu_name VARCHAR(50) NOT NULL,
  menu_path VARCHAR(100),
  icon VARCHAR(50),
  sort_num INT NOT NULL DEFAULT 0,
  menu_type VARCHAR(20) NOT NULL DEFAULT 'menu',
  permission_code VARCHAR(100),
  description VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_menu (
  id INT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(50) NOT NULL,
  menu_id INT NOT NULL,
  UNIQUE KEY uk_role_menu (role_code, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


INSERT INTO admin(username,password,name) VALUES ('admin','123456','Test administrator');
INSERT INTO student(username,password,name,email) VALUES ('stu1','123456','Test student A','a@example.com'),('stu2','123456','Test student B','b@example.com');
INSERT INTO dorm_manager(username,password,dormbuild_id,name,email) VALUES ('dorm1','123456',1,'Test reviewer','reviewer@example.com');
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

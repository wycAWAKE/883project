-- First install only, isolated English demo database. No original database changes.
CREATE DATABASE dormitory_en_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE dormitory_en_demo;
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


INSERT INTO admin(username,password,name) VALUES ('admin','123456','Administrator');
INSERT INTO student(username,password,name,email) VALUES ('stu1','123456','Alex Chen','stu1@example.com'),('stu2','123456','Jamie Wang','stu2@example.com');
INSERT INTO dorm_manager(username,password,dormbuild_id,name,email) VALUES ('dorm1','123456',1,'Taylor Morgan','dorm1@example.com');
INSERT INTO dorm_build(dormbuild_id,dormbuild_name,dormbuild_detail) VALUES (1,'Building A','Campus accommodation'),(2,'Building B','Campus accommodation');
INSERT INTO dorm_room(dormroom_id,dormbuild_id,floor_num,max_capacity,current_capacity,first_bed) VALUES (101,1,1,4,1,'stu1'),(201,2,2,4,1,'stu2');
INSERT INTO dorm_check(id,operatorName,roomid,grade,reason,date) VALUES (910000,'dorm1',101,'优秀','Clean and tidy','2026-10-26 09:00:00');

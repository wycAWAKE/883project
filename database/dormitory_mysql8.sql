CREATE DATABASE IF NOT EXISTS dormitory
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE dormitory;

DROP TABLE IF EXISTS adjust_room;
DROP TABLE IF EXISTS sys_role_menu;
DROP TABLE IF EXISTS sys_menu;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS dorm_check;
DROP TABLE IF EXISTS dorm_electric;
DROP TABLE IF EXISTS dorm_water;
DROP TABLE IF EXISTS repair;
DROP TABLE IF EXISTS visitor;
DROP TABLE IF EXISTS notice;
DROP TABLE IF EXISTS dorm_room;
DROP TABLE IF EXISTS dorm_build;
DROP TABLE IF EXISTS dorm_manager;
DROP TABLE IF EXISTS student;
DROP TABLE IF EXISTS admin;

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

INSERT INTO admin (username, password, name, gender, age, phone_num, email, avatar) VALUES
('admin', '123456', '系统管理员', '男', 30, '13800000000', 'admin@example.com', NULL);

-- 答辩链路-初始化数据来源：这些演示学生是 SQL 脚本直接插入的；如果页面新增学生，则走 StuInfo.js -> StudentController.add() -> studentMapper.insert()。
INSERT INTO student (username, password, name, age, gender, phone_num, email, avatar) VALUES
('stu1', '123456', '张三', 20, '男', '13800000001', 'stu1@example.com', NULL),
('stu2', '123456', '李四', 21, '女', '13800000002', 'stu2@example.com', NULL);

INSERT INTO dorm_manager (username, password, dormbuild_id, name, gender, age, phone_num, email, avatar) VALUES
('dorm1', '123456', 1, '宿管一号', '女', 35, '13800000003', 'dorm1@example.com', NULL);

INSERT INTO dorm_build (dormbuild_id, dormbuild_name, dormbuild_detail) VALUES
(1, '一号宿舍楼', '男生宿舍楼'),
(2, '二号宿舍楼', '女生宿舍楼');

INSERT INTO dorm_room (dormroom_id, dormbuild_id, floor_num, max_capacity, current_capacity, first_bed, second_bed, third_bed, fourth_bed) VALUES
(101, 1, 1, 4, 1, 'stu1', NULL, NULL, NULL),
(102, 1, 1, 4, 0, NULL, NULL, NULL, NULL),
(201, 2, 2, 4, 1, 'stu2', NULL, NULL, NULL);

INSERT INTO notice (title, content, author, release_time) VALUES
('系统上线通知', '宿舍管理系统已完成课程技术栈适配，可用于日常宿舍管理演示。', 'admin', '2026-05-30 09:00:00');

INSERT INTO repair (repairer, dormbuild_id, dormroom_id, title, content, state, order_buildtime, order_finishtime) VALUES
('stu1', 1, 101, '宿舍灯管损坏', '101 宿舍灯管闪烁，需要维修。', '待处理', '2026-05-30 10:00:00', NULL);

INSERT INTO dorm_water (operatorName, roomid, type, money, date) VALUES
('admin', 101, '充值', 100.00, '2026-05-30 10:10:00');

INSERT INTO dorm_electric (operatorName, roomid, type, money, date) VALUES
('admin', 101, '充值', 120.00, '2026-05-30 10:15:00');

INSERT INTO dorm_check (operatorName, roomid, grade, reason, date) VALUES
('dorm1', 101, '优秀', '卫生整洁', '2026-05-30 10:20:00');

INSERT INTO sys_role (role_code, role_name, description) VALUES
('admin', '系统管理员', '拥有系统全部管理菜单和账号维护权限'),
('dormManager', '宿舍管理员', '负责学生、宿舍、报修、调宿、访客、卫生检查等日常管理'),
('stu', '学生', '查看个人宿舍信息，提交报修和调宿申请，维护个人资料');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_path, icon, sort_num, menu_type, permission_code, description) VALUES
(1, 0, '首页', '/home', 'House', 1, 'menu', 'home:view', '系统首页和统计看板'),
(2, 0, '用户管理', NULL, 'User', 2, 'catalog', 'user:manage', '学生和宿管账号管理'),
(21, 2, '学生信息', '/stuInfo', NULL, 21, 'menu', 'student:manage', '学生账号增删改查'),
(22, 2, '宿管信息', '/dormManagerInfo', NULL, 22, 'menu', 'dorm-manager:manage', '宿管账号增删改查'),
(3, 0, '宿舍管理', NULL, 'Coin', 3, 'catalog', 'dorm:manage', '楼宇和房间维护'),
(31, 3, '楼宇信息', '/buildingInfo', NULL, 31, 'menu', 'building:manage', '宿舍楼管理'),
(32, 3, '房间信息', '/roomInfo', NULL, 32, 'menu', 'room:manage', '宿舍房间与床位管理'),
(4, 0, '信息管理', NULL, 'Message', 4, 'catalog', 'info:manage', '公告与报修管理'),
(41, 4, '公告信息', '/noticeInfo', NULL, 41, 'menu', 'notice:manage', '公告发布与维护'),
(42, 4, '报修信息', '/repairInfo', NULL, 42, 'menu', 'repair:manage', '报修工单处理'),
(5, 0, '申请管理', NULL, 'PieChart', 5, 'catalog', 'apply:manage', '调宿申请审核'),
(51, 5, '调宿申请', '/adjustRoomInfo', NULL, 51, 'menu', 'adjust-room:manage', '调宿申请审核和处理'),
(6, 0, '访客管理', '/visitorInfo', 'View', 6, 'menu', 'visitor:manage', '访客登记管理'),
(7, 0, '我的宿舍', '/myRoomInfo', 'School', 7, 'menu', 'my-room:view', '学生查看自己的宿舍和床位'),
(8, 0, '申请调宿', '/applyChangeRoom', 'TakeawayBox', 8, 'menu', 'adjust-room:apply', '学生提交调宿申请'),
(9, 0, '报修申请', '/applyRepairInfo', 'SetUp', 9, 'menu', 'repair:apply', '学生提交报修申请'),
(10, 0, '水电费管理', NULL, 'SetUp', 10, 'catalog', 'fee:manage', '水费和电费记录管理'),
(101, 10, '水费管理', '/WaterInfo', NULL, 101, 'menu', 'water:manage', '水费充值和扣费记录'),
(102, 10, '电费管理', '/ElectricInfo', NULL, 102, 'menu', 'electric:manage', '电费充值和扣费记录'),
(11, 0, '卫生检查', '/CheckInfo', 'Tickets', 11, 'menu', 'check:manage', '宿舍卫生检查记录'),
(12, 0, '权限说明', '/permissionInfo', 'Key', 12, 'menu', 'permission:view', '角色菜单权限说明'),
(13, 0, '个人信息', '/selfInfo', 'Setting', 99, 'menu', 'self:update', '个人资料维护');

INSERT INTO sys_role_menu (role_code, menu_id) VALUES
('admin', 1), ('admin', 2), ('admin', 21), ('admin', 22), ('admin', 3), ('admin', 31), ('admin', 32),
('admin', 4), ('admin', 41), ('admin', 42), ('admin', 5), ('admin', 51), ('admin', 6), ('admin', 10),
('admin', 101), ('admin', 102), ('admin', 11), ('admin', 12), ('admin', 13),
('dormManager', 1), ('dormManager', 2), ('dormManager', 21), ('dormManager', 3), ('dormManager', 31),
('dormManager', 32), ('dormManager', 4), ('dormManager', 42), ('dormManager', 5), ('dormManager', 51),
('dormManager', 6), ('dormManager', 11), ('dormManager', 13),
('stu', 1), ('stu', 7), ('stu', 8), ('stu', 9), ('stu', 10), ('stu', 101), ('stu', 102), ('stu', 11), ('stu', 13);

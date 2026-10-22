USE dormitory;

DROP TABLE IF EXISTS sys_role_menu;
DROP TABLE IF EXISTS sys_menu;
DROP TABLE IF EXISTS sys_role;

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

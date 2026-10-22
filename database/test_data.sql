USE dormitory;

-- 可重复执行的测试数据：覆盖账号、宿舍、公告、报修、访客、水电、查寝、调宿等功能。
START TRANSACTION;

DELETE FROM adjust_room WHERE username IN ('test_stu01', 'test_stu02', 'test_stu03', 'test_stu04');
DELETE FROM dorm_check WHERE roomid IN (301, 302, 401, 402);
DELETE FROM dorm_electric WHERE roomid IN (301, 302, 401, 402);
DELETE FROM dorm_water WHERE roomid IN (301, 302, 401, 402);
DELETE FROM repair WHERE repairer IN ('王小明', '赵雨晴', '陈一凡', '刘欣然') OR title LIKE '测试-%';
DELETE FROM visitor WHERE phone_num IN ('13910001001', '13910001002', '13910001003');
DELETE FROM notice WHERE title LIKE '测试-%';
DELETE FROM dorm_room WHERE dormroom_id IN (301, 302, 401, 402);
DELETE FROM dorm_build WHERE dormbuild_id IN (3, 4);
DELETE FROM dorm_manager WHERE username IN ('test_dorm03', 'test_dorm04');
DELETE FROM student WHERE username IN ('test_stu01', 'test_stu02', 'test_stu03', 'test_stu04', 'test_stu05');

INSERT INTO student (username, password, name, age, gender, phone_num, email, avatar) VALUES
('test_stu01', '123456', '王小明', 19, '男', '13900001001', 'test_stu01@example.com', NULL),
('test_stu02', '123456', '赵雨晴', 20, '女', '13900001002', 'test_stu02@example.com', NULL),
('test_stu03', '123456', '陈一凡', 21, '男', '13900001003', 'test_stu03@example.com', NULL),
('test_stu04', '123456', '刘欣然', 22, '女', '13900001004', 'test_stu04@example.com', NULL),
('test_stu05', '123456', '周未分配', 20, '男', '13900001005', 'test_stu05@example.com', NULL);

INSERT INTO dorm_manager (username, password, dormbuild_id, name, gender, age, phone_num, email, avatar) VALUES
('test_dorm03', '123456', 3, '三号楼宿管', '女', 36, '13920001003', 'test_dorm03@example.com', NULL),
('test_dorm04', '123456', 4, '四号楼宿管', '男', 38, '13920001004', 'test_dorm04@example.com', NULL);

INSERT INTO dorm_build (dormbuild_id, dormbuild_name, dormbuild_detail) VALUES
(3, '三号宿舍楼', '测试数据：混合状态宿舍楼'),
(4, '四号宿舍楼', '测试数据：用于调宿和空床位演示');

INSERT INTO dorm_room (dormroom_id, dormbuild_id, floor_num, max_capacity, current_capacity, first_bed, second_bed, third_bed, fourth_bed) VALUES
(301, 3, 3, 4, 2, 'test_stu01', 'test_stu03', NULL, NULL),
(302, 3, 3, 4, 4, 'test_stu02', 'stu1', 'stu2', 'test_stu04'),
(401, 4, 4, 4, 0, NULL, NULL, NULL, NULL),
(402, 4, 4, 4, 1, 'test_stu05', NULL, NULL, NULL);

INSERT INTO notice (title, content, author, release_time) VALUES
('测试-停水通知', '三号宿舍楼本周五 14:00-16:00 进行管道检修，请提前储水。', 'admin', '2026-05-30 11:10:00'),
('测试-卫生检查安排', '四号宿舍楼晚间开展卫生抽查，请保持宿舍整洁。', 'test_dorm04', '2026-05-30 11:15:00'),
('测试-门禁维护提醒', '宿舍门禁设备将进行维护，维护期间请携带学生证进出。', 'admin', '2026-05-30 11:20:00');

INSERT INTO repair (repairer, dormbuild_id, dormroom_id, title, content, state, order_buildtime, order_finishtime) VALUES
('王小明', 3, 301, '测试-空调遥控器失灵', '301 宿舍空调遥控器无法开机，需要更换电池或维修。', '未完成', '2026-05-30 11:25:00', NULL),
('赵雨晴', 3, 302, '测试-水龙头漏水', '302 洗手台水龙头持续滴水。', '完成', '2026-05-29 09:30:00', '2026-05-29 15:40:00'),
('陈一凡', 3, 301, '测试-椅子损坏', '301 二号床椅子腿松动，存在安全隐患。', '未完成', '2026-05-28 18:20:00', NULL);

INSERT INTO visitor (name, gender, phone_num, origin_city, visit_time, content) VALUES
('测试访客一', '男', '13910001001', '南京', '2026-05-30 12:00:00', '家长来访，前往三号楼 301。'),
('测试访客二', '女', '13910001002', '苏州', '2026-05-30 13:30:00', '同学来访，登记后离开。'),
('测试访客三', '男', '13910001003', '杭州', '2026-05-31 09:00:00', '维修人员，处理四号楼门禁。');

INSERT INTO dorm_water (operatorName, roomid, type, money, date) VALUES
('test_dorm03', 301, '充值', 80.00, '2026-05-30 11:30:00'),
('test_dorm03', 302, '扣费', 18.50, '2026-05-30 11:35:00'),
('admin', 401, '充值', 60.00, '2026-05-30 11:40:00');

INSERT INTO dorm_electric (operatorName, roomid, type, money, date) VALUES
('test_dorm03', 301, '充值', 120.00, '2026-05-30 11:45:00'),
('test_dorm03', 302, '扣费', 32.80, '2026-05-30 11:50:00'),
('admin', 402, '充值', 100.00, '2026-05-30 11:55:00');

INSERT INTO dorm_check (operatorName, roomid, grade, reason, date) VALUES
('test_dorm03', 301, '优秀', '桌面整洁，地面干净。', '2026-05-30 12:00:00'),
('test_dorm03', 302, '良好', '整体较好，阳台杂物较多。', '2026-05-30 12:05:00'),
('test_dorm04', 401, '待改进', '空房间待入住，需补充基础用品。', '2026-05-30 12:10:00');

INSERT INTO adjust_room (username, name, currentroom_id, currentbed_id, towardsroom_id, towardsbed_id, state, apply_time, finish_time) VALUES
('test_stu01', '王小明', 301, 1, 401, 1, '未处理', '2026-05-30 12:15:00', NULL),
('test_stu02', '赵雨晴', 302, 1, 401, 2, '通过', '2026-05-29 10:00:00', '2026-05-29 16:00:00'),
('test_stu03', '陈一凡', 301, 2, 402, 2, '驳回', '2026-05-28 08:40:00', '2026-05-28 11:20:00');

COMMIT;

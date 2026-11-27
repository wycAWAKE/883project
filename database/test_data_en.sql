-- Optional synthetic accounts only, for the isolated English demo database.
USE dormitory_en_demo;
INSERT INTO dorm_manager(username,password,dormbuild_id,name,email) VALUES
('test_dorm03','123456',1,'Morgan Lee','test_dorm03@example.com'),
('test_dorm04','123456',2,'Casey Liu','test_dorm04@example.com')
ON DUPLICATE KEY UPDATE name=VALUES(name),email=VALUES(email);
INSERT INTO student(username,password,name,email) VALUES
('test_stu01','123456','Chris Lin','test_stu01@example.com'),
('test_stu02','123456','Sam Wu','test_stu02@example.com'),
('test_stu03','123456','Jordan Li','test_stu03@example.com'),
('test_stu04','123456','Robin Sun','test_stu04@example.com'),
('test_stu05','123456','Avery Zhao','test_stu05@example.com')
ON DUPLICATE KEY UPDATE name=VALUES(name),email=VALUES(email);
INSERT INTO dorm_check(id,operatorName,roomid,grade,reason,date) VALUES
(910001,'test_dorm03',101,'合格','No issues found','2026-10-26 10:00:00')
ON DUPLICATE KEY UPDATE grade=VALUES(grade),reason=VALUES(reason);

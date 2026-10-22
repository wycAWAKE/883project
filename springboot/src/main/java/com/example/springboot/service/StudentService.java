package com.example.springboot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.springboot.entity.Student;


public interface StudentService extends IService<Student> {

    //学生登陆
    // 答辩链路-登录业务定义：Controller.login() 调用这里；下一步看 StudentServiceImpl.stuLogin() 使用 StudentMapper 查询 student 表。
    Student stuLogin(String username, String password);

    //新增学生
    // 答辩链路-新增业务定义：Controller.add() 调用这里；下一步看 StudentServiceImpl.addNewStudent() 调 studentMapper.insert()。
    int addNewStudent(Student student);

    //查询学生
    // 答辩链路-查询业务定义：Controller.findPage() 调用这里；下一步看 StudentServiceImpl.find() 做分页查询。
    Page find(Integer pageNum, Integer pageSize, String search);

    //更新学生信息
    int updateNewStudent(Student student);

    //删除学生信息
    int deleteStudent(String username);

    //统计学生人数
    int stuNum();

    //床位信息，查询该学生信息
    Student stuInfo(String username);

}

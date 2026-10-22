package com.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.springboot.entity.Student;

// 答辩链路-Mapper数据库接口：继承 BaseMapper 后自动拥有 insert、deleteById、updateById、selectPage 等方法；下一步看 entity/Student.java 如何映射 student 表。
public interface StudentMapper extends BaseMapper<Student> {
}

package com.example.springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.springboot.entity.Student;
import com.example.springboot.mapper.StudentMapper;
import com.example.springboot.service.StudentService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;


@Service
public class StudentServiceImpl extends ServiceImpl<StudentMapper, Student> implements StudentService {

    /**
     * 注入DAO层对象
     */
    @Resource
    // 答辩链路-数据库接口：ServiceImpl 通过 StudentMapper 操作 student 表；下一步看 mapper/StudentMapper.java。
    private StudentMapper studentMapper;

    /**
     * 学生登陆
     */
    @Override
    public Student stuLogin(String username, String password) {
        // 答辩链路-登录查库：按 username 和 password 构造查询条件；下一步 studentMapper.selectOne(qw) 查询 student 表。
        QueryWrapper<Student> qw = new QueryWrapper<>();
        qw.eq("username", username);
        qw.eq("password", password);
        Student student = studentMapper.selectOne(qw);
        if (student != null) {
            return student;
        } else {
            return null;
        }
    }

    /**
     * 学生新增
     */
    @Override
    public int addNewStudent(Student student) {
        // 答辩链路-数据入库：新增学生最终走 studentMapper.insert(student)；下一步 MyBatis-Plus 根据 Student 实体映射写入 student 表。
        int insert = studentMapper.insert(student);
        return insert;
    }

    /**
     * 分页查询学生
     */
    @Override
    public Page find(Integer pageNum, Integer pageSize, String search) {
        // 答辩链路-分页对象：pageNum/pageSize 来自前端 StuInfo.js；下一步 QueryWrapper 设置 name 模糊搜索。
        Page page = new Page<>(pageNum, pageSize);
        QueryWrapper<Student> qw = new QueryWrapper<>();
        // 答辩链路-搜索条件：按 student 表的 name 字段模糊查询；下一步 studentMapper.selectPage(page, qw) 真正查数据库。
        qw.like("name", search);
        Page studentPage = studentMapper.selectPage(page, qw);
        return studentPage;
    }

    /**
     * 更新学生信息
     */
    @Override
    public int updateNewStudent(Student student) {
        // 答辩链路-修改入库：根据 Student 的主键 username 更新 student 表；下一步返回影响行数给 StudentController.update()。
        int i = studentMapper.updateById(student);
        return i;
    }

    /**
     * 删除学生信息
     */
    @Override
    public int deleteStudent(String username) {
        // 答辩链路-删除入库：根据主键 username 删除 student 表记录；下一步返回影响行数给 StudentController.delete()。
        int i = studentMapper.deleteById(username);
        return i;
    }


    /**
     * 主页顶部：学生统计
     */
    @Override
    public int stuNum() {
        // 答辩链路-首页学生总数查库：统计 student 表 username 非空的记录数；下一步返回 StudentController.stuNum()。
        QueryWrapper<Student> qw = new QueryWrapper<>();
        qw.isNotNull("username");
        int stuNum = Math.toIntExact(studentMapper.selectCount(qw));
        return stuNum;
    }

    /**
     * 床位信息，查询该学生信息
     */
    @Override
    public Student stuInfo(String username) {
        QueryWrapper<Student> qw = new QueryWrapper<>();
        qw.eq("username", username);
        Student student = studentMapper.selectOne(qw);
        return student;
    }
}

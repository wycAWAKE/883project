package com.example.springboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.springboot.common.Result;
import com.example.springboot.entity.Student;
import com.example.springboot.entity.User;
import com.example.springboot.service.StudentService;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@RestController
// 答辩链路-学生接口前缀：Vite 转发后的 /stu/find、/stu/add 都会先匹配这里；下一步看具体 @GetMapping 或 @PostMapping 方法。
@RequestMapping("/stu")
public class StudentController {

    @Resource
    // 答辩链路-调用业务层：Controller 不直接操作数据库，而是调用 StudentService；下一步看 service/StudentService.java 和 impl/StudentServiceImpl.java。
    private StudentService studentService;

    /**
     * 添加学生信息
     */
    @PostMapping("/add")
    public Result<?> add(@RequestBody Student student) {
        // 答辩链路-新增学生入口：前端 StuInfo.js 的 request.post('/stu/add') 到这里；下一步调用 StudentServiceImpl.addNewStudent() 写入 student 表。
        int i = studentService.addNewStudent(student);
        if (i == 1) {
            return Result.success();
        } else {
            return Result.error("-1", "添加失败");
        }

    }

    /**
     * 更新学生信息
     */
    @PutMapping("/update")
    public Result<?> update(@RequestBody Student student) {
        // 答辩链路-修改学生入口：前端 StuInfo.js 的 request.put('/stu/update') 到这里；下一步调用 StudentServiceImpl.updateNewStudent() 更新 student 表。
        if (StringUtils.equals("test",student.getUsername())){
            return Result.error("-1", "禁止修改测试账号");
        }
        int i = studentService.updateNewStudent(student);
        if (i == 1) {
            return Result.success();
        } else {
            return Result.error("-1", "更新失败");
        }
    }

    /**
     * 删除学生信息
     */
    @DeleteMapping("/delete/{username}")
    public Result<?> delete(@PathVariable String username) {
        // 答辩链路-删除学生入口：前端 StuInfo.js 的 request.delete('/stu/delete/{username}') 到这里；下一步调用 StudentServiceImpl.deleteStudent() 删除 student 表记录。
        if (StringUtils.equals("test",username)){
            return Result.error("-1", "禁止删除测试账号");
        }
        int i = studentService.deleteStudent(username);
        if (i == 1) {
            return Result.success();
        } else {
            return Result.error("-1", "删除失败");
        }
    }

    /**
     * 查找学生信息
     */
    @GetMapping("/find")
    public Result<?> findPage(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(defaultValue = "") String search) {
        // 答辩链路-查询学生入口：前端 StuInfo.js 的 request.get('/stu/find') 到这里；下一步调用 StudentServiceImpl.find() 做分页和姓名搜索。
        Page page = studentService.find(pageNum, pageSize, search);
        if (page != null) {
            // 答辩链路-统一返回：把 Page 包成 {code,msg,data} JSON；下一步看 common/Result.java 的 success(T data)，再回到前端 StuInfo.js 的 then(res)。
            return Result.success(page);
        } else {
            return Result.error("-1", "查询失败");
        }
    }

    /**
     * 学生登录
     */
    @PostMapping("/login")
    public Result<?> login(@RequestBody User user, HttpSession session) {
        // 答辩链路-学生登录入口：前端 Login.js 拼出 /stu/login 到这里；下一步 StudentServiceImpl.stuLogin() 查询 student 表验证账号密码。
        System.out.println(user.getUsername());
        System.out.println(user.getPassword());
        Object o = studentService.stuLogin(user.getUsername(), user.getPassword());
        if (o != null) {
            System.out.println(o);
            //存入session
            // 答辩链路-后端Session：登录成功后把身份和用户对象存进服务器 Session；下一步浏览器通过 JSESSIONID Cookie 继续携带登录状态。
            session.setAttribute("Identity", "stu");
            session.setAttribute("User", o);
            return Result.success(o);
        } else {
            return Result.error("-1", "用户名或密码错误");
        }
    }

    /**
     * 主页顶部：学生统计
     */
    @GetMapping("/stuNum")
    public Result<?> stuNum() {
        // 答辩链路-首页学生总数：Home.js 请求 /stu/stuNum 到这里；下一步 StudentServiceImpl.stuNum() 统计 student 表。
        int num = studentService.stuNum();
        if (num > 0) {
            return Result.success(num);
        } else {
            return Result.error("-1", "查询失败");
        }
    }


    /**
     * 床位信息，查询是否存在该学生
     * 床位信息，查询床位上的学生信息
     */
    @GetMapping("/exist/{value}")
    public Result<?> exist(@PathVariable String value) {
        Student student = studentService.stuInfo(value);
        if (student != null) {
            return Result.success(student);
        } else {
            return Result.error("-1", "不存在该学生");
        }
    }
}

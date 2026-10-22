# 高校宿舍管理系统

基于 Spring Boot + MySQL 8.0 + Vue 3 + Vite 的前后端分离宿舍管理系统，可作为课程大作业中的“其他管理系统”项目提交。

## 项目简介

系统围绕高校宿舍管理场景设计，支持管理员、宿舍管理员、学生三种角色。功能覆盖账号维护、宿舍楼管理、房间床位管理、公告管理、报修处理、调宿审核、水电费记录、卫生检查、访客登记、角色菜单权限控制等模块。

项目已补充权限表设计、动态菜单渲染、移动端基础适配、测试数据脚本和完整技术文档。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 后端 | Spring Boot 2.6.3、MyBatis-Plus、Maven |
| 数据库 | MySQL 8.0 |
| 前端 | Vue 3、Vite、Element Plus、Vue Router、Axios |
| 开发环境 | JDK 1.8、Node.js + npm、IntelliJ IDEA |

## 功能模块

- 登录认证：支持管理员、宿舍管理员、学生三类身份登录
- 动态菜单：基于 `sys_role`、`sys_menu`、`sys_role_menu` 返回不同角色菜单
- 用户管理：学生账号管理、宿舍管理员账号管理
- 宿舍管理：宿舍楼管理、房间管理、床位分配
- 信息管理：公告发布、报修工单处理
- 申请管理：学生调宿申请、管理员/宿管审核
- 费用管理：水费、电费记录
- 日常管理：卫生检查、访客登记
- 个人中心：个人信息维护和头像上传
- 双端适配：PC 后台布局，移动端横向菜单和自适应表单/弹窗

## 目录结构

```text
dormitory-manager
├── database                  # 数据库脚本和测试数据
├── docs                      # 技术文档
├── springboot                # Spring Boot 后端
├── vue                       # Vue 3 + Vite 前端
└── picture                   # 项目截图
```

## 数据库脚本

| 文件 | 说明 |
| --- | --- |
| `database/dormitory_mysql8.sql` | 初始化数据库、基础业务表、权限表和基础数据 |
| `database/permission_tables.sql` | 单独创建权限表和菜单数据 |
| `database/test_data.sql` | 可重复导入的测试数据 |
| `database/test_cases.md` | 测试账号和建议测试流程 |

## 快速启动

### 1. 启动 MySQL

```bash
brew services start mysql
```

### 2. 导入数据库

```bash
cd dormitory-manager
mysql -uroot < database/dormitory_mysql8.sql
mysql -uroot < database/test_data.sql
```

如果只是给已有数据库补权限表：

```bash
mysql -uroot < database/permission_tables.sql
```

### 3. 启动后端

使用 IntelliJ IDEA 打开：

```text
springboot
```

运行：

```text
src/main/java/com/example/springboot/SpringbootApplication.java
```

后端默认端口：

```text
http://localhost:9090
```

### 4. 启动前端

```bash
cd vue
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:8080
```

## 测试账号

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | admin | 123456 |
| 宿舍管理员 | dorm1 | 123456 |
| 学生 | stu1 | 123456 |
| 测试宿管 | test_dorm03 | 123456 |
| 测试学生 | test_stu01 | 123456 |

## 权限设计

系统包含三张权限相关表：

- `sys_role`：角色表，保存管理员、宿舍管理员、学生三种角色
- `sys_menu`：菜单表，保存菜单名称、路由、图标、权限标识
- `sys_role_menu`：角色菜单关联表，控制不同角色可访问的菜单

前端侧边栏通过接口获取菜单：

```text
GET /permission/menus
```

后端根据登录 Session 中的身份查询菜单树，实现不同角色显示不同菜单。

## 技术文档

详细设计、数据库说明、接口说明、启动流程、测试用例见：

[docs/技术文档.md](docs/技术文档.md)

从 0 启动项目的完整步骤见：

[docs/从0启动项目.md](docs/从0启动项目.md)

## 验证命令

后端编译：

```bash
cd springboot
mvn -DskipTests compile
```

前端构建：

```bash
cd vue
npm run build
```

## 课程要求对应

| 课程要求 | 本项目实现 |
| --- | --- |
| 管理系统项目 | 高校宿舍管理系统 |
| 数据库表结构设计 | 用户表、宿舍楼表、房间表、权限表、业务表 |
| 三种角色 | 管理员、宿舍管理员、学生 |
| 后端接口开发 | Spring Boot Controller + Service + Mapper |
| Vue 前端页面 | Vue 3 + Element Plus |
| 登录权限拦截 | Vue Router 路由守卫 + Session |
| 菜单动态渲染 | 权限表 + `/permission/menus` |
| 核心功能 | 登录、账号维护、宿舍管理、角色权限控制 |
| 双端适配 | PC 后台布局 + 移动端响应式适配 |

## 仓库整理说明

- 本仓库是宿舍管理系统课程大作业的唯一维护仓库，不与普通 Java Web 实验仓库合并。
- 数据库脚本、后端、前端、技术文档和截图统一保存在本仓库，保证提交物完整。
- 修改业务功能时同步更新 `docs/技术文档.md`、`docs/从0启动项目.md` 和本 README 的功能清单。

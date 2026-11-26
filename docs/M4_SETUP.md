# M4 运行和阶段验证记录

当前后端、学生及员工界面均已完成。最新启动、演示及验证说明优先见 M4_RUNBOOK.md；本文件保留阶段记录。

## 建表

已提交的 `database/m4_schema.sql` 是新增建表脚本；**本次没有对现有业务库执行该脚本**。
实际使用新 API 前，在明确选定的数据库中首次执行脚本。不要重导原 `dormitory_mysql8.sql`，该旧脚本包含破坏性重建。

PowerShell 示例，先将 YOUR_CHOSEN_DATABASE 换成明确选择的开发库名：

```powershell
Set-Location D:\Projects\883project
Get-Content -Raw database/m4_schema.sql | mysql --batch --default-character-set=utf8mb4 -u root -p -D YOUR_CHOSEN_DATABASE
```

此文件不创建账号，不迁移旧 repair 记录，不包含业务数据库密码。目标库还须已有项目登录需要的账号数据。
本次只在随机隔离的测试库验证了建表、持久化、归属和事务；测试库在结束时清理。

## 基础测试

```powershell
Set-Location D:\Projects\883project\springboot
mvn -B -ntp '-DforkCount=0' test
```

本机 Java 8 与中文用户临时目录组合会使 Surefire 默认测试子进程启动失败。
`forkCount=0` 避开该问题，无需修改全组 POM。PowerShell 下对含点的 Maven 参数整体加引号。
当前结果：14 个基础/状态/上下文用例通过；MySQL 集成测试类未显式启用时跳过（报告将该类记作 1 项 skipped）。

## 真实 MySQL 集成测试

默认不运行，启用命令：

```powershell
mvn -B -ntp '-DforkCount=0' '-Dm4.mysql=true' '-Dtest=RepairTicketMysqlIntegrationTest' test
```

测试读取 M4_TEST_MYSQL_URL、M4_TEST_MYSQL_USERNAME、M4_TEST_MYSQL_PASSWORD；未提供时回退到 SPRING_DATASOURCE_* 环境变量，再回退项目 application.properties。
密码只通过本机环境变量提供。例如运行前在当前终端安全输入：

```powershell
$m4Credential = Get-Credential -UserName root -Message 'Local MySQL test connection'
$env:M4_TEST_MYSQL_USERNAME = $m4Credential.UserName
$env:M4_TEST_MYSQL_PASSWORD = $m4Credential.GetNetworkCredential().Password
try {
  mvn -B -ntp '-DforkCount=0' '-Dm4.mysql=true' '-Dtest=RepairTicketMysqlIntegrationTest' test
} finally {
  Remove-Item Env:\M4_TEST_MYSQL_USERNAME, Env:\M4_TEST_MYSQL_PASSWORD -ErrorAction SilentlyContinue
  Remove-Variable m4Credential
}
```

账号需要创建/删除测试数据库与建表的权限。测试只创建 `m4_phase1_test_` 加随机 12 位十六进制后缀的库，在自身测试库中建表和清理记录，结束时只删除自己成功创建的随机库。
不向原业务库导入 SQL，不使用硬编码密码。若创建测试库权限不足，应使用具备测试权限的本机连接，而非修改或删除原业务库。
当前结果：14 项真实 MySQL 测试通过，覆盖持久化、分页、同名和大小写归属隔离、完整处理流程、改派、越权与非法状态拒绝、并发冲突，以及创建和更新时历史失败回滚。测试仅在自身隔离库建立最小 dorm_manager 测试表与合成员工，不修改原账号表。
这 14 项接口测试使用 Spring MockMvc 和模拟已登录的真实账号类型对象；第三阶段另有独立的真实浏览器、登录与数据库联调验证，见 M4_STUDENT_UI.md。

## 运行后端时的连接

正常启动使用项目既有流程和 SPRING_DATASOURCE_* 环境变量。测试的临时密码环境变量结束后已清除，不会自动配置日常启动连接。
也可自行使用被 Git 忽略的 application-local.properties，并显式启用 local profile；该本地文件不由测试自动读取。
不要将真实密码填回可提交的 application.properties。
首次启动后先通过现有学生登录建立 Session，再调用 `/repairs`；学生可在 Fault Reporting 页面提交和查询；员工和管理员使用同一路由的 Maintenance queue。

## 故障区分

- 401：未登录或 Session 身份类型不匹配。
- 403：非学生试图提交，学生访问员工选项或写操作，或非负责人处理工单。
- 404：记录不存在，或学生试图查看他人工单。
- 400：无效字段、未知字段、非法枚举、ID 或分页参数。
- 409：旧版本冲突、非法状态转换、重复分派或关闭；刷新后重新确认。
- 500：数据库操作失败；检查是否已在选定库建表以及本地连接是否可用，不把 SQL 或连接秘密返回浏览器。
- 回滚测试中一次预期的 M4 500 错误日志是故障注入的结果，不是测试失败。


## 第二阶段请求示例

先通过既有学生登录建立 Session，然后 POST /repairs。员工在自己的独立会话中依次调用：

1. GET /repairs/staff 读取安全员工选项。
2. PUT /repairs/{id}/assignment，提交 assigneeUsername、当前 version 和可选 note。
3. PUT /repairs/{id}/status，targetStatus=IN_PROGRESS。
4. POST /repairs/{id}/progress，填写处理说明。
5. PUT /repairs/{id}/status，targetStatus=RESOLVED，填写解决说明。
6. PUT /repairs/{id}/status，targetStatus=CLOSED，填写关闭说明。

每一步使用前一步返回的最新 version。接口字段、错误和权限详见 M4_CONTRACT.md。
员工 account 仍复用 dorm_manager；新业务无需重复建立登录或 token。
M6 站内通知尚未实现，当前只能通过工单历史查询进度。

完整验证命令（先按上文配置进程环境变量）：

```powershell
mvn -B -ntp '-DforkCount=0' '-Dm4.mysql=true' test
```

第二阶段最终结果：28 项通过，0 failures，0 errors，0 skipped；其中真实 MySQL 14 项。
普通测试不启用数据库时：14 项通过，数据库测试类默认跳过。


## 第三阶段学生界面

已完成表单、本人列表、分页筛选、详情时间线、会话失效处理和失败输入恢复。测试及浏览器工具详见 M4_STUDENT_UI.md。

原 npm test 仍有两个历史 SQL 文件缺失问题；第三阶段定向检查 14 项与前端构建通过，真实浏览器场景通过，不等于全量前端测试完全通过。

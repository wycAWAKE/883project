# M4 报修模块运行与演示

更新日期：2026-11-08。学生、员工和管理员界面，以及工单后端流程已完成。
代码在原项目 D:\Projects\883project 内。当前没有常驻服务，也没有自动上传 GitHub。

## 选择运行方式

已有可用项目数据库时：沿用现有账号和连接，只首次执行 database/m4_schema.sql。若已建好两张 M4 表，不重复执行。
需要独立课堂演示时：使用下述可选脚本创建新的 smartcampus_m4_demo 库。该库只包含登录和 M4 所需表，不用于其他五个模块的演示。
本次开发仅创建、清理了随机测试库，原业务库及固定名称的演示库均未自动改动。

## 新建独立演示库

在 PowerShell 中，从仓库根目录执行：

```powershell
Set-Location D:\Projects\883project
Get-Content -Raw database/m4_demo_bootstrap.sql | mysql --batch --default-character-set=utf8mb4 -u root -p
```

输入的是本机 MySQL 密码，与下面的合成演示账号密码不同。
脚本只能在 smartcampus_m4_demo 尚不存在时运行；已存在时停下并检查，不加 --force，不改为原业务库名，也不要导入旧的破坏性初始化脚本。
MySQL DDL 不属于可整体回滚的事务；中断时先检查该新库的实际表结构。
脚本不依赖 SOURCE 或 --abort-source-on-error，后者在本机 MySQL 8.0.46 不支持。
已把脚本改名为随机测试库实测：建表及 2 学生、2 员工、1 管理员正确；批处理遇到第一个错误时停止，不执行后续语句。验证库已清理。

| 登录身份 | 合成演示账号 | 合成演示密码 |
| --- | --- | --- |
| Student | m4_demo_student1、m4_demo_student2 | DemoM4Only2026! |
| Staff | m4_demo_staff1、m4_demo_staff2 | DemoM4Only2026! |
| Administrator | m4_demo_admin | DemoM4Only2026! |

这些是公开的本机合成演示凭据，不是实际数据库密码或真实用户账号。

## 启动后端

在一个 PowerShell 终端配置本机连接。密码通过交互输入，不写入可提交的配置：

```powershell
Set-Location D:\Projects\883project\springboot
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/smartcampus_m4_demo?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$m4LocalCredential = Get-Credential -UserName root -Message 'Local MySQL connection'
$env:SPRING_DATASOURCE_USERNAME = $m4LocalCredential.UserName
$env:SPRING_DATASOURCE_PASSWORD = $m4LocalCredential.GetNetworkCredential().Password
try {
  mvn spring-boot:run
} finally {
  Remove-Item Env:\SPRING_DATASOURCE_URL,Env:\SPRING_DATASOURCE_USERNAME,Env:\SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
  Remove-Variable m4LocalCredential
}
```

默认后端端口 9090。使用已有库时将 URL 中的库名换成明确选择的开发库，并先确认 M4 表存在。

## 启动前端

新开终端：

```powershell
Set-Location D:\Projects\883project\vue
npm.cmd run dev -- --strictPort
```

访问 http://localhost:8080/Login，选择正确身份登录，再进入 Fault Reporting。
已有依赖时不必重装。首次配置环境需按根 README 的环境要求安装依赖。
Vite 默认代理到 localhost:9090；只有临时联调需要不同端口时才设置 SMARTCAMPUS_API_TARGET。
演示结束在各终端 Ctrl+C 停止，别停止其他项目共用的 MySQL 服务。

## 建议演示步骤

1. Student One 提交“Classroom light failure”，填写位置与故障说明，查看真实工单号。
2. Staff One 在 Maintenance queue 中查看新工单，分派给 Staff Two。
3. Staff Two 勾选 Only assigned to me，打开该工单，Start work 并 Confirm start。
4. Add progress 输入维修进度，Save progress；Resolve issue 填写解决说明；Close ticket 填写关闭说明。
5. Student One 刷新详情，展示分派、进度、解决和关闭的历史及时间。
6. Student Two 使用同一详情链接，应被拒绝读取；Administrator 可以管理全部工单。
7. 如需演示冲突，用两个独立会话同时打开同一版本。一个先保存后，另一个保存会收到 409；说明保留，刷新并点击 Use refreshed report 后再次确认。

不同账号使用不同浏览器配置或隐私窗口，避免共用同一个 JSESSIONID。
员工写入说明只在当前详情里编辑，发生错误会保留。登录失效时的处理草稿按角色、账号和工单恢复，过期两小时清除；恢复后必须重新核对工单。
状态与处理历史的事实来源始终是后端，按钮隐藏不替代权限检查。

## 验证命令与结果

前端定向测试与构建：

```powershell
Set-Location D:\Projects\883project\vue
node --test tests/repairs.test.mjs tests/repair-staff.test.mjs tests/smartcampus.test.mjs
npm.cmd run build
```

26 项定向检查通过，构建通过。全量 npm.cmd test 为 45 项，43 通过、2 项旧失败：delivery.test.mjs 依赖缺失的 dormitory_en_demo.sql 及英文演示数据。该问题修改前已存在，未通过删测试或伪造宿舍数据掩盖。

后端完整验收：从 springboot 目录，临时配置 M4_TEST_MYSQL_* 连接变量和本机 M4_PLAYWRIGHT_MODULE（Playwright 的 index.mjs 路径），执行：

```powershell
mvn -B -ntp '-DforkCount=0' '-Dm4.mysql=true' '-Dm4.browser=true' test
```

29 项通过：基础与状态检查、14 项真实 MySQL 接口场景，以及真实浏览器联合流程。浏览器工具使用本机已有 Edge，可选 M4_BROWSER_NODE 和 M4_BROWSER_CHANNEL；仓库未额外安装浏览器依赖。
普通后端测试默认跳过 MySQL 与浏览器类。Java 8 中文临时目录问题通过 forkCount=0 避开，PowerShell 含点参数整体加引号。

浏览器联合场景包含真实登录、学生提单、员工分派、管理员改派、权限转移、真实 409、保留说明并手动确认、解决和关闭、学生时间线、390px 手机视图、Session 失效恢复。
随后停止原 HTTP 服务，启动新的独立应用实例并重新登录，验证关闭工单和 8 条历史仍可读取。
测试全部限定在随机隔离数据库；用例结束清理自己创建的库和进程。

## 交付范围

M4 的四阶段既定范围已完成。M6 通知、附件、自动分派、评价、Cloud/Redis 改造仍属于扩展或全组工作；当前没有发送站内通知。
GitHub 提交和远端部署尚未执行；提交前检查 git status 和差异，不包含本机私密配置、密码、node_modules、target 或 dist。
关键截图和最终验证记录见 M4_ACCEPTANCE.md，接口字段见 M4_CONTRACT.md。

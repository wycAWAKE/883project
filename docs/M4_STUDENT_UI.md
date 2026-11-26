# M4 学生界面与第三阶段验证

更新日期：2026-11-08。本文件记录第三次学生界面实现；第四次员工界面现已完成，最终运行与验收见 M4_RUNBOOK.md 和 M4_ACCEPTANCE.md。

## 页面与组件

- `vue/src/views/modules/RepairModule.vue`：保留原模块路由、Layout 和标题；读取真实 Session，学生显示报修界面，员工或管理员显示第四次完成的 Maintenance queue。
- `vue/src/components/repair/RepairStudentPanel.vue`：本人列表、分页、关键词、状态筛选、提单入口和深链接。
- `RepairCreateDialog.vue`：原生 modal dialog，支持键盘取消与焦点约束；提交时禁止关闭和重复提交。
- `RepairDetailDrawer.vue`：工单详情、地点、负责人、解决/关闭时间和完整历史；支持手动刷新进度。
- `vue/src/api/repairs.js`：复用 request.js，只调用 `/repairs`，不重复添加 `/api`；处理 HTTP 错误与字符串 code。
- `vue/src/utils/repair.js`：状态文案、输入校验、请求顺序、提交恢复及学生草稿隔离。
- `vue/src/assets/css/repair.css`：M4 专用样式，兼容桌面与窄屏，不改其他模块页面。

## 学生操作

1. 用既有 Student 登录，进入 Fault Reporting。
2. New report 填写 Title、Location type、Location、Description。
3. 提交成功显示真实工单号，刷新列表，并跳转 `/repairs?ticketId=...` 打开详情。
4. 在 My fault reports 中搜索标题、筛选状态、切换页码或每页数量。
5. 点击工单标题或 View 查看详情，Refresh progress 获取真实后端处理历史。

不要求学生绑定旧宿舍；身份、初始状态和时间由后端生成，前端只提交四个允许字段。
用户输入通过 Vue 文本插值显示，包含 HTML 的故障说明也只作为文字显示。
状态标签为英文，时间明确使用 Asia/Shanghai。工单与历史均来自后端，未使用业务假数据。

## 错误与异步处理

- 加载、空记录、无匹配结果、失败重试、提交中、404 和会话过期有独立反馈。
- 切换筛选与详情时只应用最新请求结果；组件卸载后忽略仍在返回的旧请求。
- 创建请求不自动重试。网络超时保留输入并提示先检查已有工单，避免把可能成功的请求再次自动发送。
- 创建已成功但列表刷新失败时仍保留成功状态，不把它误报为提交失败。
- 401 清理登录缓存并返回登录页面，保留安全的模块返回路径。
- 为恢复登录过期时的输入，四个表单字段暂存在当前标签页 sessionStorage，按学生 username 分键，最长两小时。其他账号不会读取该草稿；成功提交后删除。不保存密码、token 或整份账号对象。
- 该草稿只用于恢复失败输入，不代表工单已保存；只有真实 POST 成功才算提交。

## 自动化验证

```powershell
Set-Location D:\Projects\883project\vue
node --test tests/repairs.test.mjs tests/smartcampus.test.mjs
npm.cmd run build
```

13 项学生端逻辑测试加 1 项路由 SSR 测试全部通过。其余五模块的占位断言保留，SSR 通过注入 Session/API 避免真实网络请求。
覆盖输入长度/空白、接口与错误、Session、深链接、旧请求覆盖、分页重置、重复提交、超时保留输入、提交成功后刷新失败、草稿隔离和过期、卸载后的请求返回。
前端构建通过。依赖中的 pure annotation 与公共包体积提示为构建警告，不是 M4 编译错误。

全量 `npm.cmd test` 当前为 33 项，31 通过、2 失败。两项失败在第三阶段修改前已经存在：`tests/delivery.test.mjs` 引用了缺少的 `database/dormitory_en_demo.sql`，并依赖相应英文演示数据脚本。
本轮没有删除或跳过这些旧检查，也没有伪造无关宿舍演示数据来把它们改绿。M4 新增及修改的检查均通过。

## 可选真实浏览器集成验证

新增 `RepairStudentBrowserIntegrationTest`（JUnit）和 `vue/tests/e2e/repair-student.browser.mjs`（Playwright）。普通测试默认跳过该类。
需已有 Node、Playwright 与浏览器；本次使用本机已有 Edge，无需在仓库新增 npm 依赖或下载浏览器。
若 Playwright 不在仓库依赖中，设置 `M4_PLAYWRIGHT_MODULE` 为本机 Playwright 的 `index.mjs` 绝对路径；可选 `M4_BROWSER_NODE` 指定 Node，`M4_BROWSER_CHANNEL` 指定浏览器 channel，默认 msedge。

在后端目录，按 M4_SETUP.md 临时配置数据库环境变量后运行：

```powershell
mvn -B -ntp '-DforkCount=0' '-Dm4.browser=true' '-Dtest=RepairStudentBrowserIntegrationTest' test
```

工具建立随机 `m4_browser_test_` 库，导入 M4 schema 和最小合成账号表；以随机端口启动真实 Spring Boot、Vite 代理与无界面浏览器。
Vite 可选变量 `SMARTCAMPUS_API_TARGET` 只改变开发代理目标，默认仍为 http://localhost:9090。
数据库密码不传给 Vite 或浏览器子进程。测试结束清理进程与自己创建的测试库，不迁移原业务库。

本次实际通过：真实学生登录和提单、标题筛选、网络失败与重试、六事件时间线、第二个学生越权拒绝、390px 手机布局、真实 Session 过期后重新登录恢复本人草稿，以及无浏览器未捕获异常。
员工处理通过已完成的真实 API 完成，用于验证学生能看到实际维修进度；不表示员工界面已经实现。
截图和详细日志位于被 Git 忽略的 `springboot/target/m4-browser`。已检查桌面详情、手机列表和手机详情。

## 运行边界

本轮未对原业务数据库建表或写入数据。日常运行前仍须选择开发库、按 M4_SETUP.md 执行一次 M4 schema，并提供本机数据库连接。
第四次完成双角色界面与联调后，M4 状态已更新为 Available。
本轮没有接入附件、M6 站内通知、Cloud 或 Redis，也没有提交或推送 GitHub。

# SmartCampus 校园服务平台

基于原 `dormitory-manager-coursework` 改造的校园服务平台，采用 Vue 3 + Vite、Spring Boot、MyBatis-Plus 和 MySQL。当前版本提供英文登录页、统一门户、六模块导航、独立模块页面和基于服务端 Session 的前端访问检查。

> **当前交付范围（2026-10-26）：门户框架。M1–M6 均为 `Under development` 占位页，尚未实现六模块业务接口。** 原宿舍业务代码、账号表、权限表和数据仍保留；当前前端没有注册原宿舍业务路由。本文面向部署与开发维护，界面及后续产品提示统一使用英文。

## 目录

- [1. 当前能力与模块入口](#1-当前能力与模块入口)
- [2. 项目结构与请求链路](#2-项目结构与请求链路)
- [3. 环境准备](#3-环境准备)
- [4. 本地部署：从数据库到登录](#4-本地部署从数据库到登录)
- [5. 构建与服务器部署](#5-构建与服务器部署)
- [6. 部署验证与问题排查](#6-部署验证与问题排查)
- [7. 公共开发约定](#7-公共开发约定)
- [8. 六个模块的开发建议](#8-六个模块的开发建议)
- [9. 建议实施顺序与完成标准](#9-建议实施顺序与完成标准)
- [10. 相关文档](#10-相关文档)

## 1. 当前能力与模块入口

### 1.1 已有能力

- 英文登录页，保留 `Student`、`Staff`、`Administrator` 三种登录身份。
- `/home` 门户首页，按 Learning、Facilities、Administration 分组显示六个服务入口。
- 首页、侧边栏与路由共用 `vue/src/config/modules.js`，每个模块有独立 Vue 文件。
- 每次进入受保护路由前查询服务端身份和用户信息；支持刷新、深链接、过期会话跳转与登录后返回原模块。
- 前端缓存用户时排除 `password` 和 `token`；退出登录需等待服务端确认。
- 桌面及移动端布局；原宿舍页面与后端保留，便于后续按需迁移。

当前侧边栏直接读取本地模块配置，**不再调用 `/permission/menus` 生成菜单**。三种已登录角色都能访问六个占位页。前端路由检查并不代表旧后端已实现完整的接口权限和数据归属保护。

### 1.2 模块列表

| 编号 | 界面名称 | 分组 | 前端路由 | 页面文件（相对 `vue/src/views/modules/`） | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| M1 | FAQ Assistance | Learning | `/faq` | `FaqModule.vue` | 占位，未接入 FAQ |
| M2 | Course Recommendations | Learning | `/courses` | `CourseModule.vue` | 占位，未实现推荐 |
| M3 | Room Booking | Facilities | `/bookings` | `BookingModule.vue` | 占位，未实现预约 |
| M4 | Fault Reporting | Facilities | `/repairs` | `RepairModule.vue` | 占位，未迁移报修业务 |
| M5 | Document Applications | Administration | `/applications` | `ApplicationModule.vue` | 占位，未实现申请审批 |
| M6 | Progress Notifications | Administration | `/notifications` | `NotificationModule.vue` | 占位，未实现历史与通知 |

Redis、统一用户表、共享附件、审批历史、业务通知，以及 Java TF-IDF / 余弦相似度检索均属于后续工作，当前启动不需要 Redis 或外部 AI 服务密钥。

## 2. 项目结构与请求链路

```text
dormitory-manager-coursework/
├── README.md
├── database/                      # 旧业务结构、权限与演示数据 SQL
├── docs/                          # 当前集成说明、设计草案和历史文档
├── springboot/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/example/springboot/
│       │   ├── SpringbootApplication.java
│       │   ├── common/             # Result 等公共类
│       │   ├── config/
│       │   ├── controller/         # 原登录、Session 与宿舍接口
│       │   ├── entity/
│       │   ├── mapper/
│       │   └── service/
│       └── main/resources/application.properties
├── vue/
│   ├── package.json
│   ├── package-lock.json
│   ├── vite.config.js
│   ├── tests/                     # Node 内置测试运行器
│   └── src/
│       ├── config/modules.js      # 六模块的统一配置入口
│       ├── router/                # routes.js 与 Session 路由守卫
│       ├── layout/Layout.vue
│       ├── components/            # Header、Aside、ModulePlaceholder 等
│       ├── utils/                 # request.js、session.js、english.js
│       └── views/modules/         # 六个模块页面
└── report-screenshots/             # 当前门户与移动端验收截图
```

开发环境中的真实请求链路：

```text
浏览器 http://localhost:8080
  → Axios 请求 /api/stu/login
  → Vite 代理删除 /api 前缀
  → Spring Boot http://localhost:9090/stu/login
  → MySQL 的 student 表
  → 服务端 Session + 浏览器 JSESSIONID Cookie
```

后端控制器路径没有统一 `/api` 前缀；`/api` 是浏览器到代理层的前缀。前后端同源访问能沿用 Session Cookie，部署时也应保留这条链路。

## 3. 环境准备

| 组件 | 本项目要求 / 说明 |
| --- | --- |
| Java | `pom.xml` 声明 Java 1.8 编译目标；课程基线为 JDK 8。使用其他 JDK 时需实际运行 Maven 验证，不应把编译目标当成当前终端版本 |
| Maven | 使用 Maven 3.9.x；仓库不包含 Maven Wrapper，需自行安装并配置 PATH |
| MySQL | MySQL 8.0 或兼容的 8.x；SQL 使用 `utf8mb4_0900_ai_ci`，不适用于直接导入 MySQL 5.7 |
| Node.js | 当前 Vite 8.0.14 / plugin-vue 6.0.7 的 engines 要求为 `^20.19.0` 或 `>=22.12.0`；可选用满足条件的 Node 22.x / 24.x |
| npm | 随 Node 安装；有锁文件，首次部署使用 `npm ci` |
| 浏览器 | 支持当前 Vue / Vite 构建产物的现代浏览器 |
| Nginx | 仅服务器静态托管场景需要，本地开发无需安装 |

后端主要依赖为 Spring Boot 2.6.3、MyBatis-Plus 3.5.1、MySQL Connector/J 8.0.33；前端依赖及实际解析版本以 `package.json`、`package-lock.json` 为准。

在终端检查环境：

```powershell
java -version
mvn -version
node --version
npm --version
mysql --version
```

`mvn -version` 显示 Maven 实际使用的 Java。首次安装依赖需要访问 Maven 仓库和 npm registry。Windows 若阻止执行 `npm.ps1`，可直接使用 `npm.cmd ci`、`npm.cmd run dev` 等命令。

## 4. 本地部署：从数据库到登录

下文命令以 **PowerShell** 为主。先将终端切换到仓库根目录，确认可以看到 `database`、`springboot` 和 `vue`。不要在 PowerShell 中直接照抄 Bash 的 `mysql < file.sql` 输入重定向写法。

### 4.1 启动并检查 MySQL

使用本机服务管理器启动已安装的 MySQL；服务名因安装方式不同可能是 `MySQL80`、`MySQL84` 或其他名称。确认主机、端口、用户名和密码后测试：

```powershell
mysql -h 127.0.0.1 -P 3306 -u root -p -e "SELECT VERSION();"
```

`-p` 会提示输入 MySQL 密码，它与后面网页的演示账号密码是两套凭据。示例默认数据库端口为 `3306`，按实际环境修改。

### 4.2 选择数据库路径

**已有可用数据库：** 当前门户改造不要求数据库迁移。保留现有库与账号，直接进入 4.3；不要为了启动新门户重新导入初始化脚本。

**首次部署 / 独立演示：** 建议新建隔离的 `dormitory_en_demo`，避免影响已有 `dormitory`。在仓库根目录依次执行：

```powershell
mysql --abort-source-on-error --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p -e "source database/dormitory_en_demo.sql"
mysql --abort-source-on-error --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p -e "source database/test_data_en.sql"
```

第一条创建新库、旧业务表、权限表和基础账号；第二条补充英文演示数据。**第一条成功后再执行第二条**。数据库已存在时第一条会停止，不会主动删除旧表；遇到错误应检查原因，不要跳过错误继续导入。数据库创建 / DDL 不应视为可以整体事务回滚的操作。

检查导入结果：

```powershell
mysql -h 127.0.0.1 -P 3306 -u root -p -D dormitory_en_demo -e "SHOW TABLES; SELECT username, name FROM student;"
```

SQL 文件的用途与影响：

| 文件 | 目标库 | 用途 / 注意事项 |
| --- | --- | --- |
| `database/dormitory_en_demo.sql` | `dormitory_en_demo` | 隔离英文演示库初始化；要求库名尚不存在 |
| `database/test_data_en.sql` | `dormitory_en_demo` | 刷新指定演示账号及关联数据；包含定向 DELETE，不用于真实业务库 |
| `database/dormitory_mysql8.sql` | `dormitory` | 原宿舍初始化，包含 DROP TABLE，会重建业务表及权限表 |
| `database/test_data.sql` | `dormitory` | 原中文测试数据；会删除并重建指定测试记录 |
| `database/permission_tables.sql` | `dormitory` | DROP / 重建原权限表；当前门户无需为导航单独执行此脚本 |

各脚本内部写有 `USE`，仅在命令行添加 `-D` 不能把它们自动改为另一个数据库。原脚本包含业务数据覆盖操作，保留用于明确需要重建的旧演示环境；不要与隔离演示脚本混用。

### 4.3 配置后端连接

默认配置文件为 `springboot/src/main/resources/application.properties`：端口 `9090`、数据库 `localhost:3306/dormitory`。部署时必须让 URL、用户名和密码匹配同一个实际数据库。不要提交真实数据库凭据。

建议在运行后端的 PowerShell 终端中设置环境变量，以覆盖默认值：

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/dormitory_en_demo?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$dbCredential = Get-Credential -UserName $env:SPRING_DATASOURCE_USERNAME -Message 'Enter the MySQL credentials'
$env:SPRING_DATASOURCE_USERNAME = $dbCredential.UserName
$env:SPRING_DATASOURCE_PASSWORD = $dbCredential.GetNetworkCredential().Password
Remove-Variable dbCredential
```

已有库使用者将 URL 中 `dormitory_en_demo` 换成已有库名；非本机 MySQL 同时修改主机与端口。上面的 URL 适用于本地演示，远程数据库应按部署环境设置 TLS。运行账号只需应用所需的数据权限，建库可单独使用初始化账号。

这些变量只作用于当前终端及其子进程，不会修改配置文件。IDEA 启动时，在 Run Configuration 的 Environment variables 中设置同名变量；其他终端也不会自动继承这里的设置。

### 4.4 启动后端

在已设置上述变量的终端执行：

```powershell
Set-Location springboot
mvn spring-boot:run
```

也可用 IDEA 打开 `springboot/pom.xml`，同步 Maven，运行 `com.example.springboot.SpringbootApplication`。看到应用成功启动并监听 `9090` 后保留终端。

在另一终端检查现有 Session 接口：

```powershell
Invoke-RestMethod http://localhost:9090/main/loadIdentity
```

未登录返回 `code: "-1"` 是正常结果，说明 HTTP 接口可达；这一步不证明数据库登录成功。项目没有配置 Actuator，不能用 `/actuator/health` 判断启动状态。

### 4.5 安装与启动前端

新开终端，从仓库根目录执行：

```powershell
Set-Location vue
npm ci
npm run dev -- --strictPort
```

打开 [登录页](http://localhost:8080/Login)。默认端口 `8080`，Vite 将 `/api` 转发到 `http://localhost:9090`。`--strictPort` 在端口占用时直接报错，便于保持文档地址一致。

需要临时改前端端口时：

```powershell
npm run dev -- --port 8081 --strictPort
```

此时访问 `http://localhost:8081/Login`。如果修改了后端端口，还需同步修改 `vue/vite.config.js` 的 `server.proxy['/api'].target` 并重启 Vite。

### 4.6 演示登录

以下账号仅指 SQL 脚本创建的合成演示数据；复用已有库时，以已有账号为准，不应据此假定密码仍相同。

| 登录页角色 | 内部角色码 | 基础演示账号 | 额外演示账号（导入 `test_data_en.sql` 后） |
| --- | --- | --- | --- |
| Administrator | `admin` | `admin` | — |
| Staff | `dormManager` | `dorm1` | `test_dorm03`、`test_dorm04` |
| Student | `stu` | `stu1`、`stu2` | `test_stu01` 至 `test_stu05` |

脚本中的上述演示账号密码均为 `123456`，仅供本地合成数据演示。选择与账号匹配的角色；Staff 目前仍对应旧 `dorm_manager` 账号体系，并非已经完成用户模型迁移。

成功登录后应看到 `/home` 的六个服务卡片。点开模块出现 `Under development` 是当前预期行为。不要按旧文档查找宿舍、床位、水电费或权限管理菜单。

### 4.7 停止与下次启动

分别在前后端终端按 `Ctrl+C`。需要清除当前 PowerShell 的数据库覆盖配置时：

```powershell
Remove-Item Env:SPRING_DATASOURCE_URL, Env:SPRING_DATASOURCE_USERNAME, Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
```

以后启动已有环境，只需启动 MySQL、重新配置当前终端的数据库变量、启动后端和前端；无需重复建库或导入数据。不要关闭其他项目共享的 MySQL 服务。

## 5. 构建与服务器部署

本节给出单台机器上的部署方式。仓库目前没有 Dockerfile、Compose 配置、Nginx 成品配置或自动部署脚本；下面的服务器目录和配置是需按实际环境落地的示例。

### 5.1 构建与打包

前端在 `vue/` 执行：

```powershell
npm ci
npm test
npm run build
```

静态产物位于 `vue/dist/`。必须由 HTTP 服务器托管，不要直接双击 `index.html`。

后端在 `springboot/` 执行：

```powershell
mvn test
mvn -DskipTests package org.springframework.boot:spring-boot-maven-plugin:2.6.3:repackage
```

当前 POM 未显式绑定 Spring Boot 的 `repackage` 执行，因此不能把普通 `mvn package` 的 JAR 默认视为可直接启动的 Spring Boot 可执行包。上面的命令显式执行重打包，目标文件为：

```text
springboot/target/springboot-0.0.1-SNAPSHOT.jar
```

打包命令中的 `-DskipTests` 避免重复运行前一步测试；不能把跳过测试的打包结果记为测试通过。现有后端仅有一个 Spring 上下文测试，通过它不代表六模块业务或真实数据库交互已验证。

### 5.2 部署文件与进程

示例布局：

```text
/opt/smartcampus/backend/springboot-0.0.1-SNAPSHOT.jar
/opt/smartcampus/web/                    # 放入 dist 内部的 index.html、assets 等
```

在服务器准备 Java、MySQL，按第 4 节初始化新环境或连接已有库，再通过进程管理器注入 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`。使用服务器的部署账号与凭据，不要复制本地真实密码到脚本或仓库。

在后端目录运行：

```bash
java -jar springboot-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=9090
```

该命令默认前台运行，用于先确认启动；持续运行时交由 systemd、Windows 服务或现有进程管理工具托管，配置工作目录、日志与重启策略。MySQL 和后端端口应仅开放给所需主机，浏览器通过前端域名访问。

### 5.3 Nginx 同源代理示例

将以下 `server` 块放入实际 Nginx 的 HTTP 配置作用域，替换域名与前端绝对目录。示例按**站点根路径**部署，不能直接套用为 `/smartcampus/` 子路径部署。

```nginx
server {
    listen 80;
    server_name campus.example.com;

    root /opt/smartcampus/web;
    index index.html;

    location /api/ {
        # 末尾 / 删除匹配到的 /api/，与开发环境保持一致。
        proxy_pass http://127.0.0.1:9090/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /assets/ {
        try_files $uri =404;
    }

    location / {
        # Vue Router 使用 history；直接打开或刷新模块时回退到入口。
        try_files $uri $uri/ /index.html;
    }
}
```

检查 Nginx 配置后再重载：

```bash
nginx -t
nginx -s reload
```

浏览器访问服务器域名的 `/Login`，API 也应走该域名下的 `/api/...`。代理需保留 `Set-Cookie` 与请求 Cookie，才能建立和复用 Session。生产构建没有 Vite 开发代理，必须配置 Nginx 或等效代理；`npm run preview` 仅用于临时预览，不能替代完整部署。

局域网演示时，前端开发服务已监听 `0.0.0.0`；其他设备使用开发机实际 IP 和前端端口访问，Vite 仍可代理本机后端。需确保防火墙允许前端端口，不要在浏览器和接口之间混用 `localhost`、`127.0.0.1` 与不同域名。

### 5.4 当前部署边界与持久数据

当前适合本地课程演示或受控环境验证。对外开放前，需要补全后端认证、角色/记录归属检查、密码哈希、响应与日志中的凭据脱敏、Session 安全和写请求保护；Nginx 配置本身不会完成这些工作。

原 `FileController` 的上传路径为 `System.getProperty("user.dir") + "/src/main/resources/files/"`，依赖进程工作目录；还保留共享静态文件名等旧实现。当前六模块未使用它，后续 M4/M5 附件应先改为配置化目录、文件元数据及归属校验，再投入业务使用。不要把打包后的 classpath 当成持久上传目录。

更新版本前备份 MySQL 数据，以及实际使用的上传目录、运行配置；保留旧 JAR / 静态产物与匹配的数据库版本。示例备份命令在运行 MySQL 客户端的终端执行：

```powershell
mysqldump -h 127.0.0.1 -P 3306 -u root -p --single-transaction --result-file=smartcampus-backup.sql dormitory_en_demo
```

备份文件包含账号与业务数据，应存放在仓库外的受控目录。恢复先在隔离库验证，注意 SQL 中可能包含目标库选择或结构重建操作。不能把重新执行初始化 SQL 当成数据库升级或回滚流程。

## 6. 部署验证与问题排查

### 6.1 启动后的最小验收

1. MySQL 可连接；后端日志无数据源或端口异常。
2. 未登录访问 `/home` 或 `/bookings`，应进入登录页。
3. 选择正确角色完成真实登录，主页出现六张卡片，侧边栏对应三个分组。
4. 逐个打开六模块并直接刷新，应保留模块页面；占位页不应调用不存在的业务接口。
5. 登录状态失效后再次导航 / 刷新，应回到登录页；后端无法连接时应显示连接提示。
6. 点击 `Sign Out`；成功后再次访问受保护路径，应要求登录。
7. 检查桌面与约 390px 手机视口、滚动、按钮焦点和长账号名。
8. 服务器部署还需验证 `/faq` 等深链接刷新，以及 `/api/main/loadIdentity` 确实返回 JSON 而非 `index.html`。

浏览器 Network 应能看到登录请求，以及 `/api/main/loadIdentity`、`/api/main/loadUserInfo` 等 Session 请求。当前没有实时业务轮询；停留在占位页期间的会话过期通常在下一次导航或刷新时发现。

### 6.2 验证命令与含义

| 目录 | 命令 | 能验证什么 |
| --- | --- | --- |
| `vue/` | `npm test` | 现有翻译、数据隔离、Session、路由和页面渲染检查 |
| `vue/` | `npm run build` | 前端生产构建，不验证真实登录与业务 API |
| `springboot/` | `mvn -DskipTests compile` | Java 编译，不运行测试 |
| `springboot/` | `mvn test` | 当前 Spring 上下文测试；不是完整业务集成测试 |

本次文档更新时的验证记录（2026-10-26）：

- README 相对文件链接与代码围栏检查、`git diff --check` 通过。
- `npm test`：20 项通过，0 项失败。
- `npm run build`：成功；仍有依赖纯函数注解和产物体积提示。
- 后端离线执行 `mvn -o -DskipTests package org.springframework.boot:spring-boot-maven-plugin:2.6.3:repackage` 成功；检查 JAR 清单确认入口为 `JarLauncher`，启动类为 `SpringbootApplication`。此次打包跳过后端测试。
- 本次未重新运行 `mvn test`，未执行数据库导入、JAR 启动、Nginx 部署或浏览器端到端验收。历史运行证据见当前集成说明，不能据此宣称六模块业务完成。

### 6.3 常见问题

| 现象 | 检查与处理 |
| --- | --- |
| `mvn`、`node` 或 `mysql` 无法识别 | 检查安装与 PATH，重新打开终端；Maven 检查实际使用的 Java |
| `EBADENGINE` / Vite 无法运行 | 检查 Node 是否满足第 3 节的 engines 要求；不要仅依据“已安装 Node”判断 |
| `npm ci` 报锁文件不一致 | 核对 `package.json` 与锁文件是否来自同一版本；由维护者同步锁文件，不要在部署机直接升级依赖 |
| PowerShell 拒绝运行 `npm.ps1` | 使用 `npm.cmd` 对应命令，无需为此全局放宽执行策略 |
| `Unknown collation: utf8mb4_0900_ai_ci` | 检查是否误用 MySQL 5.7 或不兼容的数据库 |
| 演示库已存在 / 导入中断 | 先检查该库来源与已有数据；保留数据，不直接删库重试 |
| `Access denied` / `Unknown database` | 检查 URL、用户名、密码、授权和目标库；环境变量仅作用于设置它的终端 |
| 连接 MySQL 失败 | 检查服务、主机、端口；不要混淆 MySQL `3306` 与后端 `9090` |
| `8080` / `9090` 被占用 | 检查已有进程，选择空闲端口；后端改端口需同步 Vite / Nginx 代理 |
| 前端能打开但登录网络错误 | 检查后端是否启动、Vite 代理是否指向正确端口、Nginx 是否转发 `/api` |
| API 404 | 检查是否重复加了 `/api`，或生产代理未删除此前缀；六模块建议接口尚不存在 |
| 登录成功后又回登录页 | 检查角色、Cookie、域名一致性、代理及后端重启；浏览器本地缓存不是有效 Session 的证明 |
| 服务器刷新模块返回 404 | 检查 history 路由的 `try_files ... /index.html` 回退 |
| 普通 JAR 提示无主清单属性 | 按 5.1 显式执行 Spring Boot `repackage` |
| 找不到原宿舍功能 | 当前路由已切换为六模块门户；旧页面仅保留在源码中 |
| 模块始终显示开发中 | 属于当前范围；需真实完成对应业务后才能更新状态 |

## 7. 公共开发约定

本节和后面的模块建议是**后续实施建议，尚未实现的接口、表名、状态和分包不是已冻结合同**。先协调公共约定，再逐模块接入。

### 7.1 复用统一入口与登录

- 在现有 Vue 工程和 Spring Boot 应用内开发，优先按业务领域分包，不为每个模块再建一套登录或独立服务。
- 保留模块现有路由；替换对应 `views/modules/*.vue` 的占位内容，复用 `Layout`、账号信息和响应式布局。
- `modules.js` 继续统一驱动首页、侧边栏与路由；业务闭环通过后再同步修改模块状态和页面内容。
- 统一用户标识、角色和数据归属应先约定。当前账号主键为各旧表的 `username`，不能假设已存在统一用户 ID，也不能仅用姓名关联业务记录。
- 所有新增业务 API 在后端校验登录、角色及记录所有者；禁止信任客户端传入的用户身份。

### 7.2 请求、返回与页面状态

实际认证接口（下表是后端路径，浏览器经过代理时前面有 `/api`）：

| 方法 | 后端路径 | 现有用途 |
| --- | --- | --- |
| POST | `/stu/login` | Student 登录 |
| POST | `/dormManager/login` | Staff 登录 |
| POST | `/admin/login` | Administrator 登录 |
| GET | `/main/loadIdentity` | Session 身份 |
| GET | `/main/loadUserInfo` | Session 用户 |
| GET | `/main/signOut` | 退出；当前旧合同仍为 GET |

新增前端调用继续使用 `utils/request.js`，其 `baseURL` 为 `/api`、默认超时为 5 秒且携带 Cookie。示例仅展示未来调用方式，**不是已有预约接口**：

```js
import request from '../utils/request.js'

export const listAvailability = params =>
  request.get('/bookings/availability', { params })
```

不要写成 `/api/bookings/availability` 再叠加 `baseURL`。`vue/src/api/` 尚未建立，应在真实接入时按需创建；后端候选业务前缀为 `/faq`、`/courses`、`/bookings`、`/repairs`、`/applications`、`/notifications`。

现有返回结构是 `{ code, msg, data }`，成功码为字符串 `"0"`，不是数字 `0`。公共错误码、HTTP 状态、分页、日期格式与时区需统一。当前请求封装尚未统一处理所有业务请求的会话失效，新模块不能只依赖导航守卫。

所有业务页面应覆盖加载、空数据、失败重试、未登录、无权限、提交中与成功后的真实结果。英文用于产品文案、错误、通知、FAQ 答案与推荐理由；用户姓名、原始输入和正式来源保持原文。旧后端仍存在中文消息，不能把前端英文外壳视为接口语言改造已完成。

### 7.3 数据、附件和跨模块一致性

- 新增 SQL 使用独立、可审查的迁移文件及合成测试数据，说明目标版本、影响范围和恢复方式；不要覆盖旧初始化脚本来“升级”。
- 附件共用受控存储和元数据，限制格式 / 大小、隔离用户、检查下载权限；不要直接复用旧头像上传作为申请附件服务。
- 业务状态、操作历史和通知之间应保持事务一致性；课程规模可先用同库事务，不必为此立即引入消息队列或微服务。
- 若按后续设计接入 Redis，用于 FAQ / 课程等可重建读缓存，定义更新失效策略；数据库仍负责预约等关键写入的一致性。当前项目没有 Redis 依赖或配置。

## 8. 六个模块的开发建议

### 8.1 M1 — FAQ Assistance

**入口：** `FaqModule.vue`，路由 `/faq`。目标是根据校园问题返回可核对的答案与来源，无法匹配时给出联系工作人员的指引。

- **优先实现：** 建立小规模、来源明确的 FAQ 数据集，包含问题、答案、分类和来源；先完成输入问题 → 检索 → 展示答案 / 来源 → 无匹配提示的闭环。
- **后端建议：** 在 Java Service 内实现 TF-IDF 与余弦相似度；集中管理分词、向量化与阈值。工作人员更新知识条目后重建索引或使缓存失效。阈值通过独立问题集确定，不写死“所有问题均有答案”。
- **复用建议：** 可参考同工作区 `springboot-ai-qa-system` 的检索逻辑；接入前核对其依赖、用户模型和数据来源。本仓库目前没有引用该项目，不能直接复制它的控制器、路由、登录或 token 体系。
- **页面建议：** 问题输入、提问历史、结果卡片、来源链接、无匹配提示；不把检索分数包装成未经校准的置信概率。
- **验收重点：** 用 30 个独立问题覆盖同义表达、无匹配与无效输入；核对来源、阈值边界、知识更新后的结果及英文提示，保留失败样本。

### 8.2 M2 — Course Recommendations

**入口：** `CourseModule.vue`，路由 `/courses`。目标是基于兴趣、已修课程与先修要求，返回最多三项课程及可解释理由。

- **优先实现：** 先定义课程目录、先修关系、学生已修课程和兴趣输入；先过滤已修课程及不满足先修条件的课程，再排序。
- **后端建议：** Java TF-IDF / 余弦相似度用于相关性排序，资格过滤单独实现并先执行；同分结果采用稳定顺序，合格课程不足三项时按实际数量返回。
- **数据建议：** 课程代码唯一；先修条件要明确“全部满足 / 任一满足”等语义。学生身份与已修记录来自受控数据，不能让请求参数冒充其他学生。
- **页面建议：** 展示推荐课程、先修条件与具体推荐理由，允许明确展示“没有符合条件的课程”；不要填充无资格课程凑足三项。
- **验收重点：** 20 个经人工审阅的学生画像，覆盖先修不足、已修完、空兴趣、候选不足与重复输入；分别验证资格过滤、排序和理由是否符合数据。

### 8.3 M3 — Room Booking

**入口：** `BookingModule.vue`，路由 `/bookings`。目标是查询教学空间的固定时段、预约、取消，并拒绝冲突。

- **优先实现：** 教室 / 日期 / 固定时段查询 → 创建预约 → 我的预约 → 取消并释放时段。先统一时区、可预约日期和时段边界。
- **后端建议：** 让 MySQL 事务、约束或加锁操作保证同一教室 / 日期 / 时段最多一个有效占用；不能仅用“先查空闲再插入”，前端禁用按钮也不能替代并发控制。
- **数据建议：** 明确历史预约与当前占用如何分开或关联。若保留取消记录，简单的三字段永久唯一键可能阻止重新预约，应先设计有效占用及释放规则。
- **复用建议：** 可参考旧 `DormBuild` / `DormRoom` 的地点查询结构，但床位列、入住人数、宿管楼栋绑定不等于教室预约模型，不应直接套用。
- **验收重点：** 两个独立会话并发预约同一时段仅一个成功；覆盖重复提交、取消后重约、越权取消、过期日期和边界时段，并用真实 MySQL 验证。

### 8.4 M4 — Fault Reporting

**入口：** `RepairModule.vue`，路由 `/repairs`。目标是学生提单、工作人员分派与处理、学生跟踪至关闭。

- **优先实现：** 创建工单、我的工单、工作人员待办、分派、处理更新和关闭；先确定状态、允许的转换及各角色操作范围。
- **复用建议：** 原 `RepairController`、`RepairService`、`Repair` 和旧报修页面可作为业务字段 / 交互参考；现有 `/repair` 不等于新的 `/repairs`，不能把旧接口直接视为已完成校园工单。
- **后端建议：** 扩展地点类型、提交人、负责人、时间与处理说明；提交人从 Session 获取，不能依赖 `repairer` 姓名判断归属。每次状态转换记录操作者与处理历史。
- **页面建议：** 学生看到本人记录，工作人员在授权范围内分派、更新；处理进度和关闭原因来自真实后端，非法转换明确返回英文错误。
- **验收重点：** 越权查看 / 修改、跳过必要步骤、重复关闭、缺少必填处理意见及并发更新。状态与历史同时成功或同时失败，不能只修改状态标签。

### 8.5 M5 — Document Applications

**入口：** `ApplicationModule.vue`，路由 `/applications`。目标是提交材料、校验附件、工作人员退回 / 批准 / 拒绝。

- **优先实现：** 先选一个明确的申请类型，约定字段、材料清单与审批流程；完成提交 → 审核 → 查看结果，再扩展更多类型。
- **后端建议：** 统一申请编号、申请人、类型、当前状态与处理意见；例如 Submitted → Returned / Approved / Rejected 可作为讨论起点，重新提交和终态规则需团队确认后实施。
- **附件建议：** 先完成 7.3 的共享附件能力，校验类型、大小与必填材料；上传成功不等于申请提交成功，需处理未绑定文件与失败清理。下载也必须校验归属 / 审批权限。
- **协作建议：** M5 定义状态变化与处理历史，与 M6 共用申请 ID、原因、操作者和时间；不要各存一套互相覆盖的申请状态。
- **验收重点：** 缺材料、非法文件、越权审批、重复提交、退回后修订、终态重复审批，以及事务失败时是否出现“已批准但无历史 / 通知”。

### 8.6 M6 — Progress Notifications

**入口：** `NotificationModule.vue`，路由 `/notifications`。目标是查看申请进度、退回原因、处理历史及本人的站内通知。

- **优先实现：** 与 M5 同步设计申请详情时间线、通知列表、未读数与标记已读；先用普通接口查询，按实际需要增加轻量轮询。
- **后端建议：** 申请 / 工单模块拥有业务状态；M6 读取真实状态和历史，维护收件人、通知类型、业务引用、内容、创建时间和已读时间。不要允许通知接口直接改审批状态。
- **一致性建议：** 初期将状态变化、历史和通知写入同一数据库事务；重复处理不能产生重复通知，标记已读采用可重复执行的更新。
- **页面建议：** 通知跳转到用户有权查看的业务详情，展示真实处理原因；时间线采用稳定排序。业务列表可以分页，避免一次加载全部历史。
- **验收重点：** 用户不能读取他人的通知或标记他人通知已读；未读数与列表一致；失败回滚、重复事件、重新登录、过期 Session 和业务记录不可访问时均有正确表现。

## 9. 建议实施顺序与完成标准

下面是开发排序建议，不表示相关设计已批准或业务已实现：

1. **公共基线：** 冻结用户标识、角色 / 数据归属、错误、时间与接口规则，补全后端保护，建立可恢复的迁移与测试方式。
2. **M1 首个纵向闭环：** 在统一登录下真实接入 FAQ，让问题输入、后端检索、数据库、来源和失败提示形成可复用示范。
3. **M2 / M3 / M4 分模块推进：** 共享身份、返回和页面约定；分别验证资格过滤、预约并发、工单状态流转，避免仅完成页面。
4. **M5 与 M6 联合设计：** 先约定申请 ID、历史和通知事件，再实现附件、审批与进度，避免重复建模。
5. **整体联调：** 六条学生 / 工作人员业务链、负例、英文界面、移动端、独立问题 / 画像评价和新环境部署复现。

每个模块完成后至少提交：真实页面与 API、必要 SQL / 合成数据、角色与归属校验、正常和异常用例、实际运行证据、对应文档。仅修改 `modules.js` 的状态、增加 Mock 数据或通过前端构建，都不等于业务完成。

旧学生 / 宿管 / 公告 / 宿舍业务暂按参考代码保留；账号迁移、旧表删除、权限体系切换须有单独的数据影响与恢复方案。维护 README 时继续区分“现状”“建议”和“验证证据”。

## 10. 相关文档

| 文档 | 适用范围 |
| --- | --- |
| [SmartCampus 集成说明](docs/SMARTCAMPUS_INTEGRATION.md) | 当前门户、Session 行为、模块接入与历史验证记录；理解当前版本的首选补充资料 |
| [公共身份、权限与 API 设计](docs/SmartCampus_Phase1_Batch1A_设计.md) | 设计草案；除明确确认的条目外，不应认定为已实现 / 已冻结 |
| [框架改造实施计划](docs/SmartCampus_框架改造实施计划_重构版.md) | 分阶段目标、模块依赖与最终验收计划 |
| [课程 Proposal](docs/SmartCampus-proposal.pdf) | 课程项目目标及六模块范围 |
| [旧宿舍英文演示说明](docs/ENGLISH_DEMO.md) | 隔离演示 SQL 的背景；旧页面操作流程不适用于当前门户导航 |
| [旧技术文档](docs/技术文档.md) | 原宿舍业务与接口参考 |
| [旧从零启动说明](docs/从0启动项目.md) | 历史环境说明；当前部署优先使用本 README |
| [旧测试账号与场景](database/test_cases.md) | 原宿舍数据场景；不代表六模块已可用 |

当前门户截图：

![SmartCampus desktop portal](report-screenshots/smartcampus-home-desktop.png)

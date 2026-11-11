# M2 后端代码手册

本手册合并原 docs/ 下的 14 份逐类说明；每个类仍有独立章节。先看 [后端调用链](README.md)，再按问题跳转。运行命令统一在 [运行手册](../../../../../../../../docs/m2/README.md)。

## 章节导航

- [AiCourseRecommender](#aicourserecommender)
- [Course](#course)
- [CourseAdvice](#courseadvice)
- [CourseConfiguration](#courseconfiguration)
- [CourseController](#coursecontroller)
- [CourseException](#courseexception)
- [CourseHistoryWriter](#coursehistorywriter)
- [CourseMapper](#coursemapper)
- [CourseService](#courseservice)
- [CourseSessionInterceptor](#coursesessioninterceptor)
- [DeepSeekClient](#deepseekclient)
- [Recommendation](#recommendation)
- [RecommendationOutcome](#recommendationoutcome)
- [Recommender](#recommender)

<a id="aicourserecommender"></a>

## AiCourseRecommender.java — DeepSeek 直接选择合格课程

[源代码](AiCourseRecommender.java) · [后端入口](README.md)

### 目标与调用者

`CourseService.assisted()` 传入全部启用课程、服务器保存的已修课程、学生兴趣与目标、最多推荐数量。本类让 Java 先检查资格，再把全部合格课程摘要交给 DeepSeek 直接选择，不再用 TF-IDF 截取候选。它只返回 `RecommendationOutcome`；画像和历史由 Service / CourseHistoryWriter 保存。

### recommend() 的执行顺序

1. `Recommender.eligible()` 排除已修、停用、先修未满足和 `review_required` 课程。没剩下合格课程时返回 `ELIGIBILITY_FILTER / NO_ELIGIBLE_COURSES`；不调用模型，也不声称模型已执行。
2. 检查模型开关和密钥。如果未配置，执行原 TF-IDF 算法并返回 `FALLBACK / NOT_CONFIGURED`。
3. 构造完整合格课程白名单：每门包含编号、标题、主题、最多 900 字符的描述；加上原始查询和最大结果数。不传学生账号、成绩单或历史。
4. 使用 ObjectMapper 估算 JSON 字符数；超过 100 万字符时明确返回 `FALLBACK / CATALOG_TOO_LARGE`。这避免悄悄截断课程列表，但字符限制不等于供应商的 token 窗口。
5. `client.acquire()` 获取并发和调用额度；不成功时返回 `FALLBACK / LIMIT_REACHED`。成功后 `client.complete()` 向 DeepSeek 发一次请求，要求根据中文或英文原始兴趣和排除偏好选择最多三门，并提供英文解释。
6. 模型返回 `recommendations` 数组。逐条检查编号在白名单、未重复、数量没有超限、解释为非空且不超过 1200 字符，再按本次读取的已修集合复核资格。
7. 用 `byCode` 中的真实 Course 填回结果；DeepSeek 直接选择没有 TF-IDF 分数，因此 `score` 为 `null`、`matchingTerms` 为空。返回 `LLM_ASSISTED / OK`。
8. 调用或校验异常时才计算原算法，标记 `FALLBACK / PROVIDER_UNAVAILABLE_OR_INVALID`；`finally` 归还并发名额。原算法使用原始查询，中文和否定理解可能不佳。

### 为什么要保留规则与白名单

大模型可以根据语义选择，但可能虚构课程编号或先修条件。数据库和 Java 仍是课程与资格的权威来源；模型只能返回白名单里的编号。无匹配时允许模型返回空列表，不要求凑满三门。

`requiredText()` 用于校验模型输出而非前端请求；JSON 语法、重复字段、响应截断和 HTTP 超时由 `DeepSeekClient` 处理。`ObjectMapper` 这里用于估算请求长度，不读取密钥。

### 按方法阅读代码（可读性重构）

`recommend()` 保留执行顺序、回退分支以及 `finally` 释放并发名额的逻辑。三个辅助方法分别展开细节：

- `findEligibleCourses()`：收集全部合格课程，不做相关性筛选。
- `buildPayload()`：构造发送给模型的查询、推荐数量和公开课程摘要。
- `validateRecommendations()`：核对模型返回的课程，并从本地白名单取回真实 Course；不相信模型自行编造的课程资料。

因此可以先读主流程，再进入某个方法了解细节。提示词、请求字段、长度限制、回退原因以及返回结构均保持原样。

### 真实能力边界

- 解释内容、语义相关性、中文否定与偏好能否准确遵守，需要独立画像和人工评估；当前 Java 只验证编号、数量、资格和解释结构，不验证每句话的事实。
- 学生输入可能含个人信息，应由学生避免主动输入敏感资料；服务端不会额外把姓名、账号或成绩单拼进模型请求。
- 100 万字符守卫不会保证供应商接受该上下文；一旦拒绝仍明确回退，不 silently 限制课程数量。
- 历史快照不参与训练或个性化学习。既有历史记录格式和原算法接口保持兼容。

检查入口：`DirectDeepSeekRecommendationTest` 覆盖 40 门合格课、超过第 30 门且没有词语重合的课程、无合格课和目录过大；`DeepSeekRecommendationTest` 覆盖无效编号、重复、超量、超时、401、严格 JSON、回退与模型返回空结果。这些测试使用模拟供应商，并不能证明真实模型质量。


<a id="course"></a>

## Course.java — 课程资料为什么单独建一个类

[源代码](Course.java) · [后端入口](README.md)

### 它的意图与位置

`Course` 表示“一门课程的公开资料”，让 SQL、推荐器和页面使用同一套字段。`CourseMapper.catalog()` 读取基础字段，`CourseService.catalog()` 再把先修关系填入对象。它不表示成绩、学籍、实际选课名额，也不负责资格判断。

### 字段怎样理解

| 字段 | 来源及用途 |
| --- | --- |
| `code` | 项目内课程编号；用于去重、关联、已修排除和结果校验 |
| `sourceCourseCode` | 原站点编号，保留归一化前的信息 |
| `title`、`description`、`topics` | 课程描述；原算法用于文本向量，DeepSeek 模式作为合格课程摘要 |
| `sourceUrl`、`syllabusUrl` | 用户核对课程及先修证据的链接 |
| `sourceSha256` | 原始元数据的文件校验值，不是密码 |
| `licenseUrl`、`instructorsJson` | 使用许可和教师署名；后者目前是 JSON 字符串 |
| `sourceYear` | 来源课程的历史年份，不是本学期开放年份 |
| `active` | 是否进入当前启用目录 |
| `prerequisiteStatus` | `structured`、`no_formal_course` 或 `review_required` |
| `prerequisiteEvidence` | 规则对应的原文或审核说明 |
| `prerequisiteGroups` | Java 组装出的课程编号列表，不是数据库里的一整段 JSON |

例如 `[["6.006"], ["6.042J", "18.062J"]]` 表示 `6.006 AND (6.042J OR 18.062J)`。`review_required` 即使列表为空也不能当作“没有先修”。实际判定见 `Recommender.eligible()`。

### 为什么没有手写 getter/setter

`@Data` 是 Lombok 注解，编译时生成访问方法、`equals`、`hashCode` 等。JSON 序列化使用这些属性。改名或改变字段类型时，需同步检查 Mapper 别名、前端和历史快照兼容性；课程来源不明确时应更新数据审核流程，而非在这个类中自动猜测。

检查入口：`RecommenderTest` 验证资格规则，`MySqlCourseTest` 验证真实目录的组装。


<a id="courseadvice"></a>

## CourseAdvice.java — 前端如何稳定地理解失败

[源代码](CourseAdvice.java) · [后端入口](README.md)

### 为什么需要统一错误出口

输入、会话、JSON 解析和数据库都可能抛异常。前端需要稳定的 HTTP 状态和 `{code,msg,data}`，而不是时而 HTML、时而堆栈。本类用 `@RestControllerAdvice(basePackageClasses=CourseController.class)` 限定对 M2 控制器包的处理范围。

### 三条分支

| 方法 | 捕获的异常 | 返回意图 |
| --- | --- | --- |
| `expected()` | `CourseException` | 保留已确定的 HTTP 状态和用户提示，code 为状态的字符串 |
| `malformed()` | `HttpMessageNotReadableException` | 请求连合法 JSON 都不是时返回 400 |
| `failure()` | 其他 Exception | 服务端记录异常，页面只拿通用 500 提示 |

Controller 的 `validate()` 负责合法 JSON 中字段的业务含义；`malformed()` 负责在 Controller 获得 JsonNode 前的解析失败。这两层不能互相替代。

`failure()` 目前会记录异常堆栈。它没有主动记录请求画像，但不能因此假设所有上游异常内容都天然不含敏感信息；维护日志时仍不要加入 key、供应商原文或学生资料。返回给前端的内容则保持通用。

本类不会把大模型失败变成学生会话失败：辅助编排器应已经捕获并返回回退结果。只有数据库保存等仍未处理的错误继续到此处；持久化事务失败时不能返回“推荐已保存”。

检查入口：`CourseApiTest` 验证错误状态与结构；真实事务回滚见 `MySqlCourseTest`。改变错误码类型需同步前端，成功码依旧是字符串 `"0"`。


<a id="courseconfiguration"></a>

## CourseConfiguration.java — 让 M2 被应用实际使用

[源代码](CourseConfiguration.java) · [后端入口](README.md)

### 为什么需要配置类

有 Mapper 接口和拦截器源码，不代表 Spring 自动按本模块需要使用它们。这个类把两件事接进当前框架：

1. `@Configuration` 声明这是应用启动配置。
2. `@MapperScan("com.example.springboot.m2.mapper")` 扫描本模块 SQL 接口并生成代理。
3. `addInterceptors()` 注册 `CourseSessionInterceptor`，路径为 `/courses/**`。

它由 Spring 在启动时使用，没有前端请求参数或业务返回值，不负责登录、读课程或创建数据库。

### 为什么用统一路径匹配

课程目录、个人画像、历史、原算法和辅助模式共享这个前缀。保护整个前缀可以让后来新增的课程子路径也经过学生和 CSRF 检查。前端菜单可见不等于后端已经授权，不能因为有导航守卫就删除此注册。

若修改业务前缀，应同步前端 API、控制器映射、拦截器范围及测试。若调整 Mapper 包，应同步扫描路径，避免启动能成功但请求时找不到代理 Bean。

检查入口：实际应用启动与 HTTP 测试可验证注册；`CourseApiTest` 的 standalone 测试是手动加入拦截器的，所以它单独通过不能证明本配置文件仍被正确扫描。


<a id="coursecontroller"></a>

## CourseController.java — HTTP 请求与业务逻辑的分界

[源代码](CourseController.java) · [后端入口](README.md)

### 它为什么存在

前端发来的是 HTTP、JSON 和 Cookie，推荐器需要的是可信用户、字符串和整数。Controller 负责完成这层转换，把业务交给 `CourseService`，因此这里没有 SQL 或供应商请求。

在到达本类前，`CourseSessionInterceptor` 已验证身份，并写入 `m2.username`、`m2.csrf`。`username()` 只读取这两个内部属性之一，不从 JSON 接受用户账号。

### 各接口分别做什么

| 接口 | 方法 | 返回的 `data` |
| --- | --- | --- |
| `GET /courses/catalog` | `catalog()` | 启用课程及先修条件列表 |
| `GET /courses/me` | `me()` | 当前学生、已修课程、画像、提示和 CSRF token |
| `GET /courses/history` | `history()` | 当前学生最近历史 |
| `POST /courses/recommendations` | `recommend()` | 原算法推荐数组 |
| `POST /courses/recommendations/assisted` | `assisted()` | 带实际模式的 `RecommendationOutcome` |

统一成功响应为 `{code:"0", msg:"Success", data:...}`。浏览器使用的 `/api` 来自代理，不是这里的映射前缀；避免把路径重复写成 `/api/api/...`。

### 参数校验为什么两个接口共用

`validate()` 只允许 `interests`、`goals`、`topK`，生成内部 `Inputs`。兴趣必填、去首尾空白后 1–500 字符；目标可缺省、最多 300 字符；数量缺省 3，必须为整数 1–3。未知字段（如 `completed`、`studentId`）直接返回 400，不能误导调用者认为成绩写入已被支持。

```json
{"interests":"python programming","goals":"data analysis","topK":3}
```

写请求还需要有效 Session 和 `X-M2-CSRF`。JSON 语法本身错误由 Spring / `CourseAdvice` 处理；本类校验的是已经解析出的 JSON 的业务形状。

构造器里的 `ObjectMapper unused` 目前未参与业务，保留是为了兼容既有构造调用和测试；若移除，要同步调整测试而不是误以为这里在直接解析供应商回复。

检查入口：`CourseApiTest` 包含身份、CSRF、伪造成绩、字段类型、数量边界和两种入口测试。扩充字段需同步两个入口、前端和文档。


<a id="courseexception"></a>

## CourseException.java — 预期错误怎样携带 HTTP 状态

[源代码](CourseException.java) · [后端入口](README.md)

### 为什么不用任意 RuntimeException

“兴趣为空”和“数据库突然断开”不是同一种错误。前者应是明确的 400，后者是服务端 500。本类在 RuntimeException 的消息之外保存一个 `status`，让 `CourseAdvice.expected()` 可以保留预期状态。

Controller 用它报告字段、类型或长度错误；Session 拦截器用它报告 401 / 403。构造器只保存状态和消息，`getStatus()` 给 Advice 读取；它本身不会写响应，也不会回滚数据库或产生用户界面。

例子：`throw new CourseException(400, "topK must be an integer between 1 and 3.")` 最终由 Advice 变成 HTTP 400 和统一错误 body。产品提示保持英文，异常消息不要包含密钥、SQL 或学生私人资料。

供应商 API 的 401 不应该用本类直接映射成学生 401；模型调用异常由辅助编排器回退，否则会把供应商密钥错误误当成学生需要重新登录。

检查入口：`CourseApiTest` 通过状态和 JSON body 验证此异常到响应的映射；无需为简单的 getter 重复写镜像测试。


<a id="coursehistorywriter"></a>

## CourseHistoryWriter.java — 为什么要独立保存画像与历史

[源代码](CourseHistoryWriter.java) · [后端入口](README.md)

### 它解决的具体问题

一次推荐结束后有两次写入：更新学生兴趣画像、追加推荐历史。如果第一次成功而第二次失败，页面会看到已更新的画像却没有对应记录。因此 `save()` 用 `@Transactional(rollbackFor=Exception.class)` 把两步绑在一起。

调用者是 `CourseService`；输入为可信 `username`、本次兴趣、目标和已经计算完成的结果对象。它调用 `mapper.saveProfile()`，将结果序列化，再调用 `mapper.addHistory()`。任何步骤失败都把异常继续抛出，事务整体回滚，不生成“假成功”。

### 为什么不写在 Controller 或整个推荐事务里

模型调用可能很慢，不应使数据库写事务覆盖网络等待；当前先计算、后保存。独立 Spring Bean 还能确保 Service 的外部调用经过 Spring 事务代理。若搬回同类内部自调用，不能想当然认为注解仍生效。

`result` 类型为 `Object` 是为了兼容两种已存在的持久化内容：原接口传 `List<Recommendation>`，辅助接口传 `RecommendationOutcome`。对应兼容读取位于 `CourseService.history()`。

本类不决定排序、资格和用户权限，也没有自动重试。如果要增加其他写入，应明确哪些数据必须原子提交；如果前端超时后重发，需要另行考虑幂等，而不能认为事务本身能阻止重复请求。

检查入口：`MySqlCourseTest.failedHistoryInsertRollsBackProfileChange` 注入历史失败验证画像回滚；另一个辅助回退测试验证只保存一次、新旧记录可读取。只有连接真实隔离 MySQL 的测试才能验证实际事务行为。


<a id="coursemapper"></a>

## CourseMapper.java — 每个 SQL 方法为什么在这里

[源代码](mapper/CourseMapper.java) · [后端入口](README.md)

### 为什么只有接口没有实现类

`CourseConfiguration` 的 `@MapperScan` 让 MyBatis 为接口生成运行时代理，按 `@Select` / `@Insert` 注解执行 SQL。成员无需再手写一个 `CourseMapperImpl`。本层不判断身份、不算推荐、不调用模型。

### 方法与表的对应关系

| 方法 | 表 | 返回或影响 |
| --- | --- | --- |
| `catalog()` | `m2_course` | 启用课程，按 code 排序；SQL 别名对应 Course 的 Java 属性 |
| `prerequisites()` | `m2_prerequisite` | `courseCode`、`groupNo`、`prerequisiteCode` 扁平行 |
| `completed(username)` | `m2_completed` | 一个学生的已修编号集合 |
| `profile(username)` | `m2_profile` | 当前兴趣与学习目标 Map；没有记录时可为空 |
| `saveProfile(username,interests,goals)` | `m2_profile` | 首次插入，已有则更新；返回影响行数 |
| `addHistory(username,query,json)` | `m2_history` | 追加结果快照；返回影响行数 |
| `history(username)` | `m2_history` | 当前学生最新二十条，按 id 倒序 |

例如 `prerequisites()` 两行同属一组，表示这两门任选其一；它只是读行，不会直接计算逻辑。`CourseService.catalog()` 还原分组，`Recommender.eligible()` 再判断学生是否满足。

### SQL 与边界

`#{username}` 等是 MyBatis 参数绑定，不应改为拼接来自请求的 SQL。用户名仍必须由 Controller / Session 这条链路提供；参数绑定防注入并不自动防止查另一个人的数据。

写画像与写历史必须经 `CourseHistoryWriter` 的同一事务调用。单独调用两个 Mapper 方法不提供跨两次调用的原子性。

历史里的 `resultsJson` 在查询后仍是字符串，Service 才解析旧数组或新信封。JSON 中也保存当时的课程信息，所以改字段需考虑已存在记录，不能只改 SQL 别名。

DDL 在项目根 `database/m2-schema.sql`，目录数据来自 `m2-catalog.sql`。仅用增量、可审查的导入；不要把原宿舍的初始化 SQL 当作升级脚本运行。

检查入口：`MySqlCourseTest` 验证真实 SQL、目录、归属和事务；模拟 Mapper 只能证明调用关系，不能证明 MySQL SQL 方言或约束正确。


<a id="courseservice"></a>

## CourseService.java — 为什么需要一个业务编排层

[源代码](CourseService.java) · [后端入口](README.md)

### 它负责连接什么

Controller 不应知道先修表如何分组，推荐器不应知道历史如何落库。Service 将这些步骤组织成完整用例，使用四个协作者：`CourseMapper` 读资料、`Recommender` 计算原算法、`AiCourseRecommender` 可选辅助、`CourseHistoryWriter` 保存。

### 每个方法的意图

| 方法 | 输入与输出 | 关键原因 |
| --- | --- | --- |
| `catalog()` | 启用课程和先修表 → 完整 Course 列表 | 把扁平关系重建成外层 AND / 内层 OR |
| `me(username)` | 可信账号 → 已修记录和画像 | 已修记录由数据库管理，不接受本次表单冒充 |
| `recommend(...)` | 账号、兴趣、目标、数量 → 推荐数组 | 使用原算法，完成后保存一次 |
| `assisted(...)` | 同上 → 模式信封 | 模型成功或回退后，都只在这里保存一次 |
| `history(username)` | 账号 → 兼容处理后的历史列表 | 同时支持旧数组和新信封，避免升级后旧记录打不开 |

`catalog()` 用课程编号聚合，再用 `SortedMap<Integer,...>` 保留组号顺序。它只还原关系，不把“同组 OR”变成“必须全修”。没有先修表记录的课程仍需看 `prerequisiteStatus`，不是自动通过。

### 为什么这里没有大事务

辅助推荐可能等待网络。若把整个方法标成数据库写事务，会让事务跨度覆盖网络等待。当前先计算、后调用独立 Spring Bean `CourseHistoryWriter.save()`，只将画像 upsert 和历史 insert 放在短事务里。真正的资格检查使用本次读取的记录；当前没有实现跨整个模型等待周期的成绩版本锁。

新历史存 `RecommendationOutcome`，旧历史存数组。`history()` 识别 JSON 类型并统一给出 `results`；旧历史模式标为 `BASELINE`。它展示的是当时结果快照，不会用今天的目录重新排序，也没有利用历史训练模型。

检查入口：`MySqlCourseTest` 覆盖目录、不同学生隔离、新旧历史及失败回滚。改历史格式时先考虑已有数据库记录，改资格一致性要求则需单独设计版本或重查。


<a id="coursesessioninterceptor"></a>

## CourseSessionInterceptor.java — 为什么身份检查要在 Controller 前

[源代码](CourseSessionInterceptor.java) · [后端入口](README.md)

### 意图

所有课程接口会访问学生相关数据。页面或请求 JSON 可以被伪造，因此必须在后端沿用现有 Session 判断当前用户。统一拦截比在每个 Controller 方法里复制判断更容易避免漏保护。

### preHandle() 的顺序

1. 响应设置 `Cache-Control: no-store`，减少画像和历史被浏览器缓存。
2. 用 `getSession(false)` 获取现有 Session；不为匿名用户创建一个登录 Session。没有身份返回 401。
3. `Identity` 必须是 `stu`；其他已登录角色返回 403。
4. `User` 必须是有效 Student 且 username 非空；否则返回 401。
5. 通过公共 `SessionSecurity.token(session)` 获取绑定当前身份的令牌。
6. 非 GET 请求必须带 `X-M2-CSRF`，使用 `MessageDigest.isEqual()` 比较；缺失或不匹配返回 403。
7. 写入 request 内部属性 `m2.username`、`m2.csrf` 并返回 true，允许 Controller 执行。

这些属性不是浏览器可以直接作为查询参数覆盖的值。`CourseController.me()` 把令牌给页面，页面之后带 Cookie 和 header 提交写请求。

### 本类不负责什么

登录建立、会话轮换和退出由团队公共认证逻辑及 `SessionSecurity` 管理；课程资格由 `Recommender` 管理；数据库历史按用户名过滤由 Mapper 管理。CSRF 也不能替代角色和记录归属检查。

检查入口：`CourseApiTest` 覆盖匿名、角色、会话令牌、伪造成绩；`AccountBoundaryTest` / HTTP 测试进一步覆盖跨会话、退出与登录切换。更改 Session 键名、角色值或 request 属性时必须同步调用链。


<a id="deepseekclient"></a>

## DeepSeekClient.java — 为什么把模型通信单独封装

[源代码](DeepSeekClient.java) · [后端入口](README.md)

### 职责与调用者

`AiCourseRecommender` 知道课程业务，本类只知道怎样安全、有限地调用供应商。这样 HTTP 超时、配置和 JSON 规则可以集中测试，不必分散在 Controller 或 SQL 事务中。

调用约定为：`configured()` → `acquire()` → 一次 `complete()` → `release()`。无合格课程时不调用模型。`complete()` 自身不重新检查开关或申请名额；今后其他代码复用它时不能跳过约定。`model()` 返回本进程配置的名称，`close()` 在应用销毁时关闭本组件线程池。

### 构造时读取什么

| 配置 | 当前默认值与意图 |
| --- | --- |
| `M2_AI_ENABLED` | `false`，未显式启用时不发真实请求 |
| `DEEPSEEK_API_KEY` | 空，只放后端环境，不放 Vue 或仓库 |
| `DEEPSEEK_MODEL` | `deepseek-flash`，部署时应核对账号可用模型 |
| `M2_AI_DAILY_REQUEST_LIMIT` | 100；数字无效时设 0，范围限制到 0–1000 |

生产构造器创建有超时的 `RestTemplate`；包内构造器允许测试注入模拟 HTTP。复制 `ObjectMapper` 后启用严格检查，避免改变其他模块的 JSON 规则；重复字段和首个对象后额外 token 都拒绝。

### 一次 complete() 具体做什么

1. 创建供应商消息：system 为任务指令与数据边界，user 为传入对象的 JSON。
2. 固定官方 HTTPS 地址并通过 Authorization header 发密钥，降低误把密钥转发给任意地址的风险。
3. 非流式、JSON object 输出、最多 1200 输出 tokens；当前代码使用 temperature 0 和关闭 thinking 的参数，实际兼容性仍需真实账号验证。
4. 在线程池执行请求，最多等待 6500 毫秒，超出时尝试取消等待中的任务。底层连接超时 1000 毫秒、读超时 5000 毫秒。
5. 校验原始字符串非空且不超过 100000 字符；读取外层 `choices[0]`，要求 `finish_reason == "stop"`，再解析内部 `message.content` 为 JSON 对象。

本类只保证对象语法，不能保证推荐编号或理由真实；这些业务校验在 `AiCourseRecommender`。异常向上抛，由它选择回退，而不是本类去写数据库或返回学生登录错误。

### 两种限制为何同时存在

`slots` 限制最多两个辅助推荐并发，`used` 对获准的推荐尝试计数，当前每次成功尝试最多一次 HTTP 调用。计数只在当前进程内有效，UTC 换日和重启会重置，不等于账户金额限制。

HTTP 工作者同样最多两个且没有无界队列，避免慢请求无限积累。Future 取消不代表供应商一定停止执行或计费；这里也没有自动重试。需要跨进程预算或更复杂重试时，应另行设计而不是只调大线程数。

检查入口：`DeepSeekRecommendationTest` 通过 MockRestServiceServer 验证请求合同、严格 JSON、供应商失败和超时分支。不要在单元测试里写真实密钥。


<a id="recommendation"></a>

## Recommendation.java — 一条推荐需要返回什么

[源代码](Recommendation.java) · [后端入口](README.md)

### 为什么需要它

只有课程列表不能说明“为什么推荐”。本类把 `course`、`score`、`matchingTerms` 和 `explanation` 放在一起，供页面展示和数据库保存结果快照。原算法与辅助模式复用同一个条目结构。

### 谁创建、谁读取

- `Recommender` 创建本地排序结果，理由来自实际匹配词模板。
- `AiCourseRecommender` 把所有合格课程交给模型选择，再用编号取回真实课程并附上模型生成的理由；它不会相信模型自造的完整 Course 对象。
- `CourseController` 返回结果；`CourseHistoryWriter` 将其序列化保存；前端读取字段展示。

原算法的 `score` 是 TF-IDF 文本向量的余弦相似度；DeepSeek 直接推荐没有 TF-IDF 分数，`score` 为 `null`、`matchingTerms` 为空列表。模型不会给出学生成绩或学习成功率。历史中旧版辅助结果可能仍有之前保存的数值。

`@AllArgsConstructor` 生成四参数构造器，参数顺序就是字段顺序。增加字段或重排字段时要检查所有创建位置、前端和历史读取；不要只改 DTO 而漏掉 API 合同。

检查入口：`RecommenderTest` 看原算法的分数和顺序；`DeepSeekRecommendationTest` 看如何从可信候选构造条目。


<a id="recommendationoutcome"></a>

## RecommendationOutcome.java — 怎样区分模型成功与原算法回退

[源代码](RecommendationOutcome.java) · [后端入口](README.md)

### 为什么列表外面还要一层对象

同一个“DeepSeek assisted”请求可能实际使用模型，也可能因为缺少密钥等原因回退。单纯返回一个数组会让页面无法区分，空数组时更没有地方记录状态。因此辅助接口使用此信封：

```json
{
  "results": [],
  "mode": "FALLBACK",
  "reason": "NOT_CONFIGURED",
  "message": "...",
  "model": null
}
```

### 字段与产生位置

| 字段 | 意义 |
| --- | --- |
| `results` | 最多三条推荐，也允许为空 |
| `mode` | 实际结果为 `LLM_ASSISTED`、`FALLBACK` 或 `ELIGIBILITY_FILTER` |
| `reason` | `OK`、`NO_ELIGIBLE_COURSES`、`CATALOG_TOO_LARGE`、`NOT_CONFIGURED`、`LIMIT_REACHED`、`PROVIDER_UNAVAILABLE_OR_INVALID`；历史记录可能保留旧原因 |
| `message` | 页面可显示的英文说明，不能写供应商密钥或内部堆栈 |
| `model` | 辅助成功时的配置模型名，回退或无合格课程时为 `null` |

`AiCourseRecommender` 创建信封，`CourseService` 交给事务组件保存；读取历史时 Service 再把信封中的 `results`、`mode` 和 `model` 提取给前端。配置模型名不是独立核验的供应商计费记录。

原 `/courses/recommendations` 仍返回数组，以保持兼容。前端显示 `BASELINE` 是它对原接口的本地标记，不是这个类在原接口外加了信封。

检查入口：`DeepSeekRecommendationTest` 验证成功和回退；`MySqlCourseTest` 验证新旧历史能同时读取。


<a id="recommender"></a>

## Recommender.java — 原算法每一步的意图

[源代码](Recommender.java) · [后端入口](README.md)

### 谁使用它

`CourseService.recommend()` 用它直接给出最多三门课程；`AiCourseRecommender` 用它检查资格，并在模型无法使用时调用原算法回退。DeepSeek 成功路径不会调用 TF-IDF 来过滤候选。这个普通 Java 类没有 Spring 注解，不读写数据库、不发送网络请求，便于直接传入小样本测试。

### 先检查资格，再输出相关结果

`eligible(course, completed)` 拒绝停用、已修课程；`no_formal_course` 必须没有先修组；`structured` 必须有非空分组且每组至少一个编号出现在已修集合；`review_required` 和未知状态拒绝。例如 `[[A],[B,C]]` 在已修 `{A,C}` 时通过，在 `{B,C}` 时失败。

这种检查是规则判断，不是模型对学习能力的预测。分数再高也不能绕过。等效经历或老师许可没有在这里建模。

### 文本分数如何计算

1. `terms()` 对英文转小写，按非字母数字分隔，去掉单字符和固定停用词，记录词频。
2. 每门课程文本由标题、描述、主题组成；`documentFrequency` 记录一个词出现在多少门课程，而不是总共出现多少次。
3. 平滑逆文档频率：`IDF(t) = ln((N+1)/(DF(t)+1)) + 1`。
4. `normalizedVector()` 对出现的词使用 `TF = 1 + ln(count)`，计算 `TF × IDF`，再除以整个向量的 L2 长度。
5. 查询和课程的单位向量点积就是余弦相似度，只保留正分，降序排序；同分按 `code` 稳定排序。

`rank()` 数量范围 1–3，内部 `rankCandidates()` 数量范围 1–30。原算法用 Service 的完整启用目录计算 IDF，再排除不合格课程。DeepSeek 直接选择没有 TF-IDF 分数，不能将两种结果当作同一种概率比较。

### 按方法阅读代码（可读性重构）

先读 `rankCandidates()`：它只串起计算步骤和最后排序。再按调用顺序阅读：

- `tokenizeCourses()`：将每门课程转换成词频表，保持与课程列表相同的顺序。
- `calculateIdf()`：用完整输入目录计算词的区分度，不能提前过滤掉不合格课程。
- `normalizedVector()`：将词频转换成可比较的单位向量。
- `cosineSimilarity()`：计算查询与课程的相似度。
- `explainMatch()`：收集匹配词并生成原算法解释。

`eligible()` 单独负责资格规则；拆分没有更改公式、停用词、排序规则或解释文字。

### 常见误解和修改影响

- 英文分词不会自行理解中文、同义词或否定；DeepSeek 的意图阶段是对这一限制的辅助，不是替换所有规则。
- 查询词不在词表时不计入向量；查询向量为空或没有正分时返回空列表，不凑满三门。
- `matchingTerms` 用于原算法解释；历史当前不改变 IDF，也没有协同过滤或训练步骤。
- 改分词、停用词、权重或输入目录会改变排序，需要重新运行独立画像评价；改资格规则要核对原始先修证据。

检查入口：`RecommenderTest` 的小样本规则测试；`PublicCatalogEvaluationTest` 的固定合成画像。后者不是独立真实学生质量验证。

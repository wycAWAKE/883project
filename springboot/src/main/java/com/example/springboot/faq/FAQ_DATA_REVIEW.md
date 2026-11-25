# FAQ 小步骤 1：目标库与华中师范大学首批语料

## 2026-11-22 — FAQ 本地预览交付（最新）

用户要求尽快完成，另行明确批准首轮34/37及后续46/48调用额度。首轮32题+2诊断实际29次DeepSeek/37次BGE，保留失败结果；后续4次脱敏诊断确认H22/H26本轮ANSWER_LINK，原R08P/R10P本轮未复现。仅LlmClient增加内部校验子类型、生成证据中的CCNU地址改为Sources导航、明确数量规则与实际执行区别；输出/引用/正文链接校验不放宽，数据库原文和来源不改。

修复后固定40题回归：27/28正例返回预期来源、12/12负例健康拒答，0错误来源/引用/服务或输出异常；独立代理逐条核对27/27生成事实均受冻结资料支持。该集已暴露、H22/H26参与诊断，属于固定回归，不是新独立盲测；自动原始报告readyForProduction=false/pending_manual_review保持，另存事实核对报告。H21为正确VPN证据未召回，保留已知漏答，不据固定集事后改题或阈值。

最终后端383/383通过（包含三批真实响应的离线重放）、编译和可执行JAR打包成功；前端48/48、生产构建通过。当前本机以faq-deepseek,faq-embedding及显式semantic=true/candidate=0.60运行；真实stu1页面两条完整问题分别返回软件平台Sources和博物馆提前两天规则。页面标为Available (preview)，M2–M6不变。普通无profile启动仍不自动调用API，启用命令见docs/FAQ_DELIVERY.md。真实页面2次生成；其BGE上界5次按首次3批索引+2次查询计算，不冒充平台账单。

交付为40条已审校英文校园知识的本地预览；当前政策复核、新独立集泛化、真实Java8和生产负载/SLA仍未验。R4/R6本机回归与页面验证已交付，不将其等同于生产验收。没有SQL写入、账号变更、依赖升级、其他业务改造或Git提交；旧数据/报告/账本保留。详见docs/FAQ_DELIVERY.md，真实报告docs/faq-evaluation/delivery-final-20261122.json及-factual-review.json。以下带日期记录按历史理解。

## 2026-11-20 — 召回与拒答离线改进（当前）

本轮不访问或修改数据库，不重新核实网页政策，不改变冻结40条公开知识。新增calibration-recall-20261120.json为16组32题作者调参样本，正例只要求冻结答案支持的事实，负例区分相近主题但缺失的事实、秘密或执行能力；特别保留旧指南的current-requirements确认及博物馆special-notices限定。原调参、固定集、对话集、校准/验收/核对报告和调用账本保留。只重放旧52×40评分生成候选敏感性报告，0真实BGE/DeepSeek调用；新样本未作真实向量或模型质量测量，不是盲测。行为与验证见docs/FAQ_RECALL_ABSTENTION.md。

## 2026-11-18 — 步骤6第5–8步实现与固定集验收（当前）

已实现显式语义接收开关（默认false）、available/refused/unavailable区分、严格结构化insufficient_evidence生成拒答及原no_match映射；默认保留原词法准入+BGE排序。经用户本轮40次DeepSeek/45次BGE及US$0.50保守估算预算授权，真实固定40题已执行，实际18/43次调用、无重试、数据库前后快照相同。14/28正例返回预期来源，12/12负例无答案，2正例模型/输出边界异常，质量门槛未通过，0.73模式不启用；不据holdout调参。14条生成答案均逐条对照冻结来源支持（作者核对，非第三方）。步骤5–8实现与本轮失败验收已交付，不等于新模式可上线或R6整体完成。细节docs/FAQ_STEP6_ACCEPTANCE.md和docs/faq-evaluation/step6-final-20261118.json。保留已有修改、不提交Git；旧阶段记录按日期理解。

## 2026-11-17 — 步骤6第1–4步冻结语料测量

本批不访问数据库、不改SQL/知识/来源。使用原40条冻结公开question+空格+keywords与52道作者编写的通用英文调参问题，同轮92向量、9439 UTF-8输入字节、6次BGE请求；不发送答案/真实账号/会话/holdout。新60题集保留旧12题全部字段，新增标签在测量前冻结；原语料、调参、holdout、对话文件字节不变。标准/测量/阈值与来源事实边界见docs/FAQ_STEP6_CALIBRATION.md；不将当前来源版本核对扩展成新政策核实。

## 2026-11-17 — 步骤5只读核验

本机3306/dormitory的40条启用FAQ与冻结公开语料的问题、答案、关键词和官方来源逐条一致；待发embedding文档只含公开question+空格+keywords，共5317 UTF-8文本字节。未读取账号表，数据库前后快照相同，0写入。新增本机只读预检及MySQL+BGE专项；真实向量请求5次，无DeepSeek调用。索引在Java进程内，不新增SQL/字段/表，不落库。证据、自动审批首次拒绝后以公开数据证明获准的经过和边界见docs/FAQ_STEP5_RETRIEVAL.md。

## 2026-11-11 — 扩充至40条（当前）

用户本轮批准实施计划步骤1–3。已重新核对原5条来源，并审校35条新增英文释义；17组官方来源的日期、定位和版本限制在`database/faq_ccnu_corpus_20261111.json`，分组审校及排除来源见`docs/FAQ_CORPUS_BASELINE.md`。这表示项目侧来源核对，不是校方审批或认证英文翻译。

当前本机3306/dormitory.faq_entry为40条启用知识，ID1–40。只执行新增脚本35条INSERT；事务内检查40条及原5条完整快照后提交，旧5条ID/字段/启用/时间戳不变，不改九列结构、账号或其他业务表。真实Mapper/Service40条标准问题读取通过；这是数据库冒烟，不是独立问题评估或真实模型验证。本轮0次模型调用。

脚本`database/faq_ccnu_expansion_20261111.sql`，证据`springboot/target/faq-corpus-baseline/{db-final-inspect,db-apply}.log`及before-apply/after-apply快照。sourceKey/key是审校关联，不能假定未来环境数据库id始终等于key编号。旧指南费用/额度未采用；无法读取的图书馆规则和404个人邮箱指南未录入。博物馆10人以上边界经独立审查修正为ten or more。下方五条部署与核对记录按原日期保留。

准备及首次部署日期：2026-10-26（Asia/Shanghai）。本文保留原准备记录；当前部署结果见下节 R1 记录，不能将部署前快照误认作当前状态。

> **RAG 目标修订（2026-10-26，仅文档）：** 本批 5 条 FAQ 可继续作为第一批 RAG 知识上下文，一条 FAQ 对应一个可追溯条目。问题、审核答案、真实标题/URL 和日期边界继续适用；生成答案可据此释义，但来源只能映射这些真实记录。语料、SQL 和数据库均未因本次文档修订改变。本文“阶段 2 / 小步骤 1”为历史编号，后续衔接新计划 R1；来源核对不等于模型输出已验收，也不包含 embedding、自动网页抓取或模型训练。

## 2026-10-27 — R5-A 基础页面的真实读取验证

- 用户批准 R5-A 六步并确认使用演示 Student 账号 stu1。真实浏览器经现有登录、Session 和代理访问 `/faq/ask`，校历和选课咨询两个标准问题均显示库中的完整答案与对应官方来源；无关问题显示无匹配指引。
- 这是浏览器/接口读取验证，没有重新核对网页政策有效期、扩充语料、修改记录或执行 SQL 脚本；下方来源审校日期仍为 2026-10-26。真实第二标签页退出后，FAQ 收到 401 并清除页面结果，重新登录返回 FAQ。
- 生产构建的临时网络替身在本机 8082 制造一次延迟 503，验证防重复点击和失败后的手动重试；重试成功后仍读取真实后端。替身已停止，这部分错误注入不作为真实数据库故障证据。
- 桌面/移动端截图及详细范围见开发计划与 `report-screenshots/faq-preview-*.png`；模型、embedding 和独立问题质量评估仍未完成。

## R1 步骤 4–6：最新检索与数据库专项验证

- 用户已批准步骤 4–6。已实现 TF-IDF Top-K 与 Service 内部上下文，最多 3 条、每条问题+答案 2000 UTF-16 单元、总计 6000；副本截断不改变标准答案。来源限 CCNU 官方域名的有效 http(s) URL，格式检查不等于实时网页复核。
- 普通 `mvn test` 52 项通过，编译成功。新增 `FaqDatabaseIT` 默认不执行、不自动写库；本批显式设置 `FAQ_DB_VERIFY=true` 并指定该类运行，真实 MySQL 1 项通过。
- 目标保护核实本机 3306 / `dormitory`、InnoDB、ID 1–5 五条已批准语料；先验证五个标准问题和内部上下文。
- 单个 rollback-only 事务临时复用 ID 1：禁用后真实 Mapper、Service 及上下文均排除；重新启用并连续更新两版测试答案，同一 Service 均读取当前内容。事务后比较五条所有字段、启用标记、时间戳、表名及自增序列，全部不变。
- 没有 INSERT / DDL / COMMIT，没有新增永久记录或改动旧表；避免测试插入消耗不可回滚的自增序列。日志：`springboot/target/faq-r1-steps4-6/database-it.log`。
- 此验证限定本次受控事务及同一 Service 的读取，不声称验证跨进程缓存、并发导入、HTTP/浏览器、生成或语义召回。五条官方来源仍是下方审校内容，未重新改写或扩大知识库。

## R1 步骤 1–3：部署批次记录（后续验证见上节）

- 用户已明确批准基线检查、资料复核和 FAQ 建表/首批数据导入；本批未获准实施 Top-K、模型或前端。
- 基线 `mvn test`：28 项通过，0 失败/错误/跳过；官方来源重新核对后，五条问答保持原文，无新增未证实政策。
- 重新只读确认 `localhost:3306/dormitory`、MySQL 8.4.11；部署前原有 15 张 InnoDB 表，无 FAQ 表。
- 已执行 `database/faq.sql`，新增 9 字段 InnoDB 的 `faq_entry`；随后事务执行首批数据脚本，ID 1–5，enabled=1。
- 五条记录全部公开字段与审核脚本一致，ID/更新时间已生成；顺序重复执行脚本后，数量、ID、字段和更新时间未改变，原 15 张旧表保留，总表数 16。
- 使用既有 Spring 上下文、真实 FaqMapper/FaqService 的只读探针验证：所有字段与 datetime 映射通过，五个标准问题返回各自答案和来源。没有以 Mock 替代这次数据库读取。
- 探针生成在忽略目录 `springboot/target/`，非应用源码；不启动 Web 服务，不调用模型，不写入额外测试数据。禁用过滤与数据更新生效尚未专项验证。
- 基线日志：`springboot/target/faq-r1-baseline.log`；读取验证：`faq-r1-db-probe.log`；公开部署证据：`faq-r1-deployment-evidence.json`，均在该 target 目录，无凭据。
- 执行时建表脚本 SHA-256：`f47b292e9a3821fa2886de1e81f3e1c7abec4c0c7e2e5c90c6e859645f723423`；种子脚本 SHA-256：`6bf6ad671d7c273afca3cff67ca5f43444a805c218a142c044d732385d9fd8df`。执行后仅修正种子文件顶部状态注释，实际插入语句不变。
- 本批用 mysql `--execute` 发送脚本文本，禁用 force 和自动重连；不支持的 `--abort-source-on-error` 没有用于执行。客户端遇错处理参考 [MySQL 官方说明](https://dev.mysql.com/doc/refman/8.4/en/mysql-command-options.html)。
- 最终检查：219 个非 Markdown 文件中仅种子 SQL 状态注释变化，五条插入数据校验不变；独立只读审查未发现实质性问题。步骤 1–3 完成，后续能力仍按计划逐步验证。

## 初始准备批次范围与状态（部署前历史）

- 用户指定资料入口为 [华中师范大学官网](https://www.ccnu.edu.cn/)，因此仅使用 `ccnu.edu.cn` 官方域名的资料。
- 目标：确认当前配置的数据库，准备 3–5 条真实来源英文 FAQ 和可审查 SQL。
- 已准备 5 条，英文问题、答案、分类、关键词和来源显示名称均与现有字段兼容。
- “来源核对”指项目侧逐条比对公开官方页面和英文释义，**不代表校方审核、官方英文译文或用户已授权导入**。
- 本步仅执行只读 SELECT；没有执行建表、插入、更新、删除或种子脚本。

## 初始目标库只读核实（部署前历史）

| 项目 | 本次结果 |
| --- | --- |
| 配置文件 | `springboot/src/main/resources/application.properties` |
| 数据源主机 / 端口 | `localhost:3306` |
| 配置目标库 | `dormitory` |
| 当前执行终端的数据源 URL 覆盖 | 未设置 |
| 实际只读连接 `SELECT DATABASE(), VERSION()` | `dormitory`、`8.4.11` |
| `information_schema.TABLES` 中 `faq_entry` | 无记录 |
| `information_schema.COLUMNS` 中 `faq_entry` | 无记录 |

结论：目标库实际存在，但本次连接的库尚无 FAQ 表。这是指定配置和执行终端的核实，不据此推断其他 IDE 或已运行后端进程是否采用额外覆盖配置。未输出或保存数据库密码。

首次连接被沙箱网络限制阻止；经允许的本地只读查询重试后取得以上结果。未启动或重启 MySQL、后端服务。

## 五条语料及来源审校

以下英文内容与 `database/faq_ccnu_initial_data.sql` 一致。保留原始中文来源标题供核对；SQL 中使用对应的英文说明标题。

### CCNU-FAQ-001 — Academic calendar

**Question:** Where can I find the CCNU academic calendar for 2026-2027?

**Answer:** The CCNU Undergraduate School website publishes the academic calendar for 2026-2027. Open the linked official calendar page to view it.

**Category:** Academic calendar

**Keywords:** calendar term semester academic year timetable 2026 2027

**Source:** [华中师范大学2026-2027学年度校历](https://jwc.ccnu.edu.cn/info/1132/28511.htm)，本科生院，发布于 2026-05-21；另由 [华大校历列表](https://jwc.ccnu.edu.cn/index/hdxl.htm) 交叉确认。

**核对结论：** 已确认页面标题、年份和发布时间。答案只指向校历，不推断图片中的放假日期或考试安排；未对图片内容作转录。该条限定 2026–2027 学年，后续学年应重新核对。

### CCNU-FAQ-002 — English transcript template

**Question:** Where can I download an English undergraduate transcript template?

**Answer:** The Undergraduate School page for undergraduate transcript templates provides an English transcript template as an attachment. This is a template, not an issued transcript.

**Category:** Academic records

**Keywords:** transcript transcripts english template download grades record undergraduate

**Source:** [本科生中英文成绩单模板](https://jwc.ccnu.edu.cn/info/1115/3640.htm)，本科生院，发布于 2015-12-09；在本次访问的 [学生下载列表](https://jwc.ccnu.edu.cn/xzzx/xsxz.htm) 中仍有该入口。

**核对结论：** 正文显示英文成绩单模板附件。页面年代较早，因此仅说明公开模板入口，不声称其为最新办理要求；未下载或核验附件内部内容，不提供盖章、出具、收费或办理时限的推断。

### CCNU-FAQ-003 — Registration information change form

**Question:** Where can I find the student registration information change form?

**Answer:** The Undergraduate School Student Status Management page lists the student registration information change application form. Open the form entry on that page.

**Category:** Student registration

**Keywords:** registration information change correction status details application form

**Source:** [学籍管理](https://jwc.ccnu.edu.cn/xjxw/xjgl.htm)，本科生院；列表中的“在校生学籍信息变更申请表”日期为 2026-09-02。

**核对结论：** 列表可读取并确认条目存在。详情页本次未成功抓取，故来源指向已验证的列表，不声称已检查表单附件，不承诺审批条件、办理步骤或时限。

### CCNU-FAQ-004 — Course-selection enquiries

**Question:** Who can I contact about undergraduate course selection?

**Answer:** For undergraduate course-selection enquiries, the Undergraduate School lists 027-67863133. Check the source page for current contact details.

**Category:** Course selection

**Keywords:** course selection enrolment enrollment choosing classes consultation phone contact

**Source:** [联系我们](https://jwc.ccnu.edu.cn/ywgl/lxwm.htm)，本科生院，页面“选课咨询”栏目。

**核对结论：** 电话逐位核对。仅保留公开部门咨询号码，不增加办公时间或服务承诺。

### CCNU-FAQ-005 — Student Affairs Service Hall

**Question:** How can I contact the Undergraduate School Student Affairs Service Hall?

**Answer:** The Undergraduate School contact page lists 027-67865223 for the Student Affairs Service Hall. Use that number for enquiries about its services.

**Category:** Student services

**Keywords:** student affairs service hall desk support help office phone contact

**Source:** [联系我们](https://jwc.ccnu.edu.cn/ywgl/lxwm.htm)，本科生院，正文“联系方式”中标为“学生事务大厅”的号码。

**核对结论：** 与选课咨询区分，逐位核对号码。没有将旁边“综合业务”号码混入答案，不增加具体受理事项。

## 数据脚本与后续执行边界

脚本：仓库 `database/faq_ccnu_initial_data.sql`。

- 原准备批次仅保存文件；在本次 R1 步骤 1–3 授权后，两份脚本均已执行。建表脚本业务语句未修改，种子脚本仅修正顶部状态注释。
- 种子脚本固定使用 `dormitory.faq_entry`，避免默认库选择改变写入目标；如后续目标库改变，先重新审查脚本，不能仅修改客户端默认库。
- 本批已按“核对目标 → 建表 → 核对结构 → 导入 → 重复验证”的顺序执行。后续再次执行或变更目标库仍须符合用户当次任务范围。
- 五条记录现已 enabled=1，可参与检索式 FAQ 和本批已实现的 Top-K 上下文；RAG 生成仍未实现。
- 不指定自增 ID，不触碰用户表和旧业务表；同一 question + source_url 已存在时跳过，不改写原答案、启用状态或时间。
- 五条 INSERT 在一个 InnoDB 事务中；使用遇错即停止的客户端。任一语句失败时应在同一会话 ROLLBACK，不要继续 COMMIT。
- 重复保护仅用于单操作者顺序执行；当前表没有相应唯一约束，不支持并发导入的防重承诺。
- 事务提交前可 ROLLBACK。提交后不提供自动 DELETE 回滚，以免误删导入前已存在或后来被修改的数据；应先核对本次新增 ID 和备份，再另行授权处理。
- 来源标题/链接随记录返回；未来页面以文本展示答案，并链接到来源页。

## 验证与仍未完成的事项

- 已完成：实际目标库核实、5 条官方来源复核、建表/导入、顺序重复执行验证、真实 Mapper 字段映射、五个标准问题读取，以及后续步骤 4–6 的 Top-K、禁用/更新/回滚专项验证。
- 已通过静态检查：5 条记录互异、7 个目标字段、字段长度、英文产品字段、HTTPS 官方域名、重复执行条件与插入值一致、SQL 与本审校文档的问答一致；恰好 5 条固定目标表 INSERT，事务边界完整，无 DDL/DELETE/UPDATE/REPLACE。`git diff --check` 通过。
- 初始静态检查仅使用 Python 标准库读取文件；本批另行完成真实脚本执行和只读 Mapper 验证，两类证据分开记录。基线重跑 Maven 测试通过，未修改 Java/Vue，未运行前端测试。
- 尚未执行：embedding、LLM 或独立问题质量评估。R5-A 的真实 HTTP/浏览器基础问答已通过；禁用过滤/更新/回滚及 Top-K 已由 R1 步骤 4–6 验证，两批证据分别记录。
- 五条仅用于第一批联通准备，不代表约 40 条正式语料已完成，也不是独立检索质量评估集。
- 来源核对日期不等于 `updated_at`：后者已在实际导入时由数据库设置，并可能随记录更新而改变；不得把它解释为官方页面发布时间。

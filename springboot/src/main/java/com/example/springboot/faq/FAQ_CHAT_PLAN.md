# FAQ 对话模式实施计划

## 2026-11-22 — FAQ 本地预览交付（最新）

用户要求尽快完成，另行明确批准首轮34/37及后续46/48调用额度。首轮32题+2诊断实际29次DeepSeek/37次BGE，保留失败结果；后续4次脱敏诊断确认H22/H26本轮ANSWER_LINK，原R08P/R10P本轮未复现。仅LlmClient增加内部校验子类型、生成证据中的CCNU地址改为Sources导航、明确数量规则与实际执行区别；输出/引用/正文链接校验不放宽，数据库原文和来源不改。

修复后固定40题回归：27/28正例返回预期来源、12/12负例健康拒答，0错误来源/引用/服务或输出异常；独立代理逐条核对27/27生成事实均受冻结资料支持。该集已暴露、H22/H26参与诊断，属于固定回归，不是新独立盲测；自动原始报告readyForProduction=false/pending_manual_review保持，另存事实核对报告。H21为正确VPN证据未召回，保留已知漏答，不据固定集事后改题或阈值。

最终后端383/383通过（包含三批真实响应的离线重放）、编译和可执行JAR打包成功；前端48/48、生产构建通过。当前本机以faq-deepseek,faq-embedding及显式semantic=true/candidate=0.60运行；真实stu1页面两条完整问题分别返回软件平台Sources和博物馆提前两天规则。页面标为Available (preview)，M2–M6不变。普通无profile启动仍不自动调用API，启用命令见docs/FAQ_DELIVERY.md。真实页面2次生成；其BGE上界5次按首次3批索引+2次查询计算，不冒充平台账单。

交付为40条已审校英文校园知识的本地预览；当前政策复核、新独立集泛化、真实Java8和生产负载/SLA仍未验。R4/R6本机回归与页面验证已交付，不将其等同于生产验收。没有SQL写入、账号变更、依赖升级、其他业务改造或Git提交；旧数据/报告/账本保留。详见docs/FAQ_DELIVERY.md，真实报告docs/faq-evaluation/delivery-final-20261122.json及-factual-review.json。以下带日期记录按历史理解。

## 2026-11-20 — 召回与拒答两阶段改进（当前）

/ask和/chat仍共用FaqService，本轮只调整当前轮候选/生成，不改历史、改写或页面。语义模式且生成启用时可显式扩大candidate-threshold；默认沿用原0.73且语义模式仍关闭。候选不作摘录备用结果，明确拒答或生成异常不复活旧答案；当前知识先读取以保持数据库503边界。提示允许公开恢复流程和Sources入口，拒绝实际执行无法执行的操作。新增32题为单轮作者调参样本，不作为多轮质量证据；无真实API/DB操作，原C5边界、预算和未完成的R6专项保留。最终验证见docs/FAQ_RECALL_ABSTENTION.md。

## 2026-11-18 — 步骤6第5–8步实现与固定集验收（当前）

已实现显式语义接收开关（默认false）、available/refused/unavailable区分、严格结构化insufficient_evidence生成拒答及原no_match映射；默认保留原词法准入+BGE排序。经用户本轮40次DeepSeek/45次BGE及US$0.50保守估算预算授权，真实固定40题已执行，实际18/43次调用、无重试、数据库前后快照相同。14/28正例返回预期来源，12/12负例无答案，2正例模型/输出边界异常，质量门槛未通过，0.73模式不启用；不据holdout调参。14条生成答案均逐条对照冻结来源支持（作者核对，非第三方）。步骤5–8实现与本轮失败验收已交付，不等于新模式可上线或R6整体完成。细节docs/FAQ_STEP6_ACCEPTANCE.md和docs/faq-evaluation/step6-final-20261118.json。保留已有修改、不提交Git；旧阶段记录按日期理解。

## 2026-11-17 — 步骤6校准前半批（当前）

第1–4步只做离线标准/样本/评分/阈值比较；8道缺失话题样本仅有clarify期望标签，不调用改写器，也不将其作为C5通过证据。0DeepSeek调用，当前多轮/接口/前端不变。0.73离线候选正例仅保留14/28，不启用；后续真实生成和拒答仍待第5–8步/R6。详见docs/FAQ_STEP6_CALIBRATION.md。

## 2026-11-17 — 步骤5检索适配（当前）

/ask和/chat共用的FaqService已接入可显式启用的BGE向量索引排序；追问仍先由原C5解析，本轮重读MySQL，旧答案不作证据。向量操作使用原35秒内的8秒协作子预算；本轮词法摘录在向量I/O前发布，父超时保留已解析问题和当前标准答案。当前TF-IDF准入门槛保留，步骤6才校准向量独立接收/拒答。未修改对话合同、提示、前端或DeepSeek配置，0次真实生成调用；详见docs/FAQ_STEP5_RETRIEVAL.md。

## 2026-11-11 — 知识扩充后的C5基线（当前）

知识已由5条扩充并事务部署为40条，当前C5代码/提示/阈值不变；本轮12个上下文需求gate样本10个符合预期，D07误拦完整含代词首问、D08漏检隐含比较，作为后续改进基线保留。本轮不调用真实改写/生成模型，不将路由通过率当作多轮理解正确率。新增40题独立固定评估和12题调参集，报告docs/FAQ_CORPUS_BASELINE.md；228项后端回归/编译、MySQL40条读取通过。下方真实模型C5验收基于此前5条语料，扩库后的真实生成质量留待R6。

## 2026-11-10 — C5 基础多轮闭环完成（当前）

用户要求“使用ponytail来完成C5”，批准本批最多10次DeepSeek调用、US$0.05验证预算。复用现有Java/Vue、Session和客户端，不加依赖或数据库表。

- [x] 常见英文代词/省略追问规则；独立问题直接检索，必要时一次改写；无已建立话题先澄清。
- [x] 改写输出严格为action/question：follow_up、new_topic或clarify；拒绝损坏/多字段/非法链接/引用/残留代词等结果，失败不猜测。
- [x] 当前话题仅投影最多三组question/mode，旧助手正文不发给模型。成功追问保存已解析问题；startsTopic标记隔离旧话题，原三组×(1000+2000)字符上界保留。
- [x] 每轮重新读取启用的有效知识、应用原TF-IDF阈值和来源校验；生成只用本轮证据，历史不作事实依据。
- [x] /chat新增mode=clarify：matched=false，answer/兼容来源字段=null，sources=[]，score=0，notice=null，固定guidance=`Please name the campus service, department or document you mean, and ask a complete question.`。/ask仍为原三模式。
- [x] 页面Please clarify与安全固定提示、续接/新对话/退出/迟到隔离；仍不接受浏览器上传history或身份。
- [x] 最多一次128tokens改写+一次512tokens生成，共享35秒。改写失败/未完成不写历史；已确定问题且有本轮可靠extractive超时结果保留话题，不能承接旧话题。无自动重试。
- [x] 后端223项、前端48项、编译/生产构建通过。新增30项后端行为样本及2项前端测试；真实HTTP传输仍由既有本机受控测试验证，未删除旧回归。
- [x] 一次独立审查发现常见this/Who can I contact漏检、摘录超时丢失新话题；review-red.log四个失败样本复现，修复后全套223通过，未二次独立复审。
- [x] 真实MySQL+DeepSeek `FaqLiveMultiTurnIT`通过：选课→电话追问→校历换题→it追问→无匹配→澄清，共6次provider请求。Session为合成身份；真实浏览器另使用已授权stu1验证无上下文澄清、新对话、学生事务大厅→Who can I contact，另3次调用，返回027-67865223和[FAQ:5]。

证据：`springboot/target/faq-c5-multiturn/` 的red/guards-red/review-red/final-tests/final-compile/frontend-red/frontend-tests/frontend-build/live-disabled/live-it日志，及`report-screenshots/faq-c5-clarification-20261110.jpg`、`faq-c5-follow-up-20261110.jpg`。共9次模型调用，低于授权10次上限；精确扣费未查账单。启动和显式付费复现见`docs/FAQ_LIVE_MODEL.md`。

限制：英文规则保守且非完整语言解析器，未覆盖的省略可能无匹配，完整首问含it/this等内部指代可能要求重述；改写和引用校验不证明事实正确。R4、约40条知识、至少30个独立评估问题仍待另行完成，不把本批30项受控测试冒充评估集。以下条目按历史日期理解。

## 2026-11-10 — 当前为 DeepSeek

模型已切换官方 `deepseek-flash`（faq-deepseek profile，非思考/JSON/512tokens），独立DeepSeek密钥与配置空间；193项回归/编译和3次授权真实生成验证通过。真实浏览器电话、引用与来源正确，Key不进前端/源码/日志。旧GLM记录保留；聊天仍逐问独立，未增加多轮理解、重试、流式或其他模块。预算、启动和证据见 `docs/FAQ_LIVE_MODEL.md`。

## 2026-11-10 — 此前 GLM 单轮真实模型基础验收

- [x] 前端已经使用 `/faq/chat`，保存并续接返回的 UUID；New conversation 在 DELETE 确认后清空，离开页面请求服务端清理，46 项前端测试/构建通过。原“前端仍使用 ask”的条目仅描述旧批次。
- [x] 添加显式 `faq-glm` profile，最终默认免费 `glm-4-flash-250414`；GLM-4.7-Flash因429/1305过载暂作手动可选项。JSON提示增加当前有效ID示例，保留严格引用校验；后端191项与编译通过，普通启动仍关闭。
- [x] 添加手动 opt-in 的 `FaqLiveModelIT`，通过真实库/模型和聊天 HTTP 控制器检查生成、来源与无匹配；合成认证上下文的限制已写明。
- [x] 用户已配置本机私有Key并启动MySQL。真实API生成/来源/无匹配专项通过，stu1浏览器登录经明确授权，真实生成电话/引用、无匹配及实际过载降级已验收；原5次估计因失败诊断增至8次人工免费请求，无自动重试。
- [ ] 历史理解/改写/澄清仍未实现；会话接入不等于完整 C5。

本轮用户已明确要求真实单轮闭环，覆盖历史“暂不调用”的任务边界；默认服务是助手按前一轮推荐选择，用户可纠正。配置/启动/验证见仓库 `docs/FAQ_LIVE_MODEL.md`。以下旧记录按各自日期理解。

## 2026-10-28 最新增量 — C5 聊天接口部分

用户要求“现在增加聊天接口”。本批只完成 C5 的 HTTP/Session 基础；完整多轮理解仍未实现。执行计划：[聊天接口实施计划](../../../../../../../../docs/superpowers/plans/2026-10-28-faq-chat-api.md)。

- [x] POST /faq/chat：沿用 Session User/Identity，三种角色、严格 JSON 和 UTF-16 输入校验；原问答字段加 conversationId。
- [x] UUID 会话、每 Session 一个活动会话、最多三组有界已完成记录；外部/未知/替换后的 ID 404，并发续接 409。
- [x] DELETE /faq/chat/current 幂等清除；清除/新会话/过期/退出/更换身份取消旧等待并隔离迟到响应。
- [ ] 必要历史理解、歧义澄清、问题改写与每轮重新检索的完整多轮链路。
- [ ] 前端切换 /faq/chat，并联动新对话/页面离开的服务端清除。
- [ ] 真实模型及完整多轮质量评估；默认模型仍关闭。

### 本批可用接口

浏览器经代理使用 /api/faq/chat；后端路径为 /faq/chat。首次请求：

```http
POST /faq/chat
Content-Type: application/json

{"question":"Where can I find the CCNU academic calendar for 2026-2027?","conversationId":null}
```

成功 data 为 {conversationId,matched,answer,mode,sources,notice,guidance,faqId,sourceTitle,sourceUrl,score}，复用现有三模式；下次带同一服务端返回的 conversationId 续接。省略 ID/null 会创建并替换当前会话。请求仍必须携带既有登录 Session Cookie，不接受客户端 history/userId/role。当前回答仍只处理本次问题，返回同一 ID 不表示已经理解追问。

DELETE /faq/chat/current 返回 {code:"0",msg:"Success",data:null}，可重复调用。非法 JSON/字段/长度/UUID 为 400 / FAQ_INVALID_CHAT_REQUEST；401 / FAQ_UNAUTHENTICATED、403 / FAQ_FORBIDDEN、404 / FAQ_CONVERSATION_NOT_FOUND、409 / FAQ_CONVERSATION_BUSY、503 / FAQ_UNAVAILABLE。错误信息英文且脱敏。

最近三组服务端记录与当前页面五组显示记录不同；本批未连接二者。每组问题最多1000、回答/无匹配指引最多2000 UTF-16 单元，不拆代理对；失败不写入。记录只用于将来的历史理解，不作知识证据、不写数据库、不在服务重启后恢复。User/Identity 变更由 FAQ 专用 Servlet Session listener 清理，原登录/退出接口未改。

## 2026-10-28 最新批次 — C4 / R5-B

用户已明确“现在开发C4/R5-B”，批准此前展示的八步方案。本批复用 /faq/ask、Session 和现有 FAQ 前端：用户/助手气泡、底部输入、发送/停止、新对话、三模式、全部来源及 notice；FAQ 请求单独覆盖 45 秒，全局仍 5 秒。模型保持默认关闭，禁止把受控生成展示称为真实模型联通。新对话仅清除页面内存，不调用未来 /faq/chat 或清除接口；每问独立，不发送历史。C5 多轮、R4、真实模型/最终质量评估仍待后续。此前“R5-B 尚未授权/前端不改/5 秒”的本批说明属于 R3 或更早历史，不覆盖本次用户指令。


更新：2026-10-28。C1/C2 已完成；用户最新批准完成 C3/R3 后端 RAG 编排，保留“暂不真实调用”。C4/R5-B 已按后续明确指令实施；C5 多轮和 C6 评估仍待后续。

## 当前批次

1. **C1：合同与计划。** 冻结对话接口、身份隔离、历史边界、调用预算和实施顺序；本步只定义未来接口，不创建空控制器。
2. **C2 / R2：模型客户端。** 一个 Java 8 兼容的 Chat Completions HTTP 客户端；默认关闭，通过环境变量或私有配置启用。用受控 HTTP 响应验证请求、输出与失败；未选择具体服务/模型，不发真实请求。
3. **C3 / R3：RAG 编排。** 接入检索、生成、引用校验和 generated / extractive / no_match；可靠资料存在时可降级为标准答案。
4. **C4 / R5-B：聊天页面。** 复用 FAQ 路由与 Layout，用户/助手气泡、底部输入、发送/停止、新对话、来源。首批为完整响应，停止仅表示取消本次等待，不能承诺上游已停止计费。
5. **C5：多轮追问。** 实现下方会话合同、必要历史与问题改写；每轮重新检索，不把上轮答案当知识证据。
6. **C6：评估。** 测试追问、话题切换、信息不足、身份隔离、引用、提示注入和失败；语义检索 R4 单独验收。

R0、R1、R5-A 的历史成果保留。C1/C2 不包含聊天界面、/faq/chat 运行接口、RAG 编排、多轮或 SSE；R2 客户端完成不等于 FAQ 已调用模型。

## C1：未来对话合同（尚未实现）

- 保留 POST /faq/ask 和请求 `{"question":"..."}`；R3 已增加 mode/sources/notice 并将 no_match score 统一为 0，原标量字段保持兼容。C4 页面已消费新字段，但仍使用此单轮接口。
- 未来新增 POST /faq/chat，浏览器 /api/faq/chat。请求为 `{"question":"...","conversationId":null}`；null/缺省表示新对话，后续使用服务端返回的不透明随机 ID。只接受这两项，不接受客户端 history、角色或用户标识。
- 身份与允许角色完全复用 Session 的 User / Identity。会话仅保存在服务端当前 Session 内，不能凭 ID 访问其他 Session；未知/其他会话 ID 一律 HTTP 404、FAQ_CONVERSATION_NOT_FOUND，不泄露所属用户。
- 输入继续 Unicode trim 后 1–1000 UTF-16 单元、严格单个 JSON 对象、字符串 question；conversationId 非空时只接受服务端约定的 UUID 格式。不兼容输入返回 400；登录/权限/数据库错误仍为 401/403/503。
- 成功封装仍为 `{code:"0",msg:"Success",data:{conversationId,matched,answer,mode,sources,notice,guidance,faqId,sourceTitle,sourceUrl,score}}`。回答字段按 FAQ_CONSTRAINTS 的 R3 三模式合同；不返回历史、提示词、密钥或原始上游错误。
- 每个 Session 最多一个活动对话；新对话原子替换旧对话，离开页面、退出、Session 失效或服务重启后不恢复。新对话进行中应取消旧等待；旧响应不得写入新对话。离开前可请求下述清除，Session 失效是最终清理保障。
- 未来 DELETE /faq/chat/current 清除本 Session 当前对话，幂等；身份校验与现有入口一致。浏览器不得仅清 UI 而声称服务端已删除。
- 只保留最近三组已完成的用户/助手轮次；每组用户问题最多 1000、助手文本最多 2000 UTF-16 单元（不拆代理对），历史合计最多 9000。失败、不完整响应不入历史，no_match 仅可用于澄清，不能变成证据。
- 会话请求串行；同一会话重复并发返回 HTTP 409、FAQ_CONVERSATION_BUSY。不引入 Redis、数据库表或多实例共享会话；跨实例部署另行设计。

## 模型与历史的边界

当前 C2 只接收当前问题和最多三条公开知识，不发送历史或账号。未来 C5 只将必要且有界的历史文本用于理解代词/问题改写，不把生成文本或历史引用当作事实库；改写后重新检索启用的知识，最终生成只依据本轮证据。代词歧义先澄清，换话题清除不相关上下文，不在无证据时调用模型自由回答校园政策。

C2 单次 generate 最多一次 HTTP 请求，无自动重试、无重定向。连接/读取上限分别 3 秒/30 秒；每次输出上限 512 tokens；完整请求 JSON 最多 32768 UTF-8 字节、响应最多 65536 字节。字节上限不是精确 token 数：服务商/模型未选择，真实联调前必须验证 tokenizer、上下文窗口及输出参数支持。

R3/C3 已实现 HTTP 问答默认 35 秒业务等待截止（最多四个任务、无队列，已取得可靠答案则摘录、否则 503），以及剩余预算下的 socket 超时与协作取消；详见 FAQ_CONSTRAINTS。C4/R5-B 已单独设 FAQ 45 秒超时，全局仍为 5 秒。阻塞工作可能延后退出，不承诺上游取消/停费。未来 C5 允许必要时至多一次改写加一次生成，两次共享 35 秒，最多各 128/512 输出 tokens；无自动重试。预算不足时不再调用生成，按已有证据降级或澄清。该两次调用预算仅为未来 C5 合同，C2 仍最多一次。

## C2 实施与验证

- 配置 FaqLlmProperties：默认关闭、无默认 URL/Key/model；启用时验证可信 HTTPS 基地址（无 query/fragment/userinfo），失败只给脱敏配置消息。
- LlmClient.generate(question, contexts)：显式投影 id/question/answer，不序列化实体、来源 URL、账号、Cookie 或历史；系统规则与数据使用分离消息，禁止工具/抓取。
- 非流式 POST 到配置基地址下的 /chat/completions；Bearer key；JSON 模式，固定 English/grounding 规则与引用 [FAQ:id]。
- 输出参数默认 max_completion_tokens；只允许显式切换为 max_tokens，以兼容实现。不是所有“兼容”服务都支持 JSON 模式或两个参数，真实联调时核对，不能无提示自动重发。
- 只接受 finish_reason=stop、assistant 文本 JSON、非空 answer、唯一正整数 citedFaqIds；引用必须来自本轮上下文，正文标记与列表集合一致。拒绝工具/拒答/截断/空结果及结构损坏；只返回脱敏失败类型。
- 覆盖默认关闭与环境变量绑定、启用配置缺失/非法、请求字段投影与预算、成功解析、401/429/5xx、网络/超时、重定向、损坏/截断/未知引用；不做真实 API 调用或数据库变更。
- 完成标准：后端全量测试与编译通过、审查、同步现状；真实模型联调明确留待服务/模型/预算确定后的独立验证。

## 本轮状态

- [x] C1 合同与相关入口同步
- [x] C2 配置与客户端受控测试
- [x] C2 全量回归、编译和独立审查
- [x] C3 / R3 后端编排代码与受控验证
- [x] C4 / R5-B 页面实现及前端测试/构建
- [x] 2026-11-10按用户最新要求完成真实单轮服务基础联调，正式质量与多轮评估另计。

执行证据写入忽略目录 springboot/target/faq-chat-steps1-2/；完成后同步原开发计划，保留历史测试与新证据的区别。

## 配置使用说明

当前保持 FAQ_LLM_ENABLED=false（缺省也是 false），无需 Key 即可启动。后续真实联调在启动后端的私有运行环境设置下列项，不在前端或仓库写密钥：

| 环境变量 | 用途 |
| --- | --- |
| FAQ_LLM_ENABLED | 真实联调授权后才设 true |
| FAQ_LLM_BASE_URL | 服务商确认的 HTTPS API 基地址，不能包含 /chat/completions 末端路径、query 或 fragment |
| FAQ_LLM_API_KEY | 后端私有凭据；不要通过聊天/截图/日志传递 |
| FAQ_LLM_MODEL | 服务商实际支持的模型标识，无默认模型 |
| FAQ_LLM_OUTPUT_TOKEN_PARAMETER | 默认 max_completion_tokens；服务商明确要求时才改 max_tokens |

同名 Spring 属性见 FAQ_CONSTRAINTS.md 第 6 节；属性优先于显式环境变量别名。当前基地址校验不检测是否误填完整 /chat/completions 端点；该误配置会重复追加路径，应在配置时使用 API 基地址。R3 已将 /faq/ask 接入 LlmClient；默认关闭返回 extractive。启用配置后，有足够证据时会调用已配置服务，因此在服务/费用/token 窗口确认并获真实调用授权前保持关闭。C4 页面已显示生成模式、完整引用和降级提示；真实生成验收仍需服务/模型与费用预算确认及真实调用授权。

依据 [Chat Completions 官方协议](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create) 和 [JSON 模式说明](https://developers.openai.com/api/docs/guides/structured-outputs) 核对消息结构、JSON 输出和截断判定；这里只选择兼容协议，不宣称选定服务商。兼容服务的实际支持情况、价格与 token 窗口尚未验证。

## C1/C2 执行记录

- 2026-10-27：重读根/FAQ 必需文档及现有 Controller、Service、DTO、Entity、测试与前端。保留原工作区与既有改动，不创建分支或提交。
- 基线 mvn test：52 项通过；新增客户端测试后因两个生产类缺失编译失败（red.log）。实现时测试响应 API 方法与当前 Spring 版本不同，按当前版本改为 location(URI)，没有改依赖。
- 客户端受控测试先 46 项通过；补充真实请求工厂的 3s/30s 与禁止重定向配置检查、拒答/tool_calls 和畸形引用后，全量后端 102 项通过（原 52 + 新 50）。socket 超时/网络响应仍是受控替身，不声称真实网络超时或付费模型已验收。
- 新增配置类、客户端、客户端测试与本计划；同步根/FAQ AGENTS、原计划、约束及 README；未修改现有 Java/DTO/SQL/前端、运行配置、依赖或全局请求。客户端没有被现有接口调用，数据库专项不执行。

- 最终兼容性检查：可选 tool_calls/function_call 为 null 时不能误判为工具调用，新增回归先失败后改为仅拒绝非 null 值；真实工具调用仍拒绝。null-tools-red.log 留存。
- 独立只读审查发现 1 项 Important：Spring 5.3.15 默认响应 close 会 drain 未读正文，错误/超大响应可能持续读取。已核实本地依赖源码，用真实请求工厂与本机临时 HTTP 服务复现两个 1.5 秒慢尾响应超时（drain-red.log），随后改为拒绝/完成时 disconnect，不再 drain（drain-green.log 两项通过）。无新增依赖；连接工厂只作用 FAQ 模型客户端。另以本机 HTTP 成功响应验证实际 JSON/Bearer 发送及解析。
- 最终证据：final-tests.log 全量 **106 项通过（原 52 + 新 54），0 失败/错误/跳过**；compile.log 编译通过。使用 JDK 17，产物目标 Java 8；未在真实 Java 8 运行。新传输测试是本机明文受控 HTTP，不冒充真实 HTTPS/TLS/模型联调。首轮本机测试受执行环境网络限制，改在获自动审核允许的本机网络环境运行，未访问付费 API。
- 审查留存 Minor：完整端点误填的专门启动提示尚未实现，已在配置说明明确应填基地址；下一步接服务时可补充诊断。测试数与审查状态已在本文和原计划同步。
- 审查未判断项处理：真实服务/TLS/费用/token 窗口按用户暂不调用要求留待选型；事实支持/提示注入需 R3 和独立评估；降级/Session/数据库/总截止时间/前端/多轮按后续步骤；不以本批结构校验或 JDK17 结果替代这些验收。

## 2026-10-28 — C3 / R3 交付

- 用户授权 R3 后端编排，默认模型关闭，保留此前暂不真实调用的选择；C4/R5-B 页面与 C5 多轮仍未实施。
- /faq/ask 已接入真实 Service/检索/客户端链路，三模式与 sources/notice 已实现；no_match score=0，原来源标量字段对应首个实际引用来源。
- 同一知识快照提供完整原文降级与最多三条有界模型上下文；来源仅取真实记录。HTTP 默认35秒等待、四任务无队列，超时有证据摘录、无证据503；预算/协作取消贯穿检索后和客户端。
- 本轮最终后端153项通过（旧106+R3新增47）、编译成功。独立审查指出正文伪造链接问题，10项回归RED→GREEN修复；当前计划状态冲突已同步。证据在 target/faq-r3/。
- 未调用真实模型或执行数据库脚本，未改前端/SQL/原配置/依赖/全局请求；未重跑浏览器、真实DB或Java8运行。生成事实、资料时效、召回质量、TLS/token/费用仍待独立验收，不能由受控测试推定。

## 2026-10-28 — C4 / R5-B 实施与验证

- 用户批准此前八步方案；重读根/FAQ 文档并保留已有修改。复用当前工作区，不创建分支或提交；本批仅改四个 FAQ 前端文件、两个前端测试、七份开发文档并新增两张页面截图。
- 基线 npm test 32 项通过；先修改测试观察 45 秒预算、时间顺序、三模式/来源、停止/新对话和页面断言失败，再实现。完整前端 37 项通过，npm run build 成功；仍有既有依赖 PURE 注解与 chunk 大小提示。后端未修改，未重跑 Maven，用历史 R3 153 项记录作背景而非本批测试证据。
- 页面按时间显示用户/助手气泡、等待问题、底部输入、Send / Stop / New conversation，Ctrl+Enter 发送、Enter 换行。每问独立，只上传 question，最多五组已完成记录保留在页面内存，不写 localStorage 或数据库。引用保留纯文本 [FAQ:id]，列表标明相同 ID，不引入 Markdown 渲染依赖。
- 响应只投影公共展示字段，区分 generated / extractive / no_match，显示全部来源和固定脱敏 notice；未知/矛盾模式、坏答案/引用不显示为成功。来源安全检查保留；FAQ 45000ms，全局仍 5000ms。停止/新对话/离开立即隔离旧请求，迟到成功、失败与 finally 不能覆盖新请求；新对话不能解除 401 登录状态。
- 真实浏览器：使用既有授权 Student 演示账号 stu1，真实 Session + MySQL 返回校历、选课电话 027-67863133 及官方来源/摘录说明；volcano eruption 返回无匹配；Ctrl+Enter 可发送。另一标签退出后旧 FAQ 收到 401，清空结果并禁用发送；新对话不能绕过，重新登录返回 FAQ。没有创建账号、改密码或执行数据库脚本。
- 生产构建临时本机 8082 替身验证 generated 标签、两条引用、Stop 保留草稿/已完成记录、等待中 New conversation 清空，延迟响应未恢复；503/损坏响应脱敏且无成功历史，手动重试可恢复。请求记录每次只含 question；这不是模型联通/真实模型异常验收。替身测试结束后停止。
- 默认浏览器可用宽度实测 871px、移动 390px，clientWidth 均等于 scrollWidth；桌面请求 1280px 覆盖未实际扩大受面板限制的视口，因此不声称完成 1280px 验证。截图见 report-screenshots/faq-chat-desktop.jpg 和 faq-chat-mobile.jpg。移动 fullPage 截图接口失败后以普通视口截图留证，UI 验证正常。
- 证据：springboot/target/faq-c4/ 的 before-hashes.json、progress.md、baseline-tests.log、red-tests.log、green-tests.log、build.log、browser-requests.jsonl、scope-check.txt。独立审查结果另补。
- 未调用真实模型、修改 Java/SQL/依赖/原配置/全局 Axios/其他模块；未扩充语料。模型默认关闭，模块保持 Under development。真实模型/TLS/费用/事实支持/质量评估仍待独立验收，C5 多轮与 R4 embedding 后续单独开发。


C4 最终独立只读审查：无 Critical / Important，可交付当前范围。两项 Minor 暂留：README 当前摘要及 FAQ AGENTS 内少量旧措辞仍混用“待生成页面验收/拟新增字段”，以顶部最新 C4 和约束 API 合同为准；可补充 New conversation 后旧 401 在替代请求等待时返回的专项用例，当前身份守卫已隔离该路径。没有二次审查或额外实现。范围指纹检查 15 个预期文件通过；未改 Java/SQL/配置/依赖/全局请求/其他模块，未删除已有文件。


## 2026-10-28 — C5 聊天接口部分验证记录

- 本次按“现在增加聊天接口”实现 HTTP/Session 基础。新增 FaqChatController、FaqChatRequest、FaqChatResponse、FaqConversationService 与接口测试；请求预算绑定 Future，使清除/替换/失效能唤醒旧等待。复用原有有界执行器和 RAG，未增加依赖或改全局认证。
- 基线 mvn -o test：153 项中四项既有本机 HTTP 测试因沙箱 Permission denied: connect 报错，未删减测试；在允许本机回环连接的环境重跑完整测试后通过。新增测试先因缺少聊天实现编译失败，随后 HTTP/Session 用例通过；一次 Mockito 重新设桩方式错误改为 doReturn，不以修改生产逻辑绕过。
- 最终 mvn -o test：**189 项通过（原153 + 本批36），0失败/错误/跳过**。验证真实 Controller/Session 状态机/Runner/检索及默认关闭客户端，数据库和模型网络为受控边界；原有本机 HTTP 传输测试正常通过。JDK17 运行、Java8 源码目标，未在真实Java8运行。
- 独立只读审查发现认证后 Session 恰好失效的窄窗口会返回500。按 HTTP 生命周期正确性处理：两个用例先 RED，再在公共操作边界将真正失效转换401；36项聊天回归及189项完整回归均 GREEN。其余输入/隔离/并发/取消/历史边界未发现阻断项；没有二次审查。
- 未做真实 Servlet 容器的监听器注销/注册联调或浏览器联调；新接口未单独验证 generated 序列化和恰好9000单位的总长度样本，前者复用已有RAG/响应字段，后者由3组×(1000+2000)的输入/存储上界约束。Session清理通过受控绑定/属性事件验证，不能称为真实模型或完整C5验收。
- 未修改前端、SQL、业务数据、运行配置、依赖或其他模块；未提交Git、创建分支或删除基线文件。页面仍使用 /faq/ask；问题改写、上下文理解、前端切换、真实模型与R4/R6继续待开发。
- 证据：springboot/target/faq-c5-api/ 的 before-hashes.json、progress.md、baseline.log、red.log、tests.log、expiry-red.log、expiry-green.log、final-tests.log、final-compile.log 和 scope-check.txt。

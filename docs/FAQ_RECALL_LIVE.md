# FAQ 两阶段策略真实联调 — 步骤1

日期：2026-11-22。用户已要求执行步骤1。本页保留原准备范围并记录已授权真实结果；首轮实际29次DeepSeek、37次BGE。后续收尾另见FAQ_DELIVERY.md。

## 固定执行范围

- 新32题：16有资料、16无资料，各执行一次。另复测历史H22/H26两题，只诊断、不混入32题分母。
- 使用当前生产Service、BGE-M3与DeepSeek deepseek-flash；semantic-threshold0.73、candidate-threshold0.60、Top3和当前提示固定。本轮不调参、改标签或修改生产代码/运行配置。
- 真实MySQL仅连接已锁定的本机3306/dormitory，读取FAQ40条公开知识并在末尾比对完整快照；SQL初始化关闭，不读账号表、不写数据库。
- 最多34次DeepSeek、37次BGE：40条文档3批+34个问题；无改写、重试或模型/工具切换。向量不可用立即阻止本题生成并停止测量，鉴权/限流也停止。
- 普通测试不调用真实服务。本轮已显式选择`FaqRecallLiveIT`并设置`FAQ_RECALL_LIVE_VERIFY=true`和`FAQ_RECALL_LIVE_BUDGET=34:37`执行；永久标记阻止重复执行，不复用旧额度。
- 不可覆盖运行标记位于`docs/faq-evaluation/recall-live-20261122-claim.json`；另有逐请求计数和响应。Maven clean不恢复额度，失败后不删除标记重跑。

## 数据与诊断

32题及旧诊断报告、三个生产类均固定SHA；发送前逐项核对ID/问题/答案。嵌入只发送冻结公开question+keywords或当前测试问题；生成只发送当前问题与有效公开知识，不带Session、Cookie或个人信息。

记录实际候选、最终来源、未调用/明确拒答/生成/故障、LlmException.Type和耗时。向量失败的占位匹配不作召回命中；生成已返回但服务随后超时的no_match不作健康拒答。两个历史错误本身没有细分日志，本轮只能报告是否复现及本轮错误分类，不能倒推出历史具体原因。INVALID_RESPONSE只能定位到输出校验边界，不进一步猜测原始格式/链接原因。

人工逐条对照冻结知识审校生成答案，单独保存事实核对与原始响应/报告SHA。字面事实检查、来源命中和正常拒答均不是事实正确性的自动证明；本轮不是新的独立验收，也不证明当前学校政策有效。

## 调用预算估算

2026-11-22核对的[DeepSeek官方价格](https://api-docs.deepseek.com/quick_start/pricing/)：采用峰值cache-miss输入US$0.30/百万tokens、输出US$1.20/百万tokens保守计算。[硅基流动官方BGE-M3页面](https://www.siliconflow.cn/models/BAAI%2Fbge-m3)列出输入免费。

生成请求仍限制32KiB JSON及512输出tokens，文本为这批英文公开资料。输入字节不等于token，按每次约32768输入tokens的保守估算，34次约US$0.36；拟确认US$0.50以内。**这是估算，不是平台硬扣费封顶**；实际账单和精确token用量不作未经核实的声明。

## 准备与验证

test-only脚本：`springboot/src/test/java/com/example/springboot/faq/FaqRecallLiveIT.java`。

报告与安全检查：`FaqRecallLiveReportTest`、`FaqRecallLiveSafetyTest`。已先RED复现未实现报告，以及独立审查指出的占位候选、伪健康拒答、上下文问题未校验、向量故障仍预留生成、一次性标记问题；修正后定向测试通过。连接前拒绝远程/错端口/错库另有受控检查。沙箱内Java临时文件rename被拒后，在获准本机环境验证；不据环境权限失败放宽账本规则。

最终完整离线回归373项：372通过、0失败/错误，1项新真实响应重放因尚无响应数据而按开关跳过；编译成功。安全检查单独验证34/37上限、失败调用预记账、不可覆盖标记、完整上下文和本机数据源边界。证据`springboot/target/faq-recall-live-20261122/`的`prelive-final-tests.log`、`prelive-compile.log`及RED/GREEN日志。旧固定验收集/报告/调用账本、生产代码、配置、前端和依赖保持原样，无Git提交。

## 执行结果

已于2026-11-22 22:51执行34题，无重试，实际29次DeepSeek/37次BGE，数据库前后完整快照相同。新32题：正例候选Top1/Top3均15/16，预期来源答案12/16，1未召回、1明确误拒答、2输出校验异常；负例16/16健康拒答，0输出答案。额外H22/H26均INVALID_RESPONSE，不混入32题分母。measurementComplete=false反映含4个异常，不能把执行完34题冒充全部健康。

12条生成经独立代理逐条核对冻结事实均支持，原始报告保留pending_manual_review，另见faq-evaluation/recall-live-20261122-factual-review.json；原始响应另永久保存为recall-live-20261122-responses.json。后续已另获46/48新额度完成诊断、修复和固定40题回归及2题真实页面，详见[最终交付](FAQ_DELIVERY.md)。历史首轮不重跑、报告不覆盖，候选命中不当作答案正确率。

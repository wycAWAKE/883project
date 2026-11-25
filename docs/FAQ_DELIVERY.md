# FAQ 开发收尾与交付

更新：2026-11-22。用户要求尽快完成。沿用40条公开知识、现有Java/Vue/Session、DeepSeek及BGE-M3，不新增框架、不编造或导入未经核实的宿舍政策。

## 最终交付结果

本地预览版本已完成代码、固定回归、独立冻结事实核对与真实页面验收；前后端正在本机运行，可打开[FAQ页面](http://localhost:8080/faq)。页面状态为`Available (preview)`，没有将其标为生产验收通过。

| 最终固定40题指标 | 结果 |
| --- | --- |
| 有答案题返回预期来源 | 27/28；预先要求至少24/28 |
| 无答案题健康拒答 | 12/12；输出答案0 |
| 生成事实支持 | 独立代理逐条核对27/27支持 |
| 错误来源 / 无效引用 / 服务或输出失败 | 0 / 0 / 0 |
| 正例候选Top1 / Top3含预期证据 | 25/28、27/28；不等同最终答案指标 |
| 真实API尝试 | 35次DeepSeek、43次BGE，无重试 |
| FAQ数据库前后快照 | 全部字段一致 |
| 单次Service测量p50 / p95 / max | 791 / 1422 / 2251ms；不是负载SLA |

最终完整后端383/383通过，0失败/错误/跳过，包括原11月18日、首轮34题和最终40题的离线重放；编译及可执行JAR打包通过。前端48/48通过、生产构建成功，仍保留既有依赖注解和chunk体积提示，未升级依赖。

真实页面用已授权演示学生账号登录后提交两条完整独立问题：正版软件平台回答含[FAQ:32]和正确软件平台Sources；12人博物馆参观回答提前两天并附[FAQ:38]和官方指南。首轮索引3批+两个查询，浏览器BGE调用上界为5；生成2次，未触发改写/重试。截图见[页面证据](../report-screenshots/faq-delivery-20261122.png)。本次没有重新实测移动端、负载或真实Java8。

首轮34题29/37、诊断4/0、最终40题35/43及页面2/至多5，合计70次DeepSeek、至多85次BGE，均在分别批准的34/37和46/48上限内；浏览器部分是按执行路径计算的上界，不是平台账单。没有查询精确token或实际扣费，额度只约束本轮代理验证，不是应用运行期消费上限。

保留一个已知漏答：H21的Windows笔记本VPN同义问题只召回软件平台，未找到正确VPN证据026；模型对收到的资料拒答合理。没有为追求满分事后改题、改标签或放宽输出校验。当前40题已暴露且两题参与诊断，故本次是固定回归，新的独立集泛化、学校当前政策和生产上线仍未验；原始自动报告`readyForProduction=false`及`pending_manual_review`不覆盖，事实审查另存。

证据：[固定回归](faq-evaluation/delivery-final-20261122.json)、[独立事实核对](faq-evaluation/delivery-final-20261122-factual-review.json)、[完整响应](faq-evaluation/delivery-final-20261122-responses.json)、[四题诊断](faq-evaluation/output-diagnostics-20261122.json)。源代码/语料/题集SHA和冻结门槛见报告policy。

## 启动与使用

当前后端9090和前端8080已启动。重新启动时，在仓库根目录使用已安装的JDK17及本地数据库；不要重建数据库或重新导入旧SQL。后端私有环境需要`DEEPSEEK_API_KEY`和`SILICONFLOW_API_KEY`，密钥不写入命令文字、仓库或前端。

```powershell
$env:JAVA_HOME = 'D:/WorkTool/jdk/jdk17'
foreach ($faqKeyName in @('DEEPSEEK_API_KEY', 'SILICONFLOW_API_KEY')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($faqKeyName, 'Process'))) {
        [Environment]::SetEnvironmentVariable($faqKeyName, [Environment]::GetEnvironmentVariable($faqKeyName, 'User'), 'Process')
    }
}
& "$env:JAVA_HOME/bin/java.exe" -jar springboot/target/springboot-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=9090 --spring.profiles.active=faq-deepseek,faq-embedding --faq.embedding.semantic-acceptance-enabled=true --faq.embedding.candidate-threshold=0.60 --faq.embedding.semantic-threshold=0.73 --spring.sql.init.mode=never
```

另一个终端在`vue`目录运行`npm.cmd run dev -- --host 127.0.0.1 --strictPort`，打开`http://localhost:8080/faq`。这组显式参数启用本次测过的两阶段模式；普通无profile启动仍不自动联网。停止上述各终端中的进程即可停止本机预览；移除显式semantic参数可恢复旧词法准入模式，数据库不需要回滚。

## 修复与审查说明

`LlmClient`只增加内部脱敏ValidationReason，原错误Type/对外消息不变；已知CCNU地址仅在发给生成器的证据副本中改为Sources指引，数据库原文、来源和输出链接校验不变。补充公开数字规则与实际预约操作的区别。检索器、阈值、语料和旧评估题未改。

新增失败分类及投影测试先RED再GREEN。独立代码审查发现子类继承旧live测试可绕过专属开关，JUnit测试发现两个入口的RED复现后改为同名override，GREEN及全量383通过；不作二次代码复审。另有独立代理核对首轮12条及最终27条生成事实。原历史验收、claim和逐次计数都保留，不删除标记重跑。

实施选择：沿用现有脏工作区并按原有指纹核对范围，保留他人修改、不提交Git；使用构建目录外CREATE_NEW标记防止清理target恢复额度；将首次作者调参和历史诊断分开统计。这些选择不等同新独立验收，若需要生产泛化证据仍需新独立问题集。原登录模块的日志实现不在本次FAQ改造范围，运行日志不纳入版本化交付证据。

## 已取得的新证据

两阶段策略首次真实运行已执行34题，实际29次DeepSeek、37次BGE，无重试，数据库完整前后快照一致。新32题中正例预期来源Top3为15/16，最终有答案12/16；负例健康拒答16/16。4个正例未回答：R03P未召回，R13P明确误拒答，R08P/R10P输出校验失败。额外H22/H26也出现输出校验失败，不混入32题分母。

独立代理逐条对照冻结语料确认12条生成均有事实支持；这不是当前学校政策复核，也不是新的盲测。不同题集的12/16和旧14/28不能直接比较为准确率改善。原始结果及失败保留，模式仍默认关闭，不标为上线通过。

首次收尾回归：后端373/373通过（包括新旧保存响应的离线重放），前端48/48通过、生产构建成功。首次沙箱内本机网络/临时文件测试受限，允许的本机环境完整复核通过；未删减测试。

## 为定位实际失败补充的代码

`LlmClient.LlmException`保留既有INVALID_RESPONSE类型和脱敏消息，另提供内部ValidationReason，区分响应大小、外层合同、JSON、答案结构、正文链接、引用ID和引用标记。只保存枚举，不保留上游错误正文、密钥或提示；HTTP响应和严格校验不放宽。7个受控用例先因缺少接口失败，实现后连同原拒答用例通过。

## 本次收尾执行范围（已批准并执行）

1. `FaqOutputDiagnosticsIT`：只对本次4个输出失败问题各生成一次，复用其已冻结公开上下文；最多4次DeepSeek、0次BGE，不启动Spring或连接数据库。构建目录外一次性标记防止删除target恢复额度；记录校验子原因，不保存无效原文。
2. 确有证据的缺陷以失败回归定位并修复，先完成离线验证。不能从INVALID_RESPONSE反推一定是链接，也不能通过事后改题/改标签或放松引用校验提高成绩。
3. `FaqDeliveryAcceptanceIT`：在修复后的固定代码上一次运行原40题，候选0.60/语义0.73/Top3不变；最多40次DeepSeek、43次BGE，本机FAQ只读，完整前后快照核对。它复用经检查的RecallLive保护与统计，并使用独立输出和永久一次性标记；不覆盖11月18日或本次34题报告。数据已暴露，是固定回归，不声称新独立盲测。
4. 若固定回归达到原门槛（正例至少24/28、负例回答0/12、错误来源/引用错误0、服务与输出错误0），人工逐条核对事实，再通过已授权stu1真实页面最多2个完整独立问题验证显示与来源。最多另2次生成、5次BGE；不触发追问改写、不重试。未通过则保留失败结果，不默认启用实验模式。

整个后续批次合计最多46次DeepSeek、48次BGE，无重试、不写数据库，不复用已完成34题的额度。按2026-11-22核对的官方峰值输入US$0.30/百万tokens、输出US$1.20/百万tokens，沿用32768输入tokens及512输出tokens的每次保守估算，46次约US$0.48；拟确认US$1以内，**不是平台硬扣费上限**。BGE-M3官方列出输入免费。价格来源：[DeepSeek](https://api-docs.deepseek.com/quick_start/pricing/)、[BGE-M3](https://www.siliconflow.cn/models/BAAI%2Fbge-m3)。

## 交付边界

目标为当前校园公开FAQ的课程演示功能，保留英文与有来源回答、拒答和明确降级。宿舍政策扩库、当前网页政策复核、真正Java8运行和生产负载/SLA不是已有测量证据。普通配置默认值未改，本地预览通过显式参数启用已测策略；不把构建或模拟模型测试当作真实生成质量。

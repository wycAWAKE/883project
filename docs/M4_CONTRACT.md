# M4 Fault Reporting 接口合同

更新日期：2026-11-08。当前为四阶段完成状态，**后端、学生及员工/管理员界面均已实现**。
M6 通知未接入。后端接口本轮未改变；运行与最终验收见 M4_RUNBOOK.md 和 M4_ACCEPTANCE.md。

## 公共约定

- 在原项目 `D:\Projects\883project` 继续开发，不使用副本或独立服务。
- 复用 Spring Boot 2.6.3、Java 8、MyBatis-Plus 3.5.1、MySQL 8 和现有 Session。
- 后端前缀 `/repairs`；前端复用 `utils/request.js` 后调用 `/repairs`，浏览器请求实际为 `/api/repairs`。
- 成功：HTTP 200，`{"code":"0","msg":"Success","data":...}`。所有 code 均为字符串。
- M4 错误：HTTP 400/401/403/404/409/500，code 对应 HTTP 状态字符串；409 表示版本冲突、非法状态操作或重复分派。
- 错误处理器只作用于 `RepairTicketController`，不改写旧接口合同。
- 所有时间由后端按 `Asia/Shanghai` 生成，MySQL 保存 DATETIME，JSON 格式 `yyyy-MM-dd HH:mm:ss`；可空字段输出 null。
- 产品固定文案与错误为英文，用户输入保持原文；响应不包含完整账号对象或密码。

## 身份与读取权限

`M4SessionResolver` 根据 Session 的 `Identity` 和 `User` 解析账号，不信任请求提供的身份。

| Identity | User 的实际类型 | 创建 | 列表与详情 |
| --- | --- | --- | --- |
| `stu` | `Student` | 可以 | 仅 requester_username 等于 Session 中 username 的工单 |
| `dormManager` | `DormManager` | 不可以 | 校园工单队列；mine=true 时只看自己的负责工单 |
| `admin` | `Admin` | 不可以 | 全部；mine=true 返回 400，因为管理员不是负责人角色 |

缺少 Session、角色与对象类型不匹配、空账号均返回 401。学生读取其他人的详情或历史返回 404。
姓名仅用于显示，不作为归属依据；新表使用大小写敏感的 `utf8mb4_bin`。
员工队列目前按课程演示约定覆盖全部校园设施，不自动套用旧宿管楼栋权限；若团队要求部门/楼栋隔离，需要先调整合同和验证。

## 已实现接口

### POST /repairs

仅学生。请求只允许以下四个字段；未知字段（含伪造 requesterUsername、status、version 等）返回 400。

```json
{
  "title": "Light broken",
  "description": "The ceiling light is not working.",
  "locationType": "CLASSROOM",
  "locationText": "Block A room 101"
}
```

去除字段首尾 Unicode 空白（含全角空格和不换行空格）后：title 长度 1–100，description 1–2000，locationText 1–200；按 Java 字符串长度检查。
locationType 只允许 `DORM`、`CLASSROOM`、`PUBLIC_AREA`、`OTHER`。
提交人、状态、版本和时间均由后端写入；初始状态 SUBMITTED、version=0。
工单插入和一条 CREATED 历史在同一事务内完成；任一步失败回滚全部变更。
返回 data 为完整安全工单视图（与详情相同）。

### GET /repairs

| 参数 | 默认值 | 规则 |
| --- | --- | --- |
| pageNum | 1 | 正整数 |
| pageSize | 10 | 1–50 |
| keyword | 空 | 最多 100 字，按 title 执行参数化 LIKE 搜索 |
| status | 空 | 为空不筛选；否则必须是下文状态枚举 |
| mine | false | 员工 true 时筛选 assignee；学生无论传何值都只查本人 |

data 使用 MyBatis-Plus 分页形状，稳定字段为 `records`、`total`、`current`、`size`、`pages`。
按 created_at DESC、id DESC 排序；空结果 records=[]、total=0。

### GET /repairs/{id}

ID 必须是正整数。学生查询时在 SQL 中同时限定 requester_username；记录不存在或本人不可访问时返回 404。
data 字段：id、requesterUsername、title、description、locationType、locationText、assigneeUsername、status、version、createdAt、updatedAt、resolvedAt、closedAt。
初始 assigneeUsername、resolvedAt、closedAt 为 null。

### GET /repairs/{id}/history

与详情共用相同的工单访问检查，按历史 id 升序返回数组。
历史字段：id、ticketId、action、fromStatus、toStatus、actorRole、actorUsername、note、createdAt、ticketVersion。
创建历史为 action=CREATED、fromStatus=null、toStatus=SUBMITTED、ticketVersion=0，操作者是当前学生。

## 数据库 v1

独立表 `m4_repair_ticket`、`m4_repair_history`，不复用旧 `repair`。
脚本：`database/m4_schema.sql`，不含 USE、DROP、DELETE；必须显式选择目标数据库，首次执行。
MySQL 至少 8.0.16；索引覆盖学生列表、员工待办、状态筛选与历史排序。
历史 ticket_id 外键限制删除，(ticket_id,ticket_version) 唯一。
脚本不使用 IF NOT EXISTS，重复运行会失败，避免隐藏结构不一致。不要用忽略错误的方式重跑。

## 第二次已实现的写接口和员工选项

| 接口 | 用途 | 请求 |
| --- | --- | --- |
| GET /repairs/staff | 安全员工选项 | 无；仅返回 username、displayName |
| PUT /repairs/{id}/assignment | 首次分派或未开始工单改派 | assigneeUsername、version、note |
| PUT /repairs/{id}/status | 处理、解决、关闭 | targetStatus、version、note |
| POST /repairs/{id}/progress | 追加处理说明 | version、note |

状态为 SUBMITTED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED。
分派权限为员工或管理员；分派/改派仅允许 SUBMITTED 或 ASSIGNED。
状态更新与追加进度仅当前负责人或管理员；解决与关闭必须有说明。
IN_PROGRESS 可以追加说明而不改变状态；ASSIGNED 可以改派并写 REASSIGNED 历史；CLOSED 不再修改。
所有写操作按旧 version 与旧 status 条件更新并 version+1；负责人写入还检查 assignee，影响行数不为 1 返回 409；状态与历史同事务。
允许转换由 RepairTicketTransitions 集中检查；真实 MySQL 已验证并发、越权、重复关闭与回滚。


### 员工选项

`GET /repairs/staff` 仅员工或管理员可访问；学生 403，未登录 401。
data 为 `[{"username":"staff1","displayName":"First Staff"}, ...]`，按 username 升序。
仅从数据库查询 username 和 name，并只序列化两个选项字段，不返回密码、联系方式或完整账号。

### 写请求共同规则

- 写请求只接受本操作定义的字段，未知字段返回 400。
- `version` 必须为 JSON 整数，范围 0–2147483646；缺少、null、负数、浮点、数字字符串和越界均返回 400。
- 请求版本不等于当前版本，或 SQL 条件更新的影响行数不为 1，返回 409。
- 每次成功操作将 version 加一，更新 updatedAt，并追加 ticketVersion 对应的新历史。
- 失败不改变工单或历史；客户端收到 409 应刷新并重新确认，禁止自动重放写入。
- 返回 data 为本次成功写入后的 RepairTicketView，不额外返回完整账号。

### 分派和改派

```json
{"assigneeUsername":"staff1","version":0,"note":"Please inspect the light."}
```

note 可省略，最多 1800 字；服务端在历史说明前加入 `Assigned to <账号>.`，保留明确目标。
目标账号必须存在于 dorm_manager；按旧表查询后使用数据库返回的规范 username 保存，避免大小写不同造成负责人权限不匹配。
仅 SUBMITTED、ASSIGNED 可以分派；当前负责人相同时返回 409，不追加历史。
首次分派 action=ASSIGNED；改派 action=REASSIGNED，状态保持 ASSIGNED。改派后原负责人不能开始处理。

### 状态更新

```json
{"targetStatus":"IN_PROGRESS","version":1,"note":"Inspection started."}
```

仅允许 ASSIGNED→IN_PROGRESS（STARTED）、IN_PROGRESS→RESOLVED（RESOLVED）、RESOLVED→CLOSED（CLOSED）。
未知或空目标状态 400；跳步、回退、同状态重复操作和再次关闭返回 409。
开始处理可省略 note，后端默认 `Work started.`；解决、关闭必须有非空说明，最长 2000 字。
RESOLVED 设置 resolvedAt；CLOSED 设置 closedAt 并保留 resolvedAt。
员工须是当前负责人；管理员可处理任意工单，但仍遵守状态顺序和版本。

### 进度备注

```json
{"version":2,"note":"The replacement part has arrived."}
```

仅 IN_PROGRESS 且当前负责人或管理员可以追加；note 必填且最多 2000 字。
action=PROGRESS_ADDED，fromStatus 和 toStatus 都为 IN_PROGRESS；version 仍递增。
历史按 id 升序返回，包含操作者角色、账号、说明、时间和版本。

### 事务和并发

三个写方法均有 Spring 事务。SQL 条件包含 id、旧 status 和旧 version；员工处理与备注操作还包含当前 assignee_username。
在同一数据库事务中完成条件更新与历史插入，失败抛异常回滚；不会“状态已变化但历史丢失”。
并发测试使用两个线程、独立请求和同一旧版本，分别验证分派和进度更新只有一个 200，另一个 409，且只有一条新增历史。
本次无表结构变化，继续使用 database/m4_schema.sql v1，不需要为第二阶段重复执行建表。

## M6 协作范围

M4 独立维护工单与历史，当前没有发送通知，也没有实现 M6。
未来事件合同候选：eventId（历史 ID）、ticketId、ticketVersion、action、fromStatus、toStatus、recipientRole、recipientUsername、occurredAt、detailPath（`/repairs?ticketId=...`）。
是否接入与采用何种事务策略由两模块负责人共同确定，不能用空通知方法声称发送成功。

# B站直播场次、事件留存与导出

最后更新：2026-08-17

## 能力范围

`/bilibili/live` 的“场次统计与导出”面板按直播场次展示开始/结束边界、弹幕、礼物、付费、身份记录和消费金额。页面默认每 10 秒刷新，允许设置 `1`～`3600` 秒的刷新间隔，也可点击“立即刷新”；刷新时保留仍然存在的当前场次选择。导出支持原生 Excel XLSX、弹幕 CSV、礼物 CSV、用户 CSV 或完整 ZIP。

这套数据的真实口径是：**项目部署后，弹幕 WebSocket 在线期间成功解析并持久化的受支持事件**。它不代表 Bilibili 平台全量历史；未知或畸形帧、连接中断空档、持久化失败以及部署前未采集的明细不会出现在统计或导出中。

## 场次状态与边界

| 状态 | 含义 |
| --- | --- |
| `OPEN` | 已确认开播，场次仍在进行。 |
| `END_PENDING` | 收到下播信号，等待下一次 REST 状态复核。 |
| `CLOSED` | 已确认结束。 |
| `INCOMPLETE` | 历史状态事件只有开始边界，结束时间未知。 |

场次边界会综合 REST 快照、WebSocket `LIVE` / `PREPARING` 事件和迁移时的历史状态事件。`END_PENDING` 不会因为单个 `PREPARING` 包立即关闭；REST 复核确认后才写入最终结束边界。进程重启时遗留的 WebSocket 连接以最后心跳/连接时间保守收口，不把停机空档计为在线覆盖。

## 数据模型

Flyway `V10__bilibili_live_session.sql`：

- 给 `bilibili_live_danmaku_session` 增加 `connected_at`，区分开始连接与真正鉴权在线。
- 新增 `bilibili_live_session`，保存场次状态、平台开播时间、来源、待确认结束信号和最终边界。
- 新增 `bilibili_live_session_event`，保存受支持事件的命令、类型、发送者、礼物/付费字段、发生/接收时间、传输会话和原始 JSON。
- 对有可靠上游 ID 的事件按强 ID 去重；没有可靠 ID 的事件按连接会话和接收序号逐条保存。
- 从旧 `bilibili_live_status_event` 回填成对边界；无法配对的历史开播记录保存为 `INCOMPLETE`，不伪造结束时间。

Flyway `V11__bilibili_live_danmaku_recent_sender_uid.sql` 为最近弹幕表增加可空 `sender_uid`。扫码登录成功后，后端会发布凭据激活事件，把当前仍处于 `ANONYMOUS` 的弹幕连接异步重连为 `LOGIN`；之后新收到的弹幕会尽量同时保存昵称和正 UID。已有历史记录如果只有脱敏昵称且缺少 UID，不会进行不可靠的猜测回填。

事件类型包括 `DANMAKU`、`GIFT`、`SUPER_CHAT`、`GUARD_BUY`、`LIVE`、`PREPARING`、`METRICS` 和 `NOTIFICATION`。金额字段统一使用 `milli_yuan`（千分之一元）；免费/银瓜子礼物不把平台币价格误写成人民币单价。

## 身份与统计口径

- 只有正 UID 才计入“已识别用户”人数。
- 没有可靠 UID 的互动、送礼和付费按“未解析事件”计数，不把同名昵称合并成同一人。
- Top 身份记录最多返回 100 条，昵称与 UID 分列展示；超长文本可通过悬停查看完整值。
- 用户工作台的实时弹幕列表以固定宽度单列展示“昵称 · UID”；UID 不存在时只展示昵称。
- 消费金额只汇总可确认的人民币付费事件；展示和导出单位均明确为 `milli_yuan`。

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/bilibili/live-monitor/rooms/{monitorId}/sessions?limit=20` | 查询直播间最近场次。 |
| `GET` | `/api/bilibili/live-monitor/sessions/{sessionId}` | 查询单场汇总。 |
| `GET` | `/api/bilibili/live-monitor/sessions/{sessionId}/users?limit=100` | 查询单场身份记录。 |
| `GET` | `/api/bilibili/live-monitor/sessions/{sessionId}/export?category=danmaku` | 下载弹幕 CSV。 |
| `GET` | `/api/bilibili/live-monitor/sessions/{sessionId}/export?category=gifts` | 下载礼物/付费 CSV。 |
| `GET` | `/api/bilibili/live-monitor/sessions/{sessionId}/export?category=users` | 下载用户聚合 CSV。 |
| `GET` | `/api/bilibili/live-monitor/sessions/{sessionId}/export?category=xlsx` | 下载包含摘要、弹幕、礼物和用户四个工作表的原生 XLSX。 |
| `GET` | `/api/bilibili/live-monitor/sessions/{sessionId}/export?category=all` | 下载完整 ZIP。 |

XLSX 文件名为 `bilibili-live-session-{sessionId}.xlsx`，工作表固定为“场次摘要”“弹幕”“礼物”“用户”。标识符（特别是长 UID）按文本单元格写入，避免 Excel 的 15 位数字精度限制；用户文本始终写成字符串而不是公式。工作簿使用流式行窗口生成，适合直接在 Excel 中筛选、查看和保留列类型。

完整 ZIP 包含 `manifest.json`、`summary.csv`、`danmaku.csv`、`gifts.csv` 和 `users.csv`。CSV 使用 UTF-8 BOM、CRLF 和标准引号转义，并对可能触发电子表格公式执行的文本做安全处理。CSV 适合跨工具交换或按单类流式处理，XLSX 更适合直接用 Excel 查看；两者使用相同数据源和覆盖口径。所有导出都在 PostgreSQL `REPEATABLE READ` 只读事务中完成，保证同一次下载中的汇总和明细来自同一快照。

## 覆盖状态

| `coverageStatus` | 含义 |
| --- | --- |
| `RECEIVED_WHILE_ONLINE` | 该场次与至少一个有效 WebSocket 在线区间相交，统计来自实际留存事件。 |
| `NO_ONLINE_COVERAGE` | 有场次边界，但没有可确认的 WebSocket 在线覆盖。 |
| `BOUNDARY_ONLY` | 由历史状态事件回填，只能确认部分边界，明细不可用。 |

CSV 和 XLSX 通过响应头返回 `X-Capture-Scope`、`X-Coverage-Status`、传输会话数和采集起止时间；完整 ZIP 还在 `manifest.json` 中记录相同信息。历史明细不可用时，接口返回 `null` 而不是伪装成真实的 `0`。

## 运维检查

```powershell
# 最近 20 个场次
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/live-monitor/rooms/{monitorId}/sessions

# 单场汇总与身份记录
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/live-monitor/sessions/{sessionId}
Invoke-RestMethod http://127.0.0.1:8080/api/bilibili/live-monitor/sessions/{sessionId}/users

# 完整导出
Invoke-WebRequest `
  'http://127.0.0.1:8080/api/bilibili/live-monitor/sessions/{sessionId}/export?category=all' `
  -OutFile bilibili-live-session.zip

# 推荐给 Excel 用户的原生工作簿
Invoke-WebRequest `
  'http://127.0.0.1:8080/api/bilibili/live-monitor/sessions/{sessionId}/export?category=xlsx' `
  -OutFile bilibili-live-session.xlsx
```

排查“统计为零”时先看 `coverageStatus`、`transportSessionCount`、`captureStartedAt` 和 `captureEndedAt`。只有 `RECEIVED_WHILE_ONLINE` 且存在有效覆盖区间时，零值才表示在线采集范围内没有对应事件。

删除直播间监控会按外键级联删除其场次和事件；只想停止继续采集时应停用监控，不要删除。

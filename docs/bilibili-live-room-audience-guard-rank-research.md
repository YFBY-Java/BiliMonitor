# Bilibili 直播房间观众与大航海榜单数据调研

调研日期：2026-06-18  
本地资料：`bilibili-api-collect-new-research/repo`  
实测房间：`26854650`，主播 UID `3493118494116797`，请求未携带登录 Cookie

## 结论

截图里的数据大部分可以拿到。

- 房间观众括号数、在线榜贡献值、进房时间、日榜、周榜、月榜：可以通过 `queryContributionRank` 获取。
- 大航海括号数、周榜、月榜、陪伴榜、Top3、上周/上月 Top1 提示：可以通过 `guardTab/topListNew` 获取。
- 旧接口 `getOnlineGoldRank` 仍可用，但只覆盖在线贡献榜，不覆盖日/周/月与进房时间。
- 大航海榜单的排序结果可拿到，但“周/月大航海亲密度”的具体分值未在当前响应里暴露，`score` 实测恒为 `0`，只能使用 `rank` 和顺序。
- 用户自己的排名、是否关注、自动续费、我的大航海信息等个人态字段，不登录时通常为 `null`、`0` 或空值；需要 Cookie 才能补齐。
- 这些是 Web 私有业务接口，不是官方开放平台稳定契约。可以用于监控/研究型采集，但需要做降级、限频、字段兼容和合规处理。

## 前置流程

先把房间号解析成真实房间 ID 和主播 UID：

```http
GET https://api.live.bilibili.com/room/v1/Room/room_init?id={room}
```

关键响应字段：

| 字段 | 含义 |
| --- | --- |
| `data.room_id` | 真实直播间 ID，后续建议用这个 |
| `data.short_id` | 短号 |
| `data.uid` | 主播 UID，即后续接口里的 `ruid` |
| `data.live_status` | 0 未开播，1 直播中，2 轮播 |

注意：`room/v1/Room/get_info` 里的 `online` 更像直播间人气/热度值，不等同于截图右侧“房间观众(2598)”括号数。截图里的括号数应使用下面榜单接口的 `count/count_text` 或旧接口的 `onlineNum/onlineNumText`。

## 房间观众榜

推荐接口：

```http
GET https://api.live.bilibili.com/xlive/general-interface/v1/rank/queryContributionRank
```

基础参数：

| 参数 | 说明 |
| --- | --- |
| `ruid` | 主播 UID |
| `room_id` | 真实房间 ID |
| `page` | 页码 |
| `page_size` | 每页数量，页面脚本使用 100，保守建议 50-100 |
| `type` | 一级榜单类型 |
| `switch` | 二级榜单/排序开关 |
| `platform` | `web` |
| `web_location` | `444.8` |
| `wts` / `w_rid` | WBI 签名参数 |

Tab 对应关系：

| UI | `type` | `switch` | 说明 |
| --- | --- | --- | --- |
| 在线榜 - 贡献值 | `online_rank` | `contribution_rank` | 截图默认列表；有 `score` 贡献值 |
| 在线榜 - 进房时间 | `online_rank` | `entry_time_rank` | 按进房顺序；`score` 通常为 0 |
| 日榜 - 当日 | `daily_rank` | `today_rank` | 当日贡献值排序 |
| 日榜 - 昨日 | `daily_rank` | `yesterday_rank` | 昨日贡献值前列 |
| 周榜 - 本周 | `weekly_rank` | `current_week_rank` | 当周贡献值排序 |
| 周榜 - 上周 | `weekly_rank` | `last_week_rank` | 上周贡献值前列 |
| 月榜 - 本月 | `monthly_rank` | `current_month_rank` | 当月贡献值排序 |
| 月榜 - 上月 | `monthly_rank` | `last_month_rank` | 上月贡献值前列 |

关键响应字段：

| 字段 | 含义 |
| --- | --- |
| `data.count` / `data.count_text` | 房间观众括号数；在线贡献榜实测返回当前在线榜人数 |
| `data.item[]` | 榜单用户 |
| `item.rank` | 排名 |
| `item.uid` / `item.name` / `item.face` | 用户 UID、昵称、头像 |
| `item.score` | 贡献值；仅在线贡献榜有明确展示意义 |
| `item.medal_info` | 粉丝牌摘要 |
| `item.guard_level` | 大航海等级，1 总督，2 提督，3 舰长 |
| `item.wealth_level` | 财富/荣耀等级 |
| `item.uinfo.medal` | 更完整粉丝牌信息 |
| `item.uinfo.guard.expired_str` | 大航海到期时间，存在时可取 |
| `data.config.value_text` | 页面右侧指标文案，实测为“贡献值” |

实测结果：`online_rank/contribution_rank`、`entry_time_rank`、日/周/月当前与上一周期均返回 `code=0` 和用户列表。未登录也可拿到公开榜单；个人态字段为空。

### 旧在线贡献榜接口

本地 `bilibili-API-collect` 记录的旧接口仍可用：

```http
GET https://api.live.bilibili.com/xlive/general-interface/v1/rank/getOnlineGoldRank
```

参数：

| 参数 | 说明 |
| --- | --- |
| `roomId` | 房间 ID |
| `ruid` | 主播 UID |
| `page` | 页码 |
| `pageSize` | 页大小，文档写最大 50 |

关键字段：

| 字段 | 含义 |
| --- | --- |
| `data.onlineNum` / `data.onlineNumText` | 在线榜人数/房间观众括号数 |
| `data.OnlineRankItem[]` | 在线贡献榜 |
| `OnlineRankItem.score` | 贡献值 |
| `OnlineRankItem.medalInfo` | 粉丝牌 |
| `OnlineRankItem.guard_level` | 大航海等级 |
| `OnlineRankItem.wealth_level` | 财富等级 |

这个接口无需 WBI 签名，适合作为在线榜的备用接口；但不支持日榜、周榜、月榜和进房时间。

## 大航海榜

推荐接口：

```http
GET https://api.live.bilibili.com/xlive/app-room/v2/guardTab/topListNew
```

参数：

| 参数 | 说明 |
| --- | --- |
| `roomid` | 真实房间 ID |
| `ruid` | 主播 UID |
| `page` | 页码 |
| `page_size` | 每页数量，文档写默认 20、最大 30 |
| `typ` | 榜单类型 |

2026-06-18 实测 `typ` 对应关系：

| UI | `typ` | 响应提示 |
| --- | --- | --- |
| 周榜 | `4` | `榜单每周更新，统计周大航海亲密度` |
| 月榜 | `3` | `榜单每月更新，统计月大航海亲密度` |
| 陪伴榜 | `1` | `上船后可积累陪伴天数` |
| 默认/旧榜 | `0` 或 `5` | `头号粉丝大航海，上船后可上榜` |

注意：本地文档写的是 `typ=3,4,5` 分别为周/月/总航海亲密度，但实测当前页面语义是 `4=周榜`、`3=月榜`、`1=陪伴榜`。实现时应按响应 `remind_msg` 做一次自检，避免 B 站再次调整。

关键响应字段：

| 字段 | 含义 |
| --- | --- |
| `data.info.num` | 大航海总数，即截图“大航海(80)”括号数 |
| `data.info.page` / `data.info.now` | 总页数、当前页 |
| `data.top3[]` | 榜单前三 |
| `data.list[]` | 后续列表；`page=1` 时通常从第 4 名开始 |
| `rank` | 排名 |
| `accompany` | 陪伴天数 |
| `uinfo.uid` / `uinfo.base.name` / `uinfo.base.face` | 用户信息 |
| `uinfo.medal` | 粉丝牌 |
| `uinfo.guard.level` | 大航海等级，1 总督，2 提督，3 舰长 |
| `extop[]` | 周/月榜上一个周期 TOP1，页面用于“上周 TOP1”等提示 |
| `my_follow_info` | 当前登录用户自己的大航海/陪伴信息；不登录为空 |
| `remind_msg` | 榜单说明文案，可用于校验 tab 类型 |

实测结果：`typ=4/3/1` 均返回 `code=0`，无需 Cookie、无需 WBI 签名。`data.info.num` 与页面大航海括号数一致；`top3` 和 `list` 拼起来就是完整榜单页。周/月榜响应里有 `extop`，可还原截图里的上一周期 TOP1 区块。

## WBI 签名

`queryContributionRank` 当前需要 WBI 签名。流程：

1. 请求 `https://api.bilibili.com/x/web-interface/nav`，读取 `data.wbi_img.img_url` 和 `data.wbi_img.sub_url`。
2. 截取两个 URL 文件名作为 `img_key`、`sub_key`。
3. 用 WBI 混淆表生成 `mixin_key`。
4. 原始参数加 `wts`，按键名升序编码后拼接 `mixin_key`，计算 MD5 得到 `w_rid`。
5. 最终请求追加 `wts` 和 `w_rid`。

`img_key` 和 `sub_key` 通常每日更替，建议缓存到当天并在签名失败时刷新。

## 采集建议

- 输入房间号后先调 `room_init`，统一转换成真实房间 ID 和 `ruid`。
- 在线榜建议 30-60 秒轮询；日/周/月榜 5-15 分钟轮询即可；大航海榜 5-15 分钟或更低频。
- 分页抓取时按 `page_size=50/100` 控制请求量。大航海接口 `top3` 独立返回，`list` 从后续名次开始，入库前要合并去重。
- 字段要做宽松解析：昵称可能脱敏，`medal_info`、`uinfo.guard`、`wealth` 可能为空。
- 不要把 `room/v1/Room/get_info.data.online` 当作“房间观众括号数”；它和榜单人数不是同一个指标。
- 官方直播开放平台更适合拿实时事件，例如进入房间、付费大航海事件；但它不等价于页面右侧完整历史榜单。若做商业化或长期稳定产品，优先评估官方开放平台，私有 Web 接口只作为补充。

## 参考来源

- 本地：`bilibili-api-collect-new-research/repo/docs/live/user.md`
- 本地：`bilibili-api-collect-new-research/repo/docs/live/guard.md`
- 本地：`bilibili-api-collect-new-research/repo/docs/misc/sign/wbi.md`
- GitHub 镜像文档：<https://github.com/pskdje/bilibili-API-collect/blob/main/docs/live/user.md>
- GitHub 镜像文档：<https://github.com/pskdje/bilibili-API-collect/blob/main/docs/live/guard.md>
- GitHub 镜像文档：<https://github.com/pskdje/bilibili-API-collect/blob/main/docs/misc/sign/wbi.md>
- 前端脚本线索：<https://greasyfork.org/zh-CN/scripts/21416-bilibili%E7%9B%B4%E6%92%AD%E5%87%80%E5%8C%96/code>
- B 站贡献榜说明：<https://live.bilibili.com/p/contribute-rank-description-h5/index.html>
- B 站直播开放平台：<https://open-live.bilibili.com/document/>

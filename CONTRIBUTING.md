# 贡献指南

感谢你改进 BiliMonitor。当前仓库仍处于早期阶段，范围清晰、改动聚焦的提交最容易审查和合并。

## 开发环境

1. 将 `social-data-monitor/.env.example` 复制为 `social-data-monitor/.env.local`。
2. 填写本地数据库连接、开发账号密码和凭据加密 key。
3. 使用 `social-data-monitor/scripts/dev-start.cmd` 启动项目，也可以分别启动后端和前端。

## 检查命令

提交合并请求前，请按改动范围运行相关检查：

```powershell
cd social-data-monitor\backend
.\mvnw.cmd test
```

```powershell
cd social-data-monitor\frontend
npm install
npm run build
```

## 合并请求要求

- 每个 PR 尽量只解决一个问题或一个功能。
- 不要提交 `.env.local`、运行数据、浏览器 profile、本地日志或外部研究 dump。
- 行为发生变化时，请添加或更新测试。
- 环境搭建、配置项、API 行为或数据库结构变化时，请同步更新文档。
- 数据库结构变更应新增 Flyway migration；已共享的历史 migration 不要再直接修改。

## 提交信息

提交信息保持简洁、清晰，例如：

```text
新增 Bilibili 直播榜单刷新接口
```

## 安全

不要在公开 issue、合并请求、截图或日志中包含真实 token、Cookie、数据库密码、Bilibili 凭据、私钥或个人账号数据。详见 [SECURITY.md](SECURITY.md)。

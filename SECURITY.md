# 安全策略

## 支持版本

当前项目尚未发布 1.0，整体仍偏开发阶段。安全修复统一在 `main` 分支处理。

## 漏洞报告

不要在公开 issue、合并请求、截图或日志中发布真实密钥和隐私数据，包括：

- Bilibili `SESSDATA`、`bili_jct`、`DedeUserID`、`refresh_token` 或完整 Cookie 请求头。
- 数据库密码。
- `SOCIAL_MONITOR_CREDENTIAL_ENCRYPTION_KEY`。
- 私钥、访问令牌、OAuth token 或浏览器 profile 数据。
- 不应公开的个人账号 ID。

如果发现漏洞，请通过 GitHub 个人主页或仓库设置中可用的非公开渠道联系维护者。

## 当前安全注意事项

- 当前后端配置面向本地开发，`/api/**` 默认放行。
- `/api/bilibili/auth/credential` 可能返回完整 Bilibili 凭据，公开部署前必须增加访问控制。
- `social-data-monitor/.env.local` 绝不能提交到 Git。
- 生产部署也应使用项目内的 `social-data-monitor/.env.local` 或等价的项目内私有配置文件提供密钥，并确保该文件不进入 Git。

## 生产加固建议

- 为所有管理类 API 增加认证。
- 为 Bilibili 凭据相关接口增加基于角色的访问控制。
- 收紧 CORS 允许来源。
- 所有密钥都存放在项目目录内的 Git 忽略文件中，例如 `social-data-monitor/.env.local`。
- 如果 `SOCIAL_MONITOR_CREDENTIAL_ENCRYPTION_KEY` 曾经暴露，应通过规划好的迁移流程轮换。
- 增加数据库备份和保留策略。
- 请求日志需要做敏感字段脱敏。

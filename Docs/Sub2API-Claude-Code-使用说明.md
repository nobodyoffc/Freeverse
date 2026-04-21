# Sub2API + Simple 模式 + Claude Code CLI 使用说明

个人备忘：远程部署 [Sub2API](https://github.com/Wei-Shaw/sub2api) 后，在本地用 **Claude Code CLI** 经网关调用 Claude。官方文档以仓库 [README / README_CN](https://github.com/Wei-Shaw/sub2api) 为准，本文仅整理常用路径。

---

## 1. 模式选择：个人自用 → Simple 模式

| 模式 | 适用场景 |
|------|----------|
| **普通 / 完整** | 需要「订阅商品、订阅分组、多用户计费」等 SaaS 能力 |
| **Simple（简易）** | 个人或小团队：不依赖订阅体系，跳过相关计费流程，界面会隐藏部分 SaaS 功能 |

个人自用、**不采用订阅**时，在服务器上为 Sub2API 设置：

```bash
RUN_MODE=simple
```

若部署在 **生产环境** 且文档/启动日志要求显式确认，可能还需要（以当前版本说明为准）：

```bash
SIMPLE_MODE_CONFIRM=true
```

修改方式：Docker 的 `.env` / `docker-compose.yml` 的 `environment`，或 systemd 的 `Environment=`，然后 **重启 sub2api**。

---

## 2. 管理后台里仍须完成的配置（与是否 Simple 无关）

Simple 只关掉「订阅/部分 SaaS」流程，**不会**代替你配置上游：

1. **平台**：如 Anthropic（按向导添加）。
2. **上游账号**：OAuth 或 API Key，保证可用。
3. **分组**：在「分组管理」中创建分组，**计费类型选「标准 / 余额」等（非「订阅」）**，并绑定平台与账号。
4. **API Key**：在对应用户（例如管理员）下 **生成 `sk-` 开头的 Key** —— 本地环境变量填的是 **Sub2API 下发的 Key**，不是 Anthropic 官网控制台里的 Key（除非文档另有说明）。

### 「订阅分组」下拉为空时

若使用**完整模式**并要给用户配置「订阅」，需要先存在 **计费类型为「订阅」** 的分组（在「分组管理 → 创建分组」里选 **计费类型**，不是只看「分组类型」）。个人 Simple 模式一般 **不需要** 走订阅分组。

---

## 3. 本机 Mac：环境变量（Claude Code CLI）

在 **`~/.zshrc` 末尾**增加（将地址与 Key 换成自己的）：

```bash
export ANTHROPIC_BASE_URL="http://46.250.227.253:8080"
export ANTHROPIC_AUTH_TOKEN="sk-1b8a9a1db45131f31056444ebca0dcd0c659f31104d8ac79e973955b3b32f2f1"
```

保存后执行：

```bash
source ~/.zshrc
```

**注意：**

- 从 **同一终端** 启动 Claude Code CLI，或新开会自动加载 `~/.zshrc` 的终端，进程才能继承上述变量。
- 若 CLI 支持从项目/用户配置读取 `env`，也可写在官方文档指定的配置里，效果等价。

### Antigravity 专用路径（仅在使用 Antigravity 时）

官方示例（见仓库 README）：

```bash
export ANTHROPIC_BASE_URL="http://localhost:8080/antigravity"
export ANTHROPIC_AUTH_TOKEN="sk-xxx"
```

普通 Anthropic 兼容路由一般 **根地址即可**（如 `http://主机:8080`），客户端会自行拼接 `/v1/messages` 等路径；以你当前 Sub2API 版本文档为准。

---

## 4. 验证是否配置成功

| 步骤 | 命令或操作 | 预期 |
|------|------------|------|
| 变量已加载 | `env \| grep '^ANTHROPIC_'` | 两行均有值 |
| 网关存活 | `curl -sS -o /dev/null -w "%{http_code}\n" "http://你的服务器:8080/health"` | `200`（路径以实际版本为准） |
| 端到端 | 在已 `source` 的终端运行 Claude Code CLI，发一句简单对话 | 正常回复；若 401/403 检查 Key 与用户权限 |

检查变量时 **勿** 把完整 `sk-` 打印到公共日志；可用：

```bash
echo "ANTHROPIC_BASE_URL=$ANTHROPIC_BASE_URL"
echo "ANTHROPIC_AUTH_TOKEN=${ANTHROPIC_AUTH_TOKEN:+已设置(长度 ${#ANTHROPIC_AUTH_TOKEN})}"
```

---

## 5. 反向代理（Nginx）

若前面有 Nginx，官方要求在 `http` 块中启用（避免带下划线的请求头被丢弃，影响多账号/粘性会话）：

```nginx
underscores_in_headers on;
```

---

## 6. 安全提示

- **HTTP** 明文传输 API Key，公网建议 **HTTPS + 可信证书**。
- 勿将真实 Key 提交到 Git；`~/.zshrc` 含密钥时可限制权限：`chmod 600 ~/.zshrc`。
- 使用第三方中转可能涉及上游（如 Anthropic）服务条款，风险自担；见 Sub2API 仓库免责声明。

---

## 7. 官方链接

- 项目：<https://github.com/Wei-Shaw/sub2api>
- 官方说明仅认域名：`sub2api.org`、`pincc.ai`（见 README 声明）

---

## 8. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-03-31 | 初稿：Simple 模式、本机变量、验证步骤、订阅分组说明 |

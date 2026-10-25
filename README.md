# MedRAG · 医疗知识库智能问答助手

两周最小可演示版（RAG + Agent）。当前进度：**第 1 天（骨架 + 鉴权 + 直连问答）已完成**（已编译、已启动、已用 curl 验收）。

## 目录结构

```
medrag/
├── CLAUDE.md                    # 项目约定（AI 编码工具必读）
├── pom.xml                      # Spring Boot 3.4.6 + Spring AI 1.0.0
├── docker-compose.yml           # pgvector + redis（app 走 profile）
├── Dockerfile
├── .env.example
├── docs/
│   └── api-contract.md          # 9 个接口的入参/出参
└── src/main/
    ├── java/com/example/medrag/
    │   ├── MedragApplication.java
    │   ├── common/              Result / ErrorCode / BizException / GlobalExceptionHandler / PingController
    │   ├── config/              AiConfig / WebConfig / ApiKeyInterceptor
    │   └── chat/                ChatController（第 1 天：直连版，未接检索）
    └── resources/
        ├── application.yml
        ├── static/chat.html     # 演示页，访问 /chat.html
        └── db/schema.sql        # 6 张业务表 + mock 科室/医生
```

> `vector_store` 表由 Spring AI 的 PgVectorStore 自动创建，schema.sql 里不建。

## 快速开始

```bash
# 1. 起基础设施（pgvector + redis）
docker compose up -d

# 2. 配置密钥
cp .env.example .env
# 编辑 .env，填 DASHSCOPE_API_KEY（阿里云百炼控制台申请）

# 3. 启动（Windows PowerShell 用下面的写法加载 .env）
#   bash:  export $(grep -v '^#' .env | xargs) && mvn spring-boot:run
#   pwsh:  Get-Content .env | ForEach-Object { if ($_ -match '^([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2]) } }; mvn spring-boot:run
# 没有装 Maven 的话，直接用 IDEA / Trae 的 Maven 面板运行 MedragApplication
```

启动后：

- 健康检查：http://localhost:8080/actuator/health （`pgvector`、`redis` 应为 UP）
- 接口文档：http://localhost:8080/swagger-ui.html
- 请求头需带 `X-API-KEY: dev-key`

## 环境变量

| 变量 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `DASHSCOPE_API_KEY` | 是 | — | 通义千问 API Key |
| `APP_API_KEY` | 否 | `dev-key` | 本地接口鉴权 |
| `POSTGRES_PASSWORD` | 否 | `postgres` | compose 里 PG 的密码 |
| `SPRING_DATASOURCE_URL` | 否 | `jdbc:postgresql://localhost:5432/medrag` | 容器内改为 `pgvector:5432` |

## 第 1 天已交付

| 文件 | 作用 |
|---|---|
| `common/Result.java` | 统一响应体 `{code, message, data}` |
| `common/ErrorCode.java` | 0 成功 / 40001 参数 / 40100 未授权 / 50000 内部 / 50001 模型 |
| `common/BizException.java` | 业务异常，带错误码 |
| `common/GlobalExceptionHandler.java` | 业务异常、参数校验、JSON 解析、上传超限、兜底 |
| `common/PingController.java` | `GET /api/ping` 连通性探针 |
| `config/ApiKeyInterceptor.java` | 校验 `X-API-KEY`，常量时间比较，失败返回 401 |
| `config/WebConfig.java` | 把拦截器挂到 `/api/**` |
| `config/AiConfig.java` | 用 `ChatModel` 构建单例 `ChatClient`，system prompt 读配置 |
| `chat/ChatController.java` | `POST /api/chat` 直连模型（第 4/6/7 天再接检索、记忆、工具） |

### 验收命令（PowerShell / bash 通用，已实测）

```bash
# 1) 带 key → 200 + code 0
curl -H "X-API-KEY: dev-key" localhost:8080/api/ping

# 2) 不带 key → 401 + code 40100
curl localhost:8080/api/ping

# 3) 空问题 → 200 + code 40001，message "question 不能为空"
#    注意：PowerShell 直接把 JSON 写在 -d 里会把双引号吃掉，用文件传
curl -H "X-API-KEY: dev-key" -H "Content-Type: application/json" --data-binary @blank.json localhost:8080/api/chat

# 4) 正常提问 → code 0，data.answer 为模型回复（需要真实 DASHSCOPE_API_KEY）
# 5) 浏览器打开 http://localhost:8080/chat.html 直接对话
```

> 实测结果（2026-10-24，dummy key）：`ping` 200/code 0、无 key 401/40100、空问题 40001、假 key 提问 50001、`/chat.html` 200。
> 换真实 key 后第 4 条即返回正常回答。

## 第 2 天要做的事

1. `kb_document` 的实体与 `KbDocumentRepository`（JdbcTemplate + 参数占位符）
2. `GET /api/kb/documents` 列表接口
3. 文件上传接口骨架（先只落盘 + 算 sha256，不解析）
4. 验收：传一个 PDF，`kb_document` 里出现一行，`sha256` 不为空

## 常见坑

| 现象 | 原因 | 处理 |
|---|---|---|
| 启动报 `Connection refused` / `Failed to obtain JDBC Connection` | PG 没起 | 先 `docker compose up -d` |
| 启动报 `permission denied to create extension "vector"` | 连接用户不是超级用户 | 本项目 compose 用 `postgres` 超管；生产环境请单独执行建扩展脚本 |
| `expected 1536 dimensions, not 1024` | embedding 维度不一致 | 确认 `spring.ai.openai.embedding.options.dimensions=1024` 且 pgvector `dimensions=1024` |
| 找不到 `ChatClient` / `@Tool` | Spring AI 版本不对 | 必须是 1.0.0，别看 0.8 教程 |
| DashScope 返回 401 | key 没加载进环境变量 | 用 README 里的 `.env` 加载命令 |
| PowerShell 里 `-d '{"question":"x"}'` 报 40001 | PowerShell 吃掉了 JSON 里的双引号 | 用 `--data-binary @body.json` |
| 上传中文文件名乱码 | multipart 编码 | 已在 `application.yml` 配 UTF-8；仍有问题就前端 `encodeURIComponent` |

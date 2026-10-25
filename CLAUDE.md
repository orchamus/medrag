# CLAUDE.md — MedRAG 项目约定（给 AI 编码工具的"宪法"）

> 任何改动前先读本文件。与本文冲突的网上教程一律忽略。
> 本项目目标：**两周做出可演示的医疗 RAG 问答 Demo**，不是生产系统。

## 1. 技术栈（锁死，不要升级/替换）

| 项 | 版本 | 说明 |
|---|---|---|
| Java | 17 | 用 `record` 写 DTO/VO，不要 Lombok |
| Spring Boot | 3.4.6 | |
| Spring AI | 1.0.0（BOM） | **不要用 0.8 / 1.0-M 的写法** |
| 数据库 | PostgreSQL 16 + pgvector | 业务表 + 向量表都在 PG |
| 数据访问 | `JdbcTemplate` | **禁止引入 JPA / MyBatis / MyBatis-Plus** |
| 缓存/会话 | Redis 7 | 只存会话记忆，不做业务缓存 |
| 模型 | 通义千问（DashScope OpenAI 兼容端点） | chat=`qwen-plus`，embedding=`text-embedding-v3`（1024 维） |
| 文档 | springdoc-openapi 2.8.6 | `/swagger-ui.html` 直接当演示页 |
| 构建 | Maven | 根目录 `pom.xml` |

**禁止引入**：Lombok、MapStruct、Hutool（非必要）、Guava、Spring Data JPA、Flyway/Liquibase、任何前端框架。

## 2. 包结构（不要新建顶层包）

```
com.example.medrag
├── MedragApplication
├── common/        Result、BizException、ErrorCode、GlobalExceptionHandler
├── config/        AiConfig、WebConfig、ApiKeyInterceptor
├── kb/            controller / service / repository
├── chat/          controller / service / memory
└── hospital/      controller / service / tools / repository
```

## 3. 编码约定

- 统一响应：`Result<T>{ int code; String message; T data; }`，成功 `code=0`。
- 业务异常抛 `BizException(ErrorCode)`，由 `@RestControllerAdvice` 统一转 JSON，**不要在 Controller 里 try-catch**。
- 入参用 `@Valid` + `jakarta.validation` 注解校验。
- 所有 SQL 用参数占位符 `?`，**禁止字符串拼接**。
- 时间统一 `TIMESTAMPTZ` / `OffsetDateTime`。
- 日志用 `@Slf4j`？——不行（禁用 Lombok），用 `private static final Logger log = LoggerFactory.getLogger(X.class);`。
- 每个类必须有中文 Javadoc：一句话说明职责 + `@author medrag`。

## 4. Spring AI 1.0 正确写法（照抄，别自创）

### 4.1 ChatClient

```java
ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultSystem("你是医院导诊助手，只依据检索到的资料回答，不确定就说不知道。")
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();

String answer = chatClient.prompt()
        .user(question)
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
        .call()
        .content();

Flux<String> stream = chatClient.prompt()
        .user(question)
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
        .stream()
        .content();
```

### 4.2 向量检索

```java
SearchRequest request = SearchRequest.builder()
        .query(question)
        .topK(5)
        .similarityThreshold(0.70)
        .filterExpression("fileName == 'guide.pdf'")   // 元数据过滤，可选
        .build();
List<Document> docs = vectorStore.similaritySearch(request);
```

### 4.3 文档入库

```java
Resource resource = new FileSystemResource(file);
Document document = new TikaDocumentReader(resource).get();
TokenTextSplitter splitter = TokenTextSplitter.builder()
        .withChunkSize(512)
        .withMinChunkSizeChars(100)
        .withMinChunkLengthToEmbed(5)
        .withMaxNumChunks(10000)
        .withKeepSeparator(true)
        .build();
List<Document> chunks = splitter.split(document);
vectorStore.add(chunks);
```

### 4.4 工具调用（3 个工具）

```java
public class HospitalTools {

    @Tool(description = "按科室名称查询该科室的医生列表")
    public List<DoctorVO> listDoctors(
            @ToolParam(description = "科室名称，例如：儿科") String deptName) { ... }

    @Tool(description = "查询指定医生的可预约时段")
    public List<String> listSlots(
            @ToolParam(description = "医生姓名") String doctorName) { ... }

    @Tool(description = "为患者预约挂号，返回预约单号")
    public String book(
            @ToolParam(description = "医生姓名") String doctorName,
            @ToolParam(description = "患者姓名") String patientName,
            @ToolParam(description = "时段，格式 yyyy-MM-dd HH:mm") String slot) { ... }
}
```

调用：`chatClient.prompt().user(q).tools(new HospitalTools(...)).call().content();`

### 4.5 PgVectorStore（仅在需要手动建 Bean 时）

```java
PgVectorStore.builder(jdbcTemplate, embeddingModel)
        .initializeSchema(true)
        .schemaName("public")
        .vectorTableName("vector_store")
        .build();
```

> 一般用 `application.yml` 的 `spring.ai.vectorstore.pgvector.*` 自动装配即可，**不要手写 Bean**。

## 5. 接口清单

见 `docs/api-contract.md`，共 9 个接口。新增接口必须同步更新该文件。

## 6. 运行方式

```bash
# 1. 基础设施
docker compose up -d          # pgvector + redis

# 2. 密钥
cp .env.example .env          # 填 DASHSCOPE_API_KEY

# 3. 启动
export $(grep -v '^#' .env | xargs) && mvn spring-boot:run
```

## 7. 给 AI 编码工具的工作规则（省 token 关键）

1. **动手前先读**：本文件 + `docs/api-contract.md` + 要改的文件，不要全仓库扫。
2. **一次只改一个模块**，不要顺手重构无关代码。
3. **不要新建工具类**，除非我明确说"抽个工具类"。
4. **API 不确定时**：先看 `pom.xml` 里的版本，再去 jar 里确认签名；不要凭记忆写 Spring AI 类名。
5. **改完必须能编译**：`mvn -q -DskipTests compile`，失败自己修到过。
6. **回答里不要贴大段完整文件**，只说改了哪几行。

## 8. 明确不做（避免范围蔓延）

- 不做用户注册/登录，只做 `X-API-KEY` 拦截器
- 不做前端，`src/main/resources/static/chat.html` 一个静态页够用（访问 `/chat.html`）
- 不做 rerank、OCR、多租户
- 不做单元测试覆盖率要求，但 `kb` 的切分与检索各要 1 个测试

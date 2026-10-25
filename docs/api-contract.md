# MedRAG 接口契约（v1）

> 所有接口前缀 `/api`。除 `/actuator/**` 与 `/swagger-ui.html` 外，均需请求头 `X-API-KEY: ${APP_API_KEY}`。
> 统一响应体：

```json
{ "code": 0, "message": "ok", "data": {} }
```

错误码：`0` 成功；`40001` 参数错误；`40100` 未授权；`40400` 资源不存在；`50000` 服务内部错误；`50001` 模型调用失败。

---

## 1. 上传文档

`POST /api/kb/documents`　`multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| file | file | 是 | pdf / docx，≤ 50MB |

响应：

```json
{ "code": 0, "message": "ok", "data": { "id": 12, "name": "儿科就诊指南.pdf", "chunkCount": 86, "duplicated": false } }
```

> 同一文件（sha256 相同）重复上传返回 `duplicated: true` 且不重复入库。

## 2. 文档列表

`GET /api/kb/documents`

```json
{ "code": 0, "message": "ok", "data": [ { "id": 12, "name": "儿科就诊指南.pdf", "sizeBytes": 340112, "chunkCount": 86, "createdAt": "2026-10-20T10:12:00Z" } ] }
```

## 3. 删除文档

`DELETE /api/kb/documents/{id}`

```json
{ "code": 0, "message": "ok", "data": true }
```

> 需级联删除 `vector_store` 中 `metadata->>'documentId' = id` 的向量。

## 4. 问答（非流式）

`POST /api/chat`　`application/json`

```json
{ "conversationId": "3f2b6c1e-...", "question": "小孩发烧 38.5 度要挂哪个科？" }
```

`conversationId` 可为 `null`，此时新建会话并在响应里返回。

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "conversationId": "3f2b6c1e-...",
    "answer": "建议挂儿科[1]。38.5℃ 以下可先物理降温……",
    "citations": [
      { "index": 1, "fileName": "儿科就诊指南.pdf", "page": 3, "snippet": "儿童发热……" }
    ]
  }
}
```

## 5. 问答（流式）

`POST /api/chat/stream`　`text/event-stream`

请求体同上。事件格式：

```
event: citation
data: {"index":1,"fileName":"儿科就诊指南.pdf","page":3}

event: message
data: 建议挂儿科[1]

event: done
data: [DONE]
```

## 6. 会话历史

`GET /api/chat/conversations/{conversationId}/messages`

```json
{ "code": 0, "message": "ok", "data": [ { "role": "user", "content": "…", "createdAt": "…" }, { "role": "assistant", "content": "…", "createdAt": "…" } ] }
```

## 7. 科室列表

`GET /api/hospital/departments`

```json
{ "code": 0, "message": "ok", "data": [ { "id": 1, "name": "儿科", "description": "0-14 岁儿童常见病" } ] }
```

## 8. 医生列表

`GET /api/hospital/doctors?deptId=1`

```json
{ "code": 0, "message": "ok", "data": [ { "id": 1, "name": "李静", "title": "主任医师", "deptName": "儿科", "slots": ["MON 09:00"] } ] }
```

## 9. 预约挂号

`POST /api/hospital/appointments`

```json
{ "doctorId": 1, "patientName": "张三", "slotTime": "2026-10-22 09:00", "idempotencyKey": "uuid-xxx" }
```

响应：

```json
{ "code": 0, "message": "ok", "data": { "appointmentId": 501, "status": "BOOKED" } }
```

> 相同 `idempotencyKey` 重复提交返回同一 `appointmentId`，不重复建单。

---

## 工具与接口的关系

`/api/hospital/**` 是给前端和调试用的 HTTP 接口；`HospitalTools` 里的 3 个 `@Tool` 方法复用同一套 Service，**不要复制两份 SQL**。

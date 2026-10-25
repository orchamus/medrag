-- ============================================================
-- MedRAG 业务表
-- 注意：vector_store 表由 Spring AI 的 PgVectorStore 自动创建，
--       这里不要重复建，否则 initialize-schema=true 可能冲突。
-- 本脚本可重复执行（IF NOT EXISTS / ON CONFLICT）。
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- 知识库文档
CREATE TABLE IF NOT EXISTS kb_document (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    sha256      CHAR(64)     NOT NULL UNIQUE,
    size_bytes  BIGINT       NOT NULL DEFAULT 0,
    chunk_count INT          NOT NULL DEFAULT 0,
    status      VARCHAR(16)  NOT NULL DEFAULT 'READY',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 会话
CREATE TABLE IF NOT EXISTS conversation (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title      VARCHAR(255) NOT NULL DEFAULT '新会话',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 会话消息（如果改用 Redis 存记忆，这张表可只做归档）
CREATE TABLE IF NOT EXISTS chat_message (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_message_conv ON chat_message (conversation_id, id);

-- 科室
CREATE TABLE IF NOT EXISTS dept (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(64) NOT NULL UNIQUE,
    description TEXT
);

-- 医生（schedule 用 JSONB 存可约时段，MVP 不做独立排班表）
CREATE TABLE IF NOT EXISTS doctor (
    id      SERIAL PRIMARY KEY,
    dept_id INT NOT NULL REFERENCES dept(id),
    name    VARCHAR(64) NOT NULL,
    title   VARCHAR(64) NOT NULL,
    schedule JSONB NOT NULL DEFAULT '[]'::jsonb,
    UNIQUE (dept_id, name)
);

-- 预约挂号（idempotency_key 保证重复提交不重复建单）
CREATE TABLE IF NOT EXISTS appointment (
    id              BIGSERIAL PRIMARY KEY,
    doctor_id       INT NOT NULL REFERENCES doctor(id),
    patient_name    VARCHAR(64) NOT NULL,
    slot_time       TIMESTAMPTZ NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'BOOKED',
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_appointment_doctor ON appointment (doctor_id, slot_time);

-- ---------------- mock 数据 ----------------
INSERT INTO dept (name, description) VALUES
    ('儿科', '0-14 岁儿童常见病、生长发育'),
    ('内科', '呼吸、消化、心血管等内科疾病'),
    ('皮肤科', '皮炎、湿疹、痤疮等皮肤问题')
ON CONFLICT (name) DO NOTHING;

INSERT INTO doctor (dept_id, name, title, schedule)
SELECT d.id, v.name, v.title, v.schedule::jsonb
FROM (VALUES
    ('儿科',   '李静',   '主任医师', '["MON 09:00","MON 14:00","WED 09:00"]'),
    ('儿科',   '王磊',   '副主任医师', '["TUE 09:00","THU 14:00"]'),
    ('内科',   '陈曦',   '主任医师', '["MON 10:00","FRI 09:00"]'),
    ('内科',   '赵敏',   '主治医师', '["TUE 14:00","WED 10:00"]'),
    ('皮肤科', '周涛',   '副主任医师', '["THU 09:00","FRI 14:00"]'),
    ('皮肤科', '孙悦',   '主治医师', '["MON 15:00","SAT 09:00"]')
) AS v(dept_name, name, title, schedule)
JOIN dept d ON d.name = v.dept_name
ON CONFLICT (dept_id, name) DO NOTHING;

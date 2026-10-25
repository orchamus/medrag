package com.example.medrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MedRAG 启动类。
 *
 * <p>医疗知识库智能问答助手：文档入库 → 向量检索 → 带引用的多轮问答 + 工具调用。</p>
 */
@SpringBootApplication
public class MedragApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedragApplication.class, args);
    }
}

package com.example.medrag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置。
 *
 * <p>基于自动装配的 {@link ChatModel}（OpenAI 兼容协议 → 通义千问）构建单例 {@link ChatClient}，
 * 系统提示词来自 {@code app.ai.system-prompt}，方便不改代码调 prompt。</p>
 *
 * @author medrag
 */
@Configuration
public class AiConfig {

    /**
     * 全局 ChatClient。后续的检索、记忆、工具都在调用处按需组合。
     *
     * @param chatModel    自动装配的聊天模型
     * @param systemPrompt 系统提示词
     * @return ChatClient 单例
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel,
                                 @Value("${app.ai.system-prompt}") String systemPrompt) {
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .build();
    }
}

package com.example.medrag.chat;

import com.example.medrag.common.BizException;
import com.example.medrag.common.ErrorCode;
import com.example.medrag.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 问答接口（第 1 天：直连模型版本）。
 *
 * <p>先不接检索与记忆，只验证「鉴权 → 参数校验 → 模型调用 → 统一响应」这条链路。
 * 第 4 天会在这里接 {@code VectorStore}，第 6 天接会话记忆，第 7 天接工具。</p>
 *
 * @author medrag
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 单轮问答。
     *
     * @param request 问题与会话 ID
     * @return 回答；未传 conversationId 时返回新建的会话 ID
     */
    @PostMapping
    public Result<ChatResponseDto> chat(@Valid @RequestBody ChatRequest request) {
        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? UUID.randomUUID().toString()
                : request.conversationId();

        String answer;
        try {
            answer = chatClient.prompt()
                    .user(request.question())
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("模型调用失败, conversationId={}", conversationId, e);
            throw new BizException(ErrorCode.AI_ERROR, "模型调用失败，请稍后重试");
        }

        return Result.ok(new ChatResponseDto(conversationId, answer, List.of()));
    }

    /**
     * 问答请求体。
     *
     * @param conversationId 会话 ID，可为空
     * @param question       用户问题
     */
    public record ChatRequest(
            @Size(max = 64, message = "长度不能超过 64") String conversationId,
            @NotBlank(message = "不能为空") @Size(max = 2000, message = "长度不能超过 2000") String question) {
    }

    /**
     * 问答响应体。
     *
     * @param conversationId 会话 ID
     * @param answer         模型回答
     * @param citations      引用片段，第 4 天接入检索后填充
     */
    public record ChatResponseDto(String conversationId, String answer, List<Citation> citations) {
    }

    /**
     * 引用片段。
     *
     * @param index    引用序号，对应回答里的 [1][2]
     * @param fileName 文档名
     * @param page     页码，可能为空
     * @param snippet  原文片段
     */
    public record Citation(int index, String fileName, Integer page, String snippet) {
    }
}

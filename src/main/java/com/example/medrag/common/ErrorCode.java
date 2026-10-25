package com.example.medrag.common;

/**
 * 业务错误码。
 *
 * <p>code 为 0 表示成功，其余为错误；HTTP 层统一返回 200（鉴权失败除外），由 code 区分。</p>
 *
 * @author medrag
 */
public enum ErrorCode {

    OK(0, "ok"),
    PARAM_ERROR(40001, "参数错误"),
    UNAUTHORIZED(40100, "未授权"),
    NOT_FOUND(40400, "资源不存在"),
    INTERNAL_ERROR(50000, "服务内部错误"),
    AI_ERROR(50001, "模型调用失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

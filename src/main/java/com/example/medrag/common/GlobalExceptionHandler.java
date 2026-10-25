package com.example.medrag.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>把业务异常、参数校验失败、请求体解析失败、上传超限统一转成 {@link Result}，
 * 避免异常堆栈直接暴露给前端。</p>
 *
 * @author medrag
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常：按异常携带的错误码返回，HTTP 仍为 200。
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.fail(e.getErrorCode(), e.getMessage());
    }

    /**
     * 参数校验失败：汇总所有字段错误，用分号连接。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> fieldMessage(f))
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败: {}", detail);
        return Result.fail(ErrorCode.PARAM_ERROR, detail.isEmpty() ? ErrorCode.PARAM_ERROR.getMessage() : detail);
    }

    /**
     * 请求体不是合法 JSON，或缺少必填的 body。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR, "请求体格式错误，应为合法 JSON");
    }

    /**
     * 上传文件超过 50MB。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超限: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR, "文件过大，单个文件不能超过 50MB");
    }

    /**
     * 兜底：未预期异常，记录堆栈但只返回通用提示。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleOther(Exception e) {
        log.error("未预期异常", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR, "服务内部错误，请稍后重试");
    }

    private String fieldMessage(FieldError f) {
        return f.getField() + " " + (f.getDefaultMessage() == null ? "参数不合法" : f.getDefaultMessage());
    }
}

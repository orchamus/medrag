package com.example.medrag.config;

import com.example.medrag.common.ErrorCode;
import com.example.medrag.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 接口鉴权拦截器。
 *
 * <p>校验请求头 {@code X-API-KEY} 是否与配置一致；使用常量时间比较，避免时序侧信道。
 * 不做登录态与角色，Demo 够用。</p>
 *
 * @author medrag
 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyInterceptor.class);

    private static final String HEADER_NAME = "X-API-KEY";

    private final byte[] expectedKey;
    private final ObjectMapper objectMapper;

    public ApiKeyInterceptor(@Value("${app.api-key}") String expectedKey, ObjectMapper objectMapper) {
        this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求直接放行，避免浏览器跨域探测被拦
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String actual = request.getHeader(HEADER_NAME);
        if (actual != null && MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expectedKey)) {
            return true;
        }

        log.warn("鉴权失败: uri={}, remote={}", request.getRequestURI(), request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                Result.fail(ErrorCode.UNAUTHORIZED, "缺少或错误的请求头 X-API-KEY"));
        return false;
    }
}

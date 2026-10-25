package com.example.medrag.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 连通性探针。
 *
 * <p>用于验证三件事：服务起得来、{@code X-API-KEY} 拦截器生效、统一响应体格式正确。</p>
 *
 * @author medrag
 */
@RestController
@RequestMapping("/api/ping")
public class PingController {

    /**
     * 返回服务状态与当前时间。
     *
     * @return 统一响应体
     */
    @GetMapping
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("app", "medrag");
        data.put("time", OffsetDateTime.now().toString());
        return Result.ok(data);
    }
}

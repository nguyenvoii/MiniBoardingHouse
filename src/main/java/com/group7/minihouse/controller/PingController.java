package com.group7.minihouse.controller;

import com.group7.minihouse.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint kiem tra app song: GET /api/ping. Dung lam mau cho controller cua cac thanh vien. */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }
}

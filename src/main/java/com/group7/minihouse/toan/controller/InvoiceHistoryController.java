package com.group7.minihouse.toan.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.toan.dto.InvoiceHistoryDtos.HistoryItem;
import com.group7.minihouse.toan.service.InvoiceHistoryService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC128: API tra cứu lịch sử thao tác trên hóa đơn và giao dịch. */
@RestController
@RequestMapping("/api/invoice-history")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
public class InvoiceHistoryController {
    private final InvoiceHistoryService service;
    public InvoiceHistoryController(InvoiceHistoryService service) { this.service = service; }

    /** UC128: lọc lịch sử theo khoảng thời gian, loại thao tác và mã hóa đơn/giao dịch. */
    @GetMapping
    public ApiResponse<List<HistoryItem>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(from, to, actionType, keyword, page, size));
    }
}

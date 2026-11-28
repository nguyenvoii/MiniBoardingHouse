package com.group7.minihouse.viet.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.viet.dto.ExpenseDtos.ExpenseRequest;
import com.group7.minihouse.viet.dto.ExpenseDtos.ExpenseResponse;
import com.group7.minihouse.viet.dto.ExpenseDtos.ExpenseSummary;
import com.group7.minihouse.viet.service.OperatingExpenseService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC117: chi phi van hanh khu tro. Chi LANDLORD, MANAGER. */
@RestController
@RequestMapping("/api/expenses")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class ExpenseController {

    private final OperatingExpenseService service;

    public ExpenseController(OperatingExpenseService service) {
        this.service = service;
    }

    /** Tim kiem co phan trang: category, from, to (yyyy-MM-dd). */
    @GetMapping
    public ApiResponse<PageResponse<ExpenseResponse>> search(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(category, from, to, page, size));
    }

    /** Tong chi phi theo nhom trong khoang ngay. */
    @GetMapping("/summary")
    public ApiResponse<ExpenseSummary> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.summary(from, to));
    }

    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.ok(service.categories());
    }

    @GetMapping("/{id}")
    public ApiResponse<ExpenseResponse> get(@PathVariable Integer id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<ExpenseResponse> create(@RequestBody ExpenseRequest req,
                                               @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.ok(service.create(req, actor == null ? null : actor.userId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExpenseResponse> update(@PathVariable Integer id, @RequestBody ExpenseRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Đã xóa khoản chi phí", null);
    }
}

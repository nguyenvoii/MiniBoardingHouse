package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.BillingDtos.Anomaly;
import com.group7.minihouse.phi.dto.BillingDtos.CalculationResult;
import com.group7.minihouse.phi.dto.BillingDtos.CreatePeriodRequest;
import com.group7.minihouse.phi.dto.BillingDtos.FeeResponse;
import com.group7.minihouse.phi.dto.BillingDtos.Issue;
import com.group7.minihouse.phi.dto.BillingDtos.PeriodCreated;
import com.group7.minihouse.phi.dto.BillingDtos.PeriodResponse;
import com.group7.minihouse.phi.dto.BillingDtos.UpdateItemRequest;
import com.group7.minihouse.phi.service.BillingService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC66-75: tinh tien hang thang. Chi nhan vien (LANDLORD, MANAGER). */
@RestController
@RequestMapping("/api/billing")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class BillingController {

    private final BillingService service;

    public BillingController(BillingService service) {
        this.service = service;
    }

    @PostMapping("/periods")
    public ApiResponse<PeriodCreated> create(@RequestBody CreatePeriodRequest req,
                                             @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.createPeriod(req, actor(user), http.getRemoteAddr()));
    }

    @GetMapping("/periods")
    public ApiResponse<List<PeriodResponse>> list(@RequestParam Integer propertyId) {
        return ApiResponse.ok(service.listPeriods(propertyId));
    }

    @GetMapping("/periods/{id}")
    public ApiResponse<CalculationResult> get(@PathVariable Integer id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/periods/{id}/validate")
    public ApiResponse<List<Issue>> validate(@PathVariable Integer id) {
        return ApiResponse.ok(service.validate(id));
    }

    @PostMapping("/periods/{id}/calculate")
    public ApiResponse<CalculationResult> calculate(@PathVariable Integer id,
                                                    @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.calculate(id, actor(user), http.getRemoteAddr()));
    }

    @GetMapping("/periods/{id}/anomalies")
    public ApiResponse<List<Anomaly>> anomalies(@PathVariable Integer id) {
        return ApiResponse.ok(service.anomalies(id));
    }

    @PostMapping("/periods/{id}/approve")
    public ApiResponse<PeriodResponse> approve(@PathVariable Integer id, @AuthenticationPrincipal AuthUser user,
                                               HttpServletRequest http) {
        return ApiResponse.ok(service.approve(id, actor(user), http.getRemoteAddr()));
    }

    @GetMapping("/fees/{feeId}")
    public ApiResponse<FeeResponse> fee(@PathVariable Integer feeId) {
        return ApiResponse.ok(service.getFee(feeId));
    }

    @PutMapping("/fee-items/{detailId}")
    public ApiResponse<FeeResponse> updateItem(@PathVariable Integer detailId, @RequestBody UpdateItemRequest req,
                                               @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.updateItem(detailId, req, actor(user), http.getRemoteAddr()));
    }

    /** Tat bao mat (dev) thi khong co nguoi dung: tra null, service bo qua nhat ky. */
    private static Integer actor(AuthUser user) {
        return user == null ? null : user.userId();
    }
}

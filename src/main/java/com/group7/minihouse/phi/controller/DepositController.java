package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.service.DepositService;
import com.group7.minihouse.phi.service.DepositService.DepositStatus;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thu và xem tiền cọc của hợp đồng. */
@RestController
@RequestMapping("/api/contracts/{contractId}/deposit")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
public class DepositController {

    public record CollectDepositRequest(BigDecimal amount) {
    }

    private final DepositService service;

    public DepositController(DepositService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<DepositStatus> status(@PathVariable Integer contractId) {
        return ApiResponse.ok(service.status(contractId));
    }

    @PostMapping
    public ApiResponse<DepositStatus> collect(@PathVariable Integer contractId, @RequestBody CollectDepositRequest req,
                                              @AuthenticationPrincipal AuthUser actor, HttpServletRequest http) {
        return ApiResponse.ok("Da ghi nhan thu coc",
                service.collect(contractId, req == null ? null : req.amount(), actor == null ? null : actor.userId(),
                        http.getRemoteAddr()));
    }
}

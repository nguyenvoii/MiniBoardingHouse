package com.group7.minihouse.viet.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import com.group7.minihouse.viet.dto.CheckoutDtos.CheckoutResponse;
import com.group7.minihouse.viet.dto.CheckoutDtos.CreateCheckoutRequest;
import com.group7.minihouse.viet.dto.DebtDtos.DebtResponse;
import com.group7.minihouse.viet.dto.DebtDtos.InvoiceHistoryItem;
import com.group7.minihouse.viet.dto.EquipmentDtos.CreateEquipmentRequest;
import com.group7.minihouse.viet.dto.EquipmentDtos.EquipmentResponse;
import com.group7.minihouse.viet.dto.MaintenanceDtos.CreateMaintenanceRequest;
import com.group7.minihouse.viet.dto.MaintenanceDtos.MaintenanceResponse;
import com.group7.minihouse.viet.dto.MaintenanceDtos.RatingRequest;
import com.group7.minihouse.viet.service.CheckoutService;
import com.group7.minihouse.viet.service.DebtService;
import com.group7.minihouse.viet.service.EquipmentService;
import com.group7.minihouse.viet.service.MaintenanceService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API cua NGUOI THUE (duoi /api/me/**, chi role TENANT - da chan o SecurityConfig). Ma khach thue luon lay tu token,
 * khong nhan tu client. Luu y: khi app.security.enabled=false khong co token nen cac API nay tra 401 - test bang token TENANT.
 */
@RestController
@RequestMapping("/api/me")
public class TenantSelfServiceController {

    private final DebtService debtService;
    private final CheckoutService checkoutService;
    private final MaintenanceService maintenanceService;
    private final EquipmentService equipmentService;

    public TenantSelfServiceController(DebtService debtService, CheckoutService checkoutService,
                                       MaintenanceService maintenanceService, EquipmentService equipmentService) {
        this.debtService = debtService;
        this.checkoutService = checkoutService;
        this.maintenanceService = maintenanceService;
        this.equipmentService = equipmentService;
    }

    /** UC94: cong no cua toi. */
    @GetMapping("/debts")
    public ApiResponse<List<DebtResponse>> debts(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(debtService.myDebts(CurrentUser.requireTenantId(user)));
    }

    /** UC94: lich su hoa don / thanh toan cua toi. */
    @GetMapping("/debts/invoices")
    public ApiResponse<List<InvoiceHistoryItem>> invoiceHistory(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(debtService.myInvoiceHistory(CurrentUser.requireTenantId(user)));
    }

    /** UC101: gui yeu cau tra phong (contractId co the bo trong neu chi co 1 hop dong). */
    @PostMapping("/checkout-requests")
    public ApiResponse<CheckoutResponse> createCheckout(@RequestBody CreateCheckoutRequest req,
                                                        @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(checkoutService.create(req, CurrentUser.requireTenantId(user)));
    }

    @GetMapping("/checkout-requests")
    public ApiResponse<List<CheckoutResponse>> myCheckouts(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(checkoutService.mine(CurrentUser.requireTenantId(user)));
    }

    /** UC110-112: gui su co / yeu cau sua chua (kem URL anh). */
    @PostMapping("/maintenance-requests")
    public ApiResponse<MaintenanceResponse> createMaintenance(@RequestBody CreateMaintenanceRequest req,
                                                              @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(maintenanceService.create(req, CurrentUser.requireTenantId(user)));
    }

    @GetMapping("/maintenance-requests")
    public ApiResponse<List<MaintenanceResponse>> myMaintenance(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(maintenanceService.mine(CurrentUser.requireTenantId(user)));
    }

    /** UC115: danh gia chat luong xu ly {"rating": 1..5}. */
    @PostMapping("/maintenance-requests/{id}/rating")
    public ApiResponse<MaintenanceResponse> rate(@PathVariable Integer id, @RequestBody RatingRequest req,
                                                 @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(maintenanceService.rate(id, CurrentUser.requireTenantId(user), req));
    }

    @PostMapping("/equipment-requests")
    public ApiResponse<EquipmentResponse> createEquipment(@RequestBody CreateEquipmentRequest req,
                                                          @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(equipmentService.create(req, CurrentUser.requireTenantId(user)));
    }

    @GetMapping("/equipment-requests")
    public ApiResponse<List<EquipmentResponse>> myEquipment(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(equipmentService.mine(CurrentUser.requireTenantId(user)));
    }
}

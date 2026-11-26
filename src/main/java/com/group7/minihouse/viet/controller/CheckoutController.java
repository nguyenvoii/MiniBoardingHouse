package com.group7.minihouse.viet.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.storage.FileStorage;
import com.group7.minihouse.viet.dto.CheckoutDtos.CheckoutResponse;
import com.group7.minihouse.viet.dto.CheckoutDtos.ConfirmScheduleRequest;
import com.group7.minihouse.viet.dto.CheckoutDtos.CreateCheckoutRequest;
import com.group7.minihouse.viet.dto.CheckoutDtos.HandoverReport;
import com.group7.minihouse.viet.dto.CheckoutDtos.InspectionRequest;
import com.group7.minihouse.viet.dto.CheckoutDtos.InspectionResponse;
import com.group7.minihouse.viet.dto.CheckoutDtos.RefundVoucherResponse;
import com.group7.minihouse.viet.dto.CheckoutDtos.SettlementResponse;
import com.group7.minihouse.viet.service.CheckoutService;
import java.io.IOException;
import org.springframework.core.io.PathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** UC101-107: tra phong, quyet toan, hoan coc (phia nhan vien). Chi LANDLORD, MANAGER. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class CheckoutController {

    private static final String PHOTO_FOLDER = "checkout-photos";
    private static final String PHOTO_ROUTE = "/checkout-inspection-photos";

    private final CheckoutService service;
    private final FileStorage storage;

    public CheckoutController(CheckoutService service, FileStorage storage) {
        this.service = service;
        this.storage = storage;
    }

    /** Nhan vien tao ho yeu cau tra phong (contractId bat buoc). */
    @PostMapping("/checkout-requests")
    public ApiResponse<CheckoutResponse> create(@RequestBody CreateCheckoutRequest req) {
        return ApiResponse.ok(service.create(req, null));
    }

    @GetMapping("/checkout-requests")
    public ApiResponse<PageResponse<CheckoutResponse>> search(@RequestParam(required = false) String status,
                                                              @RequestParam(required = false) Integer contractId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(status, contractId, page, size));
    }

    @GetMapping("/checkout-requests/{id}")
    public ApiResponse<CheckoutResponse> get(@PathVariable Integer id) {
        return ApiResponse.ok(service.get(id));
    }

    /** UC101: xac nhan lich kiem tra phong (PENDING -> INSPECTING). */
    @PatchMapping("/checkout-requests/{id}/confirm")
    public ApiResponse<CheckoutResponse> confirm(@PathVariable Integer id,
                                                 @RequestBody(required = false) ConfirmScheduleRequest req) {
        return ApiResponse.ok(service.confirmSchedule(id, req));
    }

    @PatchMapping("/checkout-requests/{id}/cancel")
    public ApiResponse<CheckoutResponse> cancel(@PathVariable Integer id) {
        return ApiResponse.ok(service.cancel(id));
    }

    /** UC102, UC103: ghi nhan hien trang phong, thiet bi hu hong va tien boi thuong. */
    @PutMapping(value = "/checkout-requests/{id}/inspection", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<InspectionResponse> inspect(@PathVariable Integer id, @RequestBody InspectionRequest req,
                                                   @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.ok(service.recordInspection(id, req, actor == null ? null : actor.userId()));
    }

    @PutMapping(value = "/checkout-requests/{id}/inspection", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<InspectionResponse> inspectWithPhoto(@PathVariable Integer id,
            @RequestPart("request") InspectionRequest req, @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthUser actor) throws IOException {
        String name = storage.saveImage(PHOTO_FOLDER, file.getBytes(), 5L * 1024 * 1024);
        try {
            InspectionRequest withPhoto = new InspectionRequest(req.finalElectricMeter(), req.finalWaterMeter(),
                    req.damages(), "/api" + PHOTO_ROUTE + "/" + name);
            return inspect(id, withPhoto, actor);
        } catch (RuntimeException failure) {
            try {
                storage.delete(PHOTO_FOLDER, name);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @GetMapping(PHOTO_ROUTE + "/{name}")
    public ResponseEntity<PathResource> photo(@PathVariable String name) {
        return storage.find(PHOTO_FOLDER, name)
                .map(path -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(FileStorage.contentTypeOf(name)))
                        .cacheControl(CacheControl.noStore())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(new PathResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/checkout-requests/{id}/inspection")
    public ApiResponse<InspectionResponse> getInspection(@PathVariable Integer id) {
        return ApiResponse.ok(service.getInspection(id));
    }

    /** UC104, UC105: quyet toan - cong no va boi thuong khau tru vao tien coc. */
    @GetMapping("/checkout-requests/{id}/settlement")
    public ApiResponse<SettlementResponse> settlement(@PathVariable Integer id) {
        return ApiResponse.ok(service.settlement(id));
    }

    /** UC106: lap phieu hoan coc (PENDING). */
    @PostMapping("/checkout-requests/{id}/refund-voucher")
    public ApiResponse<RefundVoucherResponse> createVoucher(@PathVariable Integer id) {
        return ApiResponse.ok(service.createRefundVoucher(id));
    }

    /** M3: huy phieu hoan coc con PENDING de lap lai hoac sua hien trang phong. */
    @DeleteMapping("/checkout-requests/{id}/refund-voucher")
    public ApiResponse<Void> cancelVoucher(@PathVariable Integer id) {
        service.cancelRefundVoucher(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/checkout-requests/{id}/refund-voucher")
    public ApiResponse<RefundVoucherResponse> getVoucher(@PathVariable Integer id) {
        return ApiResponse.ok(service.getRefundVoucher(id));
    }

    /** UC106: ghi nhan da hoan tien coc (PENDING -> REFUNDED). */
    @PostMapping("/refund-vouchers/{refundId}/pay")
    public ApiResponse<RefundVoucherResponse> pay(@PathVariable Integer refundId) {
        return ApiResponse.ok(service.payRefund(refundId));
    }

    /** UC107: thanh ly hop dong, tra phong ve CLEANING (can phieu hoan coc da REFUNDED). */
    @PostMapping("/checkout-requests/{id}/finalize")
    public ApiResponse<CheckoutResponse> finalizeCheckout(@PathVariable Integer id,
                                                          @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.ok(service.finalizeCheckout(id, actor == null ? null : actor.userId()));
    }

    /** UC107: du lieu bien ban ban giao phong de in. */
    @GetMapping("/checkout-requests/{id}/handover-report")
    public ApiResponse<HandoverReport> handover(@PathVariable Integer id) {
        return ApiResponse.ok(service.handoverReport(id));
    }
}

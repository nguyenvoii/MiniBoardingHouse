package com.group7.minihouse.phi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTO hop dong thue (UC41-51). */
public final class ContractDtos {

    private ContractDtos() {}

    /** UC41. Gia thue/tien coc/chu ky/ngay han de trong thi lay mac dinh tu phong (gia, coc) va 1 thang, ngay 5. */
    public record CreateContractRequest(Integer roomId, Integer tenantId, Integer templateId,
                                        LocalDate startDate, LocalDate endDate,
                                        BigDecimal monthlyRentPrice, BigDecimal depositAmount,
                                        Integer paymentCycleMonths, Integer paymentDueDay,
                                        BigDecimal lateFeePerDay) {}

    /** UC42. */
    public record PricingRequest(BigDecimal monthlyRentPrice, BigDecimal depositAmount,
                                 Integer paymentCycleMonths, Integer paymentDueDay) {}

    /** UC43. */
    public record PaymentTermsRequest(BigDecimal lateFeePerDay, String depositRefundConditions) {}

    /** UC44. */
    public record UtilityTermsRequest(String electricTerms, String waterTerms) {}

    /** UC48. */
    public record ExtendRequest(LocalDate newEndDate) {}

    /** UC49: gui truong nao thi doi truong do (tenantId = doi nguoi dung ten hop dong). */
    public record AdjustRequest(BigDecimal monthlyRentPrice, Integer tenantId) {}

    /** UC50: chi so chot cua phong cu la tuy chon (can ca 3 truong periodId, closingElectric, closingWater). */
    public record TransferRequest(Integer newRoomId, Integer periodId, Integer closingElectric,
                                  Integer closingWater) {}

    /** UC51. */
    /** confirmRoomCode: người dùng gõ lại mã phòng để xác nhận, tránh bấm nhầm. */
    public record TerminateRequest(String reason, String confirmRoomCode) {}

    public record ContractResponse(Integer contractId, String contractCode, Integer roomId, String roomCode,
                                   Integer tenantId, String tenantName, Integer templateId,
                                   LocalDate startDate, LocalDate endDate, BigDecimal monthlyRentPrice,
                                   BigDecimal depositAmount, Integer paymentCycleMonths, Integer paymentDueDay,
                                   BigDecimal lateFeePerDay, String electricTerms, String waterTerms,
                                   String depositRefundConditions, String digitalSignatureUrl,
                                   String pdfContractUrl, String status, LocalDateTime createdAt) {}

    public record AdjustmentResponse(Integer adjustmentId, String adjustmentType, String oldValue,
                                     String newValue, Integer adjustedBy, LocalDateTime adjustedAt) {}

    /** UC47: hop dong + lich su thay doi. */
    public record ContractDetail(ContractResponse contract, List<AdjustmentResponse> adjustments) {}
}

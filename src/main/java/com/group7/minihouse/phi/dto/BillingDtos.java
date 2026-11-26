package com.group7.minihouse.phi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTO tinh tien hang thang (UC66-75). */
public final class BillingDtos {

    private BillingDtos() {}

    /** UC66: monthYear dang YYYY-MM. */
    public enum SharedAllocation { PER_RESIDENT, PER_ROOM }

    public record CreatePeriodRequest(Integer propertyId, String monthYear) {}

    /** UC74: sua so luong/don gia 1 dong chi tiet. Gui truong nao doi truong do. */
    public record UpdateItemRequest(BigDecimal quantity, BigDecimal unitPrice) {}

    /** Them 1 khoan tay vao bang tinh cua phong (ky DRAFT). unitPrice am = giam tru. */
    public record AddItemRequest(String itemName, BigDecimal quantity, BigDecimal unitPrice) {}

    /** Xem truoc anh huong cua 1 dich vu: so phong, so don vi (phong/nguoi/xe) va tien moi thang cho toan khu. */
    public record ServicePreview(long rooms, long units, BigDecimal monthlyAmount) {}

    public record PeriodResponse(Integer billingPeriodId, Integer propertyId, String monthYear, String status,
                                 LocalDateTime creationDate, long roomCount, BigDecimal totalAmount) {}

    /** Hop dong co hieu luc trong thang (UC66). */
    public record EligibleContract(Integer contractId, Integer roomId, String roomCode, Integer tenantId,
                                   String tenantName, String contractStatus) {}

    public record PeriodCreated(PeriodResponse period, List<EligibleContract> contracts) {}

    /** UC67: roomCode null = loi chung cua ca khu. */
    public record Issue(String roomCode, String message) {}

    public record ItemResponse(Integer detailId, String itemName, BigDecimal quantity, BigDecimal unitPrice,
                               BigDecimal subtotal) {}

    public record FeeResponse(Integer calculatedFeeId, Integer roomId, String roomCode, Integer contractId,
                              String tenantName, BigDecimal rentAmount, BigDecimal electricAmount,
                              BigDecimal waterAmount, BigDecimal serviceAmount, BigDecimal surchargeAmount,
                              BigDecimal discountAmount, BigDecimal latePenaltyAmount,
                              BigDecimal totalCalculatedAmount, List<ItemResponse> items) {}

    /** UC68-73: ket qua tinh + canh bao (phong thieu chi so, nhieu hop dong...). */
    public record CalculationResult(PeriodResponse period, List<FeeResponse> fees, List<Issue> warnings) {}

    /** UC75: hoa don vuot 150% trung binh cac ky truoc cua chinh phong do. */
    public record Anomaly(Integer calculatedFeeId, String roomCode, BigDecimal total, BigDecimal average) {}
}

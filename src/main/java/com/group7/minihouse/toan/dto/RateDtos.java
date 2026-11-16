package com.group7.minihouse.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO cho phan he don gia & phi dich vu (UC 61-64). */
public final class RateDtos {

    private RateDtos() {}

    public record PlanRequest(String planName, BigDecimal electricUnitPrice, BigDecimal waterUnitPrice,
                              LocalDate effectiveStartDate, LocalDate effectiveEndDate) {}

    public record PlanResponse(Integer planId, String planName, BigDecimal electricUnitPrice,
                               BigDecimal waterUnitPrice, LocalDate effectiveStartDate,
                               LocalDate effectiveEndDate) {}

    /** chargeType: FIXED_ROOM, PER_PERSON, PER_VEHICLE, PER_METER_UNIT. */
    public record ServiceRateRequest(Integer propertyId, String serviceName, String chargeType,
                                     BigDecimal unitPrice, Boolean active, Integer changedByUserId) {}

    public record ServiceRateResponse(Integer rateId, Integer propertyId, String serviceName,
                                      String chargeType, BigDecimal unitPrice, boolean active) {}

    /** customPrice duong = phu thu phat sinh, am = mien giam (UC 63). */
    public record CustomRateRequest(Integer roomId, String rateName, BigDecimal customPrice, String notes) {}

    public record CustomRateResponse(Integer customRateId, Integer roomId, String rateName,
                                     BigDecimal customPrice, String notes, String kind) {}

    public record RateHistoryResponse(Integer historyId, Integer propertyId, String serviceName,
                                      BigDecimal oldPrice, BigDecimal newPrice,
                                      Integer changedByUserId, LocalDateTime changedAt) {}
}

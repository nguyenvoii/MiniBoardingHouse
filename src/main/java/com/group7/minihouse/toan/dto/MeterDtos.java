package com.group7.minihouse.toan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTO cho phan he chi so dien nuoc (UC 53-60). */
public final class MeterDtos {

    private MeterDtos() {}

    public record PeriodRequest(Integer propertyId, String periodName, LocalDate startDate, LocalDate endDate) {}

    public record PeriodResponse(Integer periodId, Integer propertyId, String periodName,
                                 LocalDate startDate, LocalDate endDate, boolean closed) {}

    /**
     * Nhap chi so cuoi ky cua mot phong. oldElectric / oldWater de trong thi he thong tu lay tu ky truoc
     * (hoac chi so ban dau cua phong). photoUrls la anh cong to dien / dong ho nuoc (UC 55).
     */
    public record ReadingRequest(Integer periodId, Integer roomId, Integer oldElectric, Integer newElectric,
                                 Integer oldWater, Integer newWater, Integer recordedByUserId,
                                 String electricPhotoUrl, String waterPhotoUrl) {}

    public record ReadingResponse(Integer readingId, Integer periodId, Integer roomId, String roomCode,
                                  int oldElectric, int newElectric, int usedElectric,
                                  int oldWater, int newWater, int usedWater,
                                  LocalDateTime readingDate, Integer recordedByUserId, boolean verified) {}

    public record PhotoRequest(String meterType, String photoUrl) {}

    public record PhotoResponse(Integer photoId, Integer readingId, String meterType, String photoUrl) {}

    /** adjustedField: NEW_ELECTRIC hoac NEW_WATER. reason bat buoc (UC 56). */
    public record AdjustRequest(String adjustedField, Integer newValue, String reason, Integer adjustedByUserId) {}

    public record AdjustmentLogResponse(Integer adjId, Integer readingId, String adjustedField,
                                        int oldVal, int newVal, String reason,
                                        Integer adjustedByUserId, LocalDateTime adjustedAt) {}

    public record AlertResponse(Integer alertId, Integer readingId, Integer roomId, String roomCode,
                                String utilityType, BigDecimal increasePercentage,
                                String alertMessage, boolean resolved) {}

    /** Mot diem du lieu cua bieu do so sanh tieu thu giua cac thang (UC 59). */
    public record ConsumptionPoint(Integer periodId, String periodName, LocalDate startDate,
                                   long usedElectric, long usedWater) {}

    public record ConsumptionHistory(Integer roomId, String roomCode, List<ConsumptionPoint> points) {}

    /** Chi so luc bat dau thue (UC 60). */
    public record MoveInRequest(Integer roomId, Integer electric, Integer water) {}

    public record MoveInResponse(Integer roomId, String roomCode, int initialElectric, int initialWater) {}

    /** Chi so luc tra phong (UC 60): ghi thanh mot ban ghi chi so trong ky chi dinh. */
    public record MoveOutRequest(Integer roomId, Integer periodId, Integer electric, Integer water,
                                 Integer recordedByUserId, String electricPhotoUrl, String waterPhotoUrl) {}
}

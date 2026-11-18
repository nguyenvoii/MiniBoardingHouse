package com.group7.minihouse.shared.event;

import java.time.LocalDate;

/**
 * Phát ra khi một hợp đồng kết thúc (Trả phòng thanh lý hoặc Chấm dứt sớm, đều qua ContractEndService). Bên nhận
 * (package phi) ghi lịch sử chấm dứt, gỡ người ở khỏi phòng và chốt chỉ số điện nước, để tiền tháng cuối và phí
 * theo đầu người của khách sau không sai. finalElectric/finalWater có thể null nếu chưa ghi nhận chỉ số cuối.
 * previousStatus: trạng thái hợp đồng trước khi kết thúc (ACTIVE, EXPIRING_SOON hoặc EXPIRED).
 */
public record ContractEndedEvent(Integer contractId, Integer roomId, Integer propertyId, LocalDate endDate,
                                 Integer finalElectric, Integer finalWater, String reason, Integer actorId,
                                 String previousStatus) {

    public ContractEndedEvent(Integer contractId, Integer roomId, Integer propertyId, LocalDate endDate,
                              Integer finalElectric, Integer finalWater, String reason, Integer actorId) {
        this(contractId, roomId, propertyId, endDate, finalElectric, finalWater, reason, actorId, "ACTIVE");
    }
}

package com.group7.minihouse.phi.service;

import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.event.ContractEndedEvent;
import java.time.LocalDate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Điểm DUY NHẤT đổi trạng thái khi kết thúc hợp đồng: hợp đồng TERMINATED + phòng CLEANING, rồi phát
 * ContractEndedEvent để ContractEndListener ghi lịch sử TERMINATED, gỡ người ở, chốt chỉ số. Cả Trả phòng
 * (CheckoutService.finalizeCheckout, đường chuẩn) và Chấm dứt sớm (ContractService.terminate, trường hợp đặc biệt)
 * đều gọi hàm này, nên hai đường không thể lệch nhau về trạng thái hợp đồng, phòng, lịch sử ở và chỉ số.
 * Gọi trong giao dịch của bên gọi.
 */
@Service
public class ContractEndService {

    private final ApplicationEventPublisher events;

    public ContractEndService(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void end(RentalContract c, LocalDate endDate, String reason, Integer actorId, Integer finalElectric,
                    Integer finalWater) {
        String previous = c.getStatus();
        c.setStatus("TERMINATED");
        c.getRoom().setStatus("CLEANING");
        events.publishEvent(new ContractEndedEvent(c.getContractId(), c.getRoom().getRoomId(),
                c.getRoom().getProperty() == null ? null : c.getRoom().getProperty().getPropertyId(), endDate,
                finalElectric, finalWater, reason, actorId, previous));
    }
}

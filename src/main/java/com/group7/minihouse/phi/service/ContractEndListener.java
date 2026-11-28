package com.group7.minihouse.phi.service;

import com.group7.minihouse.shared.entity.ContractAdjustment;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.ResidentStayHistory;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.event.ContractEndedEvent;
import com.group7.minihouse.toan.service.MeterReadingService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Xử lý hậu quả khi hợp đồng kết thúc qua Trả phòng, giống đường chấm dứt sớm:
 * 1) ghi dòng TERMINATED vào lịch sử hợp đồng (tính tiền tháng dựa vào dòng này để dừng tính tiền phòng),
 * 2) ghi lịch sử ở và gỡ người ở khỏi phòng (phí theo đầu người của khách sau),
 * 3) chốt chỉ số điện nước cuối vào kỳ đang mở của khu trọ nếu có (khách sau tính tiếp từ đó).
 * Chạy cùng giao dịch với thanh lý: lỗi ở bước nào thì cả thanh lý hoàn tác.
 */
@Component
public class ContractEndListener {

    private static final Logger log = LoggerFactory.getLogger(ContractEndListener.class);

    private final EntityManager em;
    private final MeterReadingService meterService;

    public ContractEndListener(EntityManager em, MeterReadingService meterService) {
        this.em = em;
        this.meterService = meterService;
    }

    @EventListener
    public void onContractEnded(ContractEndedEvent e) {
        RentalContract c = em.find(RentalContract.class, e.contractId());
        if (c == null) {
            return;
        }
        ContractAdjustment a = new ContractAdjustment();
        a.setContract(c);
        a.setAdjustmentType("TERMINATED");
        a.setOldValue(e.previousStatus() == null ? "ACTIVE" : e.previousStatus());
        a.setNewValue(e.reason() == null ? "Trả phòng" : e.reason());
        if (e.actorId() != null) {
            a.setAdjustedBy(em.getReference(User.class, e.actorId()));
        }
        em.persist(a);

        ResidentStayHistory h = new ResidentStayHistory();
        h.setTenant(c.getTenant());
        h.setRoom(c.getRoom());
        h.setStartDate(c.getStartDate());
        h.setEndDate(e.endDate());
        h.setCheckoutReason(e.reason() == null ? "Trả phòng" : e.reason());
        em.persist(h);
        em.createQuery("update RoomResident r set r.isActive = false, r.endDate = :d "
                        + "where r.room.roomId = :room and r.isActive = true")
                .setParameter("d", e.endDate()).setParameter("room", e.roomId()).executeUpdate();

        closeMeters(e);
    }

    /**
     * Chốt chỉ số cuối vào kỳ ghi số đang mở chứa ngày trả phòng (thường đã ghi từ lúc kiểm tra phòng, F6; ở đây chỉ
     * bổ sung nếu chưa có). Số được kiểm trước khi ghi (recordInspection kiểm từ đầu), nên không dựa vào try/catch:
     * dịch vụ trả false thay vì ném lỗi, giao dịch thanh lý không bị đánh dấu rollback (F1).
     */
    private void closeMeters(ContractEndedEvent e) {
        if (e.finalElectric() == null || e.finalWater() == null || e.actorId() == null) {
            return;
        }
        if (!meterService.recordFinalReading(e.roomId(), e.endDate(), e.finalElectric(), e.finalWater(),
                e.actorId())) {
            log.info("Khong chot duoc chi so khi tra phong phong {} (khong co ky ghi so dang mo hoac so khong hop le)",
                    e.roomId());
        }
    }
}

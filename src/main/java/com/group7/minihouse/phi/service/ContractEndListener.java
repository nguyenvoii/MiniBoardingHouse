package com.group7.minihouse.phi.service;

import com.group7.minihouse.shared.entity.ContractAdjustment;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.ResidentStayHistory;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.event.ContractEndedEvent;
import com.group7.minihouse.toan.dto.MeterDtos.MoveOutRequest;
import com.group7.minihouse.toan.service.MeterReadingService;
import jakarta.persistence.EntityManager;
import java.util.List;
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
        a.setOldValue("ACTIVE");
        a.setNewValue(e.reason() == null ? "Tra phong" : e.reason());
        if (e.actorId() != null) {
            a.setAdjustedBy(em.getReference(User.class, e.actorId()));
        }
        em.persist(a);

        ResidentStayHistory h = new ResidentStayHistory();
        h.setTenant(c.getTenant());
        h.setRoom(c.getRoom());
        h.setStartDate(c.getStartDate());
        h.setEndDate(e.endDate());
        h.setCheckoutReason(e.reason() == null ? "Tra phong" : e.reason());
        em.persist(h);
        em.createQuery("update RoomResident r set r.isActive = false, r.endDate = :d "
                        + "where r.room.roomId = :room and r.isActive = true")
                .setParameter("d", e.endDate()).setParameter("room", e.roomId()).executeUpdate();

        closeMeters(e);
    }

    /** Ghi chỉ số cuối như một lần đọc của kỳ chứa ngày trả phòng, nếu kỳ đó có và phòng chưa có số đọc. */
    private void closeMeters(ContractEndedEvent e) {
        if (e.finalElectric() == null || e.finalWater() == null || e.actorId() == null || e.propertyId() == null) {
            return;
        }
        List<?> periods = em.createQuery("select p.periodId from UtilityReadingPeriod p where p.property.propertyId = :pr "
                        + "and p.startDate <= :d and p.endDate >= :d and p.isClosed = false")
                .setParameter("pr", e.propertyId()).setParameter("d", e.endDate()).getResultList();
        if (periods.isEmpty()) {
            return;
        }
        Integer periodId = (Integer) periods.get(0);
        Long existing = (Long) em.createQuery("select count(m) from MeterReading m where m.period.periodId = :p "
                        + "and m.room.roomId = :r").setParameter("p", periodId).setParameter("r", e.roomId())
                .getSingleResult();
        if (existing > 0) {
            return;
        }
        try {
            meterService.recordMoveOut(new MoveOutRequest(e.roomId(), periodId, e.finalElectric(), e.finalWater(),
                    e.actorId(), null, null));
        } catch (RuntimeException ex) {
            // Chỉ số cuối thấp hơn chỉ số kỳ trước (nhập sai): không chặn thanh lý, nhân viên sửa ở màn Chỉ số
            log.warn("Khong chot duoc chi so khi tra phong phong {}: {}", e.roomId(), ex.getMessage());
        }
    }
}

package com.group7.minihouse.viet.service;

import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Component;

/** Tim phong / hop dong dang hieu luc cua mot khach thue. */
@Component
public class TenantRoomResolver {

    @PersistenceContext
    private EntityManager em;

    public List<RentalContract> activeContracts(Integer tenantId) {
        return em.createQuery("select c from RentalContract c where c.tenant.tenantId = :t"
                + " and c.status in ('ACTIVE','EXPIRING_SOON','EXPIRED') order by c.contractId", RentalContract.class)
                .setParameter("t", tenantId).getResultList();
    }

    /** roomId null: tu chon neu khach chi co dung 1 phong; co roomId: phai la phong khach dang thue. */
    public Room resolveRoom(Integer tenantId, Integer roomId) {
        List<RentalContract> contracts = activeContracts(tenantId);
        if (contracts.isEmpty()) {
            throw new BusinessException("Khach thue chua co hop dong dang hieu luc");
        }
        if (roomId != null) {
            return contracts.stream().map(RentalContract::getRoom)
                    .filter(r -> r.getRoomId().equals(roomId)).findFirst()
                    .orElseThrow(() -> new BusinessException("Phong nay khong thuoc khach thue"));
        }
        if (contracts.size() > 1) {
            throw new BusinessException("Khach thue co nhieu phong, can chon roomId");
        }
        return contracts.get(0).getRoom();
    }
}

package com.group7.minihouse.viet.service;

import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomResident;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.TenantRoomDtos.Resident;
import com.group7.minihouse.viet.dto.TenantRoomDtos.TenantRoom;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public List<TenantRoom> roomsForTenant(Integer tenantId) {
        LocalDate today = LocalDate.now();
        return activeContracts(tenantId).stream().map(contract -> {
            Room room = contract.getRoom();
            List<Resident> residents = em.createQuery("select r from RoomResident r"
                            + " where r.room.roomId = :room and r.isActive = true"
                            + " and r.startDate <= :today and (r.endDate is null or r.endDate >= :today)"
                            + " order by r.residentId", RoomResident.class)
                    .setParameter("room", room.getRoomId())
                    .setParameter("today", today)
                    .getResultList().stream()
                    .map(r -> new Resident(r.getFullName(), r.getRelationshipWithPrimary()))
                    .toList();
            return new TenantRoom(room.getRoomId(), room.getRoomCode(), room.getAreaSqm(), room.getMaxOccupants(),
                    contract.getContractId(), contract.getContractCode(), residents);
        }).toList();
    }

    /** roomId null: tu chon neu khach chi co dung 1 phong; co roomId: phai la phong khach dang thue. */
    public Room resolveRoom(Integer tenantId, Integer roomId) {
        List<RentalContract> contracts = activeContracts(tenantId);
        if (contracts.isEmpty()) {
            throw new BusinessException("Khách thuê chưa có hợp đồng đang hiệu lực");
        }
        if (roomId != null) {
            return contracts.stream().map(RentalContract::getRoom)
                    .filter(r -> r.getRoomId().equals(roomId)).findFirst()
                    .orElseThrow(() -> new BusinessException("Phòng này không thuộc khách thuê"));
        }
        if (contracts.size() > 1) {
            throw new BusinessException("Khách thuê có nhiều phòng, cần chọn roomId");
        }
        return contracts.get(0).getRoom();
    }
}

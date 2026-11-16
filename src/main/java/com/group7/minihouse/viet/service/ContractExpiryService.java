package com.group7.minihouse.viet.service;

import com.group7.minihouse.shared.entity.ContractNotification;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.viet.dto.ContractDtos.ExpiringContract;
import com.group7.minihouse.viet.dto.ContractDtos.StatusRefreshResult;
import com.group7.minihouse.viet.repository.ContractNotificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC52: nhac hop dong sap het han. Cap nhat trang thai ACTIVE -> EXPIRING_SOON -> EXPIRED theo end_date,
 * dong thoi ghi contract_notifications va gui thong bao vao trung tam thong bao cho khach thue + chu tro/quan ly.
 */
@Service
public class ContractExpiryService {

    public static final int DEFAULT_DAYS = 30;

    public static final String TYPE_EXPIRING = "EXPIRING_WARNING";
    public static final String TYPE_EXPIRED = "EXPIRED_NOTICE";

    @PersistenceContext
    private EntityManager em;

    private final ContractNotificationRepository notificationRepository;
    private final NotificationSender sender;

    public ContractExpiryService(ContractNotificationRepository notificationRepository, NotificationSender sender) {
        this.notificationRepository = notificationRepository;
        this.sender = sender;
    }

    @Transactional(readOnly = true)
    public List<ExpiringContract> expiring(int days) {
        int d = Math.min(Math.max(days, 1), 365);
        LocalDate today = LocalDate.now();
        List<RentalContract> list = em.createQuery("select c from RentalContract c where c.status in ('ACTIVE','EXPIRING_SOON')"
                + " and c.endDate is not null and c.endDate <= :limit order by c.endDate", RentalContract.class)
                .setParameter("limit", today.plusDays(d)).getResultList();
        return list.stream().map(c -> new ExpiringContract(c.getContractId(), c.getContractCode(),
                c.getRoom().getRoomId(), c.getRoom().getRoomCode(), c.getTenant().getTenantId(),
                c.getTenant().getFullName(), c.getEndDate(), ChronoUnit.DAYS.between(today, c.getEndDate()),
                c.getStatus())).toList();
    }

    /** Moi hop dong chi bi doi trang thai (va duoc bao) mot lan cho moi moc, nen chay lai nhieu lan khong gui trung. */
    @Transactional
    public StatusRefreshResult refreshStatuses() {
        LocalDate today = LocalDate.now();
        List<RentalContract> toExpire = em.createQuery("select c from RentalContract c"
                + " where c.status in ('ACTIVE','EXPIRING_SOON') and c.endDate is not null and c.endDate < :today",
                RentalContract.class).setParameter("today", today).getResultList();
        for (RentalContract c : toExpire) {
            c.setStatus("EXPIRED");
            notifyContract(c, TYPE_EXPIRED, "CONTRACT_EXPIRED", "Hợp đồng đã hết hạn",
                    "Hợp đồng {{contract}} phòng {{room}} đã hết hạn ngày {{endDate}}. Vui lòng liên hệ chủ trọ để gia hạn hoặc làm thủ tục trả phòng.");
        }
        List<RentalContract> toWarn = em.createQuery("select c from RentalContract c where c.status = 'ACTIVE'"
                + " and c.endDate is not null and c.endDate >= :today and c.endDate <= :limit", RentalContract.class)
                .setParameter("today", today).setParameter("limit", today.plusDays(DEFAULT_DAYS)).getResultList();
        for (RentalContract c : toWarn) {
            c.setStatus("EXPIRING_SOON");
            notifyContract(c, TYPE_EXPIRING, "CONTRACT_EXPIRING", "Hợp đồng sắp hết hạn",
                    "Hợp đồng {{contract}} phòng {{room}} sẽ hết hạn ngày {{endDate}}. Vui lòng liên hệ chủ trọ nếu muốn gia hạn.");
        }
        return new StatusRefreshResult(toWarn.size(), toExpire.size());
    }

    private void notifyContract(RentalContract c, String type, String templateCode, String title, String content) {
        ContractNotification n = new ContractNotification();
        n.setContract(c);
        n.setNotificationType(type);
        n.setSentAt(LocalDateTime.now());
        n.setIsRead(false);
        notificationRepository.save(n);
        Map<String, String> params = Map.of("contract", String.valueOf(c.getContractCode()),
                "room", String.valueOf(c.getRoom().getRoomCode()), "endDate", String.valueOf(c.getEndDate()),
                "tenant", String.valueOf(c.getTenant().getFullName()));
        sender.notifyTenant(c.getTenant().getTenantId(), templateCode, title, content, params, "CONTRACT",
                c.getContractId());
        sender.notifyStaff(List.of("LANDLORD", "MANAGER"), templateCode, title + " - " + params.get("tenant"),
                content, params, "CONTRACT", c.getContractId());
    }
}

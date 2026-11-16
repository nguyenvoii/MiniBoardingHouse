package com.group7.minihouse.viet.service;

import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.MaintenanceLog;
import com.group7.minihouse.shared.entity.MaintenanceRequest;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.MaintenanceDtos.AssignRequest;
import com.group7.minihouse.viet.dto.MaintenanceDtos.CreateMaintenanceRequest;
import com.group7.minihouse.viet.dto.MaintenanceDtos.InvoiceAdjustmentRequest;
import com.group7.minihouse.viet.dto.MaintenanceDtos.InvoiceAdjustmentResponse;
import com.group7.minihouse.viet.dto.MaintenanceDtos.MaintenanceResponse;
import com.group7.minihouse.viet.dto.MaintenanceDtos.RatingRequest;
import com.group7.minihouse.viet.dto.MaintenanceDtos.ResolveRequest;
import com.group7.minihouse.viet.repository.IncidentCategoryRepository;
import com.group7.minihouse.viet.repository.MaintenanceLogRepository;
import com.group7.minihouse.viet.repository.MaintenanceRequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC110-115: su co / yeu cau sua chua. Luong: OPEN -> ASSIGNED (phan cong) -> IN_PROGRESS -> RESOLVED
 * (nhap chi phi, ghi chu) -> CLOSED (xac nhan hoan tat). Nguoi thue danh gia (1-5 sao) sau khi RESOLVED/CLOSED.
 * UC110 (mat dien/nuoc) va UC111 (kiem tra cong to) la cac nhom su co (category) do UC113 quan ly.
 * UC111 co them buoc dieu chinh hoa don: sau khi su co xu ly xong, nhan vien giam tien hoa don bi tinh sai.
 */
@Service
public class MaintenanceService {

    static final Set<String> STATUSES = Set.of("OPEN", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "CLOSED");
    static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "URGENT");

    @PersistenceContext
    private EntityManager em;

    private final MaintenanceRequestRepository requestRepository;
    private final MaintenanceLogRepository logRepository;
    private final IncidentCategoryRepository categoryRepository;
    private final TenantRoomResolver tenantRoomResolver;
    private final NotificationSender notificationSender;

    public MaintenanceService(MaintenanceRequestRepository requestRepository, MaintenanceLogRepository logRepository,
                              IncidentCategoryRepository categoryRepository, TenantRoomResolver tenantRoomResolver,
                              NotificationSender notificationSender) {
        this.requestRepository = requestRepository;
        this.logRepository = logRepository;
        this.categoryRepository = categoryRepository;
        this.tenantRoomResolver = tenantRoomResolver;
        this.notificationSender = notificationSender;
    }

    /** tenantFromToken != null: nguoi thue tu gui (phong suy ra tu hop dong); null: nhan vien ghi nhan ho. */
    @Transactional
    public MaintenanceResponse create(CreateMaintenanceRequest req, Integer tenantFromToken) {
        if (req == null) {
            throw new BusinessException("Thieu noi dung yeu cau");
        }
        String category = VietSupport.requireText(req.category(), "nhom su co (category)").toUpperCase();
        String description = VietSupport.requireText(req.description(), "mo ta su co");
        String priority = VietSupport.enumOrNull(req.priority(), PRIORITIES, "priority");
        if (categoryRepository.findActive(category, "INCIDENT").isEmpty()) {
            throw new BusinessException("Nhom su co \"" + category + "\" khong ton tai hoac da tat");
        }
        Room room;
        Tenant tenant = null;
        if (tenantFromToken != null) {
            room = tenantRoomResolver.resolveRoom(tenantFromToken, req.roomId());
            tenant = em.getReference(Tenant.class, tenantFromToken);
        } else {
            if (req.roomId() == null) {
                throw new BusinessException("Thieu roomId");
            }
            room = em.find(Room.class, req.roomId());
            if (room == null) {
                throw BusinessException.notFound("Phong");
            }
            if (req.tenantId() != null) {
                tenant = em.find(Tenant.class, req.tenantId());
                if (tenant == null) {
                    throw BusinessException.notFound("Khach thue");
                }
            }
        }
        MaintenanceRequest r = new MaintenanceRequest();
        r.setRoom(room);
        r.setTenant(tenant);
        r.setCategory(category);
        r.setDescription(description);
        r.setIssuePhotoUrl(VietSupport.blankToNull(req.issuePhotoUrl()));
        r.setPriority(priority == null ? "NORMAL" : priority);
        r.setStatus("OPEN");
        r.setCreatedAt(LocalDateTime.now());
        return MaintenanceResponse.from(requestRepository.save(r), null);
    }

    @Transactional(readOnly = true)
    public PageResponse<MaintenanceResponse> search(String status, String priority, String category, Integer roomId,
                                                    int page, int size) {
        String st = VietSupport.enumOrNull(status, STATUSES, "status");
        String pr = VietSupport.enumOrNull(priority, PRIORITIES, "priority");
        String cat = VietSupport.blankToNull(category);
        return new PageResponse<>(requestRepository
                .search(st, pr, cat == null ? null : cat.toUpperCase(), roomId,
                        VietSupport.page(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(r -> MaintenanceResponse.from(r, latestLog(r.getRequestId()))));
    }

    @Transactional(readOnly = true)
    public MaintenanceResponse get(Integer id) {
        MaintenanceRequest r = find(id);
        return MaintenanceResponse.from(r, latestLog(id));
    }

    @Transactional(readOnly = true)
    public List<MaintenanceResponse> mine(Integer tenantId) {
        return requestRepository.findByTenant_TenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(r -> MaintenanceResponse.from(r, latestLog(r.getRequestId()))).toList();
    }

    /** UC114: phan cong nhan vien xu ly. Co the phan cong lai khi chua RESOLVED. */
    @Transactional
    public MaintenanceResponse assign(Integer id, AssignRequest req) {
        MaintenanceRequest r = find(id);
        if (Set.of("RESOLVED", "CLOSED").contains(r.getStatus())) {
            throw new BusinessException("Su co da xu ly xong, khong the phan cong");
        }
        if (req == null || req.staffId() == null) {
            throw new BusinessException("Thieu staffId");
        }
        User staff = em.find(User.class, req.staffId());
        if (staff == null) {
            throw BusinessException.notFound("Nhan vien");
        }
        MaintenanceLog log = logRepository.findFirstByRequest_RequestIdOrderByMaintenanceLogIdDesc(id)
                .orElseGet(MaintenanceLog::new);
        log.setRequest(r);
        log.setAssignedStaff(staff);
        logRepository.save(log);
        if ("OPEN".equals(r.getStatus())) {
            r.setStatus("ASSIGNED");
            requestRepository.save(r);
        }
        return MaintenanceResponse.from(r, log);
    }

    /** UC114: bat dau xu ly. */
    @Transactional
    public MaintenanceResponse start(Integer id) {
        MaintenanceRequest r = find(id);
        if (!"ASSIGNED".equals(r.getStatus())) {
            throw new BusinessException("Chi bat dau xu ly duoc su co da phan cong (ASSIGNED)");
        }
        r.setStatus("IN_PROGRESS");
        return MaintenanceResponse.from(requestRepository.save(r), latestLog(id));
    }

    /** UC114: cap nhat chi phi va ket qua xu ly -> RESOLVED. */
    @Transactional
    public MaintenanceResponse resolve(Integer id, ResolveRequest req) {
        MaintenanceRequest r = find(id);
        if (!Set.of("ASSIGNED", "IN_PROGRESS").contains(r.getStatus())) {
            throw new BusinessException("Chi hoan tat duoc su co dang ASSIGNED hoac IN_PROGRESS");
        }
        BigDecimal cost = req == null || req.repairCost() == null ? BigDecimal.ZERO : req.repairCost();
        if (cost.signum() < 0) {
            throw new BusinessException("Chi phi sua chua khong duoc am");
        }
        MaintenanceLog log = logRepository.findFirstByRequest_RequestIdOrderByMaintenanceLogIdDesc(id)
                .orElseThrow(() -> new BusinessException("Chua phan cong nhan vien xu ly"));
        log.setRepairCost(cost);
        log.setResolutionNotes(req == null ? null : VietSupport.blankToNull(req.resolutionNotes()));
        log.setCompletedAt(LocalDateTime.now());
        logRepository.save(log);
        r.setStatus("RESOLVED");
        requestRepository.save(r);
        return MaintenanceResponse.from(r, log);
    }

    /** UC114: chu tro/quan ly xac nhan hoan tat -> CLOSED. */
    @Transactional
    public MaintenanceResponse close(Integer id) {
        MaintenanceRequest r = find(id);
        if (!"RESOLVED".equals(r.getStatus())) {
            throw new BusinessException("Chi dong duoc su co da RESOLVED");
        }
        r.setStatus("CLOSED");
        return MaintenanceResponse.from(requestRepository.save(r), latestLog(id));
    }

    /**
     * UC111: dieu chinh (giam) tien hoa don sau khi kiem tra cong to/dong ho nuoc. Chi lam khi su co da
     * RESOLVED/CLOSED, hoa don phai cua dung phong, chua PAID/CANCELLED, so tien giam khong qua so con no.
     * Ghi lai vao ghi chu xu ly cua su co va thong bao cho khach thue.
     */
    @Transactional
    public InvoiceAdjustmentResponse adjustInvoice(Integer id, InvoiceAdjustmentRequest req) {
        MaintenanceRequest r = find(id);
        if (!Set.of("RESOLVED", "CLOSED").contains(r.getStatus())) {
            throw new BusinessException("Chi dieu chinh hoa don sau khi su co da duoc xu ly xong (RESOLVED/CLOSED)");
        }
        if (req == null || req.invoiceId() == null) {
            throw new BusinessException("Thieu invoiceId");
        }
        BigDecimal reduce = req.reduceAmount();
        if (reduce == null || reduce.signum() <= 0) {
            throw new BusinessException("So tien giam phai lon hon 0");
        }
        String reason = VietSupport.requireText(req.reason(), "ly do dieu chinh");
        Invoice inv = em.find(Invoice.class, req.invoiceId());
        if (inv == null) {
            throw BusinessException.notFound("Hoa don");
        }
        if (!inv.getCalculatedFee().getRoom().getRoomId().equals(r.getRoom().getRoomId())) {
            throw new BusinessException("Hoa don khong thuoc phong cua su co nay");
        }
        if (Set.of("PAID", "CANCELLED").contains(inv.getPaymentStatus())) {
            throw new BusinessException("Khong dieu chinh duoc hoa don da thanh toan hoac da huy");
        }
        BigDecimal paid = VietSupport.nz(inv.getPaidAmount());
        BigDecimal oldTotal = VietSupport.nz(inv.getTotalAmount());
        BigDecimal remaining = oldTotal.subtract(paid);
        if (reduce.compareTo(remaining) > 0) {
            throw new BusinessException("So tien giam vuot qua so con no cua hoa don (" + remaining + ")");
        }
        BigDecimal newTotal = oldTotal.subtract(reduce);
        inv.setTotalAmount(newTotal);
        if (newTotal.compareTo(paid) <= 0) {
            inv.setPaymentStatus("PAID");
        }
        MaintenanceLog log = logRepository.findFirstByRequest_RequestIdOrderByMaintenanceLogIdDesc(id)
                .orElseThrow(() -> new BusinessException("Su co chua co nhat ky xu ly"));
        String note = "[Dieu chinh hoa don " + inv.getInvoiceCode() + ": giam " + reduce + ", ly do: " + reason + "]";
        String old = log.getResolutionNotes();
        log.setResolutionNotes(old == null || old.isBlank() ? note : old + " " + note);
        logRepository.save(log);
        if (r.getTenant() != null) {
            notificationSender.notifyTenant(r.getTenant().getTenantId(), "INVOICE_ADJUSTED",
                    "Hóa đơn được điều chỉnh",
                    "Hóa đơn {{invoice}} đã được giảm {{amount}} đ sau khi kiểm tra. Lý do: {{reason}}",
                    Map.of("invoice", inv.getInvoiceCode(), "amount", reduce.toPlainString(), "reason", reason),
                    "INVOICE", inv.getInvoiceId());
        }
        return new InvoiceAdjustmentResponse(id, inv.getInvoiceId(), inv.getInvoiceCode(), oldTotal, newTotal, paid,
                newTotal.subtract(paid), inv.getPaymentStatus());
    }

    /** UC115: nguoi thue danh gia chat luong xu ly (chi su co cua chinh minh, 1 lan). */
    @Transactional
    public MaintenanceResponse rate(Integer id, Integer tenantId, RatingRequest req) {
        MaintenanceRequest r = find(id);
        if (r.getTenant() == null || !r.getTenant().getTenantId().equals(tenantId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chi duoc danh gia su co do chinh ban gui");
        }
        if (!Set.of("RESOLVED", "CLOSED").contains(r.getStatus())) {
            throw new BusinessException("Chi danh gia duoc sau khi su co da xu ly xong");
        }
        if (req == null || req.rating() == null || req.rating() < 1 || req.rating() > 5) {
            throw new BusinessException("Diem danh gia phai tu 1 den 5");
        }
        MaintenanceLog log = logRepository.findFirstByRequest_RequestIdOrderByMaintenanceLogIdDesc(id)
                .orElseThrow(() -> new BusinessException("Su co chua co ket qua xu ly"));
        if (log.getTenantRating() != null) {
            throw new BusinessException("Ban da danh gia su co nay roi");
        }
        log.setTenantRating(req.rating());
        logRepository.save(log);
        return MaintenanceResponse.from(r, log);
    }

    private MaintenanceLog latestLog(Integer requestId) {
        return logRepository.findFirstByRequest_RequestIdOrderByMaintenanceLogIdDesc(requestId).orElse(null);
    }

    private MaintenanceRequest find(Integer id) {
        return requestRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Yeu cau sua chua"));
    }
}

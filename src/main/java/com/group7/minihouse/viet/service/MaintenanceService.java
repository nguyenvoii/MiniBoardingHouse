package com.group7.minihouse.viet.service;

import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.MaintenanceLog;
import com.group7.minihouse.shared.entity.MaintenanceRequest;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.storage.FileStorage;
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
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    public static final String PHOTO_ROUTE = "/api/auth/maintenance-photos";
    private static final String PHOTO_FOLDER = "maintenance";

    @PersistenceContext
    private EntityManager em;

    private final MaintenanceRequestRepository requestRepository;
    private final MaintenanceLogRepository logRepository;
    private final IncidentCategoryRepository categoryRepository;
    private final TenantRoomResolver tenantRoomResolver;
    private final NotificationSender notificationSender;
    private final FileStorage storage;

    public MaintenanceService(MaintenanceRequestRepository requestRepository, MaintenanceLogRepository logRepository,
                              IncidentCategoryRepository categoryRepository, TenantRoomResolver tenantRoomResolver,
                              NotificationSender notificationSender, FileStorage storage) {
        this.requestRepository = requestRepository;
        this.logRepository = logRepository;
        this.categoryRepository = categoryRepository;
        this.tenantRoomResolver = tenantRoomResolver;
        this.notificationSender = notificationSender;
        this.storage = storage;
    }

    /** tenantFromToken != null: nguoi thue tu gui (phong suy ra tu hop dong); null: nhan vien ghi nhan ho. */
    @Transactional
    public MaintenanceResponse create(CreateMaintenanceRequest req, Integer tenantFromToken) {
        return create(req, tenantFromToken, null);
    }

    @Transactional
    public MaintenanceResponse create(CreateMaintenanceRequest req, Integer tenantFromToken, byte[] photo) {
        if (req == null) {
            throw new BusinessException("Thiếu nội dung yêu cầu");
        }
        String category = VietSupport.requireText(req.category(), "nhom su co (category)").toUpperCase();
        String description = VietSupport.requireText(req.description(), "mo ta su co");
        String priority = VietSupport.enumOrNull(req.priority(), PRIORITIES, "priority");
        if (categoryRepository.findActive(category, "INCIDENT").isEmpty()) {
            throw new BusinessException("Nhóm sự cố \"" + category + "\" không tồn tại hoặc đã tắt");
        }
        Room room;
        Tenant tenant = null;
        if (tenantFromToken != null) {
            room = tenantRoomResolver.resolveRoom(tenantFromToken, req.roomId());
            tenant = em.getReference(Tenant.class, tenantFromToken);
        } else {
            if (req.roomId() == null) {
                throw new BusinessException("Thiếu roomId");
            }
            room = em.find(Room.class, req.roomId());
            if (room == null) {
                throw BusinessException.notFound("Phòng");
            }
            if (req.tenantId() != null) {
                tenant = em.find(Tenant.class, req.tenantId());
                if (tenant == null) {
                    throw BusinessException.notFound("Khách thuê");
                }
            }
        }
        MaintenanceRequest r = new MaintenanceRequest();
        r.setRoom(room);
        r.setTenant(tenant);
        r.setCategory(category);
        r.setDescription(description);
        String photoUrl = VietSupport.blankToNull(req.issuePhotoUrl());
        if (photo != null) {
            String name = storage.saveImage(PHOTO_FOLDER, photo, 5L * 1024 * 1024);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        storage.delete(PHOTO_FOLDER, name);
                    }
                }
            });
            photoUrl = PHOTO_ROUTE + "/" + name;
        } else if (photoUrl != null && photoUrl.regionMatches(true, 0, PHOTO_ROUTE + "/", 0, PHOTO_ROUTE.length() + 1)) {
            throw new BusinessException("Hãy tải tệp ảnh lên cùng yêu cầu");
        }
        r.setIssuePhotoUrl(photoUrl);
        r.setPriority(priority == null ? "NORMAL" : priority);
        r.setStatus("OPEN");
        r.setCreatedAt(LocalDateTime.now());
        return MaintenanceResponse.from(requestRepository.save(r), null);
    }

    @Transactional(readOnly = true)
    public Path photo(String name, Integer tenantId) {
        MaintenanceRequest request = requestRepository.findFirstByIssuePhotoUrl(PHOTO_ROUTE + "/" + name)
                .orElseThrow(() -> BusinessException.notFound("Ảnh sự cố"));
        if (tenantId != null && (request.getTenant() == null || !tenantId.equals(request.getTenant().getTenantId()))) {
            throw BusinessException.notFound("Ảnh sự cố");
        }
        return storage.find(PHOTO_FOLDER, name).orElseThrow(() -> BusinessException.notFound("Ảnh sự cố"));
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
            throw new BusinessException("Sự cố đã xử lý xong, không thể phân công");
        }
        if (req == null || req.staffId() == null) {
            throw new BusinessException("Thiếu staffId");
        }
        User staff = em.find(User.class, req.staffId());
        if (staff == null) {
            throw BusinessException.notFound("Nhân viên");
        }
        // L12: chi phan cong cho nhan vien dang hoat dong (khong phai nguoi thue, khong phai tai khoan da khoa)
        if (!"ACTIVE".equals(staff.getStatus()) || em.createQuery("select ur.role.roleCode from UserRole ur "
                + "where ur.user.userId = :u and ur.role.roleCode in ('LANDLORD','MANAGER','CASHIER','METER_READER')")
                .setParameter("u", staff.getUserId()).getResultList().isEmpty()) {
            throw new BusinessException("Chỉ phân công cho nhân viên đang hoạt động");
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
            throw new BusinessException("Chỉ bắt đầu xử lý được sự cố đã phân công (ASSIGNED)");
        }
        r.setStatus("IN_PROGRESS");
        return MaintenanceResponse.from(requestRepository.save(r), latestLog(id));
    }

    /** UC114: cap nhat chi phi va ket qua xu ly -> RESOLVED. */
    @Transactional
    public MaintenanceResponse resolve(Integer id, ResolveRequest req) {
        MaintenanceRequest r = find(id);
        if (!Set.of("ASSIGNED", "IN_PROGRESS").contains(r.getStatus())) {
            throw new BusinessException("Chỉ hoàn tất được sự cố đang ASSIGNED hoặc IN_PROGRESS");
        }
        BigDecimal cost = req == null || req.repairCost() == null ? BigDecimal.ZERO : req.repairCost();
        if (cost.signum() < 0) {
            throw new BusinessException("Chi phí sửa chữa không được âm");
        }
        MaintenanceLog log = logRepository.findFirstByRequest_RequestIdOrderByMaintenanceLogIdDesc(id)
                .orElseThrow(() -> new BusinessException("Chưa phân công nhân viên xử lý"));
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
            throw new BusinessException("Chỉ đóng được sự cố đã RESOLVED");
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
            throw new BusinessException("Chỉ điều chỉnh hóa đơn sau khi sự cố đã được xử lý xong (RESOLVED/CLOSED)");
        }
        if (req == null || req.invoiceId() == null) {
            throw new BusinessException("Thiếu invoiceId");
        }
        BigDecimal reduce = req.reduceAmount();
        if (reduce == null || reduce.signum() <= 0) {
            throw new BusinessException("Số tiền giảm phải lớn hơn 0");
        }
        if (reduce.stripTrailingZeros().scale() > 2) {
            throw new BusinessException("Số tiền giảm chỉ được có tối đa 2 chữ số thập phân");
        }
        String reason = VietSupport.requireText(req.reason(), "ly do dieu chinh");
        Invoice inv = em.find(Invoice.class, req.invoiceId(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (inv == null) {
            throw BusinessException.notFound("Hóa đơn");
        }
        if (!inv.getCalculatedFee().getRoom().getRoomId().equals(r.getRoom().getRoomId())) {
            throw new BusinessException("Hóa đơn không thuộc phòng của sự cố này");
        }
        if (r.getTenant() != null
                && (inv.getTenant() == null || !r.getTenant().getTenantId().equals(inv.getTenant().getTenantId()))) {
            throw new BusinessException("Hóa đơn không thuộc khách thuê của sự cố này");
        }
        if (Set.of("PAID", "CANCELLED").contains(inv.getPaymentStatus())) {
            throw new BusinessException("Không điều chỉnh được hóa đơn đã thanh toán hoặc đã hủy");
        }
        BigDecimal paid = VietSupport.nz(inv.getPaidAmount());
        BigDecimal oldTotal = VietSupport.nz(inv.getTotalAmount());
        BigDecimal remaining = oldTotal.subtract(paid);
        if (reduce.compareTo(remaining) > 0) {
            throw new BusinessException("Số tiền giảm vượt quá số còn nợ của hóa đơn (" + remaining + ")");
        }
        BigDecimal newTotal = oldTotal.subtract(reduce);
        // M14/M16: tong + dong chi tiet + trang thai di qua mot noi duy nhat (InvoiceBalanceCalculator)
        new com.group7.minihouse.toan.service.InvoiceBalanceCalculator()
                .setTotalAndSync(em, inv, newTotal, reason, java.time.LocalDate.now());
        MaintenanceLog log = logRepository.findFirstByRequest_RequestIdOrderByMaintenanceLogIdDesc(id)
                .orElseThrow(() -> new BusinessException("Sự cố chưa có nhật ký xử lý"));
        String note = "[Điều chỉnh hóa đơn " + inv.getInvoiceCode() + ": giam " + reduce + ", ly do: " + reason + "]";
        String old = log.getResolutionNotes();
        log.setResolutionNotes(old == null || old.isBlank() ? note : old + " " + note);
        logRepository.save(log);
        if (inv.getTenant() != null) {
            notificationSender.notifyTenant(inv.getTenant().getTenantId(), "INVOICE_ADJUSTED",
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
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ được đánh giá sự cố do chính bạn gửi");
        }
        if (!Set.of("RESOLVED", "CLOSED").contains(r.getStatus())) {
            throw new BusinessException("Chỉ đánh giá được sau khi sự cố đã xử lý xong");
        }
        if (req == null || req.rating() == null || req.rating() < 1 || req.rating() > 5) {
            throw new BusinessException("Điểm đánh giá phải từ 1 đến 5");
        }
        MaintenanceLog log = logRepository.findFirstByRequest_RequestIdOrderByMaintenanceLogIdDesc(id)
                .orElseThrow(() -> new BusinessException("Sự cố chưa có kết quả xử lý"));
        if (log.getTenantRating() != null) {
            throw new BusinessException("Bạn đã đánh giá sự cố này rồi");
        }
        log.setTenantRating(req.rating());
        logRepository.save(log);
        return MaintenanceResponse.from(r, log);
    }

    private MaintenanceLog latestLog(Integer requestId) {
        return logRepository.findFirstByRequest_RequestIdOrderByMaintenanceLogIdDesc(requestId).orElse(null);
    }

    private MaintenanceRequest find(Integer id) {
        return requestRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Yêu cầu sửa chữa"));
    }
}

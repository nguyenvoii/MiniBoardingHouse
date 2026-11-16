package com.group7.minihouse.viet.service;

import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.IncidentCategory;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomAddonFee;
import com.group7.minihouse.shared.entity.RoomEquipmentRequest;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.EquipmentDtos.AddonFeeResponse;
import com.group7.minihouse.viet.dto.EquipmentDtos.CreateEquipmentRequest;
import com.group7.minihouse.viet.dto.EquipmentDtos.EquipmentResponse;
import com.group7.minihouse.viet.dto.EquipmentDtos.InstallRequest;
import com.group7.minihouse.viet.dto.EquipmentDtos.ReviewRequest;
import com.group7.minihouse.viet.repository.IncidentCategoryRepository;
import com.group7.minihouse.viet.repository.RoomAddonFeeRepository;
import com.group7.minihouse.viet.repository.RoomEquipmentRequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Yeu cau thiet bi (lap them / dung them) va phu phi phong hang thang.
 * Luong: PENDING -> APPROVED | REJECTED; APPROVED -> INSTALLED (tao RoomAddonFee) -> REMOVED (tat phu phi).
 */
@Service
public class EquipmentService {

    static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "INSTALLED", "REMOVED");
    static final Set<String> TYPES = Set.of("INSTALL", "EXTRA_USAGE");

    @PersistenceContext
    private EntityManager em;

    private final RoomEquipmentRequestRepository requestRepository;
    private final RoomAddonFeeRepository feeRepository;
    private final IncidentCategoryRepository categoryRepository;
    private final TenantRoomResolver tenantRoomResolver;
    private final ActorResolver actorResolver;

    public EquipmentService(RoomEquipmentRequestRepository requestRepository, RoomAddonFeeRepository feeRepository,
                            IncidentCategoryRepository categoryRepository, TenantRoomResolver tenantRoomResolver,
                            ActorResolver actorResolver) {
        this.requestRepository = requestRepository;
        this.feeRepository = feeRepository;
        this.categoryRepository = categoryRepository;
        this.tenantRoomResolver = tenantRoomResolver;
        this.actorResolver = actorResolver;
    }

    @Transactional
    public EquipmentResponse create(CreateEquipmentRequest req, Integer tenantId) {
        if (req == null) {
            throw new BusinessException("Thieu du lieu");
        }
        String name = VietSupport.requireText(req.equipmentName(), "ten thiet bi");
        String type = VietSupport.enumOrNull(req.requestType(), TYPES, "requestType");
        if (type == null) {
            throw new BusinessException("Thieu requestType (INSTALL hoac EXTRA_USAGE)");
        }
        BigDecimal fee = req.estimatedMonthlyFee() == null ? BigDecimal.ZERO : req.estimatedMonthlyFee();
        if (fee.signum() < 0) {
            throw new BusinessException("Phi du kien khong duoc am");
        }
        IncidentCategory category = null;
        if (req.categoryId() != null) {
            category = categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> BusinessException.notFound("Nhom thiet bi"));
            if (!"EQUIPMENT".equals(category.getCategoryType()) || !Boolean.TRUE.equals(category.getIsActive())) {
                throw new BusinessException("Nhom thiet bi khong hop le hoac da tat");
            }
        }
        Room room = tenantRoomResolver.resolveRoom(tenantId, req.roomId());
        RoomEquipmentRequest r = new RoomEquipmentRequest();
        r.setRoom(room);
        r.setTenant(em.getReference(Tenant.class, tenantId));
        r.setCategory(category);
        r.setEquipmentName(name);
        r.setRequestType(type);
        r.setEstimatedMonthlyFee(fee);
        r.setStatus("PENDING");
        r.setCreatedAt(LocalDateTime.now());
        return EquipmentResponse.from(requestRepository.save(r));
    }

    @Transactional(readOnly = true)
    public PageResponse<EquipmentResponse> search(String status, Integer roomId, int page, int size) {
        String st = VietSupport.enumOrNull(status, STATUSES, "status");
        return new PageResponse<>(requestRepository
                .search(st, roomId, VietSupport.page(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(EquipmentResponse::from));
    }

    @Transactional(readOnly = true)
    public List<EquipmentResponse> mine(Integer tenantId) {
        return requestRepository.findByTenant_TenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(EquipmentResponse::from).toList();
    }

    @Transactional
    public EquipmentResponse review(Integer id, ReviewRequest req, Integer actorId) {
        RoomEquipmentRequest r = find(id);
        if (!"PENDING".equals(r.getStatus())) {
            throw new BusinessException("Chi duyet duoc yeu cau dang PENDING");
        }
        if (req == null || req.approve() == null) {
            throw new BusinessException("Thieu approve (true/false)");
        }
        r.setStatus(req.approve() ? "APPROVED" : "REJECTED");
        r.setReviewedBy(actorResolver.require(actorId));
        r.setNote(VietSupport.blankToNull(req.note()));
        return EquipmentResponse.from(requestRepository.save(r));
    }

    /** Da lap/cap thiet bi xong: tao phu phi hang thang cho phong. */
    @Transactional
    public AddonFeeResponse install(Integer id, InstallRequest req) {
        RoomEquipmentRequest r = find(id);
        if (!"APPROVED".equals(r.getStatus())) {
            throw new BusinessException("Chi lap dat duoc yeu cau da duyet (APPROVED)");
        }
        BigDecimal amount = req != null && req.amount() != null ? req.amount() : VietSupport.nz(r.getEstimatedMonthlyFee());
        if (amount.signum() < 0) {
            throw new BusinessException("Phu phi khong duoc am");
        }
        int cycle = req != null && req.billingCycleMonths() != null ? req.billingCycleMonths() : 1;
        if (cycle < 1) {
            throw new BusinessException("Chu ky tinh phi toi thieu 1 thang");
        }
        RoomAddonFee fee = new RoomAddonFee();
        fee.setRoom(r.getRoom());
        fee.setEquipmentRequest(r);
        fee.setFeeName(req != null && VietSupport.blankToNull(req.feeName()) != null
                ? req.feeName().trim() : r.getEquipmentName());
        fee.setAmount(amount);
        fee.setBillingCycleMonths(cycle);
        fee.setStartDate(LocalDate.now());
        fee.setIsActive(true);
        fee = feeRepository.save(fee);
        r.setStatus("INSTALLED");
        requestRepository.save(r);
        return AddonFeeResponse.from(fee);
    }

    /** Thao thiet bi: tat phu phi dang chay cua yeu cau nay. */
    @Transactional
    public EquipmentResponse remove(Integer id) {
        RoomEquipmentRequest r = find(id);
        if (!"INSTALLED".equals(r.getStatus())) {
            throw new BusinessException("Chi thao duoc thiet bi dang INSTALLED");
        }
        for (RoomAddonFee f : feeRepository.findByEquipmentRequest_EquipmentRequestId(id)) {
            if (Boolean.TRUE.equals(f.getIsActive())) {
                f.setIsActive(false);
                f.setEndDate(LocalDate.now());
                feeRepository.save(f);
            }
        }
        r.setStatus("REMOVED");
        return EquipmentResponse.from(requestRepository.save(r));
    }

    @Transactional(readOnly = true)
    public List<AddonFeeResponse> addonFees(Integer roomId, Boolean active) {
        return feeRepository.search(roomId, active).stream().map(AddonFeeResponse::from).toList();
    }

    private RoomEquipmentRequest find(Integer id) {
        return requestRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Yeu cau thiet bi"));
    }
}

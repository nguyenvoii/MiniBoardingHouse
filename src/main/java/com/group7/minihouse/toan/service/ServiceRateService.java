package com.group7.minihouse.toan.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.dto.RateDtos.*;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.entity.PropertyServiceRate;
import com.group7.minihouse.shared.entity.RateChangeHistory;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomCustomRate;
import com.group7.minihouse.shared.entity.ServiceRatePlan;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.toan.repository.PropertyServiceRateRepository;
import com.group7.minihouse.toan.repository.RateChangeHistoryRepository;
import com.group7.minihouse.toan.repository.RoomCustomRateRepository;
import com.group7.minihouse.toan.repository.ServiceRatePlanRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Don gia & phi dich vu (UC 61-64): gia dien/nuoc, phi dich vu theo khu, phu thu / mien giam theo phong,
 * thoi diem ap dung va lich su thay doi.
 */
@Service
public class ServiceRateService {

    private static final Set<String> CHARGE_TYPES =
            Set.of("FIXED_ROOM", "PER_PERSON", "PER_VEHICLE", "PER_METER_UNIT");

    private final ServiceRatePlanRepository planRepo;
    private final PropertyServiceRateRepository serviceRateRepo;
    private final RoomCustomRateRepository customRateRepo;
    private final RateChangeHistoryRepository historyRepo;

    @PersistenceContext
    private EntityManager em;

    public ServiceRateService(ServiceRatePlanRepository planRepo, PropertyServiceRateRepository serviceRateRepo,
                              RoomCustomRateRepository customRateRepo, RateChangeHistoryRepository historyRepo) {
        this.planRepo = planRepo;
        this.serviceRateRepo = serviceRateRepo;
        this.customRateRepo = customRateRepo;
        this.historyRepo = historyRepo;
    }

    // ------------------------------------------------------------------ UC 61, 64: bang gia dien nuoc

    /** Tao bang gia moi; bang gia dang mo truoc do duoc dong vao ngay truoc ngay ap dung. */
    @Transactional
    @CacheEvict(cacheNames = {"servicePlans", "serviceRates"}, allEntries = true)
    public PlanResponse createPlan(PlanRequest req) {
        validatePlan(req);
        planRepo.findFirstByEffectiveEndDateIsNullOrderByEffectiveStartDateDesc().ifPresent(open -> {
            if (!open.getEffectiveStartDate().isBefore(req.effectiveStartDate())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Ngày áp dụng phải sau bảng giá đang hiệu lực (" + open.getEffectiveStartDate() + ")");
            }
            open.setEffectiveEndDate(req.effectiveStartDate().minusDays(1));
        });
        ServiceRatePlan p = new ServiceRatePlan();
        apply(p, req);
        return toResponse(planRepo.save(p));
    }

    @Transactional
    @CacheEvict(cacheNames = {"servicePlans", "serviceRates"}, allEntries = true)
    public PlanResponse updatePlan(Integer planId, PlanRequest req) {
        return updatePlan(planId, req, null);
    }

    /**
     * SRS 13#7: doi don gia dien/nuoc tai cho phai de lai lich su (rate_change_histories). Bang gia ap dung cho ca he
     * thong nen moi khu tro duoc ghi 1 dong "Dien"/"Nuoc"; can biet nguoi sua (actorId, lay tu token) vi changed_by bat buoc.
     */
    @Transactional
    @CacheEvict(cacheNames = {"servicePlans", "serviceRates"}, allEntries = true)
    public PlanResponse updatePlan(Integer planId, PlanRequest req, Integer actorId) {
        validatePlan(req);
        ServiceRatePlan p = planRepo.findById(planId).orElseThrow(() -> notFound("Bảng giá " + planId));
        BigDecimal oldElectric = p.getElectricUnitPrice();
        BigDecimal oldWater = p.getWaterUnitPrice();
        apply(p, req);
        if (actorId != null) {
            List<Integer> properties = em.createQuery("select pr.propertyId from Property pr", Integer.class).getResultList();
            for (Integer propertyId : properties) {
                if (oldElectric != null && oldElectric.compareTo(req.electricUnitPrice()) != 0) {
                    saveHistory(propertyId, "Điện", oldElectric, req.electricUnitPrice(), actorId);
                }
                if (oldWater != null && oldWater.compareTo(req.waterUnitPrice()) != 0) {
                    saveHistory(propertyId, "Nước", oldWater, req.waterUnitPrice(), actorId);
                }
            }
        }
        return toResponse(p);
    }

    @Cacheable("servicePlans")
    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return planRepo.findAllByOrderByEffectiveStartDateDesc().stream().map(this::toResponse).toList();
    }

    /** Bang gia ap dung tai mot ngay (mac dinh hom nay). */
    @Cacheable(cacheNames = "servicePlans", key = "'eff-' + #day")
    @Transactional(readOnly = true)
    public PlanResponse effectivePlan(LocalDate day) {
        LocalDate d = day == null ? LocalDate.now() : day;
        return planRepo.findEffectiveAt(d).stream().findFirst().map(this::toResponse)
                .orElseThrow(() -> notFound("bảng giá điện nước áp dụng ngày " + d));
    }

    // ------------------------------------------------------------------ UC 62: phi dich vu theo khu

    @Transactional
    @CacheEvict(cacheNames = {"servicePlans", "serviceRates"}, allEntries = true)
    public ServiceRateResponse createServiceRate(ServiceRateRequest req) {
        validateServiceRate(req);
        PropertyServiceRate r = new PropertyServiceRate();
        r.setProperty(em.getReference(Property.class, req.propertyId()));
        r.setServiceName(req.serviceName().trim());
        r.setChargeType(req.chargeType().toUpperCase());
        r.setUnitPrice(req.unitPrice());
        r.setIsActive(req.active() == null || req.active());
        return toResponse(serviceRateRepo.save(r));
    }

    @Transactional
    @CacheEvict(cacheNames = {"servicePlans", "serviceRates"}, allEntries = true)
    public ServiceRateResponse updateServiceRate(Integer rateId, ServiceRateRequest req) {
        validateServiceRate(req);
        PropertyServiceRate r = serviceRateRepo.findById(rateId)
                .orElseThrow(() -> notFound("Phí dịch vụ " + rateId));
        BigDecimal oldPrice = r.getUnitPrice();
        String oldName = r.getServiceName();
        r.setServiceName(req.serviceName().trim());
        r.setChargeType(req.chargeType().toUpperCase());
        r.setUnitPrice(req.unitPrice());
        if (req.active() != null) {
            r.setIsActive(req.active());
        }
        if (oldPrice.compareTo(req.unitPrice()) != 0) { // UC 64: luu lich su doi gia
            if (req.changedByUserId() == null) {
                throw bad("Đổi đơn giá cần changedByUserId");
            }
            saveHistory(r.getProperty().getPropertyId(), oldName, oldPrice, req.unitPrice(), req.changedByUserId());
        }
        return toResponse(r);
    }

    @Transactional
    @CacheEvict(cacheNames = {"servicePlans", "serviceRates"}, allEntries = true)
    public void deleteServiceRate(Integer rateId) {
        if (!serviceRateRepo.existsById(rateId)) {
            throw notFound("Phí dịch vụ " + rateId);
        }
        serviceRateRepo.deleteById(rateId);
    }

    @Cacheable(cacheNames = "serviceRates", key = "(#propertyId ?: 0) + '-' + #activeOnly")
    @Transactional(readOnly = true)
    public List<ServiceRateResponse> listServiceRates(Integer propertyId, Boolean activeOnly) {
        var list = Boolean.TRUE.equals(activeOnly)
                ? serviceRateRepo.findByPropertyPropertyIdAndIsActive(propertyId, true)
                : serviceRateRepo.findByPropertyPropertyIdOrderByServiceName(propertyId);
        return list.stream().map(this::toResponse).toList();
    }

    // ------------------------------------------------------------------ UC 63: phu thu & mien giam theo phong

    @Transactional
    public CustomRateResponse createCustomRate(CustomRateRequest req) {
        validateCustomRate(req);
        RoomCustomRate c = new RoomCustomRate();
        c.setRoom(em.getReference(Room.class, req.roomId()));
        applyCustom(c, req);
        return toResponse(customRateRepo.save(c));
    }

    @Transactional
    public CustomRateResponse updateCustomRate(Integer id, CustomRateRequest req) {
        validateCustomRate(req);
        RoomCustomRate c = customRateRepo.findById(id).orElseThrow(() -> notFound("Phụ thu/miễn giảm " + id));
        applyCustom(c, req);
        return toResponse(c);
    }

    @Transactional
    public void deleteCustomRate(Integer id) {
        if (!customRateRepo.existsById(id)) {
            throw notFound("Phụ thu/miễn giảm " + id);
        }
        customRateRepo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<CustomRateResponse> listCustomRates(Integer roomId) {
        return customRateRepo.findByRoomRoomId(roomId).stream().map(this::toResponse).toList();
    }

    // ------------------------------------------------------------------ UC 64: lich su

    @Transactional(readOnly = true)
    public List<RateHistoryResponse> history(Integer propertyId, String serviceName) {
        var list = (serviceName == null || serviceName.isBlank())
                ? historyRepo.findByPropertyPropertyIdOrderByChangedAtDesc(propertyId)
                : historyRepo.findByPropertyPropertyIdAndServiceNameOrderByChangedAtDesc(propertyId, serviceName);
        return list.stream().map(h -> new RateHistoryResponse(h.getHistoryId(), propertyId, h.getServiceName(),
                h.getOldPrice(), h.getNewPrice(), h.getChangedBy().getUserId(), h.getChangedAt())).toList();
    }

    private void saveHistory(Integer propertyId, String name, BigDecimal oldPrice, BigDecimal newPrice, Integer userId) {
        RateChangeHistory h = new RateChangeHistory();
        h.setProperty(em.getReference(Property.class, propertyId));
        h.setServiceName(name);
        h.setOldPrice(oldPrice);
        h.setNewPrice(newPrice);
        h.setChangedBy(em.getReference(User.class, userId));
        h.setChangedAt(LocalDateTime.now());
        historyRepo.save(h);
    }

    // ------------------------------------------------------------------ helpers

    private void validatePlan(PlanRequest r) {
        if (r.planName() == null || r.planName().isBlank() || r.electricUnitPrice() == null
                || r.waterUnitPrice() == null || r.effectiveStartDate() == null) {
            throw bad("Cần planName, electricUnitPrice, waterUnitPrice, effectiveStartDate");
        }
        if (r.electricUnitPrice().signum() < 0 || r.waterUnitPrice().signum() < 0) {
            throw bad("Đơn giá không được âm");
        }
        if (r.effectiveEndDate() != null && r.effectiveEndDate().isBefore(r.effectiveStartDate())) {
            throw bad("Ngày hết hiệu lực phải sau ngày áp dụng");
        }
    }

    private void validateServiceRate(ServiceRateRequest r) {
        if (r.propertyId() == null || r.serviceName() == null || r.serviceName().isBlank()
                || r.chargeType() == null || r.unitPrice() == null) {
            throw bad("Cần propertyId, serviceName, chargeType, unitPrice");
        }
        if (!CHARGE_TYPES.contains(r.chargeType().toUpperCase())) {
            throw bad("chargeType phải thuộc " + CHARGE_TYPES);
        }
        if (r.unitPrice().signum() < 0) {
            throw bad("Đơn giá không được âm");
        }
    }

    private void validateCustomRate(CustomRateRequest r) {
        if (r.roomId() == null || r.rateName() == null || r.rateName().isBlank() || r.customPrice() == null) {
            throw bad("Cần roomId, rateName, customPrice");
        }
    }

    private void apply(ServiceRatePlan p, PlanRequest r) {
        p.setPlanName(r.planName().trim());
        p.setElectricUnitPrice(r.electricUnitPrice());
        p.setWaterUnitPrice(r.waterUnitPrice());
        p.setEffectiveStartDate(r.effectiveStartDate());
        p.setEffectiveEndDate(r.effectiveEndDate());
    }

    private void applyCustom(RoomCustomRate c, CustomRateRequest r) {
        c.setRateName(r.rateName().trim());
        c.setCustomPrice(r.customPrice());
        c.setNotes(r.notes());
    }

    private PlanResponse toResponse(ServiceRatePlan p) {
        return new PlanResponse(p.getPlanId(), p.getPlanName(), p.getElectricUnitPrice(), p.getWaterUnitPrice(),
                p.getEffectiveStartDate(), p.getEffectiveEndDate());
    }

    private ServiceRateResponse toResponse(PropertyServiceRate r) {
        return new ServiceRateResponse(r.getRateId(), r.getProperty().getPropertyId(), r.getServiceName(),
                r.getChargeType(), r.getUnitPrice(), Boolean.TRUE.equals(r.getIsActive()));
    }

    private CustomRateResponse toResponse(RoomCustomRate c) {
        String kind = c.getCustomPrice().signum() < 0 ? "DISCOUNT" : "SURCHARGE";
        return new CustomRateResponse(c.getCustomRateId(), c.getRoom().getRoomId(), c.getRateName(),
                c.getCustomPrice(), c.getNotes(), kind);
    }

    private static BusinessException bad(String msg) {
        return new BusinessException(HttpStatus.BAD_REQUEST, msg);
    }

    private static BusinessException notFound(String what) {
        return new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy " + what);
    }
}

package com.group7.minihouse.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.group7.minihouse.dto.RateDtos.*;
import com.group7.minihouse.entity.Property;
import com.group7.minihouse.entity.PropertyServiceRate;
import com.group7.minihouse.entity.RateChangeHistory;
import com.group7.minihouse.entity.Room;
import com.group7.minihouse.entity.RoomCustomRate;
import com.group7.minihouse.entity.ServiceRatePlan;
import com.group7.minihouse.entity.User;
import com.group7.minihouse.repository.PropertyServiceRateRepository;
import com.group7.minihouse.repository.RateChangeHistoryRepository;
import com.group7.minihouse.repository.RoomCustomRateRepository;
import com.group7.minihouse.repository.ServiceRatePlanRepository;
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
    public PlanResponse createPlan(PlanRequest req) {
        validatePlan(req);
        planRepo.findFirstByEffectiveEndDateIsNullOrderByEffectiveStartDateDesc().ifPresent(open -> {
            if (!open.getEffectiveStartDate().isBefore(req.effectiveStartDate())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ngay ap dung phai sau bang gia dang hieu luc (" + open.getEffectiveStartDate() + ")");
            }
            open.setEffectiveEndDate(req.effectiveStartDate().minusDays(1));
        });
        ServiceRatePlan p = new ServiceRatePlan();
        apply(p, req);
        return toResponse(planRepo.save(p));
    }

    @Transactional
    public PlanResponse updatePlan(Integer planId, PlanRequest req) {
        validatePlan(req);
        ServiceRatePlan p = planRepo.findById(planId).orElseThrow(() -> notFound("Bang gia " + planId));
        apply(p, req);
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return planRepo.findAllByOrderByEffectiveStartDateDesc().stream().map(this::toResponse).toList();
    }

    /** Bang gia ap dung tai mot ngay (mac dinh hom nay). */
    @Transactional(readOnly = true)
    public PlanResponse effectivePlan(LocalDate day) {
        LocalDate d = day == null ? LocalDate.now() : day;
        return planRepo.findEffectiveAt(d).stream().findFirst().map(this::toResponse)
                .orElseThrow(() -> notFound("bang gia dien nuoc ap dung ngay " + d));
    }

    // ------------------------------------------------------------------ UC 62: phi dich vu theo khu

    @Transactional
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
    public ServiceRateResponse updateServiceRate(Integer rateId, ServiceRateRequest req) {
        validateServiceRate(req);
        PropertyServiceRate r = serviceRateRepo.findById(rateId)
                .orElseThrow(() -> notFound("Phi dich vu " + rateId));
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
                throw bad("Doi don gia can changedByUserId");
            }
            saveHistory(r.getProperty().getPropertyId(), oldName, oldPrice, req.unitPrice(), req.changedByUserId());
        }
        return toResponse(r);
    }

    @Transactional
    public void deleteServiceRate(Integer rateId) {
        if (!serviceRateRepo.existsById(rateId)) {
            throw notFound("Phi dich vu " + rateId);
        }
        serviceRateRepo.deleteById(rateId);
    }

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
        RoomCustomRate c = customRateRepo.findById(id).orElseThrow(() -> notFound("Phu thu/mien giam " + id));
        applyCustom(c, req);
        return toResponse(c);
    }

    @Transactional
    public void deleteCustomRate(Integer id) {
        if (!customRateRepo.existsById(id)) {
            throw notFound("Phu thu/mien giam " + id);
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
            throw bad("Can planName, electricUnitPrice, waterUnitPrice, effectiveStartDate");
        }
        if (r.electricUnitPrice().signum() < 0 || r.waterUnitPrice().signum() < 0) {
            throw bad("Don gia khong duoc am");
        }
        if (r.effectiveEndDate() != null && r.effectiveEndDate().isBefore(r.effectiveStartDate())) {
            throw bad("Ngay het hieu luc phai sau ngay ap dung");
        }
    }

    private void validateServiceRate(ServiceRateRequest r) {
        if (r.propertyId() == null || r.serviceName() == null || r.serviceName().isBlank()
                || r.chargeType() == null || r.unitPrice() == null) {
            throw bad("Can propertyId, serviceName, chargeType, unitPrice");
        }
        if (!CHARGE_TYPES.contains(r.chargeType().toUpperCase())) {
            throw bad("chargeType phai thuoc " + CHARGE_TYPES);
        }
        if (r.unitPrice().signum() < 0) {
            throw bad("Don gia khong duoc am");
        }
    }

    private void validateCustomRate(CustomRateRequest r) {
        if (r.roomId() == null || r.rateName() == null || r.rateName().isBlank() || r.customPrice() == null) {
            throw bad("Can roomId, rateName, customPrice");
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

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Khong tim thay " + what);
    }
}

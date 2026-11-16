package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.BillingDtos.AddItemRequest;
import com.group7.minihouse.phi.dto.BillingDtos.Anomaly;
import com.group7.minihouse.phi.dto.BillingDtos.CalculationResult;
import com.group7.minihouse.phi.dto.BillingDtos.CreatePeriodRequest;
import com.group7.minihouse.phi.dto.BillingDtos.EligibleContract;
import com.group7.minihouse.phi.dto.BillingDtos.FeeResponse;
import com.group7.minihouse.phi.dto.BillingDtos.Issue;
import com.group7.minihouse.phi.dto.BillingDtos.ItemResponse;
import com.group7.minihouse.phi.dto.BillingDtos.PeriodCreated;
import com.group7.minihouse.phi.dto.BillingDtos.PeriodResponse;
import com.group7.minihouse.phi.dto.BillingDtos.ServicePreview;
import com.group7.minihouse.phi.dto.BillingDtos.UpdateItemRequest;
import com.group7.minihouse.phi.repository.BillingPeriodRepository;
import com.group7.minihouse.phi.repository.CalculatedFeeRepository;
import com.group7.minihouse.phi.repository.FeeItemRepository;
import com.group7.minihouse.shared.entity.CalculatedMonthlyFee;
import com.group7.minihouse.shared.entity.FeeItemDetail;
import com.group7.minihouse.shared.entity.MeterReading;
import com.group7.minihouse.shared.entity.MonthlyBillingPeriod;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.entity.PropertyServiceRate;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.RoomCustomRate;
import com.group7.minihouse.shared.entity.ServiceRatePlan;
import com.group7.minihouse.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC66-75: tinh tien hang thang theo ky (khu tro + thang). Ky o trang thai DRAFT thi tinh/sua/tinh lai thoai mai,
 * duyet (APPROVED) xong thi khoa; UC77 phat hanh hoa don (InvoiceService cua Toan) chi chay khi ky khac DRAFT.
 *
 * Nguon su that cua tien la cac dong fee_item_details; cac cot rent/electric/water/service/surcharge/discount/
 * late_penalty/total o calculated_monthly_fees luon duoc tinh lai tu cac dong do (xem recompute) nen sua tay 1 dong
 * (UC74) khong lam lech tong. Dien nuoc chung cua khu (UC65/72) = cac khoan chi nhom "Dien nuoc chung" trong
 * operating_expenses (ghi bang /api/expenses cua Viet), chia cho cac phong theo so nguoi o.
 */
@Service
public class BillingService {

    static final String ITEM_RENT = "Tiền phòng";
    static final String ITEM_ELECTRIC = "Điện";
    static final String ITEM_WATER = "Nước";
    static final String ITEM_SURCHARGE = "Phụ thu";
    static final String ITEM_DISCOUNT = "Giảm trừ";
    static final String ITEM_PENALTY = "Phạt trễ hạn";
    /** Khoan nhap tay: tien to nay giu lai khi tinh lai ky, tinh vao phu thu (so duong) hoac giam tru (so am). */
    static final String MANUAL = "✎ ";
    static final String ITEM_SHARED = "Điện nước chung (phân bổ)";
    static final String SHARED_CATEGORY = "Điện nước chung";
    static final BigDecimal ANOMALY_RATIO = new BigDecimal("1.5");
    private static final List<String> LIVE_AT_MONTH =
            List.of("ACTIVE", "EXPIRING_SOON", "EXPIRED", "TERMINATED");

    private final BillingPeriodRepository periodRepo;
    private final CalculatedFeeRepository feeRepo;
    private final FeeItemRepository itemRepo;
    private final EntityManager em;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public BillingService(BillingPeriodRepository periodRepo, CalculatedFeeRepository feeRepo,
                          FeeItemRepository itemRepo, EntityManager em, ActivityLogService activityLogService,
                          Clock clock) {
        this.periodRepo = periodRepo;
        this.feeRepo = feeRepo;
        this.itemRepo = itemRepo;
        this.em = em;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ UC66: tao ky

    @Transactional
    public PeriodCreated createPeriod(CreatePeriodRequest req, Integer actorId, String ip) {
        if (req == null || req.propertyId() == null || req.monthYear() == null) {
            throw bad("Can propertyId va monthYear (YYYY-MM)");
        }
        YearMonth ym = parseMonth(req.monthYear());
        Property property = em.find(Property.class, req.propertyId());
        if (property == null) {
            throw BusinessException.notFound("Khu tro " + req.propertyId());
        }
        String key = ym.toString();
        if (periodRepo.existsByPropertyPropertyIdAndMonthYear(req.propertyId(), key)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Khu tro da co ky tinh tien thang " + key);
        }
        MonthlyBillingPeriod p = new MonthlyBillingPeriod();
        p.setProperty(property);
        p.setMonthYear(key);
        p.setCreationDate(LocalDateTime.now(clock));
        p.setStatus("DRAFT");
        MonthlyBillingPeriod saved = periodRepo.save(p);
        audit(actorId, "CREATE_BILLING_PERIOD", "Tao ky tinh tien " + key + " khu tro " + req.propertyId(), ip);
        List<EligibleContract> contracts = eligibleContracts(saved, ym).stream()
                .map(c -> new EligibleContract(c.getContractId(), c.getRoom().getRoomId(),
                        c.getRoom().getRoomCode(), c.getTenant().getTenantId(), c.getTenant().getFullName(),
                        c.getStatus()))
                .toList();
        return new PeriodCreated(toResponse(saved, 0, BigDecimal.ZERO), contracts);
    }

    @Transactional(readOnly = true)
    public List<PeriodResponse> listPeriods(Integer propertyId) {
        return periodRepo.findByPropertyPropertyIdOrderByMonthYearDesc(propertyId).stream()
                .map(p -> {
                    List<CalculatedMonthlyFee> fees = feeRepo.findByPeriod(p.getBillingPeriodId());
                    return toResponse(p, fees.size(), sum(fees));
                }).toList();
    }

    @Transactional(readOnly = true)
    public CalculationResult get(Integer periodId) {
        MonthlyBillingPeriod p = period(periodId);
        List<CalculatedMonthlyFee> fees = feeRepo.findByPeriod(periodId);
        return new CalculationResult(toResponse(p, fees.size(), sum(fees)),
                fees.stream().map(f -> toResponse(f, false)).toList(), List.of());
    }

    @Transactional(readOnly = true)
    public FeeResponse getFee(Integer feeId) {
        return toResponse(fee(feeId), true);
    }

    // ------------------------------------------------------------------ UC67: kiem tra dau vao

    @Transactional(readOnly = true)
    public List<Issue> validate(Integer periodId) {
        MonthlyBillingPeriod p = period(periodId);
        YearMonth ym = YearMonth.parse(p.getMonthYear());
        List<Issue> issues = new ArrayList<>();
        if (ratePlan(ym) == null) {
            issues.add(new Issue(null, "Chua co don gia dien nuoc hieu luc trong thang " + p.getMonthYear()));
        }
        var readingPeriod = readingPeriod(p.getProperty().getPropertyId(), ym);
        if (readingPeriod == null) {
            issues.add(new Issue(null, "Chua co ky ghi chi so dien nuoc cho thang " + p.getMonthYear()));
        }
        for (RentalContract c : eligibleContracts(p, ym)) {
            if (readingPeriod == null) {
                break;
            }
            MeterReading m = reading(c.getRoom().getRoomId(), readingPeriod.getPeriodId());
            if (m == null) {
                issues.add(new Issue(c.getRoom().getRoomCode(), "Thieu chi so dien nuoc"));
            } else if (!Boolean.TRUE.equals(m.getIsVerified())) {
                issues.add(new Issue(c.getRoom().getRoomCode(), "Chi so chua xac nhan"));
            }
        }
        return issues;
    }

    // ------------------------------------------------------------------ UC68-73: tinh tien

    /** Tinh (hoac tinh lai, UC74) toan bo ky DRAFT: xoa bang tinh cu roi dung lai tu hop dong, chi so, don gia. */
    @Transactional
    public CalculationResult calculate(Integer periodId, Integer actorId, String ip) {
        MonthlyBillingPeriod p = period(periodId);
        requireDraft(p);
        YearMonth ym = YearMonth.parse(p.getMonthYear());
        Integer propertyId = p.getProperty().getPropertyId();
        List<Issue> warnings = new ArrayList<>();

        // Khoan nhap tay (tien to MANUAL) duoc giu lai theo phong, dung lai sau khi tinh
        Map<Integer, List<Line>> manual = new HashMap<>();
        for (FeeItemDetail i : em.createQuery("select i from FeeItemDetail i where i.calculatedFee.billingPeriod.billingPeriodId = :p "
                + "and i.itemName like :pre order by i.detailId", FeeItemDetail.class)
                .setParameter("p", periodId).setParameter("pre", MANUAL + "%").getResultList()) {
            manual.computeIfAbsent(i.getCalculatedFee().getRoom().getRoomId(), k -> new ArrayList<>())
                    .add(new Line(i.getItemName(), i.getQuantity(), i.getUnitPrice()));
        }
        feeRepo.deleteItemsOfPeriod(periodId);
        feeRepo.deleteFeesOfPeriod(periodId);
        em.flush();
        em.clear();
        p = period(periodId);

        ServiceRatePlan plan = ratePlan(ym);
        if (plan == null) {
            warnings.add(new Issue(null, "Chua co don gia dien nuoc hieu luc, tien dien nuoc se la 0"));
        }
        var readingPeriod = readingPeriod(propertyId, ym);
        List<PropertyServiceRate> services = em.createQuery(
                "select s from PropertyServiceRate s where s.property.propertyId = :p and s.isActive = true "
                        + "order by s.rateId", PropertyServiceRate.class)
                .setParameter("p", propertyId).getResultList();

        // lan 1: moi phong 1 hop dong, dien nuoc theo chi so; ghi nho tong so nguoi o de chia dien nuoc chung
        List<Draft> drafts = new ArrayList<>();
        Set<Integer> usedRooms = new HashSet<>();
        for (RentalContract c : eligibleContracts(p, ym)) {
            Integer roomId = c.getRoom().getRoomId();
            if (!usedRooms.add(roomId)) {
                warnings.add(new Issue(c.getRoom().getRoomCode(), "Phong co nhieu hop dong trong thang, bo qua hop dong "
                        + c.getContractCode() + " (chinh tay o ky nay neu can)"));
                continue;
            }
            Draft d = new Draft(c);
            int days = daysInMonth(c, ym, effectiveEnd(c));
            if (days <= 0) {
                continue;
            }
            d.add(ITEM_RENT, BigDecimal.ONE, prorateRent(c.getMonthlyRentPrice(), days, ym.lengthOfMonth()));
            addMeter(d, c, plan, readingPeriod == null ? null : reading(roomId, readingPeriod.getPeriodId()),
                    warnings);
            d.residents = 1 + (int) othersInRoom(roomId, c.getTenant().getTenantId());
            drafts.add(d);
        }

        // lan 2: dich vu hang thang, phu thu/giam tru, phat tre han, dien nuoc chung
        BigDecimal sharedTotal = sharedCost(propertyId, ym);
        int totalResidents = drafts.stream().mapToInt(d -> d.residents).sum();
        for (Draft d : drafts) {
            RentalContract c = d.contract;
            for (PropertyServiceRate s : services) {
                addService(d, s);
            }
            for (RoomCustomRate r : em.createQuery("select r from RoomCustomRate r where r.room.roomId = :x "
                    + "order by r.customRateId", RoomCustomRate.class)
                    .setParameter("x", c.getRoom().getRoomId()).getResultList()) {
                if (r.getCustomPrice().signum() > 0) {
                    d.add(ITEM_SURCHARGE + ": " + r.getRateName(), BigDecimal.ONE, r.getCustomPrice());
                } else if (r.getCustomPrice().signum() < 0) {
                    d.add(ITEM_DISCOUNT + ": " + r.getRateName(), BigDecimal.ONE, r.getCustomPrice());
                }
            }
            long overdueDays = em.createQuery("select coalesce(sum(t.overdueDays), 0) from TenantDebt t "
                    + "where t.room.roomId = :room and t.tenant.tenantId = :tenant and t.debtStatus <> 'CLEARED'",
                    Long.class).setParameter("room", c.getRoom().getRoomId())
                    .setParameter("tenant", c.getTenant().getTenantId()).getSingleResult();
            if (overdueDays > 0 && c.getLateFeePerDay() != null && c.getLateFeePerDay().signum() > 0) {
                d.add(ITEM_PENALTY, BigDecimal.valueOf(overdueDays), c.getLateFeePerDay());
            }
            if (sharedTotal.signum() > 0 && totalResidents > 0) {
                BigDecimal share = sharedTotal.multiply(BigDecimal.valueOf(d.residents))
                        .divide(BigDecimal.valueOf(totalResidents), 0, RoundingMode.HALF_UP);
                d.add(ITEM_SHARED, BigDecimal.ONE, share);
            }
        }

        for (Draft d : drafts) {
            List<Line> kept = manual.remove(d.contract.getRoom().getRoomId());
            if (kept != null) {
                d.lines.addAll(kept);
            }
        }
        for (Map.Entry<Integer, List<Line>> lost : manual.entrySet()) {
            warnings.add(new Issue(null, "Co " + lost.getValue().size() + " khoan nhap tay cua phong khong con trong ky "
                    + "(het hop dong trong thang) nen bi bo: " + lost.getValue().stream()
                    .map(l -> l.name().substring(MANUAL.length())).toList()));
        }
        MonthlyBillingPeriod periodRef = em.getReference(MonthlyBillingPeriod.class, periodId);
        for (Draft d : drafts) {
            CalculatedMonthlyFee f = new CalculatedMonthlyFee();
            f.setBillingPeriod(periodRef);
            f.setRoom(d.contract.getRoom());
            f.setContract(d.contract);
            zero(f);
            f = feeRepo.save(f);
            List<FeeItemDetail> items = new ArrayList<>();
            for (Line l : d.lines) {
                FeeItemDetail i = new FeeItemDetail();
                i.setCalculatedFee(f);
                i.setItemName(l.name);
                i.setQuantity(l.quantity);
                i.setUnitPrice(l.unitPrice);
                i.setSubtotal(subtotal(l.quantity, l.unitPrice));
                items.add(itemRepo.save(i));
            }
            recompute(f, items);
        }
        audit(actorId, "CALCULATE_BILLING", "Tinh tien ky " + p.getMonthYear() + " (" + drafts.size() + " phong)", ip);
        List<CalculatedMonthlyFee> fees = feeRepo.findByPeriod(periodId);
        return new CalculationResult(toResponse(p, fees.size(), sum(fees)),
                fees.stream().map(f -> toResponse(f, false)).toList(), warnings);
    }

    // ------------------------------------------------------------------ UC74: sua 1 dong

    @Transactional
    public FeeResponse updateItem(Integer detailId, UpdateItemRequest req, Integer actorId, String ip) {
        FeeItemDetail item = itemRepo.findById(detailId)
                .orElseThrow(() -> BusinessException.notFound("Dong chi tiet " + detailId));
        CalculatedMonthlyFee fee = item.getCalculatedFee();
        requireDraft(fee.getBillingPeriod());
        if (req == null || req.quantity() == null && req.unitPrice() == null) {
            throw bad("Can quantity hoac unitPrice");
        }
        BigDecimal q = req.quantity() != null ? req.quantity() : item.getQuantity();
        BigDecimal u = req.unitPrice() != null ? req.unitPrice() : item.getUnitPrice();
        if (q.signum() < 0) {
            throw bad("So luong khong duoc am");
        }
        boolean credit = item.getItemName().startsWith(ITEM_DISCOUNT);
        if (!credit && u.signum() < 0 || credit && u.signum() > 0) {
            throw bad(credit ? "Dong giam tru phai co don gia am" : "Don gia khong duoc am");
        }
        item.setQuantity(q);
        item.setUnitPrice(u);
        item.setSubtotal(subtotal(q, u));
        recompute(fee, itemRepo.findByCalculatedFeeCalculatedFeeIdOrderByDetailId(fee.getCalculatedFeeId()));
        audit(actorId, "UPDATE_FEE_ITEM", "Sua dong " + item.getItemName() + " cua bang tinh " + fee.getCalculatedFeeId(),
                ip);
        return toResponse(fee, true);
    }

    // ------------------------------------------------------------------ UC75: bat thuong, duyet

    @Transactional(readOnly = true)
    public List<Anomaly> anomalies(Integer periodId) {
        period(periodId);
        List<Anomaly> out = new ArrayList<>();
        for (CalculatedMonthlyFee f : feeRepo.findByPeriod(periodId)) {
            BigDecimal avg = feeRepo.averageOfOtherPeriods(f.getRoom().getRoomId(), periodId);
            if (isAnomaly(f.getTotalCalculatedAmount(), avg)) {
                out.add(new Anomaly(f.getCalculatedFeeId(), f.getRoom().getRoomCode(),
                        f.getTotalCalculatedAmount(), avg.setScale(0, RoundingMode.HALF_UP)));
            }
        }
        return out;
    }

    @Transactional
    public PeriodResponse approve(Integer periodId, Integer actorId, String ip) {
        MonthlyBillingPeriod p = period(periodId);
        requireDraft(p);
        List<CalculatedMonthlyFee> fees = feeRepo.findByPeriod(periodId);
        if (fees.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ky chua co bang tinh nao, hay tinh tien truoc khi duyet");
        }
        p.setStatus("APPROVED");
        audit(actorId, "APPROVE_BILLING", "Duyet ky tinh tien " + p.getMonthYear(), ip);
        return toResponse(p, fees.size(), sum(fees));
    }

    // ------------------------------------------------------------------ them / xoa khoan trong bang tinh (ky DRAFT)

    /** Them 1 khoan tay vao phong. Khoan nay duoc giu lai khi tinh lai ky. */
    @Transactional
    public FeeResponse addItem(Integer feeId, AddItemRequest req, Integer actorId, String ip) {
        CalculatedMonthlyFee fee = fee(feeId);
        requireDraft(fee.getBillingPeriod());
        if (req == null || req.itemName() == null || req.itemName().isBlank()) {
            throw bad("Can ten khoan");
        }
        String name = req.itemName().trim();
        if (name.length() > 90) {
            throw bad("Ten khoan toi da 90 ky tu");
        }
        if (req.quantity() == null || req.quantity().signum() <= 0) {
            throw bad("So luong phai lon hon 0");
        }
        if (req.unitPrice() == null || req.unitPrice().signum() == 0) {
            throw bad("Don gia phai khac 0 (so am la giam tru)");
        }
        FeeItemDetail i = new FeeItemDetail();
        i.setCalculatedFee(fee);
        i.setItemName(MANUAL + name);
        i.setQuantity(req.quantity());
        i.setUnitPrice(req.unitPrice());
        i.setSubtotal(subtotal(req.quantity(), req.unitPrice()));
        itemRepo.saveAndFlush(i);
        recompute(fee, itemRepo.findByCalculatedFeeCalculatedFeeIdOrderByDetailId(feeId));
        audit(actorId, "ADD_FEE_ITEM", "Them khoan '" + name + "' vao bang tinh " + feeId, ip);
        return toResponse(fee, true);
    }

    /** Xoa 1 dong (ke ca dong tu tinh; tinh lai ky thi dong tu tinh se hien lai). */
    @Transactional
    public FeeResponse deleteItem(Integer detailId, Integer actorId, String ip) {
        FeeItemDetail item = itemRepo.findById(detailId).orElseThrow(() -> BusinessException.notFound("Dong chi tiet " + detailId));
        CalculatedMonthlyFee fee = item.getCalculatedFee();
        requireDraft(fee.getBillingPeriod());
        String name = item.getItemName();
        itemRepo.delete(item);
        itemRepo.flush();
        recompute(fee, itemRepo.findByCalculatedFeeCalculatedFeeIdOrderByDetailId(fee.getCalculatedFeeId()));
        audit(actorId, "DELETE_FEE_ITEM", "Xoa dong '" + name + "' cua bang tinh " + fee.getCalculatedFeeId(), ip);
        return toResponse(fee, true);
    }

    // ------------------------------------------------------------------ xem truoc anh huong cua dich vu

    @Transactional(readOnly = true)
    public ServicePreview servicePreview(String chargeType, BigDecimal unitPrice) {
        if (chargeType == null || unitPrice == null || unitPrice.signum() < 0) {
            throw bad("Can chargeType va unitPrice");
        }
        List<RentalContract> live = em.createQuery("select c from RentalContract c where c.status in ('ACTIVE', 'EXPIRING_SOON')",
                RentalContract.class).getResultList();
        long rooms = live.stream().map(c -> c.getRoom().getRoomId()).distinct().count();
        long units;
        switch (chargeType) {
            case "FIXED_ROOM" -> units = rooms;
            case "PER_PERSON" -> units = live.stream().mapToLong(c -> 1 + othersInRoom(c.getRoom().getRoomId(), c.getTenant().getTenantId())).sum();
            case "PER_VEHICLE" -> units = live.stream().map(c -> c.getTenant().getTenantId()).distinct().mapToLong(t ->
                    em.createQuery("select count(v) from TenantVehicle v where v.tenant.tenantId = :x and v.isActive = true", Long.class)
                            .setParameter("x", t).getSingleResult()).sum();
            default -> throw bad("chargeType khong ho tro tinh tu dong: " + chargeType);
        }
        return new ServicePreview(rooms, units, unitPrice.multiply(BigDecimal.valueOf(units)));
    }

    /** So nguoi o cung da khai trong phong (khong tinh nguoi dung ten hop dong, nguoi do luon la 1 nguoi). */
    private long othersInRoom(Integer roomId, Integer contractTenantId) {
        return em.createQuery("select count(r) from RoomResident r where r.room.roomId = :room and r.isActive = true "
                + "and (r.tenant is null or r.tenant.tenantId <> :tenant)", Long.class)
                .setParameter("room", roomId).setParameter("tenant", contractTenantId).getSingleResult();
    }

    // ------------------------------------------------------------------ cong thuc thuan (de test)

    /** Tien thuc te = gia * so ngay o / so ngay trong thang, lam tron den dong; du thang thi giu nguyen gia. */
    static BigDecimal prorateRent(BigDecimal monthlyRent, int days, int daysInMonth) {
        if (days >= daysInMonth) {
            return monthlyRent;
        }
        return monthlyRent.multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(daysInMonth), 0, RoundingMode.HALF_UP);
    }

    static boolean isAnomaly(BigDecimal total, BigDecimal average) {
        return average != null && average.signum() > 0 && total.compareTo(average.multiply(ANOMALY_RATIO)) > 0;
    }

    static BigDecimal subtotal(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    /** Tinh lai cac cot thanh phan va tong tu danh sach dong chi tiet (nguon su that). */
    static void recompute(CalculatedMonthlyFee f, List<FeeItemDetail> items) {
        BigDecimal rent = BigDecimal.ZERO;
        BigDecimal electric = BigDecimal.ZERO;
        BigDecimal water = BigDecimal.ZERO;
        BigDecimal service = BigDecimal.ZERO;
        BigDecimal surcharge = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal penalty = BigDecimal.ZERO;
        for (FeeItemDetail i : items) {
            String n = i.getItemName();
            BigDecimal s = i.getSubtotal();
            if (n.startsWith(MANUAL)) {
                if (s.signum() >= 0) {
                    surcharge = surcharge.add(s);
                } else {
                    discount = discount.add(s.negate());
                }
            } else if (n.equals(ITEM_RENT)) {
                rent = rent.add(s);
            } else if (n.equals(ITEM_ELECTRIC)) {
                electric = electric.add(s);
            } else if (n.equals(ITEM_WATER)) {
                water = water.add(s);
            } else if (n.startsWith(ITEM_SURCHARGE)) {
                surcharge = surcharge.add(s);
            } else if (n.startsWith(ITEM_DISCOUNT)) {
                discount = discount.add(s.negate());
            } else if (n.equals(ITEM_PENALTY)) {
                penalty = penalty.add(s);
            } else {
                service = service.add(s);
            }
        }
        f.setRentAmount(rent);
        f.setElectricAmount(electric);
        f.setWaterAmount(water);
        f.setServiceAmount(service);
        f.setSurchargeAmount(surcharge);
        f.setDiscountAmount(discount);
        f.setLatePenaltyAmount(penalty);
        f.setTotalCalculatedAmount(rent.add(electric).add(water).add(service).add(surcharge).subtract(discount)
                .add(penalty));
    }

    // ------------------------------------------------------------------ noi bo

    private void addMeter(Draft d, RentalContract c, ServiceRatePlan plan, MeterReading m, List<Issue> warnings) {
        String room = c.getRoom().getRoomCode();
        if (m == null) {
            warnings.add(new Issue(room, "Thieu chi so dien nuoc, tien dien nuoc = 0"));
            return;
        }
        if (!Boolean.TRUE.equals(m.getIsVerified())) {
            warnings.add(new Issue(room, "Chi so dien nuoc chua xac nhan"));
        }
        if (plan == null) {
            return;
        }
        d.add(ITEM_ELECTRIC, BigDecimal.valueOf(m.getUsedElectric()), plan.getElectricUnitPrice());
        d.add(ITEM_WATER, BigDecimal.valueOf(m.getUsedWater()), plan.getWaterUnitPrice());
    }

    private void addService(Draft d, PropertyServiceRate s) {
        switch (s.getChargeType()) {
            case "FIXED_ROOM" -> d.add(s.getServiceName(), BigDecimal.ONE, s.getUnitPrice());
            case "PER_PERSON" -> d.add(s.getServiceName(), BigDecimal.valueOf(d.residents), s.getUnitPrice());
            case "PER_VEHICLE" -> {
                long vehicles = em.createQuery("select count(v) from TenantVehicle v where v.tenant.tenantId = :x "
                        + "and v.isActive = true", Long.class)
                        .setParameter("x", d.contract.getTenant().getTenantId()).getSingleResult();
                if (vehicles > 0) {
                    d.add(s.getServiceName(), BigDecimal.valueOf(vehicles), s.getUnitPrice());
                }
            }
            default -> { /* PER_METER_UNIT: chua ap dung tu dong, nhap tay o UC74 neu can */ }
        }
    }

    private BigDecimal sharedCost(Integer propertyId, YearMonth ym) {
        BigDecimal v = em.createQuery("select coalesce(sum(e.amount), 0) from OperatingExpense e "
                + "where e.property.propertyId = :p and e.expenseCategory = :c "
                + "and e.expenseDate between :from and :to", BigDecimal.class)
                .setParameter("p", propertyId).setParameter("c", SHARED_CATEGORY)
                .setParameter("from", ym.atDay(1)).setParameter("to", ym.atEndOfMonth()).getSingleResult();
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Hop dong chong len thang ym cua khu tro; thu tu theo ma phong. */
    private List<RentalContract> eligibleContracts(MonthlyBillingPeriod p, YearMonth ym) {
        return em.createQuery("select c from RentalContract c where c.room.property.propertyId = :p "
                + "and c.startDate <= :last and c.endDate >= :first and c.status in :st "
                + "order by c.room.roomCode, c.startDate", RentalContract.class)
                .setParameter("p", p.getProperty().getPropertyId())
                .setParameter("first", ym.atDay(1)).setParameter("last", ym.atEndOfMonth())
                .setParameter("st", LIVE_AT_MONTH).getResultList().stream()
                .filter(c -> daysInMonth(c, ym, effectiveEnd(c)) > 0).toList();
    }

    /** Ngay het hieu luc thuc te: hop dong da cham dut thi lay ngay cham dut, nguoc lai ngay het han. */
    private LocalDate effectiveEnd(RentalContract c) {
        if (!"TERMINATED".equals(c.getStatus())) {
            return c.getEndDate();
        }
        LocalDateTime at = em.createQuery("select max(a.adjustedAt) from ContractAdjustment a "
                + "where a.contract.contractId = :x and a.adjustmentType = 'TERMINATED'", LocalDateTime.class)
                .setParameter("x", c.getContractId()).getSingleResult();
        return at == null || at.toLocalDate().isAfter(c.getEndDate()) ? c.getEndDate() : at.toLocalDate();
    }

    private static int daysInMonth(RentalContract c, YearMonth ym, LocalDate end) {
        LocalDate from = c.getStartDate().isAfter(ym.atDay(1)) ? c.getStartDate() : ym.atDay(1);
        LocalDate to = end.isBefore(ym.atEndOfMonth()) ? end : ym.atEndOfMonth();
        return (int) ChronoUnit.DAYS.between(from, to) + 1;
    }

    private ServiceRatePlan ratePlan(YearMonth ym) {
        List<ServiceRatePlan> plans = em.createQuery("select p from ServiceRatePlan p "
                + "where p.effectiveStartDate <= :last and (p.effectiveEndDate is null or p.effectiveEndDate >= :first) "
                + "order by p.effectiveStartDate desc", ServiceRatePlan.class)
                .setParameter("first", ym.atDay(1)).setParameter("last", ym.atEndOfMonth())
                .setMaxResults(1).getResultList();
        return plans.isEmpty() ? null : plans.get(0);
    }

    private com.group7.minihouse.shared.entity.UtilityReadingPeriod readingPeriod(Integer propertyId, YearMonth ym) {
        List<com.group7.minihouse.shared.entity.UtilityReadingPeriod> l = em.createQuery(
                "select r from UtilityReadingPeriod r where r.property.propertyId = :p "
                        + "and r.startDate between :first and :last order by r.startDate desc",
                com.group7.minihouse.shared.entity.UtilityReadingPeriod.class)
                .setParameter("p", propertyId).setParameter("first", ym.atDay(1))
                .setParameter("last", ym.atEndOfMonth()).setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    private MeterReading reading(Integer roomId, Integer periodId) {
        List<MeterReading> l = em.createQuery("select m from MeterReading m where m.room.roomId = :r "
                + "and m.period.periodId = :p", MeterReading.class)
                .setParameter("r", roomId).setParameter("p", periodId).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    private long count(String jpql, Integer id) {
        return em.createQuery(jpql, Long.class).setParameter("x", id).getSingleResult();
    }

    private MonthlyBillingPeriod period(Integer id) {
        return periodRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Ky tinh tien " + id));
    }

    private CalculatedMonthlyFee fee(Integer id) {
        return feeRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Bang tinh tien " + id));
    }

    private static void requireDraft(MonthlyBillingPeriod p) {
        if (!"DRAFT".equals(p.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Ky " + p.getMonthYear() + " o trang thai " + p.getStatus() + ", chi sua duoc khi DRAFT");
        }
    }

    private static YearMonth parseMonth(String s) {
        try {
            return YearMonth.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw bad("monthYear phai dang YYYY-MM (vd 2026-11)");
        }
    }

    private static void zero(CalculatedMonthlyFee f) {
        f.setRentAmount(BigDecimal.ZERO);
        f.setElectricAmount(BigDecimal.ZERO);
        f.setWaterAmount(BigDecimal.ZERO);
        f.setServiceAmount(BigDecimal.ZERO);
        f.setSurchargeAmount(BigDecimal.ZERO);
        f.setDiscountAmount(BigDecimal.ZERO);
        f.setLatePenaltyAmount(BigDecimal.ZERO);
        f.setTotalCalculatedAmount(BigDecimal.ZERO);
    }

    private static BigDecimal sum(List<CalculatedMonthlyFee> fees) {
        return fees.stream().map(CalculatedMonthlyFee::getTotalCalculatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void audit(Integer actorId, String action, String description, String ip) {
        if (actorId != null) {
            activityLogService.log(actorId, action, description, ip);
        }
    }

    private static BusinessException bad(String m) {
        return new BusinessException(HttpStatus.BAD_REQUEST, m);
    }

    private static PeriodResponse toResponse(MonthlyBillingPeriod p, long rooms, BigDecimal total) {
        return new PeriodResponse(p.getBillingPeriodId(), p.getProperty().getPropertyId(), p.getMonthYear(),
                p.getStatus(), p.getCreationDate(), rooms, total);
    }

    private FeeResponse toResponse(CalculatedMonthlyFee f, boolean withItems) {
        List<ItemResponse> items = withItems
                ? itemRepo.findByCalculatedFeeCalculatedFeeIdOrderByDetailId(f.getCalculatedFeeId()).stream()
                        .map(i -> new ItemResponse(i.getDetailId(), i.getItemName(), i.getQuantity(),
                                i.getUnitPrice(), i.getSubtotal())).toList()
                : List.of();
        return new FeeResponse(f.getCalculatedFeeId(), f.getRoom().getRoomId(), f.getRoom().getRoomCode(),
                f.getContract().getContractId(), f.getContract().getTenant().getFullName(), f.getRentAmount(),
                f.getElectricAmount(), f.getWaterAmount(), f.getServiceAmount(), f.getSurchargeAmount(),
                f.getDiscountAmount(), f.getLatePenaltyAmount(), f.getTotalCalculatedAmount(), items);
    }

    /** Ban nhap 1 phong truoc khi luu. */
    private static final class Draft {
        final RentalContract contract;
        final List<Line> lines = new ArrayList<>();
        int residents = 1;

        Draft(RentalContract contract) {
            this.contract = contract;
        }

        void add(String name, BigDecimal quantity, BigDecimal unitPrice) {
            lines.add(new Line(name, quantity, unitPrice));
        }
    }

    private record Line(String name, BigDecimal quantity, BigDecimal unitPrice) {}
}

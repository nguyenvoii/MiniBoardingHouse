package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.BillingDtos.SharedAllocation;
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
import jakarta.persistence.LockModeType;
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
    static final String ITEM_SHARED = "Điện nước và dịch vụ chung (phân bổ)";
    /** Phụ phí thiết bị lắp cho phòng (room_addon_fees), tính vào nhóm dịch vụ. */
    static final String ITEM_ADDON = "Thiết bị: ";
    private static final List<String> SHARED_CATEGORIES = List.of("Điện nước chung", "Dịch vụ chung");
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
        addContractWarnings(p, ym, issues);
        for (RentalContract c : eligibleContracts(p, ym)) {
            if (readingPeriod == null) {
                break;
            }
            MeterReading m = reading(c.getRoom().getRoomId(), readingPeriod.getPeriodId());
            if (!readingAppliesTo(m, c)) {
                issues.add(new Issue(c.getRoom().getRoomCode(), "Thieu chi so dien nuoc"));
            } else if (!Boolean.TRUE.equals(m.getIsVerified())) {
                issues.add(new Issue(c.getRoom().getRoomCode(), "Chi so chua xac nhan"));
            }
        }
        return issues;
    }

    // ------------------------------------------------------------------ UC68-73: tinh tien

    @Transactional
    public boolean calculateUnpreparedPeriod(Integer periodId) {
        MonthlyBillingPeriod period = period(periodId);
        em.refresh(period, LockModeType.PESSIMISTIC_WRITE);
        if (!"DRAFT".equals(period.getStatus()) || !feeRepo.findByPeriod(periodId).isEmpty()
                || !validate(periodId).isEmpty()) {
            return false;
        }
        calculate(period, null, null, SharedAllocation.PER_RESIDENT);
        return true;
    }

    /** Tinh (hoac tinh lai, UC74) toan bo ky DRAFT: xoa bang tinh cu roi dung lai tu hop dong, chi so, don gia. */
    @Transactional
    public CalculationResult calculate(Integer periodId, Integer actorId, String ip) {
        return calculate(periodId, actorId, ip, SharedAllocation.PER_RESIDENT);
    }

    @Transactional
    public CalculationResult calculate(Integer periodId, Integer actorId, String ip,
                                       SharedAllocation sharedAllocation) {
        MonthlyBillingPeriod p = period(periodId);
        em.refresh(p, LockModeType.PESSIMISTIC_WRITE);
        return calculate(p, actorId, ip, sharedAllocation);
    }

    private CalculationResult calculate(MonthlyBillingPeriod p, Integer actorId, String ip,
                                        SharedAllocation sharedAllocation) {
        requireDraft(p);
        Integer periodId = p.getBillingPeriodId();
        long invoiced = em.createQuery("select count(i) from Invoice i "
                + "where i.calculatedFee.billingPeriod.billingPeriodId = :p", Long.class)
                .setParameter("p", periodId).getSingleResult();
        if (invoiced > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ky " + p.getMonthYear() + " da co hoa don (ke ca hoa don "
                    + "da huy) nen khong tinh lai toan ky duoc. Sua tung khoan trong bang tinh cua phong can sua roi duyet lai");
        }
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
        // Xac dinh hop dong nao duoc tinh TRUOC khi xoa bang tinh cu (hop dong da tung duoc tinh = da ban giao)
        Set<Integer> eligibleIds = new HashSet<>();
        eligibleContracts(p, ym).forEach(c -> eligibleIds.add(c.getContractId()));
        addContractWarnings(p, ym, warnings);
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
        // Phong doi khach giua thang (nhieu hop dong chong len thang): moi hop dong 1 bang tinh, tien phong va dich vu
        // theo so ngay o cua tung hop dong; dien nuoc (1 chi so/phong/ky) tinh cho hop dong sau cung (khach hien tai).
        Map<Integer, List<MeterReading>> closingReadings = transferClosingReadings(eligibleIds, ym);
        List<Draft> drafts = new ArrayList<>();
        Map<Integer, List<RentalContract>> byRoom = new java.util.LinkedHashMap<>();
        for (RentalContract c : candidateContracts(p, ym)) {
            if (!eligibleIds.contains(c.getContractId())) {
                continue;
            }
            byRoom.computeIfAbsent(c.getRoom().getRoomId(), k -> new ArrayList<>()).add(c);
        }
        for (Map.Entry<Integer, List<RentalContract>> e : byRoom.entrySet()) {
            Integer roomId = e.getKey();
            List<RentalContract> list = e.getValue();
            boolean shared = list.size() > 1;
            for (int k = 0; k < list.size(); k++) {
                RentalContract c = list.get(k);
                int days = daysInMonth(c, ym, effectiveEnd(c));
                if (days <= 0) {
                    continue;
                }
                Draft d = new Draft(c);
                d.shared = shared;
                d.primary = k == list.size() - 1;
                d.days = days;
                d.add(ITEM_RENT, BigDecimal.ONE, prorateRent(c.getMonthlyRentPrice(), days, ym.lengthOfMonth()));
                if (d.primary) {
                    MeterReading current = readingPeriod == null ? null : reading(roomId, readingPeriod.getPeriodId());
                    if (!readingAppliesTo(current, c)) {
                        addMeter(d, c.getRoom().getRoomCode(), plan, null, warnings);
                    } else if (current.getClosingContract() == null) {
                        addMeter(d, c.getRoom().getRoomCode(), plan, current, warnings);
                    }
                }
                for (MeterReading closing : closingReadings.getOrDefault(c.getContractId(), List.of())) {
                    addMeter(d, closing.getRoom().getRoomCode(), plan, closing, warnings);
                }
                d.residents = 1 + (int) othersInRoom(roomId, c.getTenant().getTenantId());
                drafts.add(d);
            }
            if (shared) {
                warnings.add(new Issue(list.get(0).getRoom().getRoomCode(), "Phong doi khach giua thang (" + list.size()
                        + " hop dong): tien phong va dich vu tinh theo so ngay cua tung khach, dien nuoc tinh cho khach "
                        + "sau cung; chinh tay (them/sua khoan) neu can chia dien nuoc"));
            }
        }

        // lan 2: dich vu hang thang, phu thu/giam tru, phat tre han, dien nuoc chung
        BigDecimal sharedTotal = sharedCost(propertyId, ym);
        int totalWeight = drafts.stream().filter(d -> d.primary)
                .mapToInt(d -> sharedAllocation == SharedAllocation.PER_ROOM ? 1 : d.residents).sum();
        int allocatedWeight = 0;
        BigDecimal allocatedCost = BigDecimal.ZERO;
        for (Draft d : drafts) {
            RentalContract c = d.contract;
            for (PropertyServiceRate s : services) {
                addService(d, s, ym);
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
            // So ngay tre = tu han cua hoa don con no cu nhat den hom nay, tinh truc tiep tu hoa don (khong dung
            // bang tenant_debts vi bang do chi duoc lam moi khi co nguoi mo trang Cong no / chay job).
            LocalDate oldestDue = em.createQuery("select min(i.dueDate) from Invoice i "
                    + "where i.calculatedFee.room.roomId = :room and i.tenant.tenantId = :tenant "
                    + "and i.paymentStatus in ('UNPAID', 'PARTIALLY_PAID', 'OVERDUE') "
                    + "and i.totalAmount > i.paidAmount and i.dueDate < :today", LocalDate.class)
                    .setParameter("room", c.getRoom().getRoomId())
                    .setParameter("tenant", c.getTenant().getTenantId())
                    .setParameter("today", LocalDate.now(clock)).getSingleResult();
            // M11: khong cong don. Ngay nao da bi phat o ky truoc (ky co dong phat tre han cua hop dong nay) thi khong
            // phat lai: chi dem tu ngay lap ky truoc do tro di.
            LocalDateTime lastPenaltyAt = em.createQuery("select max(i.calculatedFee.billingPeriod.creationDate) "
                            + "from FeeItemDetail i where i.calculatedFee.contract.contractId = :c "
                            + "and i.itemName = :n and i.calculatedFee.billingPeriod.monthYear < :ym", LocalDateTime.class)
                    .setParameter("c", c.getContractId()).setParameter("n", ITEM_PENALTY)
                    .setParameter("ym", ym.toString()).getSingleResult();
            LocalDate countFrom = oldestDue;
            if (oldestDue != null && lastPenaltyAt != null && lastPenaltyAt.toLocalDate().isAfter(oldestDue)) {
                countFrom = lastPenaltyAt.toLocalDate();
            }
            long overdueDays = countFrom == null ? 0 : ChronoUnit.DAYS.between(countFrom, LocalDate.now(clock));
            if (overdueDays > 0 && c.getLateFeePerDay() != null && c.getLateFeePerDay().signum() > 0) {
                d.add(ITEM_PENALTY, BigDecimal.valueOf(overdueDays), c.getLateFeePerDay());
            }
            if (d.primary) {
                addAddonFees(d, ym);
            }
            if (d.primary && sharedTotal.signum() > 0 && totalWeight > 0) {
                allocatedWeight += sharedAllocation == SharedAllocation.PER_ROOM ? 1 : d.residents;
                BigDecimal cumulativeCost = sharedTotal.multiply(BigDecimal.valueOf(allocatedWeight))
                        .divide(BigDecimal.valueOf(totalWeight), 0, RoundingMode.HALF_UP);
                d.add(ITEM_SHARED, BigDecimal.ONE, cumulativeCost.subtract(allocatedCost));
                allocatedCost = cumulativeCost;
            }
        }

        for (int k = drafts.size() - 1; k >= 0; k--) { // phong doi khach: khoan nhap tay ve hop dong sau cung
            Draft d = drafts.get(k);
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
        if (item.getItemName().startsWith(MANUAL)) {
            // khoan nhap tay: don gia duong = phu thu, am = giam tru (cho sua so luong / don gia, khong duoc bang 0)
            if (u.signum() == 0) {
                throw bad("Don gia phai khac 0 (so am la giam tru)");
            }
        } else {
            boolean credit = item.getItemName().startsWith(ITEM_DISCOUNT);
            if (!credit && u.signum() < 0 || credit && u.signum() > 0) {
                throw bad(credit ? "Dong giam tru phai co don gia am" : "Don gia khong duoc am");
            }
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
        return approve(periodId, actorId, ip, false);
    }

    /**
     * Duyet ky. Phong thieu chi so dien nuoc se bi tinh dien nuoc = 0 roi khoa ky, nen mac dinh chan duyet va liet ke
     * cac phong thieu; force=true de van duyet khi chu y de dien nuoc bang 0.
     */
    @Transactional
    public PeriodResponse approve(Integer periodId, Integer actorId, String ip, boolean force) {
        MonthlyBillingPeriod p = period(periodId);
        requireDraft(p);
        List<CalculatedMonthlyFee> fees = feeRepo.findByPeriod(periodId);
        if (fees.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ky chua co bang tinh nao, hay tinh tien truoc khi duyet");
        }
        if (!force) {
            List<String> missing = roomsMissingReading(p, fees);
            if (!missing.isEmpty()) {
                throw new BusinessException(HttpStatus.CONFLICT, "Con " + missing.size() + " phong thieu chi so dien nuoc "
                        + missing + ": dien nuoc dang bang 0. Ghi chi so roi tinh tien lai, hoac duyet kem force=true "
                        + "neu dung la khong tinh dien nuoc");
            }
        }
        p.setStatus("APPROVED");
        // Ky tinh tien da duyet thi ky ghi chi so cung thang cung dong lai, de hai trang thai khong mau thuan
        var reading = readingPeriod(p.getProperty().getPropertyId(), YearMonth.parse(p.getMonthYear()));
        if (reading != null) {
            reading.setIsClosed(true);
        }
        audit(actorId, "APPROVE_BILLING", "Duyet ky tinh tien " + p.getMonthYear(), ip);
        return toResponse(p, fees.size(), sum(fees));
    }

    /**
     * T4: mo lai ky da duyet ve DRAFT de sua (vd hoa don phat hanh sai, da huy). Chi mo duoc khi khong con hoa don nao
     * dang hieu luc (hoa don da huy thi duoc); ky ghi chi so cung thang mo lai de sua chi so.
     */
    @Transactional
    public PeriodResponse reopen(Integer periodId, Integer actorId, String ip) {
        MonthlyBillingPeriod p = period(periodId);
        if (!"APPROVED".equals(p.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Chi mo lai duoc ky da duyet (hien la " + p.getStatus() + ")");
        }
        long active = em.createQuery("select count(i) from Invoice i where i.calculatedFee.billingPeriod.billingPeriodId = :p "
                + "and i.paymentStatus <> 'CANCELLED'", Long.class).setParameter("p", periodId).getSingleResult();
        if (active > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ky con " + active
                    + " hoa don dang hieu luc, hay huy cac hoa don do truoc khi mo lai ky");
        }
        p.setStatus("DRAFT");
        var reading = readingPeriod(p.getProperty().getPropertyId(), YearMonth.parse(p.getMonthYear()));
        if (reading != null) {
            reading.setIsClosed(false);
        }
        audit(actorId, "REOPEN_BILLING", "Mo lai ky tinh tien " + p.getMonthYear(), ip);
        List<CalculatedMonthlyFee> fees = feeRepo.findByPeriod(periodId);
        return toResponse(p, fees.size(), sum(fees));
    }

    private List<String> roomsMissingReading(MonthlyBillingPeriod p, List<CalculatedMonthlyFee> fees) {
        var readingPeriod = readingPeriod(p.getProperty().getPropertyId(), YearMonth.parse(p.getMonthYear()));
        Set<String> missing = new java.util.TreeSet<>();
        for (CalculatedMonthlyFee f : fees) {
            if (f.getRoom() == null) {
                continue;
            }
            if (readingPeriod == null || !readingAppliesTo(
                    reading(f.getRoom().getRoomId(), readingPeriod.getPeriodId()), f.getContract())) {
                missing.add(f.getRoom().getRoomCode());
            }
        }
        return new ArrayList<>(missing);
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

    private Map<Integer, List<MeterReading>> transferClosingReadings(Set<Integer> contractIds, YearMonth month) {
        if (contractIds.isEmpty()) {
            return Map.of();
        }
        return em.createQuery("select m from MeterReading m join fetch m.room "
                        + "where m.closingContract.contractId in :contracts "
                        + "and m.period.startDate between :first and :last order by m.readingId", MeterReading.class)
                .setParameter("contracts", contractIds)
                .setParameter("first", month.atDay(1))
                .setParameter("last", month.atEndOfMonth())
                .getResultList().stream().collect(java.util.stream.Collectors.groupingBy(
                        m -> m.getClosingContract().getContractId()));
    }

    private void addMeter(Draft d, String room, ServiceRatePlan plan, MeterReading m, List<Issue> warnings) {
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

    private void addService(Draft d, PropertyServiceRate s, YearMonth ym) {
        // phong doi khach: moi khach chi chiu dich vu theo so ngay o cua minh, khong tinh du thang cho ca hai
        BigDecimal unit = d.shared ? prorateRent(s.getUnitPrice(), d.days, ym.lengthOfMonth()) : s.getUnitPrice();
        switch (s.getChargeType()) {
            case "FIXED_ROOM" -> d.add(s.getServiceName(), BigDecimal.ONE, unit);
            case "PER_PERSON" -> d.add(s.getServiceName(), BigDecimal.valueOf(d.residents), unit);
            case "PER_VEHICLE" -> {
                long vehicles = em.createQuery("select count(v) from TenantVehicle v where v.tenant.tenantId = :x "
                        + "and v.isActive = true", Long.class)
                        .setParameter("x", d.contract.getTenant().getTenantId()).getSingleResult();
                if (vehicles > 0) {
                    d.add(s.getServiceName(), BigDecimal.valueOf(vehicles), unit);
                }
            }
            default -> { /* PER_METER_UNIT: chua ap dung tu dong, nhap tay o UC74 neu can */ }
        }
    }

    /**
     * M7: phu phi thiet bi (room_addon_fees do viet tao khi lap dat) vao hoa don thang. Phi dang chay trong thang
     * (da bat dau, chua ket thuc), chu ky N thang thi chi thu vao cac thang cach thang bat dau boi so cua N.
     */
    private void addAddonFees(Draft d, YearMonth ym) {
        List<com.group7.minihouse.shared.entity.RoomAddonFee> fees = em.createQuery(
                "select f from RoomAddonFee f where f.room.roomId = :r and f.startDate <= :last "
                        + "and ((f.endDate is null and f.isActive = true) or f.endDate >= :first) order by f.addonFeeId",
                com.group7.minihouse.shared.entity.RoomAddonFee.class)
                .setParameter("r", d.contract.getRoom().getRoomId())
                .setParameter("first", ym.atDay(1)).setParameter("last", ym.atEndOfMonth()).getResultList();
        for (var f : fees) {
            int cycle = f.getBillingCycleMonths() == null || f.getBillingCycleMonths() < 1 ? 1 : f.getBillingCycleMonths();
            long monthsSinceStart = ChronoUnit.MONTHS.between(YearMonth.from(f.getStartDate()), ym);
            if (monthsSinceStart % cycle == 0 && f.getAmount() != null && f.getAmount().signum() > 0) {
                String name = ITEM_ADDON + f.getFeeName();
                d.add(name.length() > 100 ? name.substring(0, 100) : name, BigDecimal.ONE, f.getAmount());
            }
        }
    }

    private BigDecimal sharedCost(Integer propertyId, YearMonth ym) {
        BigDecimal v = em.createQuery("select coalesce(sum(e.amount), 0) from OperatingExpense e "
                + "where e.property.propertyId = :p and e.expenseCategory in :categories "
                + "and e.expenseDate between :from and :to", BigDecimal.class)
                .setParameter("p", propertyId).setParameter("categories", SHARED_CATEGORIES)
                .setParameter("from", ym.atDay(1)).setParameter("to", ym.atEndOfMonth()).getSingleResult();
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * Hop dong chong len thang ym cua khu tro; thu tu theo ma phong. Hop dong het han ma khach con o (chua
     * TERMINATED) van duoc tinh (F3); hop dong lap do chua nhan phong (M10) thi chua tinh.
     */
    private List<RentalContract> eligibleContracts(MonthlyBillingPeriod p, YearMonth ym) {
        return candidateContracts(p, ym).stream().filter(c -> !notHandedOver(c)).toList();
    }

    private List<RentalContract> candidateContracts(MonthlyBillingPeriod p, YearMonth ym) {
        return em.createQuery("select c from RentalContract c where c.room.property.propertyId = :p "
                + "and c.startDate <= :last and (c.endDate >= :first or c.status <> 'TERMINATED') "
                + "and c.status in :st order by c.room.roomCode, c.startDate", RentalContract.class)
                .setParameter("p", p.getProperty().getPropertyId())
                .setParameter("first", ym.atDay(1)).setParameter("last", ym.atEndOfMonth())
                .setParameter("st", LIVE_AT_MONTH).getResultList().stream()
                .filter(c -> daysInMonth(c, ym, effectiveEnd(c)) > 0).toList();
    }

    /**
     * M10: hop dong lap dang o wizard nhan phong (chua ket thuc, can coc > 0 ma chua thu dong nao va chua co bien ban
     * nhan phong) thi chua ban giao nen chua tinh tien; calculate/validate canh bao ro de khong bi bo sot im lang.
     */
    private boolean notHandedOver(RentalContract c) {
        if ("TERMINATED".equals(c.getStatus()) || c.getDepositAmount() == null || c.getDepositAmount().signum() <= 0) {
            return false;
        }
        // da tung duoc tinh tien (ky truoc) thi hien nhien da ban giao, khong loai giua chung
        if (!em.createQuery("select f.calculatedFeeId from CalculatedMonthlyFee f where f.contract.contractId = :c",
                Integer.class).setParameter("c", c.getContractId()).setMaxResults(1).getResultList().isEmpty()) {
            return false;
        }
        List<BigDecimal> collected = em.createQuery("select coalesce(sum(v.amount), 0) from ReceiptVoucher v "
                        + "where v.voucherType = 'DEPOSIT' and v.note like :p", BigDecimal.class)
                .setParameter("p", "Thu cọc hợp đồng " + c.getContractCode() + " phòng %").getResultList();
        if (!collected.isEmpty() && collected.get(0) != null && collected.get(0).signum() > 0) {
            return false;
        }
        List<Integer> inspections = em.createQuery("select k.inspectionId from CheckinInspection k "
                        + "where k.contract.contractId = :c", Integer.class)
                .setParameter("c", c.getContractId()).setMaxResults(1).getResultList();
        return inspections.isEmpty();
    }

    /** Canh bao cho hop dong chua nhan phong (khong tinh) va hop dong het han ma khach van o (van tinh). */
    private void addContractWarnings(MonthlyBillingPeriod p, YearMonth ym, List<Issue> warnings) {
        LocalDate today = LocalDate.now(clock);
        for (RentalContract c : candidateContracts(p, ym)) {
            String room = c.getRoom().getRoomCode();
            if (notHandedOver(c)) {
                warnings.add(new Issue(room, "Hop dong " + c.getContractCode() + " chua nhan phong (chua thu coc, chua co "
                        + "bien ban nhan phong) nen chua tinh tien; hoan tat nhan phong hoac chap dut hop dong"));
            } else if (!"TERMINATED".equals(c.getStatus()) && c.getEndDate().isBefore(today)) {
                warnings.add(new Issue(room, "Hop dong " + c.getContractCode() + " da het han ngay " + c.getEndDate()
                        + " nhung chua tra phong/cham dut: van tinh tien den hom nay, hay gia han hoac lam thu tuc tra phong"));
            }
        }
    }

    /**
     * Ngay het hieu luc thuc te de tinh tien: ngay het han hop dong, nhung som hon neu hop dong da cham dut hoac
     * khach da bao tra phong (ngay tra du kien/thuc te cua yeu cau tra phong chua huy). Nho do tien phong cua thang
     * cuoi tinh dung theo ngay o, ke ca khi chua thanh ly va chua hoan coc.
     */
    private LocalDate effectiveEnd(RentalContract c) {
        LocalDate end = c.getEndDate();
        if (!"TERMINATED".equals(c.getStatus()) && end.isBefore(LocalDate.now(clock))) {
            end = LocalDate.now(clock); // het han ma khach con o (chua tra phong/cham dut) thi tinh tiep den hom nay (F3)
        }
        LocalDate checkout = em.createQuery("select min(coalesce(r.actualCheckoutDate, r.expectedCheckoutDate)) "
                        + "from CheckoutRequest r where r.contract.contractId = :x "
                        + "and (r.status = 'FINALIZED' or (r.status in ('PENDING', 'INSPECTING') "
                        + "and coalesce(r.actualCheckoutDate, r.expectedCheckoutDate) >= :today))", LocalDate.class)
                .setParameter("x", c.getContractId()).setParameter("today", LocalDate.now(clock))
                .getResultList().stream()
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (checkout != null && checkout.isBefore(end)) {
            end = checkout;
        }
        if ("TERMINATED".equals(c.getStatus())) {
            LocalDateTime at = em.createQuery("select max(a.adjustedAt) from ContractAdjustment a "
                    + "where a.contract.contractId = :x and a.adjustmentType = 'TERMINATED'", LocalDateTime.class)
                    .setParameter("x", c.getContractId()).getSingleResult();
            if (at != null && at.toLocalDate().isBefore(end)) {
                end = at.toLocalDate();
            }
        }
        return end;
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

    private boolean readingAppliesTo(MeterReading reading, RentalContract contract) {
        return reading != null && (reading.getClosingContract() == null
                || reading.getClosingContract().getContractId().equals(contract.getContractId()));
    }

    private MeterReading reading(Integer roomId, Integer periodId) {
        List<MeterReading> l = em.createQuery("select m from MeterReading m where m.room.roomId = :r "
                + "and m.period.periodId = :p and m.closingContract is null", MeterReading.class)
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
        /** Phong co nhieu hop dong trong thang. */
        boolean shared;
        /** Hop dong sau cung cua phong: nhan dien nuoc va phan bo dien nuoc chung. */
        boolean primary = true;
        int days;

        Draft(RentalContract contract) {
            this.contract = contract;
        }

        void add(String name, BigDecimal quantity, BigDecimal unitPrice) {
            lines.add(new Line(name, quantity, unitPrice));
        }
    }

    private record Line(String name, BigDecimal quantity, BigDecimal unitPrice) {}
}

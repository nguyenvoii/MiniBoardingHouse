package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.dto.BillingDtos.CalculationResult;
import com.group7.minihouse.phi.repository.BillingPeriodRepository;
import com.group7.minihouse.phi.repository.CalculatedFeeRepository;
import com.group7.minihouse.phi.repository.FeeItemRepository;
import com.group7.minihouse.shared.entity.CalculatedMonthlyFee;
import com.group7.minihouse.shared.entity.FeeItemDetail;
import com.group7.minihouse.shared.entity.MonthlyBillingPeriod;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomAddonFee;
import com.group7.minihouse.shared.entity.Tenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * F3 (hop dong het han ma khach con o van tinh tien), M7 (phu phi thiet bi vao hoa don), M10 (hop dong lap do chua
 * nhan phong khong tinh), M11 (phat tre han khong cong don). Dung em gia tra ket qua theo noi dung cau JPQL.
 */
class BillingContractsTest {

    BillingPeriodRepository periodRepo = mock(BillingPeriodRepository.class);
    CalculatedFeeRepository feeRepo = mock(CalculatedFeeRepository.class);
    FeeItemRepository itemRepo = mock(FeeItemRepository.class);
    EntityManager em = mock(EntityManager.class);
    BillingService service;
    MonthlyBillingPeriod period;
    Property property;
    Room room;
    Tenant tenant;
    /** doan cau JPQL -> ket qua tra ve (getResultList; getSingleResult lay phan tu dau hoac null). */
    Map<String, Object> answers = new LinkedHashMap<>();
    List<FeeItemDetail> savedItems = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // hom nay = 18/11/2026
        Clock clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new BillingService(periodRepo, feeRepo, itemRepo, em, mock(ActivityLogService.class), clock);
        property = new Property();
        property.setPropertyId(1);
        period = new MonthlyBillingPeriod();
        period.setBillingPeriodId(10);
        period.setProperty(property);
        period.setMonthYear("2026-11");
        period.setStatus("DRAFT");
        when(periodRepo.findById(10)).thenReturn(Optional.of(period));
        room = new Room();
        room.setRoomId(7);
        room.setRoomCode("P101");
        tenant = new Tenant();
        tenant.setTenantId(3);
        tenant.setFullName("An");
        when(em.createQuery(anyString(), any(Class.class))).thenAnswer(inv -> queryFor(inv.getArgument(0)));
        when(feeRepo.save(any(CalculatedMonthlyFee.class))).thenAnswer(i -> {
            CalculatedMonthlyFee f = i.getArgument(0);
            f.setCalculatedFeeId(100);
            return f;
        });
        when(itemRepo.save(any(FeeItemDetail.class))).thenAnswer(i -> {
            savedItems.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(em.getReference(any(Class.class), anyInt())).thenReturn(period);
        answers.put("select count(i) from Invoice i", 0L);
        answers.put("select count(r) from RoomResident", 0L);
        answers.put("coalesce(sum(e.amount)", BigDecimal.ZERO);
    }

    @SuppressWarnings("unchecked")
    private TypedQuery<Object> queryFor(String jpql) {
        Object result = List.of();
        for (Map.Entry<String, Object> e : answers.entrySet()) {
            if (jpql.contains(e.getKey())) {
                result = e.getValue();
            }
        }
        TypedQuery<Object> q = mock(TypedQuery.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.setMaxResults(anyInt())).thenReturn(q);
        if (result instanceof List<?> l) {
            when(q.getResultList()).thenReturn((List<Object>) l);
            when(q.getSingleResult()).thenReturn(l.isEmpty() ? null : l.get(0));
        } else {
            when(q.getResultList()).thenReturn(List.of(result));
            when(q.getSingleResult()).thenReturn(result);
        }
        return q;
    }

    private RentalContract contract(String status, LocalDate start, LocalDate end) {
        RentalContract c = new RentalContract();
        c.setContractId(1);
        c.setContractCode("HD1");
        c.setRoom(room);
        c.setTenant(tenant);
        c.setStartDate(start);
        c.setEndDate(end);
        c.setStatus(status);
        c.setMonthlyRentPrice(new BigDecimal("3000000"));
        c.setDepositAmount(BigDecimal.ZERO);
        c.setLateFeePerDay(BigDecimal.ZERO);
        return c;
    }

    private FeeItemDetail itemNamed(String prefix) {
        return savedItems.stream().filter(i -> i.getItemName().startsWith(prefix)).findFirst().orElse(null);
    }

    // ---- F3

    @Test
    void expiredContractWithTenantStillThereIsBilledUntilToday() {
        // het han 10/11, khach van o: thang 11 tinh den hom nay 18/11 = 18 ngay (khong bi loai nhu truoc)
        answers.put("select c from RentalContract c where c.room.property",
                List.of(contract("EXPIRED", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 10))));
        CalculationResult r = service.calculate(10, null, "ip");
        FeeItemDetail rent = itemNamed(BillingService.ITEM_RENT);
        assertEquals(0, new BigDecimal("1800000").compareTo(rent.getSubtotal()), rent.getSubtotal().toPlainString());
        assertTrue(r.warnings().stream().anyMatch(w -> w.message().contains("het han")));
    }

    @Test
    void expiredContractIsBilledForTheWholeLaterMonthsSoFar() {
        // het han 10/10, ky 10 da qua: ca thang 10 tu 1/10 den 10/10 la 10 ngay, thang 11 phan con lai den hom nay
        answers.put("select c from RentalContract c where c.room.property",
                List.of(contract("EXPIRED", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 10))));
        service.calculate(10, null, "ip");
        // thang 11: hop dong het han thang truoc nhung chua TERMINATED -> van co dong tien phong (den hom nay)
        assertEquals(0, new BigDecimal("1800000").compareTo(itemNamed(BillingService.ITEM_RENT).getSubtotal()));
    }

    @Test
    void terminatedContractEndedBeforeTheMonthIsNotBilled() {
        RentalContract c = contract("TERMINATED", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 10));
        answers.put("select c from RentalContract c where c.room.property", List.of(c));
        service.calculate(10, null, "ip");
        assertEquals(null, itemNamed(BillingService.ITEM_RENT));
    }

    // ---- M10

    @Test
    void contractNotHandedOverIsNotBilledAndWarned() {
        RentalContract c = contract("ACTIVE", LocalDate.of(2026, 11, 1), LocalDate.of(2027, 11, 1));
        c.setDepositAmount(new BigDecimal("3000000"));
        answers.put("select c from RentalContract c where c.room.property", List.of(c));
        // chua thu coc, chua co bien ban nhan phong, chua tung tinh tien
        CalculationResult r = service.calculate(10, null, "ip");
        assertEquals(null, itemNamed(BillingService.ITEM_RENT));
        assertTrue(r.warnings().stream().anyMatch(w -> w.message().contains("chua nhan phong")));
    }

    @Test
    void contractWithDepositCollectedIsBilled() {
        RentalContract c = contract("ACTIVE", LocalDate.of(2026, 11, 1), LocalDate.of(2027, 11, 1));
        c.setDepositAmount(new BigDecimal("3000000"));
        answers.put("select c from RentalContract c where c.room.property", List.of(c));
        answers.put("coalesce(sum(v.amount)", new BigDecimal("3000000"));
        service.calculate(10, null, "ip");
        assertEquals(0, new BigDecimal("3000000").compareTo(itemNamed(BillingService.ITEM_RENT).getSubtotal()));
    }

    @Test
    void contractAlreadyBilledBeforeIsNeverDroppedMidway() {
        RentalContract c = contract("ACTIVE", LocalDate.of(2026, 11, 1), LocalDate.of(2027, 11, 1));
        c.setDepositAmount(new BigDecimal("3000000"));
        answers.put("select c from RentalContract c where c.room.property", List.of(c));
        answers.put("select f.calculatedFeeId", List.of(55));
        service.calculate(10, null, "ip");
        assertTrue(itemNamed(BillingService.ITEM_RENT) != null);
    }

    // ---- M7

    @Test
    void roomAddonFeeAppearsAsAServiceLineOnTheBill() {
        answers.put("select c from RentalContract c where c.room.property",
                List.of(contract("ACTIVE", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))));
        RoomAddonFee fee = new RoomAddonFee();
        fee.setRoom(room);
        fee.setFeeName("May lanh");
        fee.setAmount(new BigDecimal("150000"));
        fee.setBillingCycleMonths(1);
        fee.setStartDate(LocalDate.of(2026, 9, 5));
        fee.setIsActive(true);
        answers.put("from RoomAddonFee", List.of(fee));
        service.calculate(10, null, "ip");
        FeeItemDetail addon = itemNamed(BillingService.ITEM_ADDON);
        assertEquals("Thiết bị: May lanh", addon.getItemName());
        assertEquals(0, new BigDecimal("150000").compareTo(addon.getSubtotal()));
    }

    @Test
    void addonFeeWithThreeMonthCycleOnlyBillsEveryThirdMonth() {
        answers.put("select c from RentalContract c where c.room.property",
                List.of(contract("ACTIVE", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))));
        RoomAddonFee fee = new RoomAddonFee();
        fee.setRoom(room);
        fee.setFeeName("Tu lanh");
        fee.setAmount(new BigDecimal("300000"));
        fee.setBillingCycleMonths(3);
        fee.setStartDate(LocalDate.of(2026, 9, 5)); // thu vao 9, 12 -> thang 11 khong thu
        fee.setIsActive(true);
        answers.put("from RoomAddonFee", List.of(fee));
        service.calculate(10, null, "ip");
        assertEquals(null, itemNamed(BillingService.ITEM_ADDON));
    }

    // ---- M11

    @Test
    void lateFeeIsNotChargedAgainForDaysAlreadyPenalisedInAnEarlierPeriod() {
        RentalContract c = contract("ACTIVE", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        c.setLateFeePerDay(new BigDecimal("10000"));
        answers.put("select c from RentalContract c where c.room.property", List.of(c));
        answers.put("select min(i.dueDate)", LocalDate.of(2026, 10, 5)); // hoa don no tu han 5/10
        // ky thang 10 da phat (lap ngay 8/11) -> ky nay chi dem tu 8/11 den 18/11 = 10 ngay, khong phai 44 ngay
        answers.put("select max(i.calculatedFee.billingPeriod.creationDate)", LocalDateTime.of(2026, 11, 8, 9, 0));
        service.calculate(10, null, "ip");
        FeeItemDetail penalty = itemNamed(BillingService.ITEM_PENALTY);
        assertEquals(0, new BigDecimal("10").compareTo(penalty.getQuantity()), penalty.getQuantity().toPlainString());
    }

    @Test
    void lateFeeCountsFromTheDueDateWhenNeverPenalisedBefore() {
        RentalContract c = contract("ACTIVE", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        c.setLateFeePerDay(new BigDecimal("10000"));
        answers.put("select c from RentalContract c where c.room.property", List.of(c));
        answers.put("select min(i.dueDate)", LocalDate.of(2026, 11, 8));
        service.calculate(10, null, "ip");
        assertEquals(0, new BigDecimal("10").compareTo(itemNamed(BillingService.ITEM_PENALTY).getQuantity()));
        assertFalse(savedItems.isEmpty());
    }
}

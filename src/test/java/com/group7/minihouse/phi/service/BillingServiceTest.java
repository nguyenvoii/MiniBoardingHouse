package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.dto.BillingDtos.CreatePeriodRequest;
import com.group7.minihouse.phi.dto.BillingDtos.UpdateItemRequest;
import com.group7.minihouse.phi.repository.BillingPeriodRepository;
import com.group7.minihouse.phi.repository.CalculatedFeeRepository;
import com.group7.minihouse.phi.repository.FeeItemRepository;
import com.group7.minihouse.shared.entity.CalculatedMonthlyFee;
import com.group7.minihouse.shared.entity.FeeItemDetail;
import com.group7.minihouse.shared.entity.MonthlyBillingPeriod;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/** UC66-75 tinh tien thang: cong thuc va quy tac trang thai (Mockito, khong can DB). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceTest {

    @Mock BillingPeriodRepository periodRepo;
    @Mock CalculatedFeeRepository feeRepo;
    @Mock FeeItemRepository itemRepo;
    @Mock EntityManager em;
    @Mock ActivityLogService activityLogService;
    @Mock TypedQuery<Object> emptyQuery;

    BillingService service;
    Property property;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new BillingService(periodRepo, feeRepo, itemRepo, em, activityLogService, clock);
        property = new Property();
        property.setPropertyId(1);
        when(em.createQuery(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<Class<Object>>any())).thenReturn(emptyQuery);
        when(emptyQuery.setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(emptyQuery);
        when(emptyQuery.setMaxResults(org.mockito.ArgumentMatchers.anyInt())).thenReturn(emptyQuery);
        when(emptyQuery.getResultList()).thenReturn(List.of());
        when(periodRepo.save(any(MonthlyBillingPeriod.class))).thenAnswer(i -> {
            MonthlyBillingPeriod p = i.getArgument(0);
            p.setBillingPeriodId(10);
            return p;
        });
    }

    private static FeeItemDetail item(String name, String qty, String price) {
        FeeItemDetail i = new FeeItemDetail();
        i.setItemName(name);
        i.setQuantity(new BigDecimal(qty));
        i.setUnitPrice(new BigDecimal(price));
        i.setSubtotal(BillingService.subtotal(i.getQuantity(), i.getUnitPrice()));
        return i;
    }

    private MonthlyBillingPeriod period(String status) {
        MonthlyBillingPeriod p = new MonthlyBillingPeriod();
        p.setBillingPeriodId(10);
        p.setProperty(property);
        p.setMonthYear("2026-11");
        p.setStatus(status);
        when(periodRepo.findById(10)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void fullMonthKeepsRentUnchanged() {
        assertEquals(new BigDecimal("3000000"), BillingService.prorateRent(new BigDecimal("3000000"), 30, 30));
    }

    @Test
    void partialMonthIsProratedByActualDaysAndRounded() {
        // vao o ngay 21/11: 10 ngay tren 30 -> 1.000.000
        assertEquals(new BigDecimal("1000000"), BillingService.prorateRent(new BigDecimal("3000000"), 10, 30));
        // 10/31 cua 3.000.000 = 967741.93... lam tron len 967742
        assertEquals(new BigDecimal("967742"), BillingService.prorateRent(new BigDecimal("3000000"), 10, 31));
    }

    @Test
    void recomputeSplitsItemsIntoComponentsAndTotal() {
        CalculatedMonthlyFee f = new CalculatedMonthlyFee();
        List<FeeItemDetail> items = List.of(
                item(BillingService.ITEM_RENT, "1", "3000000"),
                item(BillingService.ITEM_ELECTRIC, "100", "3500"),
                item(BillingService.ITEM_WATER, "5", "15000"),
                item("Internet", "1", "100000"),
                item(BillingService.ITEM_SHARED, "1", "20000"),
                item(BillingService.ITEM_SURCHARGE + ": May lanh", "1", "50000"),
                item(BillingService.ITEM_DISCOUNT + ": Khuyen mai", "1", "-30000"),
                item(BillingService.ITEM_PENALTY, "2", "10000"));
        BillingService.recompute(f, items);
        assertEquals(0, new BigDecimal("3000000").compareTo(f.getRentAmount()));
        assertEquals(0, new BigDecimal("350000").compareTo(f.getElectricAmount()));
        assertEquals(0, new BigDecimal("75000").compareTo(f.getWaterAmount()));
        assertEquals(0, new BigDecimal("120000").compareTo(f.getServiceAmount())); // internet + dien nuoc chung
        assertEquals(0, new BigDecimal("50000").compareTo(f.getSurchargeAmount()));
        assertEquals(0, new BigDecimal("30000").compareTo(f.getDiscountAmount()));
        assertEquals(0, new BigDecimal("20000").compareTo(f.getLatePenaltyAmount()));
        // 3.000.000 + 350.000 + 75.000 + 120.000 + 50.000 - 30.000 + 20.000
        assertEquals(0, new BigDecimal("3585000").compareTo(f.getTotalCalculatedAmount()));
    }

    @Test
    void anomalyNeedsHistoryAndMoreThan150Percent() {
        assertFalse(BillingService.isAnomaly(new BigDecimal("9000000"), null));
        assertFalse(BillingService.isAnomaly(new BigDecimal("4500000"), new BigDecimal("3000000"))); // bang 150%
        assertTrue(BillingService.isAnomaly(new BigDecimal("4500001"), new BigDecimal("3000000")));
    }

    @Test
    void createPeriodValidatesMonthFormat() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPeriod(new CreatePeriodRequest(1, "11/2026"), 5, "ip"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createPeriodRejectsUnknownPropertyAndDuplicateMonth() {
        assertThrows(BusinessException.class,
                () -> service.createPeriod(new CreatePeriodRequest(1, "2026-11"), 5, "ip"));
        when(em.find(Property.class, 1)).thenReturn(property);
        when(periodRepo.existsByPropertyPropertyIdAndMonthYear(1, "2026-11")).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPeriod(new CreatePeriodRequest(1, "2026-11"), 5, "ip"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void approvedPeriodCannotBeRecalculatedOrApprovedAgain() {
        period("APPROVED");
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(BusinessException.class, () -> service.calculate(10, 5, "ip")).getStatus());
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(BusinessException.class, () -> service.approve(10, 5, "ip")).getStatus());
    }

    @Test
    void approveAlsoClosesTheMeterReadingPeriodOfThatMonth() {
        period("DRAFT");
        CalculatedMonthlyFee f = new CalculatedMonthlyFee();
        f.setTotalCalculatedAmount(new BigDecimal("100"));
        when(feeRepo.findByPeriod(10)).thenReturn(List.of(f));
        var reading = new com.group7.minihouse.shared.entity.UtilityReadingPeriod();
        reading.setIsClosed(false);
        when(emptyQuery.getResultList()).thenReturn(List.of(reading));
        service.approve(10, 5, "ip");
        assertEquals(true, reading.getIsClosed());
    }

    @Test
    void cannotApproveEmptyPeriod() {
        period("DRAFT");
        when(feeRepo.findByPeriod(10)).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.approve(10, 5, "ip"));
    }

    @Test
    void approveMovesDraftToApproved() {
        MonthlyBillingPeriod p = period("DRAFT");
        CalculatedMonthlyFee f = new CalculatedMonthlyFee();
        f.setTotalCalculatedAmount(new BigDecimal("100"));
        when(feeRepo.findByPeriod(10)).thenReturn(List.of(f));
        assertEquals("APPROVED", service.approve(10, 5, "ip").status());
        assertEquals("APPROVED", p.getStatus());
    }

    @Test
    void updateItemRecomputesFeeTotals() {
        MonthlyBillingPeriod p = period("DRAFT");
        CalculatedMonthlyFee f = new CalculatedMonthlyFee();
        f.setCalculatedFeeId(20);
        f.setBillingPeriod(p);
        FeeItemDetail rent = item(BillingService.ITEM_RENT, "1", "3000000");
        rent.setDetailId(30);
        rent.setCalculatedFee(f);
        when(itemRepo.findById(30)).thenReturn(Optional.of(rent));
        when(itemRepo.findByCalculatedFeeCalculatedFeeIdOrderByDetailId(20)).thenReturn(List.of(rent));
        try {
            service.updateItem(30, new UpdateItemRequest(null, new BigDecimal("2800000")), 5, "ip");
        } catch (NullPointerException expectedFromUnmockedResponseBuild) {
            // toResponse can chuoi room/contract cua phong; phan tinh lai da chay truoc do
        }
        assertEquals(0, new BigDecimal("2800000").compareTo(f.getTotalCalculatedAmount()));
        assertEquals(0, new BigDecimal("2800000").compareTo(f.getRentAmount()));
    }

    @Test
    void updateItemRejectsApprovedPeriodAndBadSigns() {
        MonthlyBillingPeriod p = period("APPROVED");
        CalculatedMonthlyFee f = new CalculatedMonthlyFee();
        f.setCalculatedFeeId(20);
        f.setBillingPeriod(p);
        FeeItemDetail rent = item(BillingService.ITEM_RENT, "1", "3000000");
        rent.setCalculatedFee(f);
        when(itemRepo.findById(30)).thenReturn(Optional.of(rent));
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.updateItem(30, new UpdateItemRequest(BigDecimal.ONE, null), 5, "ip")).getStatus());

        p.setStatus("DRAFT");
        assertThrows(BusinessException.class,
                () -> service.updateItem(30, new UpdateItemRequest(null, new BigDecimal("-1")), 5, "ip"));
        FeeItemDetail discount = item(BillingService.ITEM_DISCOUNT + ": km", "1", "-1000");
        discount.setCalculatedFee(f);
        when(itemRepo.findById(31)).thenReturn(Optional.of(discount));
        assertThrows(BusinessException.class,
                () -> service.updateItem(31, new UpdateItemRequest(null, new BigDecimal("500")), 5, "ip"));
        assertThrows(BusinessException.class, () -> service.updateItem(30, new UpdateItemRequest(null, null), 5, "ip"));
    }

    // ------------------------------------------------------------------ khoan nhap tay

    private CalculatedMonthlyFee draftFee() {
        MonthlyBillingPeriod p = period("DRAFT");
        CalculatedMonthlyFee f = new CalculatedMonthlyFee();
        f.setCalculatedFeeId(20);
        f.setBillingPeriod(p);
        when(feeRepo.findById(20)).thenReturn(Optional.of(f));
        return f;
    }

    @Test
    void manualLinesAreClassifiedBySignAndSurvive() {
        CalculatedMonthlyFee f = new CalculatedMonthlyFee();
        BillingService.recompute(f, List.of(
                item(BillingService.ITEM_RENT, "1", "3000000"),
                item(BillingService.MANUAL + "Sua may lanh", "1", "200000"),
                item(BillingService.MANUAL + "Khuyen mai", "1", "-50000")));
        assertEquals(0, new BigDecimal("200000").compareTo(f.getSurchargeAmount()));
        assertEquals(0, new BigDecimal("50000").compareTo(f.getDiscountAmount()));
        assertEquals(0, new BigDecimal("3150000").compareTo(f.getTotalCalculatedAmount()));
    }

    @Test
    void addItemStoresWithManualPrefixAndRecomputes() {
        CalculatedMonthlyFee f = draftFee();
        when(itemRepo.findByCalculatedFeeCalculatedFeeIdOrderByDetailId(20)).thenAnswer(i -> List.of(
                item(BillingService.ITEM_RENT, "1", "3000000"), item(BillingService.MANUAL + "Sua khoa", "2", "100000")));
        try {
            service.addItem(20, new com.group7.minihouse.phi.dto.BillingDtos.AddItemRequest("  Sua khoa ", new BigDecimal("2"), new BigDecimal("100000")), 5, "ip");
        } catch (NullPointerException expectedFromUnmockedResponseBuild) {
            // dung toResponse can room/contract; phan luu va tinh lai da chay truoc do
        }
        org.mockito.ArgumentCaptor<FeeItemDetail> cap = org.mockito.ArgumentCaptor.forClass(FeeItemDetail.class);
        org.mockito.Mockito.verify(itemRepo).saveAndFlush(cap.capture());
        assertEquals(BillingService.MANUAL + "Sua khoa", cap.getValue().getItemName());
        assertEquals(0, new BigDecimal("200000").compareTo(cap.getValue().getSubtotal()));
        assertEquals(0, new BigDecimal("3200000").compareTo(f.getTotalCalculatedAmount()));
    }

    @Test
    void addItemValidatesInputAndOnlyWorksInDraft() {
        draftFee();
        var ok = new BigDecimal("1");
        assertThrows(BusinessException.class, () -> service.addItem(20, new com.group7.minihouse.phi.dto.BillingDtos.AddItemRequest(" ", ok, ok), 5, "ip"));
        assertThrows(BusinessException.class, () -> service.addItem(20, new com.group7.minihouse.phi.dto.BillingDtos.AddItemRequest("x", BigDecimal.ZERO, ok), 5, "ip"));
        assertThrows(BusinessException.class, () -> service.addItem(20, new com.group7.minihouse.phi.dto.BillingDtos.AddItemRequest("x", ok, BigDecimal.ZERO), 5, "ip"));
        assertThrows(BusinessException.class, () -> service.addItem(20, new com.group7.minihouse.phi.dto.BillingDtos.AddItemRequest("x".repeat(91), ok, ok), 5, "ip"));
        MonthlyBillingPeriod approved = period("APPROVED");
        CalculatedMonthlyFee locked = new CalculatedMonthlyFee();
        locked.setBillingPeriod(approved);
        when(feeRepo.findById(21)).thenReturn(Optional.of(locked));
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.addItem(21, new com.group7.minihouse.phi.dto.BillingDtos.AddItemRequest("x", ok, ok), 5, "ip")).getStatus());
    }

    @Test
    void deleteItemRemovesLineAndRecomputes() {
        CalculatedMonthlyFee f = draftFee();
        FeeItemDetail internet = item("Internet", "1", "100000");
        internet.setDetailId(40);
        internet.setCalculatedFee(f);
        when(itemRepo.findById(40)).thenReturn(Optional.of(internet));
        when(itemRepo.findByCalculatedFeeCalculatedFeeIdOrderByDetailId(20)).thenReturn(List.of(item(BillingService.ITEM_RENT, "1", "3000000")));
        try {
            service.deleteItem(40, 5, "ip");
        } catch (NullPointerException expectedFromUnmockedResponseBuild) {
            // toResponse can room/contract
        }
        org.mockito.Mockito.verify(itemRepo).delete(internet);
        assertEquals(0, new BigDecimal("3000000").compareTo(f.getTotalCalculatedAmount()));
    }

    @Test
    void servicePreviewRejectsBadInput() {
        assertThrows(BusinessException.class, () -> service.servicePreview(null, BigDecimal.TEN));
        assertThrows(BusinessException.class, () -> service.servicePreview("FIXED_ROOM", new BigDecimal("-1")));
    }
}

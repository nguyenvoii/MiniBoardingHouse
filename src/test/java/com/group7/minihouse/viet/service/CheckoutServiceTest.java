package com.group7.minihouse.viet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.service.ContractEndService;
import com.group7.minihouse.shared.entity.CheckoutInspection;
import com.group7.minihouse.shared.entity.CheckoutRequest;
import com.group7.minihouse.shared.entity.ContractAdjustment;
import com.group7.minihouse.shared.entity.DepositRefundVoucher;
import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.event.ContractEndedEvent;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.service.MeterReadingService;
import com.group7.minihouse.viet.dto.CheckoutDtos.CreateCheckoutRequest;
import com.group7.minihouse.viet.dto.CheckoutDtos.InspectionRequest;
import com.group7.minihouse.viet.dto.CheckoutDtos.InspectionResponse;
import com.group7.minihouse.viet.dto.CheckoutDtos.RefundVoucherResponse;
import com.group7.minihouse.viet.repository.CheckoutInspectionRepository;
import com.group7.minihouse.viet.repository.CheckoutRequestRepository;
import com.group7.minihouse.viet.repository.DepositRefundVoucherRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** Tra phong: F1 (so cuoi < ky truoc), F2 (EXPIRED), F6 (ghi so cuoi), F8, M3, duong ket thuc dung chung (Mockito). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutServiceTest {

    @Mock CheckoutRequestRepository checkoutRepo;
    @Mock CheckoutInspectionRepository inspectionRepo;
    @Mock DepositRefundVoucherRepository voucherRepo;
    @Mock TenantRoomResolver resolver;
    @Mock ActorResolver actorResolver;
    @Mock DebtService debtService;
    @Mock MeterReadingService meterService;
    @Mock ApplicationEventPublisher publisher;
    @Mock EntityManager em;
    @Mock Query query;
    @Mock Query ledger;
    @Mock TypedQuery<Invoice> invoiceQuery;

    CheckoutService service;
    RentalContract contract;
    Room room;
    CheckoutRequest request;
    User actor;

    @BeforeEach
    void setUp() {
        service = new CheckoutService(checkoutRepo, inspectionRepo, voucherRepo, resolver, actorResolver, debtService,
                new ContractEndService(publisher), meterService);
        ReflectionTestUtils.setField(service, "em", em);
        room = new Room();
        room.setRoomId(7);
        room.setRoomCode("P101");
        room.setStatus("RENTED");
        contract = new RentalContract();
        contract.setContractId(1);
        contract.setContractCode("HD1");
        contract.setRoom(room);
        Tenant t = new Tenant();
        t.setTenantId(3);
        t.setFullName("An");
        contract.setTenant(t);
        contract.setDepositAmount(new BigDecimal("3000000"));
        contract.setStatus("ACTIVE");
        request = new CheckoutRequest();
        request.setCheckoutId(50);
        request.setContract(contract);
        request.setStatus("INSPECTING");
        request.setExpectedCheckoutDate(LocalDate.now());
        actor = new User();
        actor.setUserId(5);
        when(em.find(RentalContract.class, 1)).thenReturn(contract);
        when(checkoutRepo.findById(50)).thenReturn(Optional.of(request));
        when(checkoutRepo.save(any(CheckoutRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(inspectionRepo.save(any(CheckoutInspection.class))).thenAnswer(i -> i.getArgument(0));
        when(actorResolver.require(5)).thenReturn(actor);
        when(voucherRepo.findByCheckout_CheckoutId(50)).thenReturn(Optional.empty());
        when(inspectionRepo.findByCheckout_CheckoutId(50)).thenReturn(Optional.empty());
        when(em.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);
        when(query.getResultList()).thenReturn(List.of());
        when(em.createQuery(anyString(), eq(Invoice.class))).thenReturn(invoiceQuery);
        when(invoiceQuery.setParameter(anyString(), any())).thenReturn(invoiceQuery);
        when(invoiceQuery.getResultList()).thenReturn(List.of());
    }

    // ---- F2: hop dong EXPIRED van tra phong duoc

    @Test
    void expiredContractCanRequestCheckout() {
        contract.setStatus("EXPIRED");
        var r = service.create(new CreateCheckoutRequest(1, LocalDate.now()), null);
        assertEquals("PENDING", r.status());
        assertEquals("EXPIRED", contract.getStatus());
    }

    @Test
    void terminatedContractCannotRequestCheckout() {
        contract.setStatus("TERMINATED");
        assertThrows(BusinessException.class, () -> service.create(new CreateCheckoutRequest(1, LocalDate.now()), null));
    }

    // ---- F1: so cuoi < so ky truoc bi chan ngay luc lap bien ban, truoc khi co phieu hoan coc

    @Test
    void finalMeterBelowPreviousPeriodIsRejectedAtInspection() {
        doThrow(new BusinessException("Chi so dien cuoi (90) nho hon chi so ky truoc (100)"))
                .when(meterService).validateFinalReading(eq(7), any(LocalDate.class), eq(90), eq(20));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recordInspection(50, new InspectionRequest(90, 20, List.of(), null), 5));
        assertTrue(ex.getMessage().contains("nho hon"));
        verify(inspectionRepo, never()).save(any());
        verify(meterService, never()).recordFinalReading(anyInt(), any(), anyInt(), anyInt(), anyInt());
    }

    // ---- F6: so cuoi duoc ghi thanh so doc ngay khi lap bien ban

    @Test
    void inspectionRecordsFinalMetersAsReadingRightAway() {
        when(meterService.recordFinalReading(eq(7), any(LocalDate.class), eq(120), eq(30), eq(5))).thenReturn(true);
        InspectionResponse r = service.recordInspection(50, new InspectionRequest(120, 30, List.of(), null), 5);
        assertTrue(r.meterRecorded());
        verify(meterService).validateFinalReading(eq(7), any(LocalDate.class), eq(120), eq(30));
    }

    @Test
    void inspectionStillSavesWhenNoReadingPeriodIsOpen() {
        when(meterService.recordFinalReading(anyInt(), any(), anyInt(), anyInt(), anyInt())).thenReturn(false);
        InspectionResponse r = service.recordInspection(50, new InspectionRequest(120, 30, List.of(), null), 5);
        assertEquals(false, r.meterRecorded());
        verify(inspectionRepo).save(any(CheckoutInspection.class));
    }

    // ---- duong ket thuc dung chung + F8

    private DepositRefundVoucher refundedVoucher() {
        DepositRefundVoucher v = new DepositRefundVoucher();
        v.setRefundId(9);
        v.setCheckout(request);
        v.setOriginalDepositAmount(new BigDecimal("1000000"));
        v.setDeductedDebtAmount(BigDecimal.ZERO);
        v.setDeductedDamageAmount(new BigDecimal("1000000"));
        v.setActualRefundAmount(BigDecimal.ZERO);
        v.setStatus("REFUNDED");
        when(voucherRepo.findByCheckout_CheckoutId(50)).thenReturn(Optional.of(v));
        return v;
    }

    @Test
    void finalizeEndsContractThroughTheSharedEndPath() {
        refundedVoucher();
        CheckoutInspection ins = new CheckoutInspection();
        ins.setCheckout(request);
        ins.setFinalElectricMeter(120);
        ins.setFinalWaterMeter(30);
        ins.setDamageCompensationAmount(new BigDecimal("1000000"));
        ins.setInspectedBy(actor);
        when(inspectionRepo.findByCheckout_CheckoutId(50)).thenReturn(Optional.of(ins));
        service.finalizeCheckout(50, 5);
        assertEquals("TERMINATED", contract.getStatus());
        assertEquals("VACANT", room.getStatus());
        assertEquals("FINALIZED", request.getStatus());
        ArgumentCaptor<ContractEndedEvent> ev = ArgumentCaptor.forClass(ContractEndedEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertEquals(120, ev.getValue().finalElectric());
        assertEquals(7, ev.getValue().roomId());
        assertEquals("ACTIVE", ev.getValue().previousStatus());
    }

    @Test
    void finalizeOfExpiredContractKeepsTheExpiredAsPreviousStatus() {
        contract.setStatus("EXPIRED");
        refundedVoucher();
        service.finalizeCheckout(50, 5);
        ArgumentCaptor<ContractEndedEvent> ev = ArgumentCaptor.forClass(ContractEndedEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertEquals("EXPIRED", ev.getValue().previousStatus());
        assertEquals("TERMINATED", contract.getStatus());
    }

    /** F8: boi thuong vuot coc khong con bien mat, duoc ghi vao lich su hop dong. */
    @Test
    void finalizeRecordsShortfallWhenDamageExceedsDeposit() {
        refundedVoucher();
        CheckoutInspection ins = new CheckoutInspection();
        ins.setCheckout(request);
        ins.setDamageCompensationAmount(new BigDecimal("2500000")); // coc chi 1.000.000 -> thieu 1.500.000
        ins.setInspectedBy(actor);
        when(inspectionRepo.findByCheckout_CheckoutId(50)).thenReturn(Optional.of(ins));
        service.finalizeCheckout(50, 5);
        ArgumentCaptor<Object> persisted = ArgumentCaptor.forClass(Object.class);
        verify(em).persist(persisted.capture());
        ContractAdjustment a = (ContractAdjustment) persisted.getValue();
        assertEquals("DEPOSIT_SHORTFALL", a.getAdjustmentType());
        assertTrue(a.getNewValue().contains("1500000"), a.getNewValue());
    }

    @Test
    void finalizeWritesNoShortfallRecordWhenDepositCoversEverything() {
        DepositRefundVoucher v = refundedVoucher();
        v.setDeductedDamageAmount(BigDecimal.ZERO);
        service.finalizeCheckout(50, 5);
        verify(em, never()).persist(any());
    }

    @Test
    void finalizeNeedsRefundedVoucher() {
        DepositRefundVoucher v = refundedVoucher();
        v.setStatus("PENDING");
        assertThrows(BusinessException.class, () -> service.finalizeCheckout(50, 5));
        verify(publisher, never()).publishEvent(any());
    }

    // ---- M3: phieu duoc tinh lai theo so lieu hien tai luc hoan tien

    @Test
    void payRefundRecomputesTheVoucherFromCurrentNumbers() {
        // phieu cu: tru no 800.000, hoan 2.200.000; luc hoan no da duoc khach tra het (khong con hoa don no)
        DepositRefundVoucher v = new DepositRefundVoucher();
        v.setRefundId(9);
        v.setCheckout(request);
        v.setOriginalDepositAmount(new BigDecimal("3000000"));
        v.setDeductedDebtAmount(new BigDecimal("800000"));
        v.setDeductedDamageAmount(BigDecimal.ZERO);
        v.setActualRefundAmount(new BigDecimal("2200000"));
        v.setStatus("PENDING");
        when(voucherRepo.findById(9)).thenReturn(Optional.of(v));
        when(voucherRepo.save(any(DepositRefundVoucher.class))).thenAnswer(i -> i.getArgument(0));
        when(em.createQuery(org.mockito.ArgumentMatchers.startsWith("select coalesce(sum(v.amount)"))).thenReturn(ledger);
        when(ledger.setParameter(anyString(), any())).thenReturn(ledger);
        when(ledger.getSingleResult()).thenReturn(new BigDecimal("3000000")); // da thu coc 3.000.000
        RefundVoucherResponse r = service.payRefund(9);
        assertEquals("REFUNDED", r.status());
        assertEquals(0, new BigDecimal("3000000").compareTo(r.actualRefundAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.deductedDebtAmount()));
    }

    @Test
    void pendingVoucherCanBeCancelledButRefundedOneCannot() {
        DepositRefundVoucher v = refundedVoucher();
        assertThrows(BusinessException.class, () -> service.cancelRefundVoucher(50));
        v.setStatus("PENDING");
        service.cancelRefundVoucher(50);
        verify(voucherRepo).delete(v);
    }
}

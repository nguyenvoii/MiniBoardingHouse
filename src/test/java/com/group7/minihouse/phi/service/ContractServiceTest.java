package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.dto.ContractDtos.AdjustRequest;
import com.group7.minihouse.phi.dto.ContractDtos.ContractResponse;
import com.group7.minihouse.phi.dto.ContractDtos.CreateContractRequest;
import com.group7.minihouse.phi.dto.ContractDtos.ExtendRequest;
import com.group7.minihouse.phi.dto.ContractDtos.PricingRequest;
import com.group7.minihouse.phi.dto.ContractDtos.TerminateRequest;
import com.group7.minihouse.phi.dto.ContractDtos.TransferRequest;
import com.group7.minihouse.phi.repository.ContractAdjustmentRepository;
import com.group7.minihouse.phi.repository.RentalContractRepository;
import com.group7.minihouse.phi.repository.TenantReadRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.shared.entity.ContractAdjustment;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableExporter;
import com.group7.minihouse.shared.storage.FileStorage;
import com.group7.minihouse.toan.service.MeterReadingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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
import org.springframework.http.HttpStatus;

/** UC41-51 hop dong thue phong (Mockito, khong can DB). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractServiceTest {

    @Mock RentalContractRepository repo;
    @Mock ContractAdjustmentRepository adjustmentRepo;
    @Mock TenantReadRepository tenantRepo;
    @Mock UserRepository userRepo;
    @Mock EntityManager em;
    @Mock FileStorage fileStorage;
    @Mock TableExporter exporter;
    @Mock MeterReadingService meterService;
    @Mock ActivityLogService activityLogService;
    @Mock Query query;
    @Mock org.springframework.context.ApplicationEventPublisher publisher;
    @Mock DepositService depositService;

    ContractService service;
    Room room;
    Room room2;
    Tenant tenant;

    @BeforeEach
    void setUp() {
        service = new ContractService(repo, adjustmentRepo, tenantRepo, userRepo, em, fileStorage, exporter,
                meterService, activityLogService, new ContractEndService(publisher), depositService);
        room = room(1, "P101", "VACANT");
        room2 = room(2, "P102", "VACANT");
        tenant = new Tenant();
        tenant.setTenantId(7);
        tenant.setFullName("Nguyen Van A");
        tenant.setPhoneNumber("0900000001");
        tenant.setIdentityNumber("012345678901");
        when(em.find(Room.class, 1)).thenReturn(room);
        when(em.find(Room.class, 2)).thenReturn(room2);
        when(tenantRepo.findById(7)).thenReturn(Optional.of(tenant));
        when(repo.save(any(RentalContract.class))).thenAnswer(i -> {
            RentalContract c = i.getArgument(0);
            c.setContractId(100);
            return c;
        });
        when(em.getReference(eq(User.class), anyInt())).thenReturn(new User());
        when(em.createQuery(any(String.class))).thenReturn(query);
        when(query.setParameter(any(String.class), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
    }

    private static Room room(int id, String code, String status) {
        Room r = new Room();
        r.setRoomId(id);
        r.setRoomCode(code);
        r.setStatus(status);
        r.setBaseRentPrice(new BigDecimal("3000000"));
        r.setDepositAmount(new BigDecimal("3000000"));
        return r;
    }

    private RentalContract live(String status) {
        RentalContract c = new RentalContract();
        c.setContractId(100);
        c.setContractCode("HD1");
        c.setRoom(room);
        c.setTenant(tenant);
        c.setStartDate(LocalDate.of(2026, 1, 1));
        c.setEndDate(LocalDate.of(2026, 12, 31));
        c.setMonthlyRentPrice(new BigDecimal("3000000"));
        c.setDepositAmount(new BigDecimal("3000000"));
        c.setPaymentCycleMonths(1);
        c.setPaymentDueDay(5);
        c.setStatus(status);
        room.setStatus("RENTED");
        when(repo.findById(100)).thenReturn(Optional.of(c));
        return c;
    }

    private static CreateContractRequest req(LocalDate start, LocalDate end) {
        return new CreateContractRequest(1, 7, null, start, end, null, null, null, null, null);
    }

    @Test
    void createTakesDefaultsFromRoomAndMarksRoomRented() {
        ContractResponse r = service.create(req(LocalDate.of(2026, 3, 1), LocalDate.of(2027, 3, 1)), 5, "ip");
        assertEquals("ACTIVE", r.status());
        assertEquals(new BigDecimal("3000000"), r.monthlyRentPrice());
        assertEquals(1, r.paymentCycleMonths());
        assertEquals(5, r.paymentDueDay());
        assertEquals("RENTED", room.getStatus());
        assertEquals("HD20260301-P101", r.contractCode());
    }

    @Test
    void createRejectsOccupiedRoom() {
        room.setStatus("RENTED");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(req(LocalDate.of(2026, 3, 1), LocalDate.of(2027, 3, 1)), 5, "ip"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void createRejectsRoomThatStillHasLiveContract() {
        when(repo.countLiveByRoom(1, 0)).thenReturn(1L);
        assertThrows(BusinessException.class,
                () -> service.create(req(LocalDate.of(2026, 3, 1), LocalDate.of(2027, 3, 1)), 5, "ip"));
    }

    @Test
    void createRejectsEndBeforeStart() {
        assertThrows(BusinessException.class,
                () -> service.create(req(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1)), 5, "ip"));
    }

    @Test
    void createWorksWithoutLoggedInUserWhenSecurityIsOff() {
        service.create(req(LocalDate.of(2026, 3, 1), LocalDate.of(2027, 3, 1)), null, "ip");
        verify(activityLogService, org.mockito.Mockito.never()).log(any(), any(), any(), any());
    }

    @Test
    void createAvoidsDuplicateCode() {
        when(repo.existsByContractCode("HD20260301-P101")).thenReturn(true);
        ContractResponse r = service.create(req(LocalDate.of(2026, 3, 1), LocalDate.of(2027, 3, 1)), 5, "ip");
        assertEquals("HD20260301-P101-2", r.contractCode());
    }

    @Test
    void pricingChangeIsRecordedInHistory() {
        RentalContract c = live("ACTIVE");
        service.updatePricing(100, new PricingRequest(new BigDecimal("3500000"), null, null, 10), 5, "ip");
        assertEquals(new BigDecimal("3500000"), c.getMonthlyRentPrice());
        assertEquals(10, c.getPaymentDueDay());
        ArgumentCaptor<ContractAdjustment> cap = ArgumentCaptor.forClass(ContractAdjustment.class);
        verify(em, org.mockito.Mockito.atLeast(2)).persist(cap.capture());
        List<String> types = cap.getAllValues().stream().map(ContractAdjustment::getAdjustmentType).toList();
        assertTrue(types.contains("PRICE_CHANGE"));
        assertTrue(types.contains("PAYMENT_SCHEDULE"));
    }

    @Test
    void pricingRejectsBadDueDay() {
        live("ACTIVE");
        assertThrows(BusinessException.class,
                () -> service.updatePricing(100, new PricingRequest(null, null, null, 31), 5, "ip"));
    }

    @Test
    void terminatedContractCannotBeEdited() {
        live("TERMINATED");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updatePricing(100, new PricingRequest(new BigDecimal("1"), null, null, null), 5, "ip"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void extendMovesEndDateAndReactivates() {
        RentalContract c = live("EXPIRING_SOON");
        ContractResponse r = service.extend(100, new ExtendRequest(LocalDate.of(2027, 6, 30)), 5, "ip");
        assertEquals(LocalDate.of(2027, 6, 30), r.endDate());
        assertEquals("ACTIVE", c.getStatus());
    }

    @Test
    void extendRejectsEarlierDate() {
        live("ACTIVE");
        assertThrows(BusinessException.class,
                () -> service.extend(100, new ExtendRequest(LocalDate.of(2026, 6, 30)), 5, "ip"));
    }

    @Test
    void extendExpiredContractFailsWhenRoomTakenByAnother() {
        RentalContract c = live("EXPIRED");
        room.setStatus("RENTED");
        when(repo.countLiveByRoom(1, 100)).thenReturn(1L);
        assertThrows(BusinessException.class,
                () -> service.extend(100, new ExtendRequest(LocalDate.of(2027, 6, 30)), 5, "ip"));
        assertEquals("EXPIRED", c.getStatus());
    }

    @Test
    void adjustChangesPriceAndTenantAndClearsSignature() {
        RentalContract c = live("ACTIVE");
        c.setDigitalSignatureUrl("/x");
        Tenant t2 = new Tenant();
        t2.setTenantId(8);
        t2.setFullName("B");
        when(tenantRepo.findById(8)).thenReturn(Optional.of(t2));
        service.adjust(100, new AdjustRequest(new BigDecimal("3200000"), 8), 5, "ip");
        assertEquals(8, c.getTenant().getTenantId());
        assertEquals(new BigDecimal("3200000"), c.getMonthlyRentPrice());
        assertEquals(null, c.getDigitalSignatureUrl());
    }

    @Test
    void adjustNeedsAtLeastOneField() {
        live("ACTIVE");
        assertThrows(BusinessException.class, () -> service.adjust(100, new AdjustRequest(null, null), 5, "ip"));
    }

    @Test
    void transferSwapsRoomStatusesAndMovesResidents() {
        RentalContract c = live("ACTIVE");
        service.transferRoom(100, new TransferRequest(2, null, null, null), 5, "ip");
        assertEquals(2, c.getRoom().getRoomId());
        assertEquals("VACANT", room.getStatus());
        assertEquals("RENTED", room2.getStatus());
    }

    @Test
    void transferRejectsOccupiedTargetAndSameRoom() {
        live("ACTIVE");
        room2.setStatus("RENTED");
        assertThrows(BusinessException.class,
                () -> service.transferRoom(100, new TransferRequest(2, null, null, null), 5, "ip"));
        assertThrows(BusinessException.class,
                () -> service.transferRoom(100, new TransferRequest(1, null, null, null), 5, "ip"));
    }

    @Test
    void transferRejectsHalfFilledMeterClosing() {
        live("ACTIVE");
        assertThrows(BusinessException.class,
                () -> service.transferRoom(100, new TransferRequest(2, 9, 100, null), 5, "ip"));
    }

    @Test
    void transferClosesMetersWhenGiven() {
        live("ACTIVE");
        service.transferRoom(100, new TransferRequest(2, 9, 100, 50), 5, "ip");
        verify(meterService).recordMoveOut(any());
    }

    @Test
    void terminateFreesRoomAndPublishesTheSharedEndEvent() {
        RentalContract c = live("ACTIVE");
        ContractResponse r = service.terminate(100, new TerminateRequest("Khach chuyen di noi khac", "P101"), 5, "ip");
        assertEquals("TERMINATED", r.status());
        assertEquals("VACANT", room.getStatus());
        assertNotNull(c);
        // lich su o / TERMINATED / chot chi so do ContractEndListener lam khi nhan su kien, chung voi Tra phong
        ArgumentCaptor<com.group7.minihouse.shared.event.ContractEndedEvent> ev =
                ArgumentCaptor.forClass(com.group7.minihouse.shared.event.ContractEndedEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertEquals("ACTIVE", ev.getValue().previousStatus());
        assertEquals(100, ev.getValue().contractId());
    }

    @Test
    void terminateWorksForExpiredContractToo() {
        RentalContract c = live("EXPIRED");
        ContractResponse r = service.terminate(100, new TerminateRequest("Khach da don di tu truoc", "P101"), 5, "ip");
        assertEquals("TERMINATED", r.status());
        assertEquals("VACANT", room.getStatus());
        ArgumentCaptor<com.group7.minihouse.shared.event.ContractEndedEvent> ev =
                ArgumentCaptor.forClass(com.group7.minihouse.shared.event.ContractEndedEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertEquals("EXPIRED", ev.getValue().previousStatus());
        assertNotNull(c);
    }

    @Test
    void terminateBlockedWhileCheckoutRequestIsOpen() {
        live("ACTIVE");
        when(query.getResultList()).thenReturn(List.of(5));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.terminate(100, new TerminateRequest("Khach chuyen di noi khac", "P101"), 5, "ip"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void terminateWithFinalMetersValidatesThemFirst() {
        live("ACTIVE");
        org.mockito.Mockito.doThrow(new BusinessException("Chi so dien cuoi nho hon ky truoc"))
                .when(meterService).validateFinalReading(eq(1), any(LocalDate.class), eq(10), eq(5));
        assertThrows(BusinessException.class, () -> service.terminate(100,
                new TerminateRequest("Khach chuyen di noi khac", "P101", 10, 5), 5, "ip"));
        org.mockito.Mockito.verifyNoInteractions(publisher);
    }

    @Test
    void lowerDepositThanCollectedIsRejected() {
        live("ACTIVE");
        when(query.getSingleResult()).thenReturn(new BigDecimal("3000000")); // da thu 3.000.000
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updatePricing(100, new PricingRequest(null, new BigDecimal("2000000"), null, null), 5, "ip"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void createFromBookingTurnsHeldDepositIntoDepositReceipt() {
        var booking = new com.group7.minihouse.shared.entity.BookingRequest();
        booking.setRoom(room);
        booking.setTenant(tenant);
        booking.setStatus("APPROVED");
        booking.setDepositPaid(new BigDecimal("1000000"));
        when(em.find(com.group7.minihouse.shared.entity.BookingRequest.class, 9)).thenReturn(booking);
        service.create(new CreateContractRequest(1, 7, null, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 3, 1),
                null, null, null, null, null, 9), 5, "ip");
        verify(depositService).collect(100, new BigDecimal("1000000"), 5, "ip");
        assertEquals("CANCELLED", booking.getStatus());
    }

    @Test
    void createFromBookingRejectsBookingOfAnotherRoom() {
        var booking = new com.group7.minihouse.shared.entity.BookingRequest();
        booking.setRoom(room2);
        booking.setTenant(tenant);
        booking.setStatus("APPROVED");
        when(em.find(com.group7.minihouse.shared.entity.BookingRequest.class, 9)).thenReturn(booking);
        assertThrows(BusinessException.class, () -> service.create(new CreateContractRequest(1, 7, null,
                LocalDate.of(2026, 3, 1), LocalDate.of(2027, 3, 1), null, null, null, null, null, 9), 5, "ip"));
    }

    @Test
    void terminateNeedsReason() {
        live("ACTIVE");
        assertThrows(BusinessException.class, () -> service.terminate(100, new TerminateRequest("  ", "P101"), 5, "ip"));
    }

    @Test
    void terminateNeedsLongEnoughReason() {
        live("ACTIVE");
        assertThrows(BusinessException.class, () -> service.terminate(100, new TerminateRequest("ngan", "P101"), 5, "ip"));
    }

    @Test
    void terminateNeedsRoomCodeTyped() {
        live("ACTIVE");
        assertThrows(BusinessException.class, () -> service.terminate(100, new TerminateRequest("Khach chuyen di noi khac", "P999"), 5, "ip"));
        assertThrows(BusinessException.class, () -> service.terminate(100, new TerminateRequest("Khach chuyen di noi khac", null), 5, "ip"));
    }

    @Test
    void terminateBlockedWhileUnpaidInvoices() {
        live("ACTIVE");
        when(query.getSingleResult()).thenReturn(new java.math.BigDecimal("1500000"));
        assertThrows(BusinessException.class, () -> service.terminate(100, new TerminateRequest("Khach chuyen di noi khac", "P101"), 5, "ip"));
    }

    @Test
    void signOnlyOnce() {
        RentalContract c = live("ACTIVE");
        when(fileStorage.saveImage(any(), any(), anyLong())).thenReturn("a.png");
        service.sign(100, new byte[] {1}, 5, "ip");
        assertEquals("/api/contracts/signatures/a.png", c.getDigitalSignatureUrl());
        assertThrows(BusinessException.class, () -> service.sign(100, new byte[] {1}, 5, "ip"));
    }

    @Test
    void tenantCannotSeeOrSignSomeoneElsesContract() {
        live("ACTIVE");
        assertThrows(BusinessException.class, () -> service.detailOfTenant(100, 999));
        assertThrows(BusinessException.class, () -> service.signByTenant(100, 999, new byte[] {1}, 5, "ip"));
    }

    @Test
    void sendTellsWhenTenantHasNoAccount() {
        live("ACTIVE");
        when(userRepo.findByPhoneNumber("0900000001")).thenReturn(Optional.empty());
        assertTrue(service.send(100, 5, "ip").contains("chua co tai khoan"));
    }

    @Test
    void sendCreatesNotificationWhenTenantHasAccount() {
        live("ACTIVE");
        when(userRepo.findByPhoneNumber("0900000001")).thenReturn(Optional.of(new User()));
        service.send(100, 5, "ip");
        verify(em).persist(any(com.group7.minihouse.shared.entity.UserNotification.class));
    }

    @Test
    void pdfIsReadOnlyAndUrlIsSetAtCreation() {
        RentalContract c = live("ACTIVE");
        when(exporter.toTextPdf(any(), any())).thenReturn(new byte[] {1, 2});
        assertEquals(2, service.pdf(100, 5).length);
        service.pdf(100, 5);
        org.mockito.Mockito.verify(em, org.mockito.Mockito.never()).persist(any(ContractAdjustment.class));
        assertEquals(null, c.getPdfContractUrl()); // GET khong ghi
        room.setStatus("VACANT");
        ContractResponse created = service.create(req(LocalDate.of(2026, 3, 1), LocalDate.of(2027, 3, 1)), 5, "ip");
        assertEquals("/api/contracts/100/pdf", created.pdfContractUrl());
    }
}

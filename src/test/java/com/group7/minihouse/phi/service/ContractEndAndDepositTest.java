package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.shared.entity.ContractAdjustment;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.ResidentStayHistory;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.event.ContractEndedEvent;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.util.DepositLedger;
import com.group7.minihouse.toan.dto.MeterDtos.MoveOutRequest;
import com.group7.minihouse.toan.dto.PaymentDtos.ReceiptRequest;
import com.group7.minihouse.toan.service.MeterReadingService;
import com.group7.minihouse.toan.service.ReceiptService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Thanh lý qua Trả phòng phải để lại dấu vết như chấm dứt sớm; thu cọc không được vượt số cọc của hợp đồng. */
class ContractEndAndDepositTest {

    private EntityManager em;
    private Query query;
    private RentalContract contract;

    @BeforeEach
    void setUp() {
        em = mock(EntityManager.class);
        query = mock(Query.class);
        when(em.createQuery(any(String.class))).thenReturn(query);
        when(query.setParameter(any(String.class), any())).thenReturn(query);
        Room room = new Room();
        room.setRoomCode("P101");
        contract = new RentalContract();
        contract.setContractCode("HD1");
        contract.setRoom(room);
        contract.setTenant(new Tenant());
        contract.setStartDate(LocalDate.of(2026, 1, 1));
        contract.setStatus("ACTIVE");
        contract.setDepositAmount(new BigDecimal("3000000"));
        when(em.find(eq(RentalContract.class), anyInt())).thenReturn(contract);
        when(em.getReference(eq(User.class), anyInt())).thenReturn(new User());
    }

    private ContractEndedEvent event(Integer electric, Integer water) {
        return new ContractEndedEvent(1, 7, 1, LocalDate.of(2026, 11, 18), electric, water, "Tra phong", 5);
    }

    @Test
    void endingByCheckoutWritesTerminatedHistoryAndFreesResidents() {
        MeterReadingService meters = mock(MeterReadingService.class);
        when(query.getResultList()).thenReturn(List.of());
        new ContractEndListener(em, meters).onContractEnded(event(2000, 900));
        verify(em).persist(any(ContractAdjustment.class));
        verify(em).persist(any(ResidentStayHistory.class));
        verify(query).executeUpdate();
        verify(meters, never()).recordMoveOut(any());
    }

    @Test
    void endingByCheckoutClosesMetersWhenAnOpenPeriodExists() {
        MeterReadingService meters = mock(MeterReadingService.class);
        when(query.getResultList()).thenReturn(List.of(9));
        when(query.getSingleResult()).thenReturn(0L);
        new ContractEndListener(em, meters).onContractEnded(event(2000, 900));
        verify(meters).recordMoveOut(new MoveOutRequest(7, 9, 2000, 900, 5, null, null));
    }

    @Test
    void doesNotOverwriteAnExistingReadingInThatPeriod() {
        MeterReadingService meters = mock(MeterReadingService.class);
        when(query.getResultList()).thenReturn(List.of(9));
        when(query.getSingleResult()).thenReturn(1L);
        new ContractEndListener(em, meters).onContractEnded(event(2000, 900));
        verify(meters, never()).recordMoveOut(any());
    }

    @Test
    void depositCannotExceedContractDeposit() {
        ReceiptService receipts = mock(ReceiptService.class);
        DepositService service = new DepositService(em, receipts, mock(ActivityLogService.class));
        when(query.getSingleResult()).thenReturn(new BigDecimal("2000000"));
        assertThrows(BusinessException.class, () -> service.collect(1, new BigDecimal("1500000"), 5, "ip"));
        verify(receipts, never()).create(any());
    }

    @Test
    void depositCollectionWritesVoucherTiedToTheContractNote() {
        ReceiptService receipts = mock(ReceiptService.class);
        DepositService service = new DepositService(em, receipts, mock(ActivityLogService.class));
        when(query.getSingleResult()).thenReturn(new BigDecimal("2000000"));
        service.collect(1, new BigDecimal("1000000"), 5, "ip");
        verify(receipts).create(new ReceiptRequest(null, "DEPOSIT", new BigDecimal("1000000"),
                DepositLedger.note("HD1", "P101")));
    }

    @Test
    void depositRejectedOnceContractIsNoLongerLive() {
        contract.setStatus("TERMINATED");
        DepositService service = new DepositService(em, mock(ReceiptService.class), mock(ActivityLogService.class));
        assertThrows(BusinessException.class, () -> service.collect(1, new BigDecimal("1000"), 5, "ip"));
    }
}

package com.group7.minihouse.toan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.PaymentTransaction;
import com.group7.minihouse.shared.entity.PrepaymentDeduction;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.TenantPrepayment;
import com.group7.minihouse.shared.entity.UtilityReadingPeriod;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.dto.MeterDtos.ReadingRequest;
import com.group7.minihouse.toan.dto.PaymentDtos.DeductRequest;
import com.group7.minihouse.toan.repository.InvoiceRepository;
import com.group7.minihouse.toan.repository.MeterAdjustmentLogRepository;
import com.group7.minihouse.toan.repository.MeterAnomalyAlertRepository;
import com.group7.minihouse.toan.repository.MeterPhotoRepository;
import com.group7.minihouse.toan.repository.MeterReadingRepository;
import com.group7.minihouse.toan.repository.PrepaymentDeductionRepository;
import com.group7.minihouse.toan.repository.TenantPrepaymentRepository;
import com.group7.minihouse.toan.repository.ToanUserActivityLogRepository;
import com.group7.minihouse.toan.repository.ToanUserRepository;
import com.group7.minihouse.toan.repository.UtilityReadingPeriodRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** Can tru so du (M3), dao giao dich (B2) va ghi chi so (so cuoi nho hon so dau) cua Toan, khong can DB. */
class PrepaymentAndMeterTest {

    TenantPrepaymentRepository prepaymentRepo = mock(TenantPrepaymentRepository.class);
    InvoiceRepository invoiceRepo = mock(InvoiceRepository.class);
    EntityManager em = mock(EntityManager.class);
    PrepaymentDeductionRepository deductionRepo = mock(PrepaymentDeductionRepository.class);
    PrepaymentService prepayment;
    Invoice invoice;
    TenantPrepayment pre;

    @BeforeEach
    void setUp() {
        when(deductionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        prepayment = new PrepaymentService(prepaymentRepo, deductionRepo, invoiceRepo,
                new InvoiceBalanceCalculator(), mock(ToanUserRepository.class),
                mock(ToanUserActivityLogRepository.class), mock(ToanActivityWriter.class));
        ReflectionTestUtils.setField(prepayment, "em", em);
        Tenant t = new Tenant();
        t.setTenantId(3);
        invoice = new Invoice();
        invoice.setInvoiceId(10);
        invoice.setInvoiceCode("INV-1");
        invoice.setTenant(t);
        invoice.setTotalAmount(new BigDecimal("1000000"));
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setDueDate(LocalDate.now().plusDays(5));
        invoice.setPaymentStatus("UNPAID");
        pre = new TenantPrepayment();
        pre.setTenant(t);
        pre.setExcessBalance(new BigDecimal("500000"));
        when(invoiceRepo.findByIdForUpdate(10)).thenReturn(Optional.of(invoice));
        when(prepaymentRepo.findByTenantIdForUpdate(3)).thenReturn(Optional.of(pre));
    }

    /** M3: khong can tru tien du vao hoa don da huy, tien du cua khach giu nguyen. */
    @Test
    void khongCanTruVaoHoaDonDaHuyHoacDaTraDu() {
        invoice.setPaymentStatus("CANCELLED");
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> prepayment.deduct(new DeductRequest(3, 10, new BigDecimal("100000")))).getStatus());
        invoice.setPaymentStatus("PAID");
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> prepayment.deduct(new DeductRequest(3, 10, new BigDecimal("100000")))).getStatus());
        assertEquals(0, new BigDecimal("500000").compareTo(pre.getExcessBalance()));
    }

    @Test
    void canTruTruSoDuVaCongVaoHoaDon() {
        prepayment.deduct(new DeductRequest(3, 10, new BigDecimal("200000")));
        assertEquals(0, new BigDecimal("300000").compareTo(pre.getExcessBalance()));
        assertEquals(0, new BigDecimal("200000").compareTo(invoice.getPaidAmount()));
        assertEquals("PARTIALLY_PAID", invoice.getPaymentStatus());
    }

    @Test
    void canTruVuotSoDuHoacSoConPhaiTraBiTuChoi() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> prepayment.deduct(new DeductRequest(3, 10, new BigDecimal("600000")))).getStatus());
        pre.setExcessBalance(new BigDecimal("5000000"));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> prepayment.deduct(new DeductRequest(3, 10, new BigDecimal("1000001")))).getStatus());
    }

    @Test
    void automaticDeductionLocksBalanceBeforeUsingIt() {
        prepayment.autoDeduct(invoice);

        verify(prepaymentRepo).findByTenantIdForUpdate(3);
        assertEquals(BigDecimal.ZERO, pre.getExcessBalance());
        assertEquals(new BigDecimal("500000"), invoice.getPaidAmount());
    }

    @Test
    void repeatedUndoRejectsWhenDeductionDisappearedAfterInvoiceLock() {
        var deduction = new PrepaymentDeduction();
        deduction.setTargetInvoice(invoice);
        when(deductionRepo.findById(7)).thenReturn(Optional.of(deduction));
        when(deductionRepo.findByIdForUpdate(7)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class,
                () -> prepayment.undoDeduction(7)).getStatus());
        var order = org.mockito.Mockito.inOrder(invoiceRepo, deductionRepo);
        order.verify(invoiceRepo).findByIdForUpdate(10);
        order.verify(deductionRepo).findByIdForUpdate(7);
        assertEquals(new BigDecimal("500000"), pre.getExcessBalance());
    }

    /** B2: dao giao dich khoa hoa don, doc lai trang thai: giao dich da dao roi thi khong tru lan hai. */
    @Test
    void daoGiaoDichDaDaoKhongTruLanHai() {
        PaymentTransaction t = new PaymentTransaction();
        t.setTransactionId(5);
        t.setInvoice(invoice);
        t.setAmountPaid(new BigDecimal("400000"));
        t.setStatus("SUCCESS");
        invoice.setPaidAmount(new BigDecimal("400000"));
        invoice.setPaymentStatus("PARTIALLY_PAID");
        when(em.find(PaymentTransaction.class, 5)).thenReturn(t);

        prepayment.reverseTransaction(5, null, null);
        assertEquals("REVERSED", t.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getPaidAmount()));

        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> prepayment.reverseTransaction(5, null, null)).getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getPaidAmount()));
    }

    // ------------------------------------------------------------------ chi so dien nuoc

    @Test
    void chiSoCuoiNhoHonChiSoDauBiTuChoiVaKhongGhi() {
        UtilityReadingPeriodRepository periodRepo = mock(UtilityReadingPeriodRepository.class);
        MeterReadingRepository readingRepo = mock(MeterReadingRepository.class);
        MeterReadingService meters = new MeterReadingService(periodRepo, readingRepo,
                mock(MeterPhotoRepository.class), mock(MeterAdjustmentLogRepository.class),
                mock(MeterAnomalyAlertRepository.class));
        ReflectionTestUtils.setField(meters, "em", em);
        Property property = new Property();
        property.setPropertyId(1);
        UtilityReadingPeriod period = new UtilityReadingPeriod();
        period.setPeriodId(4);
        period.setProperty(property);
        period.setStartDate(LocalDate.of(2026, 11, 1));
        period.setEndDate(LocalDate.of(2026, 11, 30));
        period.setIsClosed(false);
        Room room = new Room();
        room.setRoomId(8);
        room.setProperty(property);
        room.setInitialElectricMeter(100);
        room.setInitialWaterMeter(50);
        when(periodRepo.findById(4)).thenReturn(Optional.of(period));
        when(em.find(Room.class, 8)).thenReturn(room);

        BusinessException lowElectric = assertThrows(BusinessException.class, () -> meters.recordReading(
                new ReadingRequest(4, 8, null, 90, null, 60, 1, null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, lowElectric.getStatus());
        assertTrue(lowElectric.getMessage().contains("điện"));
        BusinessException lowWater = assertThrows(BusinessException.class, () -> meters.recordReading(
                new ReadingRequest(4, 8, null, 120, null, 40, 1, null, null)));
        assertTrue(lowWater.getMessage().contains("nước"));
        org.mockito.Mockito.verify(readingRepo, org.mockito.Mockito.never()).save(any());

        period.setIsClosed(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class, () -> meters.recordReading(
                new ReadingRequest(4, 8, null, 120, null, 60, 1, null, null))).getStatus());
    }
}

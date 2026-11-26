package com.group7.minihouse.toan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.shared.entity.MeterReading;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UtilityReadingPeriod;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.repository.MeterAdjustmentLogRepository;
import com.group7.minihouse.toan.repository.MeterAnomalyAlertRepository;
import com.group7.minihouse.toan.repository.MeterPhotoRepository;
import com.group7.minihouse.toan.repository.MeterReadingRepository;
import com.group7.minihouse.toan.repository.UtilityReadingPeriodRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** F1/F6: kiem va ghi chi so cuoi khi tra phong (so cuoi < so ky truoc phai bi chan, khong ghi hong giao dich). */
class MeterFinalReadingTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    UtilityReadingPeriodRepository periodRepo = mock(UtilityReadingPeriodRepository.class);
    MeterReadingRepository readingRepo = mock(MeterReadingRepository.class);
    EntityManager em = mock(EntityManager.class);
    @SuppressWarnings("unchecked")
    TypedQuery<UtilityReadingPeriod> periodQuery = mock(TypedQuery.class);
    MeterReadingService service;
    Room room;
    UtilityReadingPeriod period;
    MeterReading previous;

    @BeforeEach
    void setUp() {
        service = new MeterReadingService(periodRepo, readingRepo, mock(MeterPhotoRepository.class),
                mock(MeterAdjustmentLogRepository.class), mock(MeterAnomalyAlertRepository.class));
        ReflectionTestUtils.setField(service, "em", em);
        Property p = new Property();
        p.setPropertyId(1);
        room = new Room();
        room.setRoomId(7);
        room.setRoomCode("P101");
        room.setProperty(p);
        room.setInitialElectricMeter(0);
        room.setInitialWaterMeter(0);
        period = new UtilityReadingPeriod();
        period.setPeriodId(9);
        period.setProperty(p);
        period.setPeriodName("T11");
        period.setStartDate(LocalDate.of(2026, 11, 1));
        period.setEndDate(LocalDate.of(2026, 11, 30));
        period.setIsClosed(false);
        previous = new MeterReading();
        previous.setOldElectric(60);
        previous.setOldWater(20);
        previous.setNewElectric(100);
        previous.setNewWater(40);
        when(em.find(Room.class, 7)).thenReturn(room);
        User who = new User();
        who.setUserId(5);
        when(em.getReference(User.class, 5)).thenReturn(who);
        when(em.createQuery(anyString(), org.mockito.ArgumentMatchers.<Class<UtilityReadingPeriod>>any()))
                .thenReturn(periodQuery);
        when(periodQuery.setParameter(anyString(), any())).thenReturn(periodQuery);
        when(periodQuery.setMaxResults(anyInt())).thenReturn(periodQuery);
        when(periodQuery.getResultList()).thenReturn(List.of(period));
        when(readingRepo.findFirstByRoomRoomIdAndPeriodEndDateLessThanOrderByPeriodEndDateDescReadingIdDesc(7,
                LocalDate.of(2026, 11, 1))).thenReturn(Optional.of(previous));
    }

    @Test
    void finalElectricBelowPreviousPeriodIsRejectedWith400() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateFinalReading(7, TODAY, 90, 50));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("dien"));
    }

    @Test
    void finalWaterBelowPreviousPeriodIsRejected() {
        assertThrows(BusinessException.class, () -> service.validateFinalReading(7, TODAY, 150, 39));
    }

    @Test
    void validFinalMetersPassValidation() {
        service.validateFinalReading(7, TODAY, 100, 40); // bang so ky truoc van hop le (khong dung dien)
    }

    @Test
    void recordFinalReadingReturnsFalseInsteadOfThrowingWhenBelowPrevious() {
        assertFalse(service.recordFinalReading(7, TODAY, 90, 50, 5));
        verify(readingRepo, never()).save(any());
    }

    @Test
    void recordFinalReadingReturnsFalseWhenNoOpenPeriod() {
        when(periodQuery.getResultList()).thenReturn(List.of());
        assertFalse(service.recordFinalReading(7, TODAY, 150, 50, 5));
        verify(readingRepo, never()).save(any());
    }

    @Test
    void recordFinalReadingWritesVerifiedReadingForTheOpenPeriod() {
        when(periodRepo.findById(9)).thenReturn(Optional.of(period));
        when(readingRepo.findByPeriodPeriodIdAndRoomRoomId(9, 7)).thenReturn(Optional.empty());
        when(readingRepo.save(any(MeterReading.class))).thenAnswer(i -> {
            MeterReading r = i.getArgument(0);
            r.setReadingId(77);
            return r;
        });
        MeterReading saved = new MeterReading();
        when(readingRepo.findById(77)).thenAnswer(i -> Optional.of(saved));
        saved.setReadingId(77);
        saved.setPeriod(period);
        saved.setRoom(room);
        saved.setOldElectric(100);
        saved.setNewElectric(150);
        saved.setOldWater(40);
        saved.setNewWater(50);
        saved.setRecordedByUser(new User());
        saved.getRecordedByUser().setUserId(5);
        assertTrue(service.recordFinalReading(7, TODAY, 150, 50, 5));
        verify(readingRepo).save(any(MeterReading.class));
        assertTrue(saved.getIsVerified());
    }

    @Test
    void existingReadingInThePeriodIsUpdatedToTheFinalNumbers() {
        MeterReading existing = new MeterReading();
        existing.setReadingId(77);
        existing.setPeriod(period);
        existing.setRoom(room);
        existing.setOldElectric(100);
        existing.setNewElectric(130);
        existing.setOldWater(40);
        existing.setNewWater(45);
        existing.setRecordedByUser(new User());
        existing.getRecordedByUser().setUserId(5);
        when(readingRepo.findByPeriodPeriodIdAndRoomRoomId(9, 7)).thenReturn(Optional.of(existing));
        when(readingRepo.findById(77)).thenReturn(Optional.of(existing));
        when(em.getReference(User.class, 5)).thenReturn(new User());
        assertTrue(service.recordFinalReading(7, TODAY, 150, 45, 5));
        assertEquals(150, existing.getNewElectric());
        assertEquals(45, existing.getNewWater());
        assertTrue(existing.getIsVerified());

        existing.setIsVerified(false);
        assertTrue(service.recordFinalReading(7, TODAY, 150, 45, 5));
        assertTrue(existing.getIsVerified());
    }
}

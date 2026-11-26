package com.group7.minihouse.toan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.entity.UtilityReadingPeriod;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.dto.MeterDtos.PeriodRequest;
import com.group7.minihouse.toan.repository.MeterAdjustmentLogRepository;
import com.group7.minihouse.toan.repository.MeterAnomalyAlertRepository;
import com.group7.minihouse.toan.repository.MeterPhotoRepository;
import com.group7.minihouse.toan.repository.MeterReadingRepository;
import com.group7.minihouse.toan.repository.UtilityReadingPeriodRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** UC53: ky ghi so khong duoc chong len khoang thoi gian cua ky khac trong cung khu (phat hien qua E2E 2026-11-26). */
class MeterPeriodOverlapTest {

    UtilityReadingPeriodRepository periodRepo = mock(UtilityReadingPeriodRepository.class);
    MeterReadingService service = new MeterReadingService(periodRepo, mock(MeterReadingRepository.class),
            mock(MeterPhotoRepository.class), mock(MeterAdjustmentLogRepository.class), mock(MeterAnomalyAlertRepository.class));

    MeterPeriodOverlapTest() {
        ReflectionTestUtils.setField(service, "em", mock(EntityManager.class));
    }

    @Test
    void overlappingPeriodIsRejectedWithConflict() {
        LocalDate start = LocalDate.of(2029, 2, 1);
        LocalDate end = LocalDate.of(2029, 2, 28);
        when(periodRepo.existsByPropertyPropertyIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(1, end, start))
                .thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPeriod(new PeriodRequest(1, "Thang 2/2029 (trung)", start, end)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(periodRepo, never()).save(any(UtilityReadingPeriod.class));
    }

    @Test
    void nonOverlappingPeriodIsSaved() {
        LocalDate start = LocalDate.of(2029, 3, 1);
        LocalDate end = LocalDate.of(2029, 3, 31);
        when(periodRepo.save(any(UtilityReadingPeriod.class))).thenAnswer(i -> {
            UtilityReadingPeriod saved = i.getArgument(0);
            Property property = new Property();
            property.setPropertyId(1);
            saved.setProperty(property);
            return saved;
        });
        service.createPeriod(new PeriodRequest(1, "Thang 3/2029", start, end));
        verify(periodRepo).save(any(UtilityReadingPeriod.class));
    }
}

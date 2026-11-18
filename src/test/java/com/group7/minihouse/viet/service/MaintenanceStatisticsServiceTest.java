package com.group7.minihouse.viet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.IncidentCategory;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableData;
import com.group7.minihouse.viet.dto.MaintenanceStatsDtos.MaintenanceHistoryItem;
import com.group7.minihouse.viet.dto.MaintenanceStatsDtos.MaintenanceStatsResponse;
import com.group7.minihouse.viet.repository.IncidentCategoryRepository;
import com.group7.minihouse.viet.repository.MaintenanceRequestRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** UC131: thống kê sự cố và lịch sử bảo trì, không cần DB. */
class MaintenanceStatisticsServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    private final MaintenanceRequestRepository requests = mock(MaintenanceRequestRepository.class);
    private final IncidentCategoryRepository categories = mock(IncidentCategoryRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final MaintenanceStatisticsService service =
            new MaintenanceStatisticsService(requests, categories, rooms);

    private void stubCategory(String code, String name) {
        IncidentCategory c = mock(IncidentCategory.class);
        when(c.getCategoryCode()).thenReturn(code);
        when(c.getCategoryName()).thenReturn(name);
        when(categories.findAll()).thenReturn(List.of(c));
    }

    private static List<Object[]> rows(Object[]... r) {
        return new ArrayList<>(List.of(r));
    }

    @Test
    void thieuNgayPhaiTuChoi() {
        assertThrows(BusinessException.class, () -> service.statistics(null, TO));
        assertThrows(BusinessException.class, () -> service.statistics(FROM, null));
    }

    @Test
    void ngayBatDauSauNgayKetThucPhaiTuChoi() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.statistics(TO, FROM));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void khoangQua366NgayPhaiTuChoi() {
        assertThrows(BusinessException.class, () -> service.statistics(FROM, FROM.plusDays(367)));
    }

    @Test
    void khoangThoiGianLayDenHetNgayKetThuc() {
        service.statistics(FROM, TO);
        verify(requests).countByStatus(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 12, 1, 0, 0));
    }

    @Test
    void tongSoSuCoVaChiPhiTinhTuCacNhom() {
        stubCategory("ELECTRIC", "Điện");
        when(requests.countByStatus(any(), any())).thenReturn(rows(new Object[] {"OPEN", 2L},
                new Object[] {"CLOSED", 3L}));
        when(requests.statsByCategory(any(), any())).thenReturn(rows(
                new Object[] {"ELECTRIC", 3L, new BigDecimal("500000"), 4.0},
                new Object[] {"WATER", 2L, null, null}));
        MaintenanceStatsResponse r = service.statistics(FROM, TO);
        assertEquals(5, r.total());
        assertEquals(0, new BigDecimal("500000").compareTo(r.totalCost()));
        assertEquals("Điện", r.byCategory().get(0).categoryName());
        // Nhóm không còn trong danh mục thì giữ nguyên mã, chi phí null thành 0.
        assertEquals("WATER", r.byCategory().get(1).categoryName());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.byCategory().get(1).totalCost()));
        assertNull(r.byCategory().get(1).avgRating());
    }

    @Test
    void thongKeTheoThangVaPhongHayHong() {
        when(requests.countByMonth(any(), any())).thenReturn(rows(new Object[] {2026, 9, 4L},
                new Object[] {2026, 11, 1L}));
        when(requests.topRooms(any(), any(), any())).thenReturn(rows(new Object[] {7, "P101", 3L}));
        MaintenanceStatsResponse r = service.statistics(FROM, TO);
        assertEquals("2026-09", r.byMonth().get(0).month());
        assertEquals("P101", r.topRooms().get(0).roomCode());
    }

    @Test
    void thoiGianXuLyTrungBinhBoQuaDongLech() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 3, 8, 0);
        when(requests.resolvedTimes(any(), any())).thenReturn(rows(
                new Object[] {t, t.plusHours(2)},
                new Object[] {t, t.plusHours(4)},
                new Object[] {t, t.minusHours(1)}));
        assertEquals(3.0, service.statistics(FROM, TO).avgResolutionHours());
    }

    @Test
    void chuaCoSuCoXongThiThoiGianTrungBinhLaNull() {
        assertNull(service.statistics(FROM, TO).avgResolutionHours());
    }

    @Test
    void lichSuPhongKhongTonTaiPhaiTra404() {
        when(rooms.existsById(anyInt())).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.roomHistory(99));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void lichSuPhongGomNguoiXuLyChiPhiVaGioXuLy() {
        stubCategory("ELECTRIC", "Điện");
        when(rooms.existsById(7)).thenReturn(true);
        LocalDateTime t = LocalDateTime.of(2026, 11, 3, 8, 0);
        when(requests.roomHistory(any(), any())).thenReturn(rows(
                new Object[] {10, "ELECTRIC", "Mất điện", "URGENT", "CLOSED", t, "Thợ An",
                        new BigDecimal("150000"), 5, t.plusMinutes(90)},
                new Object[] {11, "ELECTRIC", "Chập đèn", "LOW", "OPEN", t, null, null, null, null}));
        List<MaintenanceHistoryItem> list = service.roomHistory(7);
        assertEquals("Điện", list.get(0).categoryName());
        assertEquals("Thợ An", list.get(0).assignedStaffName());
        assertEquals(1.5, list.get(0).resolutionHours());
        assertNull(list.get(1).resolutionHours());
    }

    @Test
    void xuatThongKeCoDongTongCuoi() {
        stubCategory("ELECTRIC", "Điện");
        when(requests.countByStatus(any(), any())).thenReturn(rows(new Object[] {"CLOSED", 3L}));
        when(requests.statsByCategory(any(), any())).thenReturn(rows(
                new Object[] {"ELECTRIC", 3L, new BigDecimal("500000"), 4.0}));
        TableData data = service.statisticsTable(FROM, TO);
        assertEquals(2, data.rows().size());
        assertEquals("TỔNG", data.rows().get(1).get(0));
        assertEquals(3L, data.rows().get(1).get(1));
        assertTrue(data.title().contains("2026-11-01"));
    }
}

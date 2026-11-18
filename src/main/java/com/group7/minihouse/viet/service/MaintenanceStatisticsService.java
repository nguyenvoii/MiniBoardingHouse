package com.group7.minihouse.viet.service;

import static com.group7.minihouse.viet.service.VietSupport.nz;

import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.IncidentCategory;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableData;
import com.group7.minihouse.viet.dto.MaintenanceStatsDtos.CategoryStat;
import com.group7.minihouse.viet.dto.MaintenanceStatsDtos.CountItem;
import com.group7.minihouse.viet.dto.MaintenanceStatsDtos.MaintenanceHistoryItem;
import com.group7.minihouse.viet.dto.MaintenanceStatsDtos.MaintenanceStatsResponse;
import com.group7.minihouse.viet.dto.MaintenanceStatsDtos.MonthStat;
import com.group7.minihouse.viet.dto.MaintenanceStatsDtos.RoomStat;
import com.group7.minihouse.viet.repository.IncidentCategoryRepository;
import com.group7.minihouse.viet.repository.MaintenanceRequestRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC131: thống kê sự cố và lịch sử bảo trì theo phòng, theo nhóm sự cố. */
@Service
public class MaintenanceStatisticsService {

    static final int MAX_DAYS = 366;
    static final int TOP_ROOMS = 5;
    static final int HISTORY_LIMIT = 500;

    private final MaintenanceRequestRepository requestRepository;
    private final IncidentCategoryRepository categoryRepository;
    private final RoomRepository roomRepository;

    public MaintenanceStatisticsService(MaintenanceRequestRepository requestRepository,
                                        IncidentCategoryRepository categoryRepository,
                                        RoomRepository roomRepository) {
        this.requestRepository = requestRepository;
        this.categoryRepository = categoryRepository;
        this.roomRepository = roomRepository;
    }

    /** UC131: thống kê các sự cố tạo trong khoảng from..to (gồm cả hai ngày). */
    @Transactional(readOnly = true)
    public MaintenanceStatsResponse statistics(LocalDate from, LocalDate to) {
        validateRange(from, to);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<CountItem> byStatus = requestRepository.countByStatus(start, end).stream()
                .map(o -> new CountItem((String) o[0], ((Number) o[1]).longValue())).toList();
        Map<String, String> names = categoryNames();
        List<CategoryStat> byCategory = requestRepository.statsByCategory(start, end).stream()
                .map(o -> new CategoryStat((String) o[0], names.getOrDefault((String) o[0], (String) o[0]),
                        ((Number) o[1]).longValue(), nz((BigDecimal) o[2]),
                        o[3] == null ? null : round1(((Number) o[3]).doubleValue())))
                .toList();
        List<MonthStat> byMonth = requestRepository.countByMonth(start, end).stream()
                .map(o -> new MonthStat(String.format("%04d-%02d", ((Number) o[0]).intValue(),
                        ((Number) o[1]).intValue()), ((Number) o[2]).longValue()))
                .toList();
        List<RoomStat> topRooms = requestRepository.topRooms(start, end, PageRequest.of(0, TOP_ROOMS)).stream()
                .map(o -> new RoomStat((Integer) o[0], (String) o[1], ((Number) o[2]).longValue())).toList();

        long total = byStatus.stream().mapToLong(CountItem::count).sum();
        BigDecimal totalCost = byCategory.stream().map(CategoryStat::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MaintenanceStatsResponse(from, to, total, totalCost,
                avgResolutionHours(requestRepository.resolvedTimes(start, end)), byStatus, byCategory, byMonth,
                topRooms);
    }

    /** UC131: lịch sử bảo trì của một phòng (tối đa 500 sự cố mới nhất). */
    @Transactional(readOnly = true)
    public List<MaintenanceHistoryItem> roomHistory(Integer roomId) {
        if (roomId == null || !roomRepository.existsById(roomId)) {
            throw BusinessException.notFound("Phòng");
        }
        Map<String, String> names = categoryNames();
        List<MaintenanceHistoryItem> items = new ArrayList<>();
        for (Object[] o : requestRepository.roomHistory(roomId, PageRequest.of(0, HISTORY_LIMIT))) {
            LocalDateTime created = (LocalDateTime) o[5];
            LocalDateTime completed = (LocalDateTime) o[9];
            items.add(new MaintenanceHistoryItem((Integer) o[0], (String) o[1],
                    names.getOrDefault((String) o[1], (String) o[1]), (String) o[2], (String) o[3],
                    (String) o[4], created, (String) o[6], (BigDecimal) o[7], (Integer) o[8], completed,
                    hours(created, completed)));
        }
        return items;
    }

    /** UC131: bảng xuất file thống kê theo nhóm sự cố, dòng cuối là tổng. */
    @Transactional(readOnly = true)
    public TableData statisticsTable(LocalDate from, LocalDate to) {
        MaintenanceStatsResponse s = statistics(from, to);
        List<List<Object>> rows = new ArrayList<>();
        for (CategoryStat c : s.byCategory()) {
            rows.add(Arrays.<Object>asList(c.categoryName(), c.count(), c.totalCost(), c.avgRating()));
        }
        rows.add(Arrays.<Object>asList("TỔNG", s.total(), s.totalCost(), null));
        return new TableData("Thống kê sự cố " + from + " đến " + to,
                List.of("Nhóm sự cố", "Số sự cố", "Tổng chi phí", "Điểm đánh giá TB"), rows);
    }

    /** UC131: bảng xuất file lịch sử bảo trì của một phòng. */
    @Transactional(readOnly = true)
    public TableData historyTable(Integer roomId) {
        List<List<Object>> rows = new ArrayList<>();
        for (MaintenanceHistoryItem i : roomHistory(roomId)) {
            rows.add(Arrays.<Object>asList(i.requestId(), i.categoryName(), i.description(), i.priority(),
                    i.status(), i.createdAt(), i.assignedStaffName(), i.repairCost(), i.tenantRating(),
                    i.completedAt(), i.resolutionHours()));
        }
        return new TableData("Lịch sử bảo trì phòng " + roomId,
                List.of("Mã yêu cầu", "Nhóm sự cố", "Mô tả", "Ưu tiên", "Trạng thái", "Ngày tạo", "Người xử lý",
                        "Chi phí", "Điểm đánh giá", "Ngày xong", "Giờ xử lý"), rows);
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException("Thiếu from hoặc to (định dạng yyyy-MM-dd)");
        }
        if (from.isAfter(to)) {
            throw new BusinessException("Ngày bắt đầu không được sau ngày kết thúc");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new BusinessException("Khoảng thời gian thống kê tối đa " + MAX_DAYS + " ngày");
        }
    }

    private Map<String, String> categoryNames() {
        Map<String, String> names = new HashMap<>();
        for (IncidentCategory c : categoryRepository.findAll()) {
            names.put(c.getCategoryCode(), c.getCategoryName());
        }
        return names;
    }

    /** Trung bình (xong - tạo) tính bằng giờ; bỏ qua dòng dữ liệu lệch (xong trước khi tạo). */
    private static Double avgResolutionHours(List<Object[]> pairs) {
        double sum = 0;
        int n = 0;
        for (Object[] p : pairs) {
            Double h = hours((LocalDateTime) p[0], (LocalDateTime) p[1]);
            if (h != null) {
                sum += h;
                n++;
            }
        }
        return n == 0 ? null : round1(sum / n);
    }

    private static Double hours(LocalDateTime created, LocalDateTime completed) {
        if (created == null || completed == null || completed.isBefore(created)) {
            return null;
        }
        return round1(Duration.between(created, completed).toMinutes() / 60.0);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}

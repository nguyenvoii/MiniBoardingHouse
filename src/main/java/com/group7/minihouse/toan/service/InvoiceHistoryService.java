package com.group7.minihouse.toan.service;

import com.group7.minihouse.shared.entity.UserActivityLog;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.dto.InvoiceHistoryDtos.HistoryItem;
import com.group7.minihouse.toan.repository.InvoiceHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC128: tra cứu lịch sử thao tác hóa đơn và giao dịch. */
@Service
public class InvoiceHistoryService {
    private static final List<String> ACTIONS = List.of("CREATE_INVOICE", "UPDATE_INVOICE", "CANCEL_INVOICE",
            "REVERSE_PAYMENT", "REFUND_PREPAYMENT", "ISSUE_INVOICE", "ADJUST_INVOICE");
    private final InvoiceHistoryRepository repository;

    public InvoiceHistoryService(InvoiceHistoryRepository repository) { this.repository = repository; }

    /** UC128: tìm lịch sử theo thời gian, loại thao tác và mã hóa đơn/giao dịch trong mô tả. */
    @Transactional(readOnly = true)
    public List<HistoryItem> search(LocalDateTime from, LocalDateTime to, String actionType,
                                    String keyword, int page, int size) {
        LocalDateTime start = from == null ? LocalDateTime.of(1900, 1, 1, 0, 0) : from;
        LocalDateTime end = to == null ? LocalDateTime.of(9999, 12, 31, 23, 59, 59) : to;
        if (start.isAfter(end)) throw new BusinessException("Thời điểm bắt đầu phải trước thời điểm kết thúc");
        if (page < 0) throw new BusinessException("Số trang không được âm");
        if (size < 1 || size > 100) throw new BusinessException("Kích thước trang phải từ 1 đến 100");
        if (actionType != null && !actionType.isBlank() && !ACTIONS.contains(actionType.trim()))
            throw new BusinessException("Loại thao tác không được hỗ trợ");
        return repository.search(ACTIONS, start, end, actionType == null ? "" : actionType.trim(),
                escapeLike(keyword), PageRequest.of(page, size,
                        Sort.by(Sort.Direction.DESC, "createdAt", "logId")))
                .stream().map(this::toItem).toList();
    }

    private String escapeLike(String keyword) {
        if (keyword == null || keyword.isBlank()) return "";
        return keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private HistoryItem toItem(UserActivityLog log) {
        return new HistoryItem(log.getLogId(), log.getCreatedAt(), log.getUser().getUserId(),
                log.getUser().getFullName(), log.getActionType(), log.getDescription(), log.getIpAddress());
    }
}

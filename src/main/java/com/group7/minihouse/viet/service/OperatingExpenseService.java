package com.group7.minihouse.viet.service;

import static com.group7.minihouse.viet.service.VietSupport.nz;

import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.OperatingExpense;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.ExpenseDtos.CategoryTotal;
import com.group7.minihouse.viet.dto.ExpenseDtos.ExpenseRequest;
import com.group7.minihouse.viet.dto.ExpenseDtos.ExpenseResponse;
import com.group7.minihouse.viet.dto.ExpenseDtos.ExpenseSummary;
import com.group7.minihouse.viet.repository.OperatingExpenseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC117: quan ly chi phi van hanh khu tro (bao tri, ve sinh, thue...). He thong chi co 1 khu tro nen
 * chi phi gan vao khu tro dau tien. Co tim kiem theo nhom/khoang ngay va thong ke tong chi theo nhom.
 */
@Service
public class OperatingExpenseService {

    static final int CATEGORY_MAX = 50;
    static final int DESCRIPTION_MAX = 255;
    static final int URL_MAX = 500;
    static final BigDecimal AMOUNT_MAX = new BigDecimal("9999999999999999.99");
    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);
    private static final List<String> DEFAULT_CATEGORIES =
            List.of("Bảo trì", "Vệ sinh", "Thuế", "Điện nước chung", "Dịch vụ chung", "Khác");

    @PersistenceContext
    private EntityManager em;

    private final OperatingExpenseRepository repository;
    private final ActorResolver actorResolver;

    public OperatingExpenseService(OperatingExpenseRepository repository, ActorResolver actorResolver) {
        this.repository = repository;
        this.actorResolver = actorResolver;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> search(String category, LocalDate from, LocalDate to, int page, int size) {
        checkRange(from, to);
        Sort sort = Sort.by(Sort.Direction.DESC, "expenseDate").and(Sort.by(Sort.Direction.DESC, "expenseId"));
        return new PageResponse<>(repository.search(VietSupport.blankToNull(category), from == null ? MIN_DATE : from,
                to == null ? MAX_DATE : to, VietSupport.page(page, size, sort)).map(ExpenseResponse::from));
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Integer id) {
        return ExpenseResponse.from(find(id));
    }

    @Transactional
    public ExpenseResponse create(ExpenseRequest req, Integer actorId) {
        validate(req);
        OperatingExpense e = new OperatingExpense();
        e.setProperty(currentProperty());
        e.setRecordedBy(actorResolver.require(actorId));
        e.setCreatedAt(LocalDateTime.now());
        apply(e, req);
        return ExpenseResponse.from(repository.save(e));
    }

    @Transactional
    public ExpenseResponse update(Integer id, ExpenseRequest req) {
        validate(req);
        OperatingExpense e = find(id);
        apply(e, req);
        return ExpenseResponse.from(repository.save(e));
    }

    @Transactional
    public void delete(Integer id) {
        repository.delete(find(id));
    }

    /** Nhom chi phi goi y: cac nhom da dung + nhom mac dinh. */
    @Transactional(readOnly = true)
    public List<String> categories() {
        Set<String> all = new LinkedHashSet<>(DEFAULT_CATEGORIES);
        all.addAll(repository.findDistinctCategories());
        return new ArrayList<>(all);
    }

    @Transactional(readOnly = true)
    public ExpenseSummary summary(LocalDate from, LocalDate to) {
        checkRange(from, to);
        List<CategoryTotal> byCategory = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        long count = 0;
        for (Object[] row : repository.summarizeByCategory(from == null ? MIN_DATE : from,
                to == null ? MAX_DATE : to)) {
            BigDecimal amount = nz((BigDecimal) row[1]);
            long n = ((Number) row[2]).longValue();
            byCategory.add(new CategoryTotal((String) row[0], amount, n));
            total = total.add(amount);
            count += n;
        }
        return new ExpenseSummary(from, to, total, count, byCategory);
    }

    /** Kiem tra dau vao (tach rieng de test duoc ma khong can DB). */
    static void validate(ExpenseRequest req) {
        if (req == null) {
            throw new BusinessException("Thiếu dữ liệu");
        }
        String category = VietSupport.requireText(req.expenseCategory(), "nhom chi phi");
        if (category.length() > CATEGORY_MAX) {
            throw new BusinessException("Nhóm chi phí tối đa " + CATEGORY_MAX + " ký tự");
        }
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw new BusinessException("Số tiền phải lớn hơn 0");
        }
        if (req.amount().compareTo(AMOUNT_MAX) > 0) {
            throw new BusinessException("Số tiền quá lớn");
        }
        if (req.expenseDate() == null) {
            throw new BusinessException("Thiếu ngày chi");
        }
        if (req.expenseDate().isAfter(LocalDate.now())) {
            throw new BusinessException("Ngày chi không được ở tương lai");
        }
        String description = VietSupport.blankToNull(req.description());
        if (description != null && description.length() > DESCRIPTION_MAX) {
            throw new BusinessException("Mô tả tối đa " + DESCRIPTION_MAX + " ký tự");
        }
        String url = VietSupport.blankToNull(req.receiptUrl());
        if (url != null && url.length() > URL_MAX) {
            throw new BusinessException("Đường dẫn chứng từ tối đa " + URL_MAX + " ký tự");
        }
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("Từ ngày phải trước hoặc bằng đến ngày");
        }
    }

    private static void apply(OperatingExpense e, ExpenseRequest req) {
        e.setExpenseCategory(req.expenseCategory().trim());
        e.setDescription(VietSupport.blankToNull(req.description()));
        e.setAmount(req.amount());
        e.setExpenseDate(req.expenseDate());
        e.setReceiptUrl(VietSupport.blankToNull(req.receiptUrl()));
    }

    private Property currentProperty() {
        List<Property> list = em.createQuery("select p from Property p order by p.propertyId", Property.class)
                .setMaxResults(1).getResultList();
        if (list.isEmpty()) {
            throw new BusinessException("Chưa cấu hình khu trọ nên chưa ghi nhận được chi phí");
        }
        return list.get(0);
    }

    private OperatingExpense find(Integer id) {
        return repository.findById(id).orElseThrow(() -> BusinessException.notFound("Khoản chi phí"));
    }
}

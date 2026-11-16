package com.group7.minihouse.toan.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.group7.minihouse.toan.dto.ExpenseDtos.*;
import com.group7.minihouse.shared.entity.OperatingExpense;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.toan.repository.OperatingExpenseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Chi phi van hanh khu tro (bao tri, ve sinh, thue, dien nuoc chung...). */
@Service
public class OperatingExpenseService {

    private final OperatingExpenseRepository repo;

    @PersistenceContext
    private EntityManager em;

    public OperatingExpenseService(OperatingExpenseRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public ExpenseResponse create(ExpenseRequest req) {
        validate(req);
        if (req.recordedByUserId() == null) {
            throw bad("Thieu recordedByUserId");
        }
        OperatingExpense e = new OperatingExpense();
        e.setProperty(em.getReference(Property.class, req.propertyId()));
        e.setRecordedBy(em.getReference(User.class, req.recordedByUserId()));
        e.setCreatedAt(LocalDateTime.now());
        apply(e, req);
        return toResponse(repo.save(e));
    }

    @Transactional
    public ExpenseResponse update(Integer id, ExpenseRequest req) {
        validate(req);
        OperatingExpense e = repo.findById(id).orElseThrow(() -> notFound("Chi phi " + id));
        apply(e, req);
        return toResponse(e);
    }

    @Transactional
    public void delete(Integer id) {
        if (!repo.existsById(id)) {
            throw notFound("Chi phi " + id);
        }
        repo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Integer id) {
        return toResponse(repo.findById(id).orElseThrow(() -> notFound("Chi phi " + id)));
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> search(Integer propertyId, String category, LocalDate from, LocalDate to) {
        return new DynamicQuery("select e from OperatingExpense e where 1 = 1")
                .always("e.property.propertyId = :pid", "pid", propertyId)
                .and("e.expenseCategory = :cat", "cat", category == null || category.isBlank() ? null : category.trim())
                .and("e.expenseDate >= :fromDate", "fromDate", from)
                .and("e.expenseDate <= :toDate", "toDate", to)
                .orderBy("e.expenseDate desc, e.expenseId desc")
                .list(em, OperatingExpense.class).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal total(Integer propertyId, LocalDate from, LocalDate to) {
        return repo.sumAmount(propertyId, from, to);
    }

    private void validate(ExpenseRequest r) {
        if (r.propertyId() == null || r.expenseCategory() == null || r.expenseCategory().isBlank()
                || r.amount() == null || r.expenseDate() == null) {
            throw bad("Can propertyId, expenseCategory, amount, expenseDate");
        }
        if (r.amount().signum() <= 0) {
            throw bad("So tien phai lon hon 0");
        }
    }

    private void apply(OperatingExpense e, ExpenseRequest r) {
        e.setExpenseCategory(r.expenseCategory().trim());
        e.setDescription(r.description());
        e.setAmount(r.amount());
        e.setExpenseDate(r.expenseDate());
        e.setReceiptUrl(r.receiptUrl());
    }

    private ExpenseResponse toResponse(OperatingExpense e) {
        return new ExpenseResponse(e.getExpenseId(), e.getProperty().getPropertyId(), e.getExpenseCategory(),
                e.getDescription(), e.getAmount(), e.getExpenseDate(), e.getReceiptUrl(),
                e.getRecordedBy().getUserId(), e.getCreatedAt());
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Khong tim thay " + what);
    }
}

package com.group7.minihouse.toan.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.group7.minihouse.toan.dto.ExpenseDtos.*;
import com.group7.minihouse.toan.service.OperatingExpenseService;

/** API chi phi van hanh khu tro. */
@RestController
@RequestMapping("/api/operating-expenses")
public class OperatingExpenseController {

    private final OperatingExpenseService service;

    public OperatingExpenseController(OperatingExpenseService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@RequestBody ExpenseRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(@PathVariable("id") Integer id, @RequestBody ExpenseRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Integer id) {
        service.delete(id);
    }

    @GetMapping("/{id}")
    public ExpenseResponse get(@PathVariable("id") Integer id) {
        return service.get(id);
    }

    @GetMapping
    public List<ExpenseResponse> search(
            @RequestParam("propertyId") Integer propertyId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.search(propertyId, category, from, to);
    }

    @GetMapping("/total")
    public BigDecimal total(
            @RequestParam("propertyId") Integer propertyId,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.total(propertyId, from, to);
    }
}

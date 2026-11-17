package com.group7.minihouse.chi.controller;

import com.group7.minihouse.chi.dto.BookingRequestDTO;
import com.group7.minihouse.chi.service.BookingRequestService;
import com.group7.minihouse.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chi/bookings")
public class BookingRequestController {

    private final BookingRequestService bookingRequestService;

    public BookingRequestController(BookingRequestService bookingRequestService) {
        this.bookingRequestService = bookingRequestService;
    }

    @GetMapping
    public ApiResponse<List<BookingRequestDTO.Response>> getAll(@RequestParam(required = false) String status) {
        return ApiResponse.ok(bookingRequestService.getAll(status));
    }

    @PostMapping
    public ApiResponse<BookingRequestDTO.Response> create(@RequestBody BookingRequestDTO.CreateRequest req) {
        return ApiResponse.ok("Tạo yêu cầu đặt phòng thành công", bookingRequestService.create(req));
    }

    @PatchMapping("/{id}/approve")
    public ApiResponse<BookingRequestDTO.Response> approve(@PathVariable Integer id) {
        return ApiResponse.ok("Đã duyệt yêu cầu đặt phòng", bookingRequestService.approve(id));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<BookingRequestDTO.Response> reject(@PathVariable Integer id,
                                                          @RequestBody(required = false) BookingRequestDTO.RejectRequest req) {
        String reason = (req != null) ? req.getRejectionReason() : null;
        return ApiResponse.ok("Đã từ chối yêu cầu đặt phòng", bookingRequestService.reject(id, reason));
    }

    @PatchMapping("/{id}/deposit")
    public ApiResponse<BookingRequestDTO.Response> updateDeposit(@PathVariable Integer id,
                                                                 @RequestBody BookingRequestDTO.DepositRequest req) {
        return ApiResponse.ok("Đã cập nhật tiền cọc", bookingRequestService.updateDeposit(id, req != null ? req.getDepositPaid() : null));
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<BookingRequestDTO.Response> cancel(@PathVariable Integer id) {
        return ApiResponse.ok("Đã hủy yêu cầu đặt phòng", bookingRequestService.cancel(id));
    }
}

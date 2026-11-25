package com.group7.minihouse.chi.controller;

import com.group7.minihouse.chi.service.BookingRequestService;
import com.group7.minihouse.shared.dto.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/booking")
public class GuestBookingController {
    public record GuestRequest(Integer roomId, String fullName, String phoneNumber,
                               LocalDate expectedStartDate, Integer occupantsCount) {}

    private final BookingRequestService service;

    public GuestBookingController(BookingRequestService service) {
        this.service = service;
    }

    @GetMapping("/rooms")
    public ApiResponse<List<BookingRequestService.AvailableRoom>> availableRooms() {
        return ApiResponse.ok(service.availableRooms());
    }

    @PostMapping
    public ApiResponse<Void> submit(@RequestBody GuestRequest request) {
        service.submitGuest(request.roomId(), request.fullName(), request.phoneNumber(),
                request.expectedStartDate(), request.occupantsCount());
        return ApiResponse.ok("Đã gửi yêu cầu thuê phòng", null);
    }
}

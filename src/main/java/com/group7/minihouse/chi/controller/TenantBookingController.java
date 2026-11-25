package com.group7.minihouse.chi.controller;

import com.group7.minihouse.chi.dto.BookingRequestDTO;
import com.group7.minihouse.chi.service.BookingRequestService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/bookings")
@PreAuthorize("hasRole('TENANT')")
public class TenantBookingController {
    public record Request(Integer roomId, LocalDate expectedStartDate, Integer occupantsCount) {}

    private final BookingRequestService service;

    public TenantBookingController(BookingRequestService service) {
        this.service = service;
    }

    @GetMapping("/available-rooms")
    public ApiResponse<List<BookingRequestService.AvailableRoom>> availableRooms(@AuthenticationPrincipal AuthUser user) {
        CurrentUser.requireTenantId(user);
        return ApiResponse.ok(service.availableRooms());
    }

    @PostMapping
    public ApiResponse<BookingRequestDTO.Response> request(@AuthenticationPrincipal AuthUser user, @RequestBody Request body) {
        return ApiResponse.ok(service.requestAsTenant(CurrentUser.requireTenantId(user), body.roomId(),
                body.expectedStartDate(), body.occupantsCount()));
    }
}

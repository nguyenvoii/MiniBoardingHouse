package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.BookingRequestDTO;
import com.group7.minihouse.chi.repository.BookingRequestRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.chi.repository.TenantRepository;
import com.group7.minihouse.shared.entity.BookingRequest;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class BookingRequestService {

    private final BookingRequestRepository bookingRequestRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;

    public BookingRequestService(BookingRequestRepository bookingRequestRepository,
                                 RoomRepository roomRepository,
                                 TenantRepository tenantRepository) {
        this.bookingRequestRepository = bookingRequestRepository;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public List<BookingRequestDTO.Response> getAll(String status) {
        List<BookingRequest> list = (status != null && !status.isBlank())
                ? bookingRequestRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase())
                : bookingRequestRepository.findAllByOrderByCreatedAtDesc();
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public BookingRequestDTO.Response create(BookingRequestDTO.CreateRequest req) {
        if (req.getRoomId() == null) throw new BusinessException("Chưa chọn phòng");
        if (req.getTenantId() == null) throw new BusinessException("Chưa chọn khách thuê");
        if (req.getExpectedStartDate() == null) throw new BusinessException("Chưa chọn ngày dự kiến dọn vào");

        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> BusinessException.notFound("Phòng"));
        Tenant tenant = tenantRepository.findById(req.getTenantId())
                .orElseThrow(() -> BusinessException.notFound("Khách thuê"));

        BookingRequest b = new BookingRequest();
        b.setRoom(room);
        b.setTenant(tenant);
        b.setExpectedStartDate(req.getExpectedStartDate());
        b.setOccupantsCount(req.getOccupantsCount() != null ? req.getOccupantsCount() : 1);
        b.setDepositPaid(req.getDepositPaid() != null ? req.getDepositPaid() : BigDecimal.ZERO);
        b.setStatus("PENDING");
        b.setCreatedAt(LocalDateTime.now());

        return toResponse(bookingRequestRepository.save(b));
    }

    public BookingRequestDTO.Response approve(Integer bookingId) {
        BookingRequest b = bookingRequestRepository.findById(bookingId)
                .orElseThrow(() -> BusinessException.notFound("Yêu cầu đặt phòng"));
        b.setStatus("APPROVED");
        return toResponse(bookingRequestRepository.save(b));
    }

    public BookingRequestDTO.Response reject(Integer bookingId, String reason) {
        BookingRequest b = bookingRequestRepository.findById(bookingId)
                .orElseThrow(() -> BusinessException.notFound("Yêu cầu đặt phòng"));
        b.setStatus("REJECTED");
        b.setRejectionReason(reason != null && !reason.isBlank() ? reason : "Không đủ điều kiện");
        return toResponse(bookingRequestRepository.save(b));
    }

    public BookingRequestDTO.Response updateDeposit(Integer bookingId, BigDecimal depositAmount) {
        BookingRequest b = bookingRequestRepository.findById(bookingId)
                .orElseThrow(() -> BusinessException.notFound("Yêu cầu đặt phòng"));
        if (depositAmount != null) {
            b.setDepositPaid(depositAmount);
        }
        return toResponse(bookingRequestRepository.save(b));
    }

    public BookingRequestDTO.Response cancel(Integer bookingId) {
        BookingRequest b = bookingRequestRepository.findById(bookingId)
                .orElseThrow(() -> BusinessException.notFound("Yêu cầu đặt phòng"));
        b.setStatus("CANCELLED");
        return toResponse(bookingRequestRepository.save(b));
    }

    private BookingRequestDTO.Response toResponse(BookingRequest b) {
        BookingRequestDTO.Response r = new BookingRequestDTO.Response();
        r.setBookingId(b.getBookingId());
        if (b.getRoom() != null) {
            r.setRoomId(b.getRoom().getRoomId());
            r.setRoomCode(b.getRoom().getRoomCode());
        }
        if (b.getTenant() != null) {
            r.setTenantId(b.getTenant().getTenantId());
            r.setTenantName(b.getTenant().getFullName());
            r.setTenantPhone(b.getTenant().getPhoneNumber());
        }
        r.setExpectedStartDate(b.getExpectedStartDate());
        r.setOccupantsCount(b.getOccupantsCount());
        r.setDepositPaid(b.getDepositPaid());
        r.setStatus(b.getStatus());
        r.setRejectionReason(b.getRejectionReason());
        r.setCreatedAt(b.getCreatedAt());
        return r;
    }
}

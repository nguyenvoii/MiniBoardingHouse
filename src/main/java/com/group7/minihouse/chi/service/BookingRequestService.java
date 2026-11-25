package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.BookingRequestDTO;
import com.group7.minihouse.chi.repository.BookingRequestRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.chi.repository.TenantRepository;
import com.group7.minihouse.shared.entity.BookingRequest;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
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

    public record AvailableRoom(Integer roomId, String roomCode, Integer maxOccupants) {}

    @Transactional(readOnly = true)
    public List<AvailableRoom> availableRooms() {
        return roomRepository.findByStatus("VACANT").stream()
                .map(room -> new AvailableRoom(room.getRoomId(), room.getRoomCode(), room.getMaxOccupants()))
                .toList();
    }

    public BookingRequestDTO.Response requestAsTenant(Integer tenantId, Integer roomId,
                                                       java.time.LocalDate startDate, Integer occupants) {
        if (roomId == null || startDate == null || occupants == null) {
            throw new BusinessException("Chọn phòng, ngày thuê và số người ở");
        }
        Room room = roomRepository.findById(roomId).orElseThrow(() -> BusinessException.notFound("Phòng"));
        if (!"VACANT".equals(room.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Phòng hiện không trống");
        }
        BookingRequestDTO.CreateRequest req = new BookingRequestDTO.CreateRequest();
        req.setTenantId(tenantId);
        req.setRoomId(roomId);
        req.setExpectedStartDate(startDate);
        req.setOccupantsCount(occupants);
        return create(req);
    }

    public BookingRequestDTO.Response create(BookingRequestDTO.CreateRequest req) {
        if (req.getRoomId() == null) throw new BusinessException("Chưa chọn phòng");
        if (req.getTenantId() == null) throw new BusinessException("Chưa chọn khách thuê");
        if (req.getExpectedStartDate() == null) throw new BusinessException("Chưa chọn ngày dự kiến dọn vào");

        if (req.getOccupantsCount() != null && req.getOccupantsCount() < 1) {
            throw new BusinessException("Số người ở phải từ 1 trở lên");
        }
        if (req.getDepositPaid() != null && req.getDepositPaid().signum() < 0) {
            throw new BusinessException("Tiền đặt cọc không được âm");
        }

        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> BusinessException.notFound("Phòng"));
        Tenant tenant = tenantRepository.findById(req.getTenantId())
                .orElseThrow(() -> BusinessException.notFound("Khách thuê"));
        if (req.getOccupantsCount() != null && room.getMaxOccupants() != null
                && req.getOccupantsCount() > room.getMaxOccupants()) {
            throw new BusinessException("Phòng chỉ cho tối đa " + room.getMaxOccupants() + " người ở");
        }

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

    // UC 37: Khách thuê tự gửi yêu cầu giữ chỗ trực tuyến
    public BookingRequestDTO.Response createPublicRequest(BookingRequestDTO.PublicCreateRequest req) {
        if (req.getRoomId() == null) throw new BusinessException("Vui lòng chọn phòng cần đặt");
        if (req.getFullName() == null || req.getFullName().isBlank()) throw new BusinessException("Vui lòng nhập họ và tên");
        if (req.getPhoneNumber() == null || req.getPhoneNumber().isBlank()) throw new BusinessException("Vui lòng nhập số điện thoại");
        if (req.getExpectedStartDate() == null) throw new BusinessException("Vui lòng chọn ngày dự kiến dọn vào ở");

        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> BusinessException.notFound("Phòng"));

        Tenant tenant = tenantRepository.findByPhoneNumber(req.getPhoneNumber().trim()).orElse(null);
        if (tenant == null && req.getIdentityNumber() != null && !req.getIdentityNumber().isBlank()) {
            tenant = tenantRepository.findByIdentityNumber(req.getIdentityNumber().trim()).orElse(null);
        }

        if (tenant == null) {
            tenant = new Tenant();
            tenant.setFullName(req.getFullName().trim());
            tenant.setPhoneNumber(req.getPhoneNumber().trim());
            tenant.setEmail(req.getEmail());
            tenant.setIdentityNumber(req.getIdentityNumber() != null && !req.getIdentityNumber().isBlank()
                    ? req.getIdentityNumber().trim()
                    : ("TEMP-" + System.currentTimeMillis()));
            tenant.setIsVerified(false);
            tenant.setCreatedAt(LocalDateTime.now());
            tenant = tenantRepository.save(tenant);
        }

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
        requireStatus(b, "duyệt", "PENDING");
        b.setStatus("APPROVED");
        return toResponse(bookingRequestRepository.save(b));
    }

    public BookingRequestDTO.Response reject(Integer bookingId, String reason) {
        BookingRequest b = bookingRequestRepository.findById(bookingId)
                .orElseThrow(() -> BusinessException.notFound("Yêu cầu đặt phòng"));
        requireStatus(b, "từ chối", "PENDING");
        b.setStatus("REJECTED");
        b.setRejectionReason(reason != null && !reason.isBlank() ? reason : "Không đủ điều kiện");
        return toResponse(bookingRequestRepository.save(b));
    }

    public BookingRequestDTO.Response updateDeposit(Integer bookingId, BigDecimal depositAmount) {
        BookingRequest b = bookingRequestRepository.findById(bookingId)
                .orElseThrow(() -> BusinessException.notFound("Yêu cầu đặt phòng"));
        requireStatus(b, "cập nhật tiền cọc", "PENDING", "APPROVED");
        if (depositAmount != null) {
            if (depositAmount.signum() < 0) {
                throw new BusinessException("Tiền đặt cọc không được âm");
            }
            b.setDepositPaid(depositAmount);
        }
        return toResponse(bookingRequestRepository.save(b));
    }

    public BookingRequestDTO.Response cancel(Integer bookingId) {
        BookingRequest b = bookingRequestRepository.findById(bookingId)
                .orElseThrow(() -> BusinessException.notFound("Yêu cầu đặt phòng"));
        requireStatus(b, "hủy", "PENDING", "APPROVED");
        b.setStatus("CANCELLED");
        return toResponse(bookingRequestRepository.save(b));
    }

    /** Chỉ cho phép chuyển trạng thái khi yêu cầu đang ở một trong các trạng thái hợp lệ. */
    private static void requireStatus(BookingRequest b, String action, String... allowed) {
        for (String a : allowed) {
            if (a.equals(b.getStatus())) {
                return;
            }
        }
        throw new BusinessException(HttpStatus.CONFLICT,
                "Không thể " + action + " yêu cầu đang ở trạng thái " + b.getStatus());
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

package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.RoomEquipmentRequestDTO;
import com.group7.minihouse.chi.repository.RoomEquipmentRequestRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.chi.repository.TenantRepository;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomEquipmentRequest;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoomEquipmentRequestService {

    private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "INSTALLED", "REMOVED");

    private final RoomEquipmentRequestRepository requestRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;

    public RoomEquipmentRequestService(RoomEquipmentRequestRepository requestRepository,
                                      RoomRepository roomRepository,
                                      TenantRepository tenantRepository) {
        this.requestRepository = requestRepository;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomEquipmentRequestDTO.Response> getAll(Integer roomId, String status) {
        List<RoomEquipmentRequest> list;
        if (roomId != null) {
            list = requestRepository.findByRoom_RoomIdOrderByCreatedAtDesc(roomId);
        } else if (status != null && !status.trim().isEmpty()) {
            list = requestRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase());
        } else {
            list = requestRepository.findAllByOrderByCreatedAtDesc();
        }
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoomEquipmentRequestDTO.Response getById(Integer id) {
        RoomEquipmentRequest req = requestRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Yêu cầu thiết bị #" + id));
        return toResponse(req);
    }

    @Transactional
    public RoomEquipmentRequestDTO.Response create(RoomEquipmentRequestDTO.CreateRequest req) {
        if (req.getRoomId() == null) throw new BusinessException("Vui lòng chọn phòng");
        if (req.getTenantId() == null) throw new BusinessException("Vui lòng chọn khách thuê");
        if (req.getEquipmentName() == null || req.getEquipmentName().trim().isEmpty()) {
            throw new BusinessException("Vui lòng nhập tên thiết bị");
        }
        if (req.getEquipmentName().trim().length() > 100) {
            throw new BusinessException("Tên thiết bị tối đa 100 ký tự");
        }
        if (req.getEstimatedMonthlyFee() != null && req.getEstimatedMonthlyFee().signum() < 0) {
            throw new BusinessException("Phí ước tính hàng tháng không được âm");
        }

        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> BusinessException.notFound("Phòng #" + req.getRoomId()));
        Tenant tenant = tenantRepository.findById(req.getTenantId())
                .orElseThrow(() -> BusinessException.notFound("Khách thuê #" + req.getTenantId()));

        RoomEquipmentRequest entity = new RoomEquipmentRequest();
        entity.setRoom(room);
        entity.setTenant(tenant);
        entity.setEquipmentName(req.getEquipmentName().trim());
        String type = (req.getRequestType() != null && req.getRequestType().equalsIgnoreCase("EXTRA_USAGE"))
                ? "EXTRA_USAGE" : "INSTALL";
        entity.setRequestType(type);
        entity.setEstimatedMonthlyFee(req.getEstimatedMonthlyFee() != null ? req.getEstimatedMonthlyFee() : BigDecimal.ZERO);
        entity.setStatus("PENDING");
        entity.setNote(req.getNote());
        entity.setCreatedAt(LocalDateTime.now());

        return toResponse(requestRepository.save(entity));
    }

    @Transactional
    public RoomEquipmentRequestDTO.Response approve(Integer id) {
        RoomEquipmentRequest entity = requestRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Yêu cầu thiết bị #" + id));
        requirePending(entity, "duyệt");
        entity.setStatus("APPROVED");
        return toResponse(requestRepository.save(entity));
    }

    @Transactional
    public RoomEquipmentRequestDTO.Response reject(Integer id, String reason) {
        RoomEquipmentRequest entity = requestRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Yêu cầu thiết bị #" + id));
        requirePending(entity, "từ chối");
        entity.setStatus("REJECTED");
        if (reason != null && !reason.trim().isEmpty()) {
            entity.setNote((entity.getNote() != null ? entity.getNote() + " | " : "") + "Từ chối: " + reason.trim());
        }
        return toResponse(requestRepository.save(entity));
    }

    @Transactional
    public RoomEquipmentRequestDTO.Response updateStatus(Integer id, String newStatus) {
        RoomEquipmentRequest entity = requestRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Yêu cầu thiết bị #" + id));
        if (newStatus == null || !STATUSES.contains(newStatus.trim().toUpperCase())) {
            throw new BusinessException("Trạng thái không hợp lệ (PENDING, APPROVED, REJECTED, INSTALLED, REMOVED)");
        }
        entity.setStatus(newStatus.trim().toUpperCase());
        return toResponse(requestRepository.save(entity));
    }

    private static void requirePending(RoomEquipmentRequest e, String action) {
        if (!"PENDING".equals(e.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Không thể " + action + " yêu cầu đang ở trạng thái " + e.getStatus());
        }
    }

    private RoomEquipmentRequestDTO.Response toResponse(RoomEquipmentRequest e) {
        RoomEquipmentRequestDTO.Response res = new RoomEquipmentRequestDTO.Response();
        res.setEquipmentRequestId(e.getEquipmentRequestId());
        if (e.getRoom() != null) {
            res.setRoomId(e.getRoom().getRoomId());
            res.setRoomCode(e.getRoom().getRoomCode());
        }
        if (e.getTenant() != null) {
            res.setTenantId(e.getTenant().getTenantId());
            res.setTenantName(e.getTenant().getFullName());
            res.setTenantPhone(e.getTenant().getPhoneNumber());
        }
        res.setEquipmentName(e.getEquipmentName());
        res.setRequestType(e.getRequestType());
        res.setEstimatedMonthlyFee(e.getEstimatedMonthlyFee());
        res.setStatus(e.getStatus());
        res.setNote(e.getNote());
        res.setCreatedAt(e.getCreatedAt());
        return res;
    }
}

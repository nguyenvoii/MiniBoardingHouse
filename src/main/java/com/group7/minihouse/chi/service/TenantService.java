package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.RoomResidentDTO;
import com.group7.minihouse.chi.dto.TenantDTO;
import com.group7.minihouse.chi.dto.TenantVehicleDTO;
import com.group7.minihouse.chi.repository.*;
import com.group7.minihouse.shared.entity.*;
import com.group7.minihouse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantIdCardRepository tenantIdCardRepository;
    private final TenantVehicleRepository tenantVehicleRepository;
    private final RoomResidentRepository roomResidentRepository;
    private final RoomRepository roomRepository;

    public TenantService(TenantRepository tenantRepository,
                         TenantIdCardRepository tenantIdCardRepository,
                         TenantVehicleRepository tenantVehicleRepository,
                         RoomResidentRepository roomResidentRepository,
                         RoomRepository roomRepository) {
        this.tenantRepository = tenantRepository;
        this.tenantIdCardRepository = tenantIdCardRepository;
        this.tenantVehicleRepository = tenantVehicleRepository;
        this.roomResidentRepository = roomResidentRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public List<TenantDTO> getAllTenants(String keyword) {
        List<Tenant> list;
        if (keyword != null && !keyword.isBlank()) {
            list = tenantRepository.findByFullNameContainingIgnoreCaseOrPhoneNumberContainingOrIdentityNumberContaining(
                    keyword.trim(), keyword.trim(), keyword.trim());
        } else {
            list = tenantRepository.findAll();
        }
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TenantDTO getById(Integer id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Khách thuê"));
        return toDTO(tenant);
    }

    public TenantDTO createTenant(TenantDTO.CreateRequest req) {
        if (req == null) {
            throw new BusinessException("Thiếu thông tin khách thuê");
        }
        if (req.getFullName() == null || req.getFullName().isBlank()) {
            throw new BusinessException("Họ tên không được để trống");
        }
        if (req.getPhoneNumber() == null || req.getPhoneNumber().isBlank()) {
            throw new BusinessException("Số điện thoại không được để trống");
        }
        if (req.getIdentityNumber() == null || req.getIdentityNumber().isBlank()) {
            throw new BusinessException("Số CCCD không được để trống");
        }

        if (req.getFullName().trim().length() > 100) {
            throw new BusinessException("Họ tên tối đa 100 ký tự");
        }
        if (req.getPhoneNumber().trim().length() > 20 || req.getIdentityNumber().trim().length() > 20) {
            throw new BusinessException("Số điện thoại và số CCCD tối đa 20 ký tự");
        }
        if (tenantRepository.findByPhoneNumber(req.getPhoneNumber().trim()).isPresent()) {
            throw new BusinessException("Số điện thoại này đã tồn tại trong hệ thống");
        }
        if (tenantRepository.findByIdentityNumber(req.getIdentityNumber().trim()).isPresent()) {
            throw new BusinessException("Số CCCD này đã tồn tại trong hệ thống");
        }

        Tenant t = new Tenant();
        t.setFullName(req.getFullName().trim());
        t.setPhoneNumber(req.getPhoneNumber().trim());
        t.setEmail(req.getEmail() == null || req.getEmail().isBlank() ? null : req.getEmail().trim());
        t.setDob(req.getDob());
        t.setIdentityNumber(req.getIdentityNumber().trim());
        t.setPermanentAddress(req.getPermanentAddress());
        t.setOccupation(req.getOccupation());
        t.setEmergencyContactName(req.getEmergencyContactName());
        t.setEmergencyContactPhone(req.getEmergencyContactPhone());
        t.setIsVerified(false);
        t.setCreatedAt(LocalDateTime.now());

        return toDTO(tenantRepository.save(t));
    }

    public void verifyTenant(Integer id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Khách thuê"));
        tenant.setIsVerified(true);
        tenantRepository.save(tenant);
    }

    public void saveIdCard(Integer tenantId, String frontUrl, String backUrl) {
        if (frontUrl == null || frontUrl.isBlank() || backUrl == null || backUrl.isBlank()) {
            throw new BusinessException("Cần đủ đường dẫn ảnh CCCD mặt trước (frontUrl) và mặt sau (backUrl)");
        }
        if (frontUrl.length() > 500 || backUrl.length() > 500) {
            throw new BusinessException("Đường dẫn ảnh tối đa 500 ký tự");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Khách thuê"));

        TenantIdCard card = tenantIdCardRepository.findByTenantTenantId(tenantId)
                .orElse(new TenantIdCard());

        card.setTenant(tenant);
        card.setFrontImageUrl(frontUrl);
        card.setBackImageUrl(backUrl);
        card.setUploadedAt(LocalDateTime.now());
        tenantIdCardRepository.save(card);
    }

    // --- Quản lý Xe ---
    @Transactional(readOnly = true)
    public List<TenantVehicleDTO> getVehiclesByTenant(Integer tenantId) {
        return tenantVehicleRepository.findByTenantTenantId(tenantId).stream()
                .map(v -> {
                    TenantVehicleDTO dto = new TenantVehicleDTO();
                    dto.setVehicleId(v.getVehicleId());
                    dto.setTenantId(v.getTenant().getTenantId());
                    dto.setTenantName(v.getTenant().getFullName());
                    dto.setLicensePlate(v.getLicensePlate());
                    dto.setVehicleType(v.getVehicleType());
                    dto.setBrand(v.getBrand());
                    dto.setColor(v.getColor());
                    dto.setIsActive(v.getIsActive());
                    return dto;
                }).collect(Collectors.toList());
    }

    /** Loại xe hợp lệ theo CHECK của bảng tenant_vehicles; nhận thêm vài tên tiếng Việt quen dùng. */
    static String normalizeVehicleType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "MOTORBIKE";
        }
        String s = raw.trim().toUpperCase();
        switch (s) {
            case "MOTORBIKE": case "BICYCLE": case "ELECTRIC_BIKE": case "CAR": case "OTHER":
                return s;
            case "XE MÁY": case "XE MAY":
                return "MOTORBIKE";
            case "XE ĐẠP": case "XE DAP":
                return "BICYCLE";
            case "XE ĐIỆN": case "XE DIEN": case "XE ĐẠP ĐIỆN": case "XE DAP DIEN":
                return "ELECTRIC_BIKE";
            case "Ô TÔ": case "O TO":
                return "CAR";
            default:
                throw new BusinessException("Loại xe không hợp lệ (MOTORBIKE, BICYCLE, ELECTRIC_BIKE, CAR, OTHER)");
        }
    }

    public TenantVehicleDTO addVehicle(TenantVehicleDTO.CreateRequest req) {
        if (req == null || req.getTenantId() == null) {
            throw new BusinessException("Chưa chọn khách thuê");
        }
        if (req.getLicensePlate() == null || req.getLicensePlate().isBlank()) {
            throw new BusinessException("Biển số xe không được để trống");
        }
        String plate = req.getLicensePlate().trim().toUpperCase();
        if (plate.length() > 20) {
            throw new BusinessException("Biển số xe tối đa 20 ký tự");
        }
        String type = normalizeVehicleType(req.getVehicleType());
        Tenant tenant = tenantRepository.findById(req.getTenantId())
                .orElseThrow(() -> BusinessException.notFound("Khách thuê"));
        if (tenantVehicleRepository.existsByLicensePlateIgnoreCase(plate)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Biển số " + plate + " đã được đăng ký");
        }

        TenantVehicle v = new TenantVehicle();
        v.setTenant(tenant);
        v.setLicensePlate(plate);
        v.setVehicleType(type);
        v.setBrand(req.getBrand());
        v.setColor(req.getColor());
        v.setIsActive(true);
        v.setCreatedAt(LocalDateTime.now());

        TenantVehicle saved = tenantVehicleRepository.save(v);
        TenantVehicleDTO dto = new TenantVehicleDTO();
        dto.setVehicleId(saved.getVehicleId());
        dto.setTenantId(tenant.getTenantId());
        dto.setTenantName(tenant.getFullName());
        dto.setLicensePlate(saved.getLicensePlate());
        dto.setVehicleType(saved.getVehicleType());
        dto.setBrand(saved.getBrand());
        dto.setColor(saved.getColor());
        dto.setIsActive(saved.getIsActive());
        return dto;
    }

    // --- Cư dân ở cùng phòng ---
    @Transactional(readOnly = true)
    public List<RoomResidentDTO> getResidentsByRoom(Integer roomId) {
        return roomResidentRepository.findByRoomRoomId(roomId).stream()
                .map(r -> {
                    RoomResidentDTO dto = new RoomResidentDTO();
                    dto.setResidentId(r.getResidentId());
                    dto.setRoomId(r.getRoom().getRoomId());
                    dto.setRoomCode(r.getRoom().getRoomCode());
                    if (r.getTenant() != null) dto.setTenantId(r.getTenant().getTenantId());
                    dto.setFullName(r.getFullName());
                    dto.setPhoneNumber(r.getPhoneNumber());
                    dto.setIdentityNumber(r.getIdentityNumber());
                    dto.setRelationshipWithPrimary(r.getRelationshipWithPrimary());
                    dto.setStartDate(r.getStartDate());
                    dto.setEndDate(r.getEndDate());
                    dto.setIsActive(r.getIsActive());
                    return dto;
                }).collect(Collectors.toList());
    }

    public RoomResidentDTO addResident(RoomResidentDTO.CreateRequest req) {
        if (req == null || req.getRoomId() == null) {
            throw new BusinessException("Chưa chọn phòng");
        }
        if (req.getFullName() == null || req.getFullName().isBlank()) {
            throw new BusinessException("Họ tên người ở cùng không được để trống");
        }
        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> BusinessException.notFound("Phòng"));

        Tenant tenant = null;
        if (req.getTenantId() != null) {
            tenant = tenantRepository.findById(req.getTenantId())
                    .orElseThrow(() -> BusinessException.notFound("Khách thuê"));
        }

        RoomResident resident = new RoomResident();
        resident.setRoom(room);
        resident.setTenant(tenant);
        resident.setFullName(req.getFullName().trim());
        resident.setPhoneNumber(req.getPhoneNumber());
        resident.setIdentityNumber(req.getIdentityNumber());
        resident.setRelationshipWithPrimary(req.getRelationshipWithPrimary() != null ? req.getRelationshipWithPrimary() : "Người ở cùng");
        resident.setStartDate(req.getStartDate() != null ? req.getStartDate() : java.time.LocalDate.now());
        resident.setIsActive(true);

        RoomResident saved = roomResidentRepository.save(resident);
        RoomResidentDTO dto = new RoomResidentDTO();
        dto.setResidentId(saved.getResidentId());
        dto.setRoomId(room.getRoomId());
        dto.setRoomCode(room.getRoomCode());
        if (tenant != null) dto.setTenantId(tenant.getTenantId());
        dto.setFullName(saved.getFullName());
        dto.setPhoneNumber(saved.getPhoneNumber());
        dto.setIdentityNumber(saved.getIdentityNumber());
        dto.setRelationshipWithPrimary(saved.getRelationshipWithPrimary());
        dto.setStartDate(saved.getStartDate());
        dto.setIsActive(saved.getIsActive());
        return dto;
    }

    public void removeResident(Integer residentId) {
        RoomResident r = roomResidentRepository.findById(residentId)
                .orElseThrow(() -> BusinessException.notFound("Cư dân"));
        r.setIsActive(false);
        r.setEndDate(java.time.LocalDate.now());
        roomResidentRepository.save(r);
    }

    private TenantDTO toDTO(Tenant t) {
        TenantDTO dto = new TenantDTO();
        dto.setTenantId(t.getTenantId());
        dto.setFullName(t.getFullName());
        dto.setPhoneNumber(t.getPhoneNumber());
        dto.setEmail(t.getEmail());
        dto.setDob(t.getDob());
        dto.setIdentityNumber(t.getIdentityNumber());
        dto.setPermanentAddress(t.getPermanentAddress());
        dto.setOccupation(t.getOccupation());
        dto.setEmergencyContactName(t.getEmergencyContactName());
        dto.setEmergencyContactPhone(t.getEmergencyContactPhone());
        dto.setIsVerified(Boolean.TRUE.equals(t.getIsVerified()));
        dto.setCreatedAt(t.getCreatedAt());

        tenantIdCardRepository.findByTenantTenantId(t.getTenantId()).ifPresent(card -> {
            dto.setFrontImageUrl(card.getFrontImageUrl());
            dto.setBackImageUrl(card.getBackImageUrl());
        });
        return dto;
    }
}

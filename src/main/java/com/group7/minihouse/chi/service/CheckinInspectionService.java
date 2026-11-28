package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.CheckinInspectionDTO;
import com.group7.minihouse.chi.entity.CheckinInspection;
import com.group7.minihouse.chi.repository.CheckinInspectionRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CheckinInspectionService {

    private final CheckinInspectionRepository repository;
    private final RoomRepository roomRepository;
    private final EntityManager entityManager;

    public CheckinInspectionService(CheckinInspectionRepository repository,
                                    RoomRepository roomRepository,
                                    EntityManager entityManager) {
        this.repository = repository;
        this.roomRepository = roomRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<CheckinInspectionDTO> getAll(Integer roomId) {
        List<CheckinInspection> list;
        if (roomId != null) {
            list = repository.findByRoom_RoomIdOrderByCheckinDateDesc(roomId);
        } else {
            list = repository.findAllByOrderByCheckinDateDesc();
        }
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CheckinInspectionDTO getById(Integer id) {
        CheckinInspection inspection = repository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Biên bản nhận phòng #" + id));
        return toDTO(inspection);
    }

    @Transactional
    public CheckinInspectionDTO create(CheckinInspectionDTO.CreateRequest req) {
        if (req.getRoomId() == null) {
            throw new BusinessException("Mã phòng không được để trống");
        }
        if (req.getTenantId() == null) {
            throw new BusinessException("Mã khách thuê không được để trống");
        }
        if (req.getInitialElectricMeter() == null || req.getInitialElectricMeter() < 0) {
            throw new BusinessException("Chỉ số điện đầu bàn giao không hợp lệ");
        }
        if (req.getInitialWaterMeter() == null || req.getInitialWaterMeter() < 0) {
            throw new BusinessException("Chỉ số nước đầu bàn giao không hợp lệ");
        }

        if (req.getKeyHandedCount() != null && req.getKeyHandedCount() < 0) {
            throw new BusinessException("Số chìa khóa bàn giao không được âm");
        }

        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> BusinessException.notFound("Phòng ID " + req.getRoomId()));

        Tenant tenant = entityManager.find(Tenant.class, req.getTenantId());
        if (tenant == null) {
            throw BusinessException.notFound("Khách thuê ID " + req.getTenantId());
        }

        RentalContract contract = null;
        if (req.getContractId() != null) {
            contract = entityManager.find(RentalContract.class, req.getContractId());
            if (contract == null) {
                throw BusinessException.notFound("Hợp đồng ID " + req.getContractId());
            }
            if (!contract.getRoom().getRoomId().equals(room.getRoomId())
                    || !contract.getTenant().getTenantId().equals(tenant.getTenantId())) {
                throw new BusinessException("Hợp đồng " + contract.getContractCode()
                        + " không thuộc phòng và khách thuê đã chọn");
            }
            if (repository.existsByContract_ContractId(contract.getContractId())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Hợp đồng " + contract.getContractCode() + " đã có biên bản nhận phòng");
            }
        }

        CheckinInspection inspection = new CheckinInspection();
        inspection.setRoom(room);
        inspection.setTenant(tenant);
        inspection.setContract(contract);
        inspection.setCheckinDate(req.getCheckinDate() != null ? req.getCheckinDate() : LocalDate.now());
        inspection.setInitialElectricMeter(req.getInitialElectricMeter());
        inspection.setInitialWaterMeter(req.getInitialWaterMeter());
        inspection.setKeyHandedCount(req.getKeyHandedCount() != null ? req.getKeyHandedCount() : 1);
        inspection.setFurnishingsCondition(req.getFurnishingsCondition());
        inspection.setInspectionPhotoUrl(req.getInspectionPhotoUrl());
        inspection.setNotes(req.getNotes());
        inspection.setTenantConfirmed(req.getTenantConfirmed() != null ? req.getTenantConfirmed() : false); // L10: khong mac dinh "khách đã đồng ý"
        inspection.setInspectedByName(req.getInspectedByName() != null && !req.getInspectedByName().isBlank()
                ? req.getInspectedByName() : "Chủ trọ / Quản lý");
        inspection.setCreatedAt(LocalDateTime.now());

        // Đồng bộ chỉ số ban đầu của phòng, nhưng chỉ khi phòng chưa từng ghi chỉ số: đổi chỉ số đầu của phòng
        // đang vận hành sẽ làm lệch mức tiêu thụ của các kỳ đã tính tiền.
        boolean hasReadings = entityManager.createQuery(
                "select count(m) from MeterReading m where m.room.roomId = :r", Long.class)
                .setParameter("r", room.getRoomId()).getSingleResult() > 0;
        if (!hasReadings) {
            room.setInitialElectricMeter(req.getInitialElectricMeter());
            room.setInitialWaterMeter(req.getInitialWaterMeter());
            roomRepository.save(room);
        }

        CheckinInspection saved = repository.save(inspection);
        return toDTO(saved);
    }

    @Transactional
    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new BusinessException("Không tìm thấy biên bản nhận phòng #" + id);
        }
        repository.deleteById(id);
    }

    private CheckinInspectionDTO toDTO(CheckinInspection e) {
        CheckinInspectionDTO dto = new CheckinInspectionDTO();
        dto.setInspectionId(e.getInspectionId());
        if (e.getRoom() != null) {
            dto.setRoomId(e.getRoom().getRoomId());
            dto.setRoomCode(e.getRoom().getRoomCode());
        }
        if (e.getTenant() != null) {
            dto.setTenantId(e.getTenant().getTenantId());
            dto.setTenantName(e.getTenant().getFullName());
            dto.setTenantPhone(e.getTenant().getPhoneNumber());
        }
        if (e.getContract() != null) {
            dto.setContractId(e.getContract().getContractId());
            dto.setContractCode(e.getContract().getContractCode());
        }
        dto.setCheckinDate(e.getCheckinDate());
        dto.setInitialElectricMeter(e.getInitialElectricMeter());
        dto.setInitialWaterMeter(e.getInitialWaterMeter());
        dto.setKeyHandedCount(e.getKeyHandedCount());
        dto.setFurnishingsCondition(e.getFurnishingsCondition());
        dto.setInspectionPhotoUrl(e.getInspectionPhotoUrl());
        dto.setNotes(e.getNotes());
        dto.setTenantConfirmed(e.getTenantConfirmed());
        dto.setInspectedByName(e.getInspectedByName());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }
}

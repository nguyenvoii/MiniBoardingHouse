package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.RoomDTO;
import com.group7.minihouse.chi.repository.PropertyFloorRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.entity.PropertyFloor;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private static final Set<String> STATUSES = Set.of("VACANT", "RENTED", "REPAIRING", "SUSPENDED");

    private final RoomRepository roomRepository;
    private final PropertyFloorRepository floorRepository;
    private final EntityManager em;

    public RoomService(RoomRepository roomRepository, PropertyFloorRepository floorRepository, EntityManager em) {
        this.roomRepository = roomRepository;
        this.floorRepository = floorRepository;
        this.em = em;
    }

    public List<RoomDTO> getAllRooms() {
        return roomRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public RoomDTO getRoomById(Integer id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Phòng có ID " + id));
        return toDTO(room);
    }

    public List<RoomDTO> getRoomsByFloor(Integer floorId) {
        return roomRepository.findByFloor_FloorId(floorId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoomDTO createRoom(RoomDTO dto) {
        if (dto == null || dto.getFloorId() == null) {
            throw new BusinessException("Chưa chọn tầng");
        }
        if (dto.getRoomCode() == null || dto.getRoomCode().isBlank()) {
            throw new BusinessException("Mã phòng không được để trống");
        }
        String code = dto.getRoomCode().trim();
        if (code.length() > 50) {
            throw new BusinessException("Mã phòng tối đa 50 ký tự");
        }
        if (dto.getAreaSqm() == null || dto.getAreaSqm().signum() <= 0) {
            throw new BusinessException("Diện tích phải lớn hơn 0");
        }
        if (dto.getMaxOccupants() == null || dto.getMaxOccupants() < 1) {
            throw new BusinessException("Số người tối đa phải từ 1 trở lên");
        }
        if (dto.getBaseRentPrice() == null || dto.getBaseRentPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Giá thuê không hợp lệ");
        }
        if (dto.getDepositAmount() == null || dto.getDepositAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Tiền cọc không hợp lệ");
        }
        if ((dto.getInitialElectricMeter() != null && dto.getInitialElectricMeter() < 0)
                || (dto.getInitialWaterMeter() != null && dto.getInitialWaterMeter() < 0)) {
            throw new BusinessException("Chỉ số điện nước ban đầu không được âm");
        }
        String status = dto.getStatus() != null ? dto.getStatus().trim().toUpperCase() : "VACANT";
        if (!STATUSES.contains(status)) {
            throw new BusinessException("Trạng thái phòng không hợp lệ (VACANT, RENTED, REPAIRING, SUSPENDED)");
        }
        if ("RENTED".equals(status)) {
            throw new BusinessException("Phòng mới chưa có hợp đồng nên không thể ở trạng thái RENTED");
        }

        PropertyFloor floor = floorRepository.findById(dto.getFloorId())
                .orElseThrow(() -> BusinessException.notFound("Tầng"));
        Property property = floor.getProperty();

        if (roomRepository.existsByProperty_PropertyIdAndRoomCode(property.getPropertyId(), code)) {
            throw new BusinessException("Mã phòng " + code + " đã tồn tại trong nhà này");
        }

        Room room = new Room();
        room.setProperty(property);
        room.setFloor(floor);
        room.setRoomCode(code);
        room.setAreaSqm(dto.getAreaSqm());
        room.setMaxOccupants(dto.getMaxOccupants());
        room.setBaseRentPrice(dto.getBaseRentPrice());
        room.setDepositAmount(dto.getDepositAmount());
        room.setInitialElectricMeter(dto.getInitialElectricMeter() != null ? dto.getInitialElectricMeter() : 0);
        room.setInitialWaterMeter(dto.getInitialWaterMeter() != null ? dto.getInitialWaterMeter() : 0);
        room.setStatus(status);
        room.setCreatedAt(LocalDateTime.now());

        return toDTO(roomRepository.save(room));
    }

    /**
     * Đổi trạng thái phòng. RENTED chỉ do hợp đồng quyết định: phòng đang có hợp đồng hiệu lực thì phải giữ RENTED
     * (muốn đổi thì chấm dứt hợp đồng), phòng không có hợp đồng thì không thể gán RENTED.
     */
    @Transactional
    public RoomDTO updateRoomStatus(Integer roomId, String newStatus) {
        if (newStatus == null || !STATUSES.contains(newStatus.trim().toUpperCase())) {
            throw new BusinessException("Trạng thái phòng không hợp lệ (VACANT, RENTED, REPAIRING, SUSPENDED)");
        }
        String status = newStatus.trim().toUpperCase();
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> BusinessException.notFound("Phòng"));
        boolean hasActiveContract = em.createQuery("select count(c) from RentalContract c where c.room.roomId = :r "
                        + "and c.status in ('ACTIVE', 'EXPIRING_SOON')", Long.class)
                .setParameter("r", roomId).getSingleResult() > 0;
        if (hasActiveContract && !"RENTED".equals(status)) {
            throw new BusinessException("Phòng đang có hợp đồng hiệu lực, hãy chấm dứt hợp đồng trước khi đổi trạng thái");
        }
        if (!hasActiveContract && "RENTED".equals(status)) {
            throw new BusinessException("Phòng chưa có hợp đồng hiệu lực nên không thể đặt RENTED");
        }
        room.setStatus(status);
        return toDTO(roomRepository.save(room));
    }

    private RoomDTO toDTO(Room r) {
        RoomDTO dto = new RoomDTO();
        dto.setRoomId(r.getRoomId());
        dto.setPropertyId(r.getProperty() != null ? r.getProperty().getPropertyId() : null);
        if (r.getFloor() != null) {
            dto.setFloorId(r.getFloor().getFloorId());
            dto.setFloorName(r.getFloor().getFloorName());
        }
        dto.setRoomCode(r.getRoomCode());
        dto.setAreaSqm(r.getAreaSqm());
        dto.setMaxOccupants(r.getMaxOccupants());
        dto.setBaseRentPrice(r.getBaseRentPrice());
        dto.setDepositAmount(r.getDepositAmount());
        dto.setInitialElectricMeter(r.getInitialElectricMeter());
        dto.setInitialWaterMeter(r.getInitialWaterMeter());
        dto.setStatus(r.getStatus());
        return dto;
    }
}

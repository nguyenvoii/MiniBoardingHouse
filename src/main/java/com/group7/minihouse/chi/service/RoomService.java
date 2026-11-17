package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.RoomDTO;
import com.group7.minihouse.chi.repository.PropertyFloorRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.entity.PropertyFloor;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final PropertyFloorRepository floorRepository;

    public RoomService(RoomRepository roomRepository, PropertyFloorRepository floorRepository) {
        this.roomRepository = roomRepository;
        this.floorRepository = floorRepository;
    }

    public List<RoomDTO> getAllRooms() {
        return roomRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public RoomDTO getRoomById(Integer id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng có ID: " + id));
        return toDTO(room);
    }

    public List<RoomDTO> getRoomsByFloor(Integer floorId) {
        return roomRepository.findByFloor_FloorId(floorId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoomDTO createRoom(RoomDTO dto) {
        PropertyFloor floor = floorRepository.findById(dto.getFloorId())
                .orElseThrow(() -> new BusinessException("Tầng không tồn tại"));

        Property property = floor.getProperty();

        if (roomRepository.existsByProperty_PropertyIdAndRoomCode(property.getPropertyId(), dto.getRoomCode())) {
            throw new BusinessException("Mã phòng " + dto.getRoomCode() + " đã tồn tại trong nhà này");
        }

        Room room = new Room();
        room.setProperty(property);
        room.setFloor(floor);
        room.setRoomCode(dto.getRoomCode());
        room.setAreaSqm(dto.getAreaSqm());
        room.setMaxOccupants(dto.getMaxOccupants());
        room.setBaseRentPrice(dto.getBaseRentPrice());
        room.setDepositAmount(dto.getDepositAmount());
        room.setInitialElectricMeter(dto.getInitialElectricMeter() != null ? dto.getInitialElectricMeter() : 0);
        room.setInitialWaterMeter(dto.getInitialWaterMeter() != null ? dto.getInitialWaterMeter() : 0);
        room.setStatus(dto.getStatus() != null ? dto.getStatus() : "VACANT");
        room.setCreatedAt(LocalDateTime.now());

        return toDTO(roomRepository.save(room));
    }

    @Transactional
    public RoomDTO updateRoomStatus(Integer roomId, String newStatus) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng"));
        room.setStatus(newStatus);
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

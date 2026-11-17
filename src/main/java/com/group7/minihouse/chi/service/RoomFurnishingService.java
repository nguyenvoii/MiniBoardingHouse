package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.RoomFurnishingDTO;
import com.group7.minihouse.chi.repository.RoomFurnishingRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomFurnishing;
import com.group7.minihouse.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RoomFurnishingService {

    private final RoomFurnishingRepository furnishingRepository;
    private final RoomRepository roomRepository;

    public RoomFurnishingService(RoomFurnishingRepository furnishingRepository, RoomRepository roomRepository) {
        this.furnishingRepository = furnishingRepository;
        this.roomRepository = roomRepository;
    }

    public List<RoomFurnishingDTO> getFurnishingsByRoom(Integer roomId) {
        return furnishingRepository.findByRoom_RoomId(roomId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<RoomFurnishingDTO> getAllFurnishings() {
        return furnishingRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoomFurnishingDTO addFurnishing(RoomFurnishingDTO dto) {
        Room room = roomRepository.findById(dto.getRoomId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng có ID: " + dto.getRoomId()));

        RoomFurnishing f = new RoomFurnishing();
        f.setRoom(room);
        f.setItemName(dto.getItemName());
        f.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 1);
        f.setConditionStatus(dto.getConditionStatus() != null ? dto.getConditionStatus() : "Mới / Hoạt động tốt");
        f.setNotes(dto.getNotes());

        return toDTO(furnishingRepository.save(f));
    }

    @Transactional
    public RoomFurnishingDTO updateFurnishing(Integer id, RoomFurnishingDTO dto) {
        RoomFurnishing f = furnishingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thiết bị có ID: " + id));

        f.setItemName(dto.getItemName());
        f.setQuantity(dto.getQuantity());
        f.setConditionStatus(dto.getConditionStatus());
        f.setNotes(dto.getNotes());

        return toDTO(furnishingRepository.save(f));
    }

    @Transactional
    public void deleteFurnishing(Integer id) {
        if (!furnishingRepository.existsById(id)) {
            throw new BusinessException("Không tìm thấy thiết bị có ID: " + id);
        }
        furnishingRepository.deleteById(id);
    }

    private RoomFurnishingDTO toDTO(RoomFurnishing f) {
        RoomFurnishingDTO dto = new RoomFurnishingDTO();
        dto.setFurnishingId(f.getFurnishingId());
        if (f.getRoom() != null) {
            dto.setRoomId(f.getRoom().getRoomId());
            dto.setRoomCode(f.getRoom().getRoomCode());
        }
        dto.setItemName(f.getItemName());
        dto.setQuantity(f.getQuantity());
        dto.setConditionStatus(f.getConditionStatus());
        dto.setNotes(f.getNotes());
        return dto;
    }
}


package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.RoomFurnishingDTO;
import com.group7.minihouse.chi.repository.RoomFurnishingRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomFurnishing;
import com.group7.minihouse.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    // UC 24: Danh mục thiết bị, nội thất chuẩn
    public List<RoomFurnishingDTO> getStandardCatalog() {
        List<RoomFurnishingDTO> catalog = new ArrayList<>();

        RoomFurnishingDTO item1 = new RoomFurnishingDTO();
        item1.setItemName("Máy lạnh Inverter 1.5 HP");
        item1.setQuantity(1);
        item1.setConditionStatus("Mới / Hoạt động tốt");
        item1.setNotes("Kèm remote điều khiển chính hãng");
        catalog.add(item1);

        RoomFurnishingDTO item2 = new RoomFurnishingDTO();
        item2.setItemName("Giường ngủ gỗ tự nhiên 1m6 x 2m");
        item2.setQuantity(1);
        item2.setConditionStatus("Mới / Tốt");
        item2.setNotes("Khung giường chắc chắn, vạt phản phẳng");
        catalog.add(item2);

        RoomFurnishingDTO item3 = new RoomFurnishingDTO();
        item3.setItemName("Tủ quần áo gỗ 2 cánh");
        item3.setQuantity(1);
        item3.setConditionStatus("Mới / Tốt");
        item3.setNotes("Có ngăn kéo và chìa khóa tủ");
        catalog.add(item3);

        RoomFurnishingDTO item4 = new RoomFurnishingDTO();
        item4.setItemName("Bình nóng lạnh Ariston 20L");
        item4.setQuantity(1);
        item4.setConditionStatus("Mới / Hoạt động tốt");
        item4.setNotes("Có chống giật ELCB an toàn");
        catalog.add(item4);

        RoomFurnishingDTO item5 = new RoomFurnishingDTO();
        item5.setItemName("Bàn ghế làm việc / học tập");
        item5.setQuantity(1);
        item5.setConditionStatus("Mới / Tốt");
        item5.setNotes("Bàn gỗ chân sắt + ghế xoay êm");
        catalog.add(item5);

        RoomFurnishingDTO item6 = new RoomFurnishingDTO();
        item6.setItemName("Tủ lạnh mini 90L");
        item6.setQuantity(1);
        item6.setConditionStatus("Mới / Hoạt động tốt");
        item6.setNotes("Tiết kiệm điện năng");
        catalog.add(item6);

        return catalog;
    }

    // UC 24: Áp dụng bộ nội thất chuẩn cho phòng
    @Transactional
    public List<RoomFurnishingDTO> applyStandardFurnishings(Integer roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng có ID: " + roomId));

        List<RoomFurnishing> existing = furnishingRepository.findByRoom_RoomId(roomId);
        List<String> existingNames = existing.stream().map(f -> f.getItemName().toLowerCase().trim()).collect(Collectors.toList());

        List<RoomFurnishingDTO> standard = getStandardCatalog();
        List<RoomFurnishingDTO> added = new ArrayList<>();

        for (RoomFurnishingDTO std : standard) {
            if (!existingNames.contains(std.getItemName().toLowerCase().trim())) {
                RoomFurnishing f = new RoomFurnishing();
                f.setRoom(room);
                f.setItemName(std.getItemName());
                f.setQuantity(std.getQuantity());
                f.setConditionStatus(std.getConditionStatus());
                f.setNotes(std.getNotes());
                added.add(toDTO(furnishingRepository.save(f)));
            }
        }

        return added;
    }

    @Transactional
    public RoomFurnishingDTO addFurnishing(RoomFurnishingDTO dto) {
        if (dto == null || dto.getRoomId() == null) {
            throw new BusinessException("Chưa chọn phòng");
        }
        if (dto.getItemName() == null || dto.getItemName().isBlank()) {
            throw new BusinessException("Tên thiết bị không được để trống");
        }
        if (dto.getQuantity() != null && dto.getQuantity() < 1) {
            throw new BusinessException("Số lượng phải từ 1 trở lên");
        }
        Room room = roomRepository.findById(dto.getRoomId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng có ID: " + dto.getRoomId()));

        RoomFurnishing f = new RoomFurnishing();
        f.setRoom(room);
        f.setItemName(dto.getItemName().trim());
        f.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 1);
        f.setConditionStatus(dto.getConditionStatus() != null ? dto.getConditionStatus() : "Mới / Hoạt động tốt");
        f.setNotes(dto.getNotes());

        return toDTO(furnishingRepository.save(f));
    }

    @Transactional
    public RoomFurnishingDTO updateFurnishing(Integer id, RoomFurnishingDTO dto) {
        RoomFurnishing f = furnishingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thiết bị có ID: " + id));

        // Chỉ ghi đè các trường có gửi lên: gửi thiếu trường không được làm mất dữ liệu hoặc vỡ ràng buộc NOT NULL
        if (dto.getItemName() != null) {
            if (dto.getItemName().isBlank()) {
                throw new BusinessException("Tên thiết bị không được để trống");
            }
            f.setItemName(dto.getItemName().trim());
        }
        if (dto.getQuantity() != null) {
            if (dto.getQuantity() < 1) {
                throw new BusinessException("Số lượng phải từ 1 trở lên");
            }
            f.setQuantity(dto.getQuantity());
        }
        if (dto.getConditionStatus() != null && !dto.getConditionStatus().isBlank()) {
            f.setConditionStatus(dto.getConditionStatus());
        }
        if (dto.getNotes() != null) {
            f.setNotes(dto.getNotes());
        }

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

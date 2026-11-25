package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.RoomFurnishingDTO;
import com.group7.minihouse.chi.repository.RoomFurnishingRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.StandardFurnishing;
import com.group7.minihouse.chi.repository.StandardFurnishingRepository;
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
    private final StandardFurnishingRepository catalogRepository;

    public RoomFurnishingService(RoomFurnishingRepository furnishingRepository, RoomRepository roomRepository, StandardFurnishingRepository catalogRepository) {
        this.furnishingRepository = furnishingRepository;
        this.roomRepository = roomRepository;
        this.catalogRepository = catalogRepository;
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
        return catalogRepository.findAllByOrderByItemNameAsc().stream().map(this::catalogDto).toList();
    }

    @Transactional
    public RoomFurnishingDTO saveStandardItem(Integer id, RoomFurnishingDTO dto) {
        String name = dto.getItemName() == null ? "" : dto.getItemName().trim();
        String condition = dto.getConditionStatus() == null ? "" : dto.getConditionStatus().trim();
        if (name.isEmpty() || name.length() > 100 || condition.isEmpty() || condition.length() > 100
                || dto.getQuantity() == null || dto.getQuantity() < 1
                || (dto.getNotes() != null && dto.getNotes().length() > 255)) {
            throw new BusinessException("Nhập tên, tình trạng và số lượng hợp lệ");
        }
        StandardFurnishing item = id == null ? new StandardFurnishing() : catalogRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Thiết bị chuẩn"));
        item.setItemName(name);
        item.setConditionStatus(condition);
        item.setQuantity(dto.getQuantity());
        item.setNotes(dto.getNotes());
        return catalogDto(catalogRepository.save(item));
    }

    @Transactional
    public void deleteStandardItem(Integer id) {
        StandardFurnishing item = catalogRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Thiết bị chuẩn"));
        catalogRepository.delete(item);
    }

    private RoomFurnishingDTO catalogDto(StandardFurnishing item) {
        RoomFurnishingDTO dto = new RoomFurnishingDTO();
        dto.setFurnishingId(item.getStandardId());
        dto.setItemName(item.getItemName());
        dto.setQuantity(item.getQuantity());
        dto.setConditionStatus(item.getConditionStatus());
        dto.setNotes(item.getNotes());
        return dto;
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
                existingNames.add(std.getItemName().toLowerCase().trim());
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

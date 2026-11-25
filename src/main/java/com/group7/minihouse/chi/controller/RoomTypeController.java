package com.group7.minihouse.chi.controller;

import com.group7.minihouse.chi.dto.RoomTypeDTO;
import com.group7.minihouse.chi.repository.PropertyRepository;
import com.group7.minihouse.chi.repository.RoomTypeRepository;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.entity.RoomType;
import com.group7.minihouse.shared.exception.BusinessException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chi/room-types")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class RoomTypeController {
    private final RoomTypeRepository repository;
    private final PropertyRepository properties;

    public RoomTypeController(RoomTypeRepository repository, PropertyRepository properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @GetMapping
    public ApiResponse<List<RoomTypeDTO>> list(@RequestParam Integer propertyId) {
        return ApiResponse.ok(repository.findByProperty_PropertyIdOrderByTypeName(propertyId).stream()
                .map(this::toDto).toList());
    }

    @PostMapping
    public ApiResponse<RoomTypeDTO> create(@RequestBody RoomTypeDTO request) {
        RoomType type = new RoomType();
        if (request.propertyId() == null) throw new BusinessException("Chưa chọn khu trọ");
        type.setProperty(properties.findById(request.propertyId())
                .orElseThrow(() -> BusinessException.notFound("Khu trọ")));
        return ApiResponse.ok("Đã thêm loại phòng", save(type, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoomTypeDTO> update(@PathVariable Integer id, @RequestBody RoomTypeDTO request) {
        RoomType type = find(id);
        if (request.propertyId() != null && !request.propertyId().equals(type.getProperty().getPropertyId())) {
            throw new BusinessException("Loại phòng phải ở cùng khu trọ");
        }
        return ApiResponse.ok("Đã cập nhật loại phòng", save(type, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        RoomType type = find(id);
        try {
            repository.delete(type);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HttpStatus.CONFLICT, "Gỡ loại phòng khỏi các phòng đang dùng trước khi xóa");
        }
        return ApiResponse.ok("Đã xóa loại phòng", null);
    }

    private RoomType find(Integer id) {
        return repository.findById(id).orElseThrow(() -> BusinessException.notFound("Loại phòng"));
    }

    private RoomTypeDTO save(RoomType type, RoomTypeDTO request) {
        String name = request.typeName() == null ? "" : request.typeName().trim();
        if (name.isEmpty() || name.length() > 100
                || request.defaultRentPrice() == null || request.defaultRentPrice().signum() < 0
                || request.defaultDepositAmount() == null || request.defaultDepositAmount().signum() < 0
                || (request.description() != null && request.description().length() > 255)) {
            throw new BusinessException("Kiểm tra tên loại phòng, mô tả, giá thuê và tiền cọc");
        }
        type.setTypeName(name);
        type.setDescription(request.description());
        type.setDefaultRentPrice(request.defaultRentPrice());
        type.setDefaultDepositAmount(request.defaultDepositAmount());
        return toDto(repository.save(type));
    }

    private RoomTypeDTO toDto(RoomType type) {
        return new RoomTypeDTO(type.getRoomTypeId(), type.getProperty().getPropertyId(), type.getTypeName(),
                type.getDescription(), type.getDefaultRentPrice(), type.getDefaultDepositAmount());
    }
}

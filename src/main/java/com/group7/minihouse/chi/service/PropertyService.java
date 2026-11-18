package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.PropertyDTO;
import com.group7.minihouse.chi.repository.PropertyFloorRepository;
import com.group7.minihouse.chi.repository.PropertyRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.exception.BusinessException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Danh sách khu trọ (chỉ đọc) để giao diện chọn khu đang làm việc. */
@Service
@Transactional(readOnly = true)
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final PropertyFloorRepository floorRepository;

    public PropertyService(PropertyRepository propertyRepository, RoomRepository roomRepository,
                           PropertyFloorRepository floorRepository) {
        this.propertyRepository = propertyRepository;
        this.roomRepository = roomRepository;
        this.floorRepository = floorRepository;
    }

    public List<PropertyDTO> getAll() {
        return propertyRepository.findAllByOrderByPropertyIdAsc().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public PropertyDTO getDefault() {
        List<Property> list = propertyRepository.findAllByOrderByPropertyIdAsc();
        return list.isEmpty() ? null : toDTO(list.get(0));
    }

    /** UC 14: sửa thông tin khu trọ. */
    @Transactional
    public PropertyDTO update(Integer id, PropertyDTO dto) {
        Property p = propertyRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy khu trọ ID: " + id));
        if (dto.getPropertyName() != null && !dto.getPropertyName().isBlank()) p.setPropertyName(dto.getPropertyName().trim());
        if (dto.getAddress() != null && !dto.getAddress().isBlank()) p.setAddress(dto.getAddress().trim());
        if (dto.getTotalFloors() != null) p.setTotalFloors(dto.getTotalFloors());
        if (dto.getSupportPhone() != null) p.setSupportPhone(dto.getSupportPhone().trim());
        return toDTO(propertyRepository.save(p));
    }

    private PropertyDTO toDTO(Property p) {
        PropertyDTO dto = new PropertyDTO();
        dto.setPropertyId(p.getPropertyId());
        dto.setPropertyName(p.getPropertyName());
        dto.setAddress(p.getAddress());
        dto.setTotalFloors(p.getTotalFloors());
        dto.setSupportPhone(p.getSupportPhone());
        dto.setRoomCount(roomRepository.countByProperty_PropertyId(p.getPropertyId()));
        dto.setFloors(floorRepository.findByProperty_PropertyIdOrderByFloorNumberAsc(p.getPropertyId()).stream()
                .map(f -> new PropertyDTO.Floor(f.getFloorId(), f.getFloorNumber(), f.getFloorName()))
                .collect(Collectors.toList()));
        return dto;
    }
}

package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.PropertyFloorDTO;
import com.group7.minihouse.chi.repository.PropertyFloorRepository;
import com.group7.minihouse.chi.repository.PropertyRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.entity.PropertyFloor;
import com.group7.minihouse.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * UC 15: Thêm, sửa, xóa tầng khu trọ.
 */
@Service
public class PropertyFloorService {

    private final PropertyFloorRepository floorRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;

    public PropertyFloorService(PropertyFloorRepository floorRepository,
                                PropertyRepository propertyRepository,
                                RoomRepository roomRepository) {
        this.floorRepository = floorRepository;
        this.propertyRepository = propertyRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public List<PropertyFloorDTO.Response> getFloors(Integer propertyId) {
        Integer resolvedId = resolvePropertyId(propertyId);
        return floorRepository.findByProperty_PropertyIdOrderByFloorNumberAsc(resolvedId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(cacheNames = "properties", allEntries = true)
    public PropertyFloorDTO.Response createFloor(PropertyFloorDTO.CreateRequest req) {
        if (req == null || req.getFloorNumber() == null) {
            throw new BusinessException("Số tầng không được để trống!");
        }
        if (req.getFloorName() == null || req.getFloorName().trim().isEmpty()) {
            throw new BusinessException("Tên tầng không được để trống!");
        }

        Integer propertyId = resolvePropertyId(req.getPropertyId());
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy khu trọ!"));

        if (floorRepository.existsByProperty_PropertyIdAndFloorNumber(propertyId, req.getFloorNumber())) {
            throw new BusinessException("Tầng số " + req.getFloorNumber() + " đã tồn tại trong khu trọ này!");
        }

        PropertyFloor floor = new PropertyFloor();
        floor.setProperty(property);
        floor.setFloorNumber(req.getFloorNumber());
        floor.setFloorName(req.getFloorName().trim());

        PropertyFloor saved = floorRepository.save(floor);
        return toDTO(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = "properties", allEntries = true)
    public PropertyFloorDTO.Response updateFloor(Integer floorId, PropertyFloorDTO.UpdateRequest req) {
        PropertyFloor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tầng với ID: " + floorId));

        if (req.getFloorNumber() != null) {
            Integer propertyId = floor.getProperty() != null ? floor.getProperty().getPropertyId() : 1;
            if (floorRepository.existsByProperty_PropertyIdAndFloorNumberAndFloorIdNot(propertyId, req.getFloorNumber(), floorId)) {
                throw new BusinessException("Tầng số " + req.getFloorNumber() + " đã trùng với tầng khác!");
            }
            floor.setFloorNumber(req.getFloorNumber());
        }

        if (req.getFloorName() != null && !req.getFloorName().trim().isEmpty()) {
            floor.setFloorName(req.getFloorName().trim());
        }

        PropertyFloor saved = floorRepository.save(floor);
        return toDTO(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = "properties", allEntries = true)
    public void deleteFloor(Integer floorId) {
        PropertyFloor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tầng với ID: " + floorId));

        long roomCount = roomRepository.countByFloor_FloorId(floorId);
        if (roomCount > 0) {
            throw new BusinessException("Không thể xóa tầng này vì đang có " + roomCount + " phòng thuộc tầng. Vui lòng chuyển hoặc xóa các phòng trước!");
        }

        floorRepository.delete(floor);
    }

    private Integer resolvePropertyId(Integer propertyId) {
        if (propertyId != null) return propertyId;
        return propertyRepository.findAll().stream().findFirst()
                .map(Property::getPropertyId)
                .orElse(1);
    }

    private PropertyFloorDTO.Response toDTO(PropertyFloor f) {
        return new PropertyFloorDTO.Response(
                f.getFloorId(),
                f.getProperty() != null ? f.getProperty().getPropertyId() : null,
                f.getFloorNumber(),
                f.getFloorName()
        );
    }
}

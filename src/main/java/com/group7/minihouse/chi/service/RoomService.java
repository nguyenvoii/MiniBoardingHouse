package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.RoomDTO;
import com.group7.minihouse.chi.repository.PropertyFloorRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.entity.PropertyFloor;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomType;
import com.group7.minihouse.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private static final Set<String> STATUSES = Set.of("VACANT", "RENTED", "REPAIRING", "CLEANING", "SUSPENDED");

    private final RoomRepository roomRepository;
    private final PropertyFloorRepository floorRepository;
    private final EntityManager em;

    public RoomService(RoomRepository roomRepository, PropertyFloorRepository floorRepository, EntityManager em) {
        this.roomRepository = roomRepository;
        this.floorRepository = floorRepository;
        this.em = em;
    }

    public List<RoomDTO> getAllRooms(Integer propertyId) {
        List<Room> list = propertyId != null ? roomRepository.findByProperty_PropertyId(propertyId) : roomRepository.findAll();
        return list.stream().map(this::toDTO).collect(Collectors.toList());
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

    // UC 19 & UC 20: Tìm kiếm theo mã phòng & lọc theo diện tích, giá
    public List<RoomDTO> searchRooms(String keyword, Integer floorId, String status,
                                    BigDecimal minArea, BigDecimal maxArea,
                                    BigDecimal minPrice, BigDecimal maxPrice) {
        return roomRepository.findAll().stream()
                .filter(r -> {
                    if (keyword != null && !keyword.isBlank()) {
                        String kw = keyword.trim().toLowerCase();
                        boolean matchCode = r.getRoomCode() != null && r.getRoomCode().toLowerCase().contains(kw);
                        if (!matchCode) return false;
                    }
                    if (floorId != null && r.getFloor() != null) {
                        if (!floorId.equals(r.getFloor().getFloorId())) return false;
                    }
                    if (status != null && !status.isBlank()) {
                        if (!status.equalsIgnoreCase(r.getStatus())) return false;
                    }
                    if (minArea != null && r.getAreaSqm() != null) {
                        if (r.getAreaSqm().compareTo(minArea) < 0) return false;
                    }
                    if (maxArea != null && r.getAreaSqm() != null) {
                        if (r.getAreaSqm().compareTo(maxArea) > 0) return false;
                    }
                    if (minPrice != null && r.getBaseRentPrice() != null) {
                        if (r.getBaseRentPrice().compareTo(minPrice) < 0) return false;
                    }
                    if (maxPrice != null && r.getBaseRentPrice() != null) {
                        if (r.getBaseRentPrice().compareTo(maxPrice) > 0) return false;
                    }
                    return true;
                })
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
@CacheEvict(cacheNames = "properties", allEntries = true)
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
        if ((dto.getInitialElectricMeter() != null && dto.getInitialElectricMeter() < 0)
                || (dto.getInitialWaterMeter() != null && dto.getInitialWaterMeter() < 0)) {
            throw new BusinessException("Chỉ số điện nước ban đầu không được âm");
        }
        String status = dto.getStatus() != null ? dto.getStatus().trim().toUpperCase() : "VACANT";
        if (!STATUSES.contains(status)) {
            throw new BusinessException("Trạng thái phòng không hợp lệ (VACANT, RENTED, REPAIRING, CLEANING, SUSPENDED)");
        }
        if ("RENTED".equals(status)) {
            throw new BusinessException("Phòng mới chưa có hợp đồng nên không thể ở trạng thái RENTED");
        }

        PropertyFloor floor = floorRepository.findById(dto.getFloorId())
                .orElseThrow(() -> BusinessException.notFound("Tầng"));
        Property property = floor.getProperty();

        RoomType type = findRoomType(dto.getRoomTypeId(), property);
        BigDecimal rent = dto.getBaseRentPrice() == null && type != null ? type.getDefaultRentPrice() : dto.getBaseRentPrice();
        BigDecimal deposit = dto.getDepositAmount() == null && type != null ? type.getDefaultDepositAmount() : dto.getDepositAmount();
        if (rent == null || rent.signum() < 0) throw new BusinessException("Giá thuê không hợp lệ");
        if (deposit == null || deposit.signum() < 0) throw new BusinessException("Tiền cọc không hợp lệ");

        if (roomRepository.existsByProperty_PropertyIdAndRoomCode(property.getPropertyId(), code)) {
            throw new BusinessException("Mã phòng " + code + " đã tồn tại trong nhà này");
        }

        Room room = new Room();
        room.setProperty(property);
        room.setFloor(floor);
        room.setRoomType(type);
        room.setRoomCode(code);
        room.setAreaSqm(dto.getAreaSqm());
        room.setMaxOccupants(dto.getMaxOccupants());
        room.setBaseRentPrice(rent);
        room.setDepositAmount(deposit);
        room.setInitialElectricMeter(dto.getInitialElectricMeter() != null ? dto.getInitialElectricMeter() : 0);
        room.setInitialWaterMeter(dto.getInitialWaterMeter() != null ? dto.getInitialWaterMeter() : 0);
        room.setStatus(status);
        room.setCreatedAt(LocalDateTime.now());

        return toDTO(roomRepository.save(room));
    }

    // UC 18 & UC 27: Cập nhật thông tin phòng, giá, cọc, chỉ số công tơ (trạng thái phòng không đổi ở đây, xem updateRoomStatus)
    @Transactional
    public RoomDTO updateRoom(Integer id, RoomDTO dto) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng có ID: " + id));

        if (dto.roomTypeSpecified()) {
            room.setRoomType(findRoomType(dto.getRoomTypeId(), room.getProperty()));
        }
        if ((dto.getInitialElectricMeter() != null && dto.getInitialElectricMeter() < 0)
                || (dto.getInitialWaterMeter() != null && dto.getInitialWaterMeter() < 0)) {
            throw new BusinessException("Chỉ số điện nước ban đầu không được âm");
        }

        if (dto.getFloorId() != null && !dto.getFloorId().equals(room.getFloor().getFloorId())) {
            PropertyFloor floor = floorRepository.findById(dto.getFloorId())
                    .orElseThrow(() -> new BusinessException("Tầng không tồn tại"));
            if (!room.getProperty().getPropertyId().equals(floor.getProperty().getPropertyId())) {
                throw new BusinessException("Tầng phải thuộc cùng khu trọ");
            }
            room.setFloor(floor);
        }

        if (dto.getRoomCode() != null && !dto.getRoomCode().equalsIgnoreCase(room.getRoomCode())) {
            if (roomRepository.existsByProperty_PropertyIdAndRoomCode(room.getProperty().getPropertyId(), dto.getRoomCode())) {
                throw new BusinessException("Mã phòng " + dto.getRoomCode() + " đã tồn tại trong nhà này");
            }
            room.setRoomCode(dto.getRoomCode());
        }

        if (dto.getAreaSqm() != null) {
            room.setAreaSqm(dto.getAreaSqm());
        }
        if (dto.getMaxOccupants() != null) {
            room.setMaxOccupants(dto.getMaxOccupants());
        }
        if (dto.getBaseRentPrice() != null) {
            room.setBaseRentPrice(dto.getBaseRentPrice());
        }
        if (dto.getDepositAmount() != null) {
            room.setDepositAmount(dto.getDepositAmount());
        }
        if (dto.getInitialElectricMeter() != null) {
            room.setInitialElectricMeter(dto.getInitialElectricMeter());
        }
        if (dto.getInitialWaterMeter() != null) {
            room.setInitialWaterMeter(dto.getInitialWaterMeter());
        }

        return toDTO(roomRepository.save(room));
    }

    /**
     * Đổi trạng thái phòng. RENTED chỉ do hợp đồng quyết định: phòng đang có hợp đồng hiệu lực thì phải giữ RENTED
     * (muốn đổi thì chấm dứt hợp đồng), phòng không có hợp đồng thì không thể gán RENTED.
     */
    @Transactional
    public RoomDTO updateRoomStatus(Integer roomId, String newStatus) {
        if (newStatus == null || !STATUSES.contains(newStatus.trim().toUpperCase())) {
            throw new BusinessException("Trạng thái phòng không hợp lệ (VACANT, RENTED, REPAIRING, CLEANING, SUSPENDED)");
        }
        String status = newStatus.trim().toUpperCase();
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> BusinessException.notFound("Phòng"));
        boolean hasActiveContract = em.createQuery("select count(c) from RentalContract c where c.room.roomId = :r "
                        + "and c.status in ('ACTIVE', 'EXPIRING_SOON', 'EXPIRED')", Long.class)
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

    // UC 29: Xác nhận phòng đã vệ sinh xong, cho thuê lại (chuyển CLEANING -> VACANT)
    @Transactional
    public RoomDTO confirmCleanedAndAvailable(Integer roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> BusinessException.notFound("Phòng"));
        if (!"CLEANING".equals(room.getStatus())) {
            throw new BusinessException("Chỉ xác nhận vệ sinh cho phòng đang chờ vệ sinh");
        }
        return updateRoomStatus(roomId, "VACANT");
    }

    private RoomType findRoomType(Integer id, Property property) {
        if (id == null) return null;
        RoomType type = em.find(RoomType.class, id);
        if (type == null || !property.getPropertyId().equals(type.getProperty().getPropertyId())) {
            throw new BusinessException("Loại phòng không thuộc khu trọ đang chọn");
        }
        return type;
    }

    private RoomDTO toDTO(Room r) {
        RoomDTO dto = new RoomDTO();
        dto.setRoomId(r.getRoomId());
        dto.setPropertyId(r.getProperty() != null ? r.getProperty().getPropertyId() : null);
        if (r.getFloor() != null) {
            dto.setFloorId(r.getFloor().getFloorId());
            dto.setFloorName(r.getFloor().getFloorName());
            dto.setFloorNumber(r.getFloor().getFloorNumber());
        }
        if (r.getRoomType() != null) {
            dto.setRoomTypeId(r.getRoomType().getRoomTypeId());
            dto.setRoomTypeName(r.getRoomType().getTypeName());
        }
        var hold = r.activeHold(LocalDateTime.now());
        if (hold != null) dto.setHoldUntil(hold.getHoldUntil());
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

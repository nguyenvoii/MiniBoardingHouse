package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.RoomAddonFeeDTO;
import com.group7.minihouse.chi.repository.RoomAddonFeeRepository;
import com.group7.minihouse.chi.repository.RoomEquipmentRequestRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomAddonFee;
import com.group7.minihouse.shared.entity.RoomEquipmentRequest;
import com.group7.minihouse.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomAddonFeeService {

    private final RoomAddonFeeRepository addonFeeRepository;
    private final RoomRepository roomRepository;
    private final RoomEquipmentRequestRepository equipmentRequestRepository;

    public RoomAddonFeeService(RoomAddonFeeRepository addonFeeRepository,
                              RoomRepository roomRepository,
                              RoomEquipmentRequestRepository equipmentRequestRepository) {
        this.addonFeeRepository = addonFeeRepository;
        this.roomRepository = roomRepository;
        this.equipmentRequestRepository = equipmentRequestRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomAddonFeeDTO.Response> getAll(Integer roomId, Boolean isActive) {
        List<RoomAddonFee> list;
        if (roomId != null && isActive != null) {
            list = addonFeeRepository.findByRoom_RoomIdAndIsActiveOrderByStartDateDesc(roomId, isActive);
        } else if (roomId != null) {
            list = addonFeeRepository.findByRoom_RoomIdOrderByStartDateDesc(roomId);
        } else if (isActive != null) {
            list = addonFeeRepository.findByIsActiveOrderByStartDateDesc(isActive);
        } else {
            list = addonFeeRepository.findAllByOrderByStartDateDesc();
        }
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoomAddonFeeDTO.Response getById(Integer id) {
        RoomAddonFee entity = addonFeeRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Phụ phí #" + id));
        return toResponse(entity);
    }

    @Transactional
    public RoomAddonFeeDTO.Response create(RoomAddonFeeDTO.CreateRequest req) {
        if (req == null || req.getRoomId() == null) throw new BusinessException("Vui lòng chọn phòng");
        if (req.getFeeName() == null || req.getFeeName().trim().isEmpty()) {
            throw new BusinessException("Vui lòng nhập tên phụ phí dịch vụ");
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Số tiền phụ phí không hợp lệ");
        }

        if (req.getFeeName().trim().length() > 100) {
            throw new BusinessException("Tên phụ phí tối đa 100 ký tự");
        }
        if (req.getBillingCycleMonths() != null && req.getBillingCycleMonths() < 1) {
            throw new BusinessException("Chu kỳ thu phải từ 1 tháng trở lên");
        }
        LocalDate start = req.getStartDate() != null ? req.getStartDate() : LocalDate.now();
        if (req.getEndDate() != null && req.getEndDate().isBefore(start)) {
            throw new BusinessException("Ngày kết thúc không được trước ngày bắt đầu");
        }

        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> BusinessException.notFound("Phòng #" + req.getRoomId()));

        RoomAddonFee entity = new RoomAddonFee();
        entity.setRoom(room);
        entity.setFeeName(req.getFeeName().trim());
        entity.setAmount(req.getAmount());
        entity.setBillingCycleMonths(req.getBillingCycleMonths() != null ? req.getBillingCycleMonths() : 1);
        entity.setStartDate(start);
        entity.setEndDate(req.getEndDate());
        entity.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);

        if (req.getEquipmentRequestId() != null) {
            RoomEquipmentRequest eq = equipmentRequestRepository.findById(req.getEquipmentRequestId())
                    .orElseThrow(() -> BusinessException.notFound("Yêu cầu thiết bị #" + req.getEquipmentRequestId()));
            if (!room.getRoomId().equals(eq.getRoom().getRoomId())) {
                throw new BusinessException("Yêu cầu thiết bị không thuộc phòng đã chọn");
            }
            entity.setEquipmentRequest(eq);
        }

        return toResponse(addonFeeRepository.save(entity));
    }

    @Transactional
    public RoomAddonFeeDTO.Response toggleActive(Integer id) {
        RoomAddonFee entity = addonFeeRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Phụ phí #" + id));
        entity.setIsActive(entity.getIsActive() == null || !entity.getIsActive());
        return toResponse(addonFeeRepository.save(entity));
    }

    @Transactional
    public void delete(Integer id) {
        if (!addonFeeRepository.existsById(id)) {
            throw BusinessException.notFound("Phụ phí #" + id);
        }
        addonFeeRepository.deleteById(id);
    }

    private RoomAddonFeeDTO.Response toResponse(RoomAddonFee e) {
        RoomAddonFeeDTO.Response res = new RoomAddonFeeDTO.Response();
        res.setAddonFeeId(e.getAddonFeeId());
        if (e.getRoom() != null) {
            res.setRoomId(e.getRoom().getRoomId());
            res.setRoomCode(e.getRoom().getRoomCode());
        }
        if (e.getEquipmentRequest() != null) {
            res.setEquipmentRequestId(e.getEquipmentRequest().getEquipmentRequestId());
            res.setEquipmentName(e.getEquipmentRequest().getEquipmentName());
        }
        res.setFeeName(e.getFeeName());
        res.setAmount(e.getAmount());
        res.setBillingCycleMonths(e.getBillingCycleMonths());
        res.setStartDate(e.getStartDate());
        res.setEndDate(e.getEndDate());
        res.setIsActive(e.getIsActive());
        return res;
    }
}

package com.group7.minihouse.chi.controller;
import com.group7.minihouse.chi.dto.RoomTypeDTO;
import com.group7.minihouse.chi.repository.*;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.entity.*;
import com.group7.minihouse.shared.exception.BusinessException;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/chi/room-types") @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class RoomTypeController {
 private final RoomTypeRepository repo; private final PropertyRepository properties;
 public RoomTypeController(RoomTypeRepository repo,PropertyRepository properties){this.repo=repo;this.properties=properties;}
 @GetMapping public ApiResponse<List<RoomTypeDTO>> list(@RequestParam Integer propertyId){return ApiResponse.ok(repo.findByProperty_PropertyIdOrderByTypeName(propertyId).stream().map(this::dto).toList());}
 @PostMapping public ApiResponse<RoomTypeDTO> create(@RequestBody RoomTypeDTO req){return ApiResponse.ok("Đã thêm loại phòng",save(new RoomType(),req));}
 @PutMapping("/{id}") public ApiResponse<RoomTypeDTO> update(@PathVariable Integer id,@RequestBody RoomTypeDTO req){RoomType x=repo.findById(id).orElseThrow(()->BusinessException.notFound("Loại phòng"));return ApiResponse.ok("Đã cập nhật loại phòng",save(x,req));}
 @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Integer id){if(!repo.existsById(id))throw BusinessException.notFound("Loại phòng");repo.deleteById(id);return ApiResponse.ok("Đã xóa loại phòng",null);}
 private RoomTypeDTO save(RoomType x,RoomTypeDTO r){if(r.propertyId()==null||r.typeName()==null||r.typeName().isBlank()||r.defaultRentPrice()==null||r.defaultDepositAmount()==null)throw new BusinessException("Thiếu thông tin loại phòng");x.setProperty(properties.findById(r.propertyId()).orElseThrow(()->BusinessException.notFound("Khu trọ")));x.setTypeName(r.typeName().trim());x.setDescription(r.description());x.setDefaultRentPrice(r.defaultRentPrice());x.setDefaultDepositAmount(r.defaultDepositAmount());return dto(repo.save(x));}
 private RoomTypeDTO dto(RoomType x){return new RoomTypeDTO(x.getRoomTypeId(),x.getProperty().getPropertyId(),x.getTypeName(),x.getDescription(),x.getDefaultRentPrice(),x.getDefaultDepositAmount());}
}

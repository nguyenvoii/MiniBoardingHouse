package com.group7.minihouse.chi.repository;
import com.group7.minihouse.shared.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RoomTypeRepository extends JpaRepository<RoomType,Integer>{List<RoomType> findByProperty_PropertyIdOrderByTypeName(Integer propertyId);}

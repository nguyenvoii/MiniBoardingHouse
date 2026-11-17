package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.Property;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("chiPropertyRepository")
public interface PropertyRepository extends JpaRepository<Property, Integer> {

    List<Property> findAllByOrderByPropertyIdAsc();
}

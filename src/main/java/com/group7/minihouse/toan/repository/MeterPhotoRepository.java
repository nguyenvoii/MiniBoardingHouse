package com.group7.minihouse.toan.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.shared.entity.MeterPhoto;

public interface MeterPhotoRepository extends JpaRepository<MeterPhoto, Integer> {

    List<MeterPhoto> findByReadingReadingId(Integer readingId);
}

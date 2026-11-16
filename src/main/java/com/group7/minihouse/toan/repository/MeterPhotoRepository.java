package com.group7.minihouse.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.entity.MeterPhoto;

public interface MeterPhotoRepository extends JpaRepository<MeterPhoto, Integer> {

    List<MeterPhoto> findByReadingReadingId(Integer readingId);
}

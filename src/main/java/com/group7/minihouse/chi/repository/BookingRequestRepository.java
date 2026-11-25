package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.BookingRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRequestRepository extends JpaRepository<BookingRequest, Integer> {

    @EntityGraph(attributePaths = {"room", "tenant"})
    List<BookingRequest> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"room", "tenant"})
    boolean existsByRoom_RoomIdAndGuestPhoneAndExpectedStartDateAndStatus(
            Integer roomId, String guestPhone, java.time.LocalDate expectedStartDate, String status);

    List<BookingRequest> findByStatusOrderByCreatedAtDesc(String status);
}

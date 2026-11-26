package com.group7.minihouse.toan.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.group7.minihouse.shared.entity.MeterReading;

public interface MeterReadingRepository extends JpaRepository<MeterReading, Integer> {

    @Query("select r from MeterReading r where r.period.periodId = :periodId "
            + "order by r.room.roomCode, r.readingId")
    List<MeterReading> findByPeriodPeriodIdOrderByRoomRoomCode(Integer periodId);

    @Query("select r from MeterReading r where r.period.periodId = :periodId "
            + "and r.room.roomId = :roomId and r.closingContract is null")
    Optional<MeterReading> findByPeriodPeriodIdAndRoomRoomId(Integer periodId, Integer roomId);

    Optional<MeterReading> findFirstByPeriodPeriodIdAndRoomRoomIdAndClosingContractIsNotNullOrderByReadingIdDesc(
            Integer periodId, Integer roomId);

    boolean existsByPeriodPeriodIdAndRoomRoomIdAndReadingIdGreaterThan(Integer periodId, Integer roomId, Integer readingId);

    /** Chi so ky ngay truoc (ky co ngay ket thuc som hon ngay bat dau ky hien tai). */
    Optional<MeterReading> findFirstByRoomRoomIdAndPeriodEndDateLessThanOrderByPeriodEndDateDescReadingIdDesc(
            Integer roomId, LocalDate beforeDate);

    /** Lich su tieu thu cua mot phong, loc theo khoang ngay cua ky (UC 58, 59). */
    @Query("select r from MeterReading r where r.room.roomId = :roomId "
            + "and r.period.startDate >= :fromDate "
            + "and r.period.endDate <= :toDate "
            + "order by r.period.startDate asc")
    List<MeterReading> findHistoryByRoom(@Param("roomId") Integer roomId,
                                         @Param("fromDate") LocalDate fromDate,
                                         @Param("toDate") LocalDate toDate);

    /** Tat ca phong trong khu theo khoang ngay (tong tieu thu toan khu, UC 59). */
    @Query("select r from MeterReading r where r.period.property.propertyId = :propertyId "
            + "and r.period.startDate >= :fromDate "
            + "and r.period.endDate <= :toDate "
            + "order by r.period.startDate asc")
    List<MeterReading> findHistoryByProperty(@Param("propertyId") Integer propertyId,
                                             @Param("fromDate") LocalDate fromDate,
                                             @Param("toDate") LocalDate toDate);
}

package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.RentalContract;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * UC130: tìm người nhận thông báo chung. Chỉ khai báo truy vấn đọc (không kế thừa JpaRepository)
 * để không trùng chức năng với RentalContractRepository của Phi.
 * Mỗi dòng trả về là cặp (Tenant, mã phòng); khách thuê nhiều phòng có nhiều dòng.
 */
public interface AnnouncementRecipientRepository extends Repository<RentalContract, Integer> {

    @Query("select distinct t, r.roomCode from RentalContract c join c.tenant t join c.room r"
            + " where c.status in ('ACTIVE','EXPIRING_SOON') order by t.fullName, r.roomCode")
    List<Object[]> findAllRecipients();

    @Query("select distinct t, r.roomCode from RentalContract c join c.tenant t join c.room r"
            + " where c.status in ('ACTIVE','EXPIRING_SOON') and r.floor.floorId in :floorIds"
            + " order by t.fullName, r.roomCode")
    List<Object[]> findRecipientsByFloors(@Param("floorIds") Collection<Integer> floorIds);
}

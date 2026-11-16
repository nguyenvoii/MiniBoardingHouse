package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.RentalContract;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalContractRepository extends JpaRepository<RentalContract, Integer> {

    boolean existsByContractCode(String contractCode);

    /** So hop dong con hieu luc cua phong (ACTIVE hoac EXPIRING_SOON), tru hop dong excludeId (truyen 0 neu khong tru). */
    @Query("select count(c) from RentalContract c where c.room.roomId = :roomId "
            + "and c.status in ('ACTIVE', 'EXPIRING_SOON') and c.contractId <> :excludeId")
    long countLiveByRoom(@Param("roomId") Integer roomId, @Param("excludeId") Integer excludeId);

    /** kw la mau LIKE da escape (ky tu escape '\'), status '' = tat ca. */
    @Query("select c from RentalContract c where (:status = '' or c.status = :status) "
            + "and (lower(c.contractCode) like :kw escape '\\' or lower(c.tenant.fullName) like :kw escape '\\' "
            + "or lower(c.room.roomCode) like :kw escape '\\')")
    Page<RentalContract> search(@Param("kw") String kw, @Param("status") String status, Pageable pageable);

    List<RentalContract> findByTenantTenantIdOrderByStartDateDesc(Integer tenantId);
}

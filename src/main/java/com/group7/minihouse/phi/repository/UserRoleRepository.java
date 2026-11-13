package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.UserRole;
import com.group7.minihouse.shared.entity.UserRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    /** Danh sach role_code cua 1 nguoi dung, vi du ["LANDLORD"]. */
    @Query("select ur.role.roleCode from UserRole ur where ur.user.userId = :userId")
    List<String> findRoleCodesByUserId(@Param("userId") Integer userId);
}

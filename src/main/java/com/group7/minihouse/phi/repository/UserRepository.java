package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.User;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Integer> {

    /** Dang nhap bang email hoac so dien thoai (truyen cung 1 gia tri vao ca 2 tham so). */
    Optional<User> findByEmailOrPhoneNumber(String email, String phoneNumber);

    boolean existsByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    @Query("select u.status from User u where u.userId = :id")
    Optional<String> findStatusById(@Param("id") Integer id);

    boolean existsByEmailAndUserIdNot(String email, Integer userId);

    boolean existsByPhoneNumberAndUserIdNot(String phoneNumber, Integer userId);

    /**
     * Tim nhan vien: co it nhat 1 vai tro nhan vien (staffRoles) va KHONG phai chu tro/nguoi thue.
     * kw la mau LIKE da escape (dung '\' lam ky tu escape), '%' = tat ca; status '' = tat ca.
     */
    @Query("select u from User u where "
            + "exists (select 1 from UserRole ur where ur.user = u and ur.role.roleCode in :staffRoles) "
            + "and not exists (select 1 from UserRole ur2 where ur2.user = u "
            + "and ur2.role.roleCode in ('LANDLORD', 'TENANT')) "
            + "and (:status = '' or u.status = :status) "
            + "and (lower(u.fullName) like :kw escape '\\' or lower(u.email) like :kw escape '\\' "
            + "or u.phoneNumber like :kw escape '\\')")
    Page<User> searchStaff(@Param("kw") String kw, @Param("status") String status,
                           @Param("staffRoles") Collection<String> staffRoles, Pageable pageable);
}

package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {

    /** Dang nhap bang email hoac so dien thoai (truyen cung 1 gia tri vao ca 2 tham so). */
    Optional<User> findByEmailOrPhoneNumber(String email, String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);
}

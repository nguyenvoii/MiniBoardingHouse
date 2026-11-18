package com.group7.minihouse.toan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.shared.entity.User;

/** Truy vấn tài khoản nhân viên để ghi nhận người thực hiện thao tác. */
public interface UserRepository extends JpaRepository<User, Integer> {
}

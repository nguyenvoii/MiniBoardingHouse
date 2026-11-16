package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.Permission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Integer> {

    List<Permission> findAllByOrderByModuleAscPermissionCodeAsc();
}

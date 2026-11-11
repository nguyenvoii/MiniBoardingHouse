package com.group7.minihouse.entity;

import jakarta.persistence.*;

/** Bang `permissions`. */
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Integer permissionId;

    @Column(name = "permission_code", nullable = false, unique = true, length = 100)
    private String permissionCode;

    @Column(name = "permission_name", nullable = false, length = 100)
    private String permissionName;

    @Column(name = "module", nullable = false, length = 50)
    private String module;

    public Permission() {}

    public Integer getPermissionId() { return permissionId; }
    public void setPermissionId(Integer permissionId) { this.permissionId = permissionId; }

    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public String getPermissionName() { return permissionName; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

}

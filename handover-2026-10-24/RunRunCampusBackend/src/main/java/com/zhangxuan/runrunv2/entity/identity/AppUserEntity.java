package com.zhangxuan.runrunv2.entity.identity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/** 新系统账号实体；普通注册仅写手机号和身份，其余默认值继续由数据库决定。 */
@TableName(value = "app_users", schema = "runrun_v2")
public class AppUserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;

    private String passwordHash;

    private String role;

    private String status;

    private Boolean v2AdminEnabled;

    private Long v2AdminSecurityVersion;

    private Boolean controlledIdentity;

    private Long version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getV2AdminEnabled() {
        return v2AdminEnabled;
    }

    public void setV2AdminEnabled(Boolean v2AdminEnabled) {
        this.v2AdminEnabled = v2AdminEnabled;
    }

    public Long getV2AdminSecurityVersion() {
        return v2AdminSecurityVersion;
    }

    public void setV2AdminSecurityVersion(Long v2AdminSecurityVersion) {
        this.v2AdminSecurityVersion = v2AdminSecurityVersion;
    }

    public Boolean getControlledIdentity() {
        return controlledIdentity;
    }

    public void setControlledIdentity(Boolean controlledIdentity) {
        this.controlledIdentity = controlledIdentity;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

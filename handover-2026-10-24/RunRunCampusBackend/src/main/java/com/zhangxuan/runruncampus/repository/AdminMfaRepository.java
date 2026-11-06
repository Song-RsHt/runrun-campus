package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.AdminMfaCredential;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/** 管理员双因素认证 JPA 仓储；只定义持久化查询和写入，不承担权限及业务决策。 */
public interface AdminMfaRepository extends JpaRepository<AdminMfaCredential, Long> {

    @Query("select m.userId from AdminMfaCredential m where m.challengeHash = :hash")
    Optional<Long> findUserIdByChallengeHash(String hash);
}

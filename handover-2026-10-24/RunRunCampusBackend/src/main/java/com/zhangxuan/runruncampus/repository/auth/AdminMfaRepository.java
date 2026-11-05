package com.zhangxuan.runruncampus.repository.auth;
import com.zhangxuan.runruncampus.model.auth.AdminMfaCredential;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AdminMfaRepository extends JpaRepository<AdminMfaCredential, Long> {
    @Query("select m.userId from AdminMfaCredential m where m.challengeHash = :hash")
    Optional<Long> findUserIdByChallengeHash(String hash);
}

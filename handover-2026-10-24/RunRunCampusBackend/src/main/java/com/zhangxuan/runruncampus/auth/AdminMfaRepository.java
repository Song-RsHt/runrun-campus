package com.zhangxuan.runruncampus.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface AdminMfaRepository extends JpaRepository<AdminMfaCredential, Long> {
    @Query("select m.userId from AdminMfaCredential m where m.challengeHash = :hash")
    Optional<Long> findUserIdByChallengeHash(String hash);
}

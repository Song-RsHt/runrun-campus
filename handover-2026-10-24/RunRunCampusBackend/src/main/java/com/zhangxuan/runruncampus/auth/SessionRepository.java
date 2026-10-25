package com.zhangxuan.runruncampus.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.zhangxuan.runruncampus.user.AppUser;

public interface SessionRepository extends JpaRepository<Session, Long> {
    @Query("select s from Session s join fetch s.user where s.token = :token")
    Optional<Session> findByToken(String token);
    long deleteByUser(AppUser user);
    List<Session> findByUserAndExpiresAtAfterOrderByCreatedAtDesc(AppUser user, Instant now);
    Optional<Session> findByIdAndUser(Long id, AppUser user);
    long deleteByUserAndIdNot(AppUser user, Long id);
}

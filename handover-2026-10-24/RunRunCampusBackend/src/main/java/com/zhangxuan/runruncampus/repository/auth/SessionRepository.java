package com.zhangxuan.runruncampus.repository.auth;

import com.zhangxuan.runruncampus.model.auth.Session;
import com.zhangxuan.runruncampus.model.user.AppUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SessionRepository extends JpaRepository<Session, Long> {
  @Query("select s from Session s join fetch s.user where s.token = :token")
  Optional<Session> findByToken(String token);

  long deleteByUser(AppUser user);

  List<Session> findByUserAndExpiresAtAfterOrderByCreatedAtDesc(AppUser user, Instant now);

  Optional<Session> findByIdAndUser(Long id, AppUser user);

  long deleteByUserAndIdNot(AppUser user, Long id);
}

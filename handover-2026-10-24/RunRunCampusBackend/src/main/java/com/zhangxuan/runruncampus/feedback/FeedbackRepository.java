package com.zhangxuan.runruncampus.feedback;

import com.zhangxuan.runruncampus.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<UserFeedback, Long> {
    List<UserFeedback> findByUserOrderByCreatedAtDesc(AppUser user);
    long countByHandlingStatus(FeedbackHandlingStatus handlingStatus);

    @Query(value = """
            select feedback from UserFeedback feedback
            join fetch feedback.user user
            where (:type is null or feedback.type = :type)
              and (:handlingStatus is null or feedback.handlingStatus = :handlingStatus)
              and (:query is null
                or lower(user.displayName) like lower(concat('%', :query, '%'))
                or user.phone like concat('%', :query, '%')
                or lower(feedback.content) like lower(concat('%', :query, '%')))
            """,
            countQuery = """
            select count(feedback) from UserFeedback feedback
            join feedback.user user
            where (:type is null or feedback.type = :type)
              and (:handlingStatus is null or feedback.handlingStatus = :handlingStatus)
              and (:query is null
                or lower(user.displayName) like lower(concat('%', :query, '%'))
                or user.phone like concat('%', :query, '%')
                or lower(feedback.content) like lower(concat('%', :query, '%')))
            """)
    Page<UserFeedback> searchForAdmin(@Param("query") String query, @Param("type") FeedbackType type,
                                      @Param("handlingStatus") FeedbackHandlingStatus handlingStatus,
                                      Pageable pageable);

    @Query(value = """
            select feedback from UserFeedback feedback
            join fetch feedback.user
            where (:type is null or feedback.type = :type)
              and (:handlingStatus is null or feedback.handlingStatus = :handlingStatus)
            """,
            countQuery = """
            select count(feedback) from UserFeedback feedback
            where (:type is null or feedback.type = :type)
              and (:handlingStatus is null or feedback.handlingStatus = :handlingStatus)
            """)
    Page<UserFeedback> filterForAdmin(@Param("type") FeedbackType type,
                                      @Param("handlingStatus") FeedbackHandlingStatus handlingStatus,
                                      Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select feedback from UserFeedback feedback join fetch feedback.user where feedback.id = :id")
    Optional<UserFeedback> findLockedById(@Param("id") Long id);
}

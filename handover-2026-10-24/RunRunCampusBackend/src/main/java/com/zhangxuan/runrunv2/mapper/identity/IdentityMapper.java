package com.zhangxuan.runrunv2.mapper.identity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhangxuan.runrunv2.config.SingleRowRequired;
import com.zhangxuan.runrunv2.entity.identity.AppUserEntity;
import com.zhangxuan.runrunv2.model.identity.IdentityUser;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 身份与会话持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface IdentityMapper extends BaseMapper<AppUserEntity> {

    /** 读取并锁定 runrun_v2.app_users 的匹配行；锁持有至调用方事务结束。 */
    List<IdentityUser> findWechatUsersForUpdate(
            @Param("appId") String appId, @Param("digest") String digest);

    int insertWechatTicket(
            @Param("tokenHash") String tokenHash,
            @Param("appId") String appId,
            @Param("openidHash") String openidHash,
            @Param("openidCiphertext") String openidCiphertext);

    @SingleRowRequired
    Long countWechatIdentity(@Param("appId") String appId, @Param("openidHash") String openidHash);

    /** 读取并锁定 runrun_v2.app_users 的匹配行；锁持有至调用方事务结束。 */
    List<IdentityUser> findPhoneUsersForUpdate(@Param("phone") String phone);

    default Long insertAccount(String phone, String role) {
        AppUserEntity account = new AppUserEntity();
        account.setPhone(phone);
        account.setRole(role);
        insert(account);
        return account.getId();
    }

    @SingleRowRequired
    Long countUserWechatBindings(@Param("appId") String appId, @Param("userId") long userId);

    int insertWechatBinding(
            @Param("userId") long userId,
            @Param("appId") String appId,
            @Param("openidHash") String openidHash,
            @Param("openidCiphertext") String openidCiphertext);

    int insertWechatBoundEvent(@Param("actorId") long actorId, @Param("subjectId") long subjectId);

    /** 读取并锁定 runrun_v2.app_users 的匹配行；锁持有至调用方事务结束。 */
    List<IdentityUser> findPasswordUsersForUpdate(@Param("phone") String phone);

    @SingleRowRequired
    Long countPasswordUserBindings(@Param("userId") long userId, @Param("appId") String appId);

    List<IdentityUser> findActiveSessionUser(@Param("hash") String hash);

    int invalidateUserSessions(@Param("accountId") Long accountId);

    int invalidateSession(@Param("hash") String hash);

    int insertRevocationEvent(@Param("actorId") Long actorId, @Param("subjectId") Long subjectId);

    int updatePassword(@Param("passwordHash") String passwordHash, @Param("userId") long userId);

    int invalidatePasswordUserSessions(@Param("userId") long userId);

    int insertPasswordRevocationEvent(
            @Param("actorId") long actorId, @Param("subjectId") long subjectId);

    List<String> findIdentityRequestHashes(
            @Param("id") Long id, @Param("scope") String scope, @Param("key") String key);

    int insertIdentityRequest(
            @Param("actorId") Long actorId,
            @Param("scope") String scope,
            @Param("key") String key,
            @Param("requestHash") String requestHash);

    int completeIdentityRequest(
            @Param("id") Long id, @Param("scope") String scope, @Param("key") String key);

    /** 读取并锁定 runrun_v2.app_users 的匹配行；锁持有至调用方事务结束。 */
    List<IdentityUser> findUser(@Param("locked") boolean locked, @Param("id") long id);

    int insertSession(
            @Param("userId") long userId,
            @Param("tokenHash") String tokenHash,
            @Param("securityVersion") long securityVersion);

    @SingleRowRequired
    Boolean isCourierEligible(@Param("userId") long userId);

    /** 读取并锁定 runrun_v2.identity_tickets 的匹配行；锁持有至调用方事务结束。 */
    List<Map<String, Object>> findTicketForUpdate(
            @Param("hash") String hash, @Param("kind") String kind);

    int consumeTicket(@Param("hash") String hash);

    List<Object> lockIdentityScope(@Param("key") String key);
}

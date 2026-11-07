package com.zhangxuan.runrunv2.mapper.identity;

import com.zhangxuan.runrunv2.config.SingleRowRequired;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 身份与会话持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface LocalTestMapper {

    void initializeIsolatedFixtures();

    List<Map<String, Object>> findLocalTestAdmin();

    @SingleRowRequired
    Boolean hasLocalTestEnableAudit();

    @SingleRowRequired
    Long countUsers();

    @SingleRowRequired
    Long insertLocalTestAdmin(@Param("passwordHash") String passwordHash);

    int insertWechatIdentity(
            @Param("userId") long userId,
            @Param("appId") String appId,
            @Param("openidHash") String openidHash,
            @Param("openidCiphertext") String openidCiphertext);
}

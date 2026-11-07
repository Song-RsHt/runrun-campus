package com.zhangxuan.runrunv2.mapper.identity;

import com.zhangxuan.runrunv2.config.SingleRowRequired;

import org.apache.ibatis.annotations.Mapper;

/** 身份与会话持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface IdentityStartupMapper {

    @SingleRowRequired
    Boolean isIdentitySchemaInstalled();

    @SingleRowRequired
    Long countUsersWithControlledIdentityFlag();

    @SingleRowRequired
    Long countIdentityTickets();
}

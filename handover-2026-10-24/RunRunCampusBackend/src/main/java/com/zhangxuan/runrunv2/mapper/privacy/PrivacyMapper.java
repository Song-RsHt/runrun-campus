package com.zhangxuan.runrunv2.mapper.privacy;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 保存协议同意的成功请求回执，按账号和版本去重。 */
@Mapper
public interface PrivacyMapper {
    int recordConsent(@Param("userId") long userId, @Param("version") String version);
}

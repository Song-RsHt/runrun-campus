package com.zhangxuan.runrunv2.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** 咨询锁函数原 RowMapper 返回 null；不能将 PostgreSQL void 驱动对象泄漏给调用方。 */
public class VoidFunctionTypeHandler extends BaseTypeHandler<Void> {
    @Override
    public void setNonNullParameter(
            PreparedStatement statement, int index, Void value, JdbcType jdbcType) {
        throw new UnsupportedOperationException("Void function result is read only");
    }

    @Override
    public Void getNullableResult(ResultSet rows, String column) {
        return null;
    }

    @Override
    public Void getNullableResult(ResultSet rows, int column) {
        return null;
    }

    @Override
    public Void getNullableResult(CallableStatement statement, int column) {
        return null;
    }
}

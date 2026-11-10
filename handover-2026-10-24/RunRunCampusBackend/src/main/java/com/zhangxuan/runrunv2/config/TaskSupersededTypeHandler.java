package com.zhangxuan.runrunv2.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 显式投影“任务是否被替代”；保留原 RowMapper 对可空时间的存在性判断。 */
public class TaskSupersededTypeHandler extends BaseTypeHandler<Boolean> {
    @Override
    public void setNonNullParameter(
            PreparedStatement statement, int index, Boolean value, JdbcType jdbcType) {
        throw new UnsupportedOperationException("Task superseded projection is read only");
    }

    @Override
    public Boolean getNullableResult(ResultSet rows, String column) throws SQLException {
        return rows.getObject(column) != null;
    }

    @Override
    public Boolean getNullableResult(ResultSet rows, int column) throws SQLException {
        return rows.getObject(column) != null;
    }

    @Override
    public Boolean getNullableResult(CallableStatement statement, int column) throws SQLException {
        return statement.getObject(column) != null;
    }
}

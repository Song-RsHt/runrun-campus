package com.zhangxuan.runrunv2.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/** 使用原 OrderJson 解析不可变快照，不改变存储结构、Jackson 配置或失败提示。 */
public abstract class OrderSnapshotTypeHandler<T> extends BaseTypeHandler<T> {
    private final OrderJson json;
    private final Class<?> type;

    protected OrderSnapshotTypeHandler(OrderJson json, Class<?> type) {
        this.json = json;
        this.type = type;
    }

    @Override
    public void setNonNullParameter(
            PreparedStatement statement, int index, T value, JdbcType jdbcType)
            throws SQLException {
        statement.setObject(index, json.write(value), Types.OTHER);
    }

    @SuppressWarnings("unchecked")
    private T read(String value) {
        return (T) json.read(value, type);
    }

    @Override
    public T getNullableResult(ResultSet rows, String column) throws SQLException {
        return read(rows.getString(column));
    }

    @Override
    public T getNullableResult(ResultSet rows, int column) throws SQLException {
        return read(rows.getString(column));
    }

    @Override
    public T getNullableResult(CallableStatement statement, int column) throws SQLException {
        return read(statement.getString(column));
    }
}

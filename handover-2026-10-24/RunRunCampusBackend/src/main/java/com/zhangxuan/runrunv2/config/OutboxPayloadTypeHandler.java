package com.zhangxuan.runrunv2.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/** 仅供 outbox XML 的 JSONB 构造映射使用，沿用应用 JSON 配置与快照结构。 */
public class OutboxPayloadTypeHandler extends BaseTypeHandler<Map<String, Object>> {
    private final ObjectMapper json;

    public OutboxPayloadTypeHandler(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void setNonNullParameter(
            PreparedStatement statement, int index, Map<String, Object> value, JdbcType jdbcType)
            throws SQLException {
        try {
            statement.setObject(index, json.writeValueAsString(value), java.sql.Types.OTHER);
        } catch (Exception exception) {
            throw new IllegalStateException("outbox payload unavailable");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String value) {
        try {
            return json.readValue(value, Map.class);
        } catch (Exception exception) {
            throw new IllegalStateException("outbox payload unavailable");
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rows, String column)
            throws SQLException {
        return read(rows.getString(column));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rows, int column) throws SQLException {
        return read(rows.getString(column));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement statement, int column)
            throws SQLException {
        return read(statement.getString(column));
    }
}

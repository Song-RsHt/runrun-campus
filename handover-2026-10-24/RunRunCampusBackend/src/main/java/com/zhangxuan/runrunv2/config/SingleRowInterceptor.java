package com.zhangxuan.runrunv2.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import java.util.Arrays;
import java.util.List;

/** 仅用于 v2 显式标注的旧单行契约，避免迁移后无记录被静默改成 null。 */
@Intercepts(
        @Signature(
                type = Executor.class,
                method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
public class SingleRowInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();
        String id = ((MappedStatement) invocation.getArgs()[0]).getId();
        int separator = id.lastIndexOf('.');
        Class<?> mapper = Class.forName(id.substring(0, separator));
        boolean required =
                Arrays.stream(mapper.getMethods())
                        .anyMatch(
                                method ->
                                        method.getName().equals(id.substring(separator + 1))
                                                && method.isAnnotationPresent(
                                                        SingleRowRequired.class));
        if (required && result instanceof List<?> rows) {
            if (rows.isEmpty()) {
                throw new EmptyResultDataAccessException(1);
            }
            if (rows.size() != 1) {
                throw new IncorrectResultSizeDataAccessException(1, rows.size());
            }
        }
        return result;
    }
}

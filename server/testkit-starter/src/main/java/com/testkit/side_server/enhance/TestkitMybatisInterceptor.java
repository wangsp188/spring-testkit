package com.testkit.side_server.enhance;

import com.testkit.trace.TraceInfo;
import com.testkit.side_server.ReflexUtils;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.ArrayUtil;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Array;
import java.sql.SQLException;
import java.util.*;

/**
 * 拦截所有 select(不包含流式查询) update insert delete
 * 摘要记录
 * 自配置实现部分场景模拟mybatis格式输出sql可用于常见工具解析mybatis日志sql
 * 建议此插件放在插件列表最前面
 *
 * @author shaopeng
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
})
public class TestkitMybatisInterceptor implements Interceptor {

    private static final TestkitMybatisInterceptor bean = new TestkitMybatisInterceptor();

    private static final TestkitMybatisInterceptor sqlBean = new TestkitMybatisInterceptor(true);


    private boolean sql;

    public TestkitMybatisInterceptor() {
    }

    public TestkitMybatisInterceptor(boolean sql) {
        this.sql = sql;
    }

    public static Object pluginExecutor(Object ori) {
        return bean.plugin(ori);
    }

    public static Object pluginExecutorWithSql(Object ori) {
        return sqlBean.plugin(ori);
    }


    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
//        预期判断,理论上应该都满足,第一个参数是MappedStatement,第二个参数是sql入参
        if (args.length < 2 || !(args[0] instanceof MappedStatement)) {
            return invocation.proceed();
        }
        TraceInfo current = TraceInfo.getCurrent();
        if (current == null) {
            return invocation.proceed();
        }
        MappedStatement statement = (MappedStatement) args[0];
        String biz;
        String action;
        try {
//            应该不会有重复的
            String id = statement.getId();
            String[] split = id.split("\\.");
            biz = split[split.length - 2];
            action = split[split.length - 1];
        } catch (Throwable e) {
            biz = statement.getSqlCommandType().toString();
            action = statement.getId();
        }
        current = new TraceInfo(current, "mybatis", biz, action).stepIn();
        try {
            Object result = invocation.proceed();
            Integer row = null;
            Boolean cachedQuery = false;
            if (statement.getSqlCommandType() == SqlCommandType.SELECT) {
                if (result instanceof Collection) {
                    row = ((Collection<?>) result).size();
                } else if (result != null) {
                    row = 1;
                }
//                    缓存sql
                cachedQuery = args.length == 6 && args[4] instanceof CacheKey && args[5] instanceof BoundSql;
            } else if (statement.getSqlCommandType() == SqlCommandType.UPDATE) {
                if (result instanceof Number) {
                    row = ((Number) result).intValue();
                }
            } else if (statement.getSqlCommandType() == SqlCommandType.INSERT) {
                if (result instanceof Number) {
                    row = ((Number) result).intValue();
                }
            } else if (statement.getSqlCommandType() == SqlCommandType.DELETE) {
                if (result instanceof Number) {
                    row = ((Number) result).intValue();
                }
            }
            List<TraceInfo.Digest> digests = new ArrayList<>();
            digests.add(new TraceInfo.Digest("row", row == null ? null : row.toString()));
            digests.add(new TraceInfo.Digest("cachedQuery", cachedQuery.toString()));
            digests.add(new TraceInfo.Digest("sql", buildPreparedSql(statement, args)));
            System.err.println(current.stepOut(result, null, null, digests));
            if (sql) {
                System.err.println(buildSql(result, statement, args, row));
            }

            return result;
        } catch (Throwable e) {
            List<TraceInfo.Digest> digests = new ArrayList<>();
            digests.add(new TraceInfo.Digest("sql", buildPreparedSql(statement, args)));
            System.err.println(current.stepOut(null, e,null,digests));
            throw e;
        }
    }


    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }


    protected String buildPreparedSql(MappedStatement statement, Object[] args) {
        try {
            BoundSql boundSql;
            if (args.length == 6) {
                // 带缓存的 query 方法
                boundSql = (BoundSql) args[5];
            } else {
                // 其他 update 或 query 方法
                boundSql = statement.getBoundSql(args[1]);
            }
            // 从 BoundSql 实例中获取 SQL 语句 sql简单去除换行
            String removed = removeBreakingWhitespace(boundSql.getSql());
            return removed.replace(";", "英文分号");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构建sql输出
     *
     * @param statement
     * @param args
     * @param row
     * @return
     */
    protected String buildSql(Object result, MappedStatement statement, Object[] args, Integer row) {
        try {
            // 获取 BoundSql 对象，包含 SQL 语句和参数映射信息
            BoundSql boundSql;
            if (args.length == 6) {
                // 带缓存的 query 方法
                boundSql = (BoundSql) args[5];
            } else {
                // 其他 update 或 query 方法
                boundSql = statement.getBoundSql(args[1]);
            }
//        count 特殊处理
            Number count = null;
            // 从 BoundSql 实例中获取 SQL 语句 sql简单去除换行
            String sql = removeBreakingWhitespace(boundSql.getSql());
            if (row != null && row == 1 && result instanceof List && ((List<?>) result).size() == 1 && ((List<?>) result).get(0) instanceof Number) {
                //          兼容pageHelper
                if (sql.startsWith("select count(")
                        || sql.startsWith("select COUNT(")
                        || sql.startsWith("SELECT count(")
                        || sql.startsWith("SELECT COUNT(")
                        || statement.getId().endsWith("_COUNT")
                ) {
                    count = (Number) ((List<?>) result).get(0);
                }
            }

            // 获取参数值
            String parameterValues = getParameterValues(boundSql, args[1]);

            String countStr = count == null ? "" : ("\n<==        Row: " + count);
            String rowStr = row == null ? "" : statement.getSqlCommandType() == SqlCommandType.SELECT ? ("\n<==      Total: " + row) : ("\n<==    Updates: " + row);
            return "\n==>  Preparing: " + sql + "\n" + "==> Parameters: " + parameterValues + countStr + rowStr + "\n";
        } catch (Throwable ignore) {
            return null;
        }
    }


    /**
     * org.apache.ibatis.logging.jdbc.BaseJdbcLogger#getParameterValueString()
     *
     * @param boundSql
     * @param parameter
     * @return
     */
    private String getParameterValues(BoundSql boundSql, Object parameter) {
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (parameterMappings == null) {
            return "";
        }
        StringBuilder parameterValues = new StringBuilder();
        for (int i = 0; i < parameterMappings.size(); i++) {
            ParameterMapping parameterMapping = parameterMappings.get(i);
            if (parameterMapping.getMode() != ParameterMode.OUT) {
                Object value = null;
                String propertyName = parameterMapping.getProperty();
                if (boundSql.hasAdditionalParameter(propertyName)) {
                    value = boundSql.getAdditionalParameter(propertyName);
                } else if (parameter != null) {
                    MetaObject metaObject = MetaObject.forObject(parameter, SystemMetaObject.DEFAULT_OBJECT_FACTORY, SystemMetaObject.DEFAULT_OBJECT_WRAPPER_FACTORY, new DefaultReflectorFactory());
                    value = metaObject.getValue(propertyName);
                }

                // 将参数值添加到 StringBuilder 中
                if (i > 0) {
                    parameterValues.append(", ");
                }

                if (value == null) {
                    parameterValues.append("null");
                } else {
                    parameterValues.append(objectValueString(value)).append("(").append(value.getClass().getSimpleName()).append(")");
                }
            }
        }
        return parameterValues.toString();
    }


    /**
     * 去除sql中的换行
     * copy from mybatis
     *
     * @param original
     * @return
     */
    private String removeBreakingWhitespace(String original) {
        StringTokenizer whitespaceStripper = new StringTokenizer(original);
        StringBuilder builder = new StringBuilder();
        while (whitespaceStripper.hasMoreTokens()) {
            builder.append(whitespaceStripper.nextToken());
            builder.append(" ");
        }
        return builder.toString();
    }

    /**
     * 格式化对象
     * 模拟mybatis
     *
     * @param value
     * @return
     */
    protected String objectValueString(Object value) {
        if (value instanceof Array) {
            try {
                return ArrayUtil.toString(((Array) value).getArray());
            } catch (SQLException e) {
                return value.toString();
            }
        } else if (value instanceof Collection || value instanceof Map) {
            try {
                return ReflexUtils.MAPPER.writeValueAsString(value);
            } catch (Throwable e) {
                return value.toString();
            }
        } else if (value instanceof Enum || value.getClass().getPackage().getName().startsWith("java.")) {
            return value.toString();
        }
        try {
            return ReflexUtils.MAPPER.writeValueAsString(value);
        } catch (Throwable e) {
            return value.toString();
        }
    }
}

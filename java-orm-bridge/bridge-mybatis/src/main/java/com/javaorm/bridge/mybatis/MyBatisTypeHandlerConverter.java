package com.javaorm.bridge.mybatis;

import com.javaorm.core.converter.ValueConverter;
import com.javaorm.core.metadata.ColumnMetadata;
import org.apache.ibatis.type.TypeHandler;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 将 MyBatis 的 {@link TypeHandler} 适配为 JavaORM 的 {@link ValueConverter}。
 * <p>
 * 通过零内存分配的 ThreadLocal 上下文代理，拦截 PreparedStatement 设置与 ResultSet 读取，
 * 使得无论是 MyBatis 内置的类型处理器、用户自定义的复杂对象 Handler（如 JSON、加密等），
 * 还是各类枚举处理器（如 MybatisEnumTypeHandler、EnumTypeHandler 等），
 * 均可在 JavaORM 的写入序列化与查询反序列化中 100% 无缝复用。
 * </p>
 *
 * @param <T> Java 实体属性类型
 */
public class MyBatisTypeHandlerConverter<T> implements ValueConverter<T, Object> {

    protected final TypeHandler<T> typeHandler;
    protected final Class<?> javaType;

    public MyBatisTypeHandlerConverter(TypeHandler<T> typeHandler) {
        this(typeHandler, null);
    }

    public MyBatisTypeHandlerConverter(TypeHandler<T> typeHandler, Class<?> javaType) {
        if (typeHandler == null) {
            throw new IllegalArgumentException("typeHandler must not be null");
        }
        this.typeHandler = typeHandler;
        this.javaType = javaType;
    }

    public TypeHandler<T> getTypeHandler() {
        return typeHandler;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    /**
     * 单线程绑定的执行上下文，避免高频并发下重复创建 JDK 动态代理对象的性能开销。
     */
    private static final ThreadLocal<CaptureContext> CONTEXT_HOLDER = ThreadLocal.withInitial(CaptureContext::new);

    private static class CaptureContext {
        Object capturedValue;
        Object currentDbValue;
        String currentColumnName;

        final PreparedStatement proxyPs;
        final ResultSet proxyRs;

        CaptureContext() {
            this.proxyPs = (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if (methodName.startsWith("set") && args != null && args.length >= 2) {
                            if ("setNull".equals(methodName)) {
                                this.capturedValue = null;
                            } else {
                                this.capturedValue = args[1];
                            }
                        }
                        return defaultValue(method.getReturnType());
                    }
            );

            this.proxyRs = (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("wasNull".equals(methodName)) {
                            return this.currentDbValue == null;
                        }
                        if (this.currentDbValue == null) {
                            return defaultValue(method.getReturnType());
                        }
                        if ("getObject".equals(methodName)) {
                            return this.currentDbValue;
                        }
                        if ("getString".equals(methodName)) {
                            return this.currentDbValue.toString();
                        }
                        if ("getInt".equals(methodName)) {
                            return this.currentDbValue instanceof Number
                                    ? ((Number) this.currentDbValue).intValue()
                                    : Integer.parseInt(this.currentDbValue.toString().trim());
                        }
                        if ("getLong".equals(methodName)) {
                            return this.currentDbValue instanceof Number
                                    ? ((Number) this.currentDbValue).longValue()
                                    : Long.parseLong(this.currentDbValue.toString().trim());
                        }
                        if ("getDouble".equals(methodName)) {
                            return this.currentDbValue instanceof Number
                                    ? ((Number) this.currentDbValue).doubleValue()
                                    : Double.parseDouble(this.currentDbValue.toString().trim());
                        }
                        if ("getFloat".equals(methodName)) {
                            return this.currentDbValue instanceof Number
                                    ? ((Number) this.currentDbValue).floatValue()
                                    : Float.parseFloat(this.currentDbValue.toString().trim());
                        }
                        if ("getBigDecimal".equals(methodName)) {
                            return this.currentDbValue instanceof BigDecimal
                                    ? this.currentDbValue
                                    : new BigDecimal(this.currentDbValue.toString().trim());
                        }
                        if ("getBoolean".equals(methodName)) {
                            if (this.currentDbValue instanceof Boolean) return this.currentDbValue;
                            if (this.currentDbValue instanceof Number) return ((Number) this.currentDbValue).intValue() != 0;
                            return Boolean.parseBoolean(this.currentDbValue.toString().trim());
                        }
                        if ("getBytes".equals(methodName)) {
                            if (this.currentDbValue instanceof byte[]) return this.currentDbValue;
                            return this.currentDbValue.toString().getBytes(StandardCharsets.UTF_8);
                        }
                        if ("getByte".equals(methodName)) {
                            return this.currentDbValue instanceof Number
                                    ? ((Number) this.currentDbValue).byteValue()
                                    : Byte.parseByte(this.currentDbValue.toString().trim());
                        }
                        if ("getShort".equals(methodName)) {
                            return this.currentDbValue instanceof Number
                                    ? ((Number) this.currentDbValue).shortValue()
                                    : Short.parseShort(this.currentDbValue.toString().trim());
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private static Object defaultValue(Class<?> returnType) {
            if (returnType == void.class) return null;
            if (returnType == boolean.class) return false;
            if (returnType == byte.class) return (byte) 0;
            if (returnType == short.class) return (short) 0;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            if (returnType == float.class) return 0.0f;
            if (returnType == double.class) return 0.0d;
            return null;
        }
    }

    @Override
    public Object serialize(T value, ColumnMetadata metadata) {
        if (value == null) {
            return null;
        }
        CaptureContext ctx = CONTEXT_HOLDER.get();
        ctx.capturedValue = null;
        try {
            typeHandler.setParameter(ctx.proxyPs, 1, value, null);
            return ctx.capturedValue;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to serialize value via MyBatis TypeHandler: " + typeHandler.getClass().getName(), e);
        } finally {
            ctx.capturedValue = null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(Object dbValue, ColumnMetadata metadata) {
        if (dbValue == null) {
            return null;
        }
        CaptureContext ctx = CONTEXT_HOLDER.get();
        ctx.currentDbValue = dbValue;
        String colName = (metadata != null && metadata.getColumnName() != null) ? metadata.getColumnName() : "column";
        ctx.currentColumnName = colName;
        try {
            try {
                return typeHandler.getResult(ctx.proxyRs, colName);
            } catch (Exception ex) {
                return typeHandler.getResult(ctx.proxyRs, 1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to deserialize column '" + colName + "' via MyBatis TypeHandler: " + typeHandler.getClass().getName(), e);
        } finally {
            ctx.currentDbValue = null;
            ctx.currentColumnName = null;
        }
    }
}

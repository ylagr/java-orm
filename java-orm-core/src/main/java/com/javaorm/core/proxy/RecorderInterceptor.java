package com.javaorm.core.proxy;

import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.metadata.RelationMetadata;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import java.lang.reflect.Method;

/**
 * ByteBuddy 动态代理调用拦截器（核心事件传感器）。
 * 拦截 getter 调用，记录属性/关联路径，返回下级代理或安全默认值。
 */
public class RecorderInterceptor {

    @RuntimeType
    public static Object intercept(@Origin Method method,
                                   @This Object proxy,
                                   @AllArguments Object[] args) throws Exception {
        String methodName = method.getName();

        // 基础 Object 方法放行或简易实现
        if ("toString".equals(methodName)) {
            return "JavaOrmProxy[" + proxy.getClass().getSuperclass().getSimpleName() + "]";
        }
        if ("hashCode".equals(methodName)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(methodName)) {
            return args != null && args.length > 0 && proxy == args[0];
        }

        // 解析属性名
        String propName = extractPropertyName(methodName);
        if (propName == null) {
            return getDefaultValue(method.getReturnType());
        }

        Class<?> entityClass = proxy.getClass().getSuperclass();
        EntityMetadata metadata = EntityMetadataManager.get(entityClass);

        if (RecordingContext.get() == null) {
            RecordingContext.start(entityClass);
        }

        // 检查该属性名是否为关联关系
        RelationMetadata relation = metadata.getRelation(propName);
        if (relation == null && methodName.startsWith("is")) {
            // 特殊容错：字段名直接是 isDeleted 的情况
            relation = metadata.getRelation(methodName);
            if (relation != null) {
                propName = methodName;
            }
        }

        if (relation != null) {
            // 记录关联跳转路径
            RecordingContext.recordRelation(propName);
            Object targetProxy = ProxyFactory.getProxy(relation.getTargetEntityClass());
            Class<?> returnType = method.getReturnType();
            if (java.util.List.class.isAssignableFrom(returnType)) {
                return java.util.Collections.singletonList(targetProxy);
            }
            if (java.util.Set.class.isAssignableFrom(returnType)) {
                return java.util.Collections.singleton(targetProxy);
            }
            if (java.util.Collection.class.isAssignableFrom(returnType)) {
                return java.util.Collections.singletonList(targetProxy);
            }
            return targetProxy;
        }

        // 否则为终端叶子属性
        Class<?> returnType = method.getReturnType();
        RecordingContext.recordTerminal(propName, returnType);
        return getDefaultValue(returnType);
    }

    private static String extractPropertyName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return null;
    }

    private static Object getDefaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0d;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }
}

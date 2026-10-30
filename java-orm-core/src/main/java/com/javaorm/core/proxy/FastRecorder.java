package com.javaorm.core.proxy;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 零反射极速属性录制器（由硬编码字节码直接调用）。
 * 所有属性名、实体类和返回类型均由编译器/生成器固化为入参常量，运行期零反射、零字符串截取、零 getSuperclass。
 */
public class FastRecorder {

    /**
     * 叶子属性录制（如 age, studentName）。
     */
    public static void recordTerminal(Object proxy, Class<?> entityClass, String propName, Class<?> returnType) {
        RecordingContext.ensureStarted(entityClass);
        String explicitAlias = RecordingContext.getProxyAlias(proxy);
        if (explicitAlias != null) {
            PropertyPath cur = RecordingContext.get();
            if (cur != null) {
                cur.setExplicitAlias(explicitAlias);
            }
        }
        RecordingContext.recordTerminal(propName, returnType);
    }

    /**
     * 关联导航属性录制（如 teacher, students）。
     */
    public static Object recordRelation(Object proxy, Class<?> entityClass, String propName, Class<?> targetClass, Class<?> returnType) {
        RecordingContext.ensureStarted(entityClass);
        String explicitAlias = RecordingContext.getProxyAlias(proxy);
        if (explicitAlias != null) {
            PropertyPath cur = RecordingContext.get();
            if (cur != null) {
                cur.setExplicitAlias(explicitAlias);
            }
        }
        RecordingContext.recordRelation(propName);
        Object targetProxy = ProxyFactory.getProxy(targetClass);
        if (List.class.isAssignableFrom(returnType)) {
            return Collections.singletonList(targetProxy);
        }
        if (Set.class.isAssignableFrom(returnType)) {
            return Collections.singleton(targetProxy);
        }
        if (Collection.class.isAssignableFrom(returnType)) {
            return Collections.singletonList(targetProxy);
        }
        return targetProxy;
    }

    /**
     * ValueObject 属性录制（如 address, area）。
     */
    public static Object recordValueObject(Object proxy, Class<?> entityClass, String propName, Class<?> voClass) {
        RecordingContext.ensureStarted(entityClass);
        String explicitAlias = RecordingContext.getProxyAlias(proxy);
        if (explicitAlias != null) {
            PropertyPath cur = RecordingContext.get();
            if (cur != null) {
                cur.setExplicitAlias(explicitAlias);
            }
        }
        RecordingContext.recordValueObject(propName, voClass);
        return ProxyFactory.getProxy(voClass);
    }

    /**
     * 代理对象的 equals 比较：避免代理类重写 equals 时触发实体内部 getter 调用。
     */
    public static boolean proxyEquals(Object thisProxy, Object other) {
        return thisProxy == other;
    }
}

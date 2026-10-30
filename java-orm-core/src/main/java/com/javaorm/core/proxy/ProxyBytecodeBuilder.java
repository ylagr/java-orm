package com.javaorm.core.proxy;

import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.metadata.RelationMetadata;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 统一的字节码代理构建器。
 * 核心方法 buildUnloaded 产出纯净的 DynamicType.Unloaded，由 JIT（开发期内存加载）与 AOT（构建期物理落盘）100% 共同复用。
 */
public class ProxyBytecodeBuilder {

    public static final String PROXY_CLASS_SUFFIX = "$JavaOrmFastRecorder";
    private static final ByteBuddy BYTE_BUDDY = new ByteBuddy();

    private static final Method RECORD_TERMINAL_METHOD;
    private static final Method RECORD_RELATION_METHOD;
    private static final Method PROXY_EQUALS_METHOD;
    private static final Method IDENTITY_HASH_CODE_METHOD;

    static {
        try {
            RECORD_TERMINAL_METHOD = FastRecorder.class.getMethod("recordTerminal",
                    Object.class, Class.class, String.class, Class.class);
            RECORD_RELATION_METHOD = FastRecorder.class.getMethod("recordRelation",
                    Object.class, Class.class, String.class, Class.class, Class.class);
            PROXY_EQUALS_METHOD = FastRecorder.class.getMethod("proxyEquals",
                    Object.class, Object.class);
            IDENTITY_HASH_CODE_METHOD = System.class.getMethod("identityHashCode",
                    Object.class);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static String getProxyClassName(Class<?> entityClass) {
        return entityClass.getName() + PROXY_CLASS_SUFFIX;
    }

    /**
     * 核心复用方法：构建未加载的字节码（Unloaded）。
     * 针对实体所有 Getter 织入写死常量的 FastRecorder 方法调用，并生成零反射实例工厂。
     */
    public static <T> DynamicType.Unloaded<T> buildUnloaded(Class<T> entityClass) {
        try {
            DynamicType.Builder<T> builder = BYTE_BUDDY
                    .subclass(entityClass)
                    .name(getProxyClassName(entityClass))
                    // 1. 实现通用工厂接口，供别名等场景纯接口虚方法分发
                    .implement(ProxyInstanceFactory.class)
                    .method(ElementMatchers.named("createNew").and(ElementMatchers.takesArguments(0)))
                    .intercept(ConstructSelfImplementation.INSTANCE)
                    // 2. 生成静态单例创建方法，供 MethodHandle 或 AOT Registry 直接调用，0 反射
                    .defineMethod("createInstance", entityClass, Modifier.PUBLIC | Modifier.STATIC)
                    .intercept(ConstructSelfImplementation.INSTANCE);

            // 3. 基础 Object 方法拦截
            builder = builder
                    .method(ElementMatchers.named("toString").and(ElementMatchers.takesArguments(0)))
                    .intercept(FixedValue.value("JavaOrmProxy[" + entityClass.getSimpleName() + "]"))
                    .method(ElementMatchers.named("hashCode").and(ElementMatchers.takesArguments(0)))
                    .intercept(MethodCall.invoke(IDENTITY_HASH_CODE_METHOD).withThis())
                    .method(ElementMatchers.named("equals").and(ElementMatchers.takesArguments(Object.class)))
                    .intercept(MethodCall.invoke(PROXY_EQUALS_METHOD).withThis().withArgument(0));

            // 4. 扫描所有 Getter 方法并分别织入硬编码指令
            for (Method method : entityClass.getMethods()) {
                if (!isGetter(method) || method.getDeclaringClass() == Object.class) {
                    continue;
                }

                String propName = extractPropertyName(method, entityClass);
                if (propName == null) {
                    continue;
                }

                // 检查是否为关联关系（如 getTeacher, getStudents）
                RelationMetadata relation = EntityMetadataManager.getRelation(entityClass, propName);
                if (relation == null && method.getName().startsWith("is")) {
                    relation = EntityMetadataManager.getRelation(entityClass, method.getName());
                    if (relation != null) {
                        propName = method.getName();
                    }
                }

                if (relation != null) {
                    // A. 关联导航属性：写死目标类与属性名
                    Class<?> targetClass = relation.getTargetEntityClass();
                    Class<?> returnType = method.getReturnType();
                    builder = builder.method(ElementMatchers.is(method))
                            .intercept(
                                    MethodCall.invoke(RECORD_RELATION_METHOD)
                                            .withThis()
                                            .with(entityClass, propName, targetClass, returnType)
                                            .withAssigner(net.bytebuddy.implementation.bytecode.assign.Assigner.DEFAULT, net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC)
                            );
                } else {
                    // B. 叶子终端列属性：写死实体类、属性名和类型，直接返回基本类型/null默认值
                    Class<?> returnType = method.getReturnType();
                    Implementation returnImpl = getReturnValueImplementation(returnType);
                    builder = builder.method(ElementMatchers.is(method))
                            .intercept(
                                    MethodCall.invoke(RECORD_TERMINAL_METHOD)
                                            .withThis()
                                            .with(entityClass, propName, returnType)
                                            .andThen(returnImpl)
                            );
                }
            }

            return builder.make();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build proxy bytecode for entity: " + entityClass.getName(), e);
        }
    }

    private static boolean isGetter(Method method) {
        if (Modifier.isStatic(method.getModifiers()) || method.getParameterTypes().length > 0) {
            return false;
        }
        if (method.getReturnType() == void.class) {
            return false;
        }
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) {
            return true;
        }
        if (name.startsWith("is") && name.length() > 2
                && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            return true;
        }
        return false;
    }

    private static String extractPropertyName(Method method, Class<?> entityClass) {
        String methodName = method.getName();
        String candidate = null;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            candidate = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            candidate = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        if (candidate == null) {
            return null;
        }
        // 特殊容错：如果类中定义了与方法名相同的属性（如 isDeleted），优先使用该属性名
        if (hasField(entityClass, methodName)) {
            return methodName;
        }
        return candidate;
    }

    private static boolean hasField(Class<?> clazz, String fieldName) {
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            try {
                cur.getDeclaredField(fieldName);
                return true;
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        return false;
    }

    private static Implementation getReturnValueImplementation(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return FixedValue.nullValue();
        }
        if (returnType == boolean.class) {
            return FixedValue.value(false);
        }
        if (returnType == int.class) {
            return FixedValue.value(0);
        }
        if (returnType == long.class) {
            return FixedValue.value(0L);
        }
        if (returnType == double.class) {
            return FixedValue.value(0.0d);
        }
        if (returnType == float.class) {
            return FixedValue.value(0.0f);
        }
        if (returnType == short.class) {
            return FixedValue.value((short) 0);
        }
        if (returnType == byte.class) {
            return FixedValue.value((byte) 0);
        }
        if (returnType == char.class) {
            return FixedValue.value('\0');
        }
        return FixedValue.nullValue();
    }
}

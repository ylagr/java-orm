package com.javaorm.core.fastbean;

import com.javaorm.core.annotation.JoColumn;

import java.lang.annotation.Annotation;
import java.lang.invoke.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体与 DTO 高性能 Lambda 动态调用工厂（基于 LambdaMetafactory 与 MethodHandle）。
 * 完全消除反射开销，生成 JVM 原生字节码级别的快速方法调用（KISS 原则对标 EasyQuery）。
 */
public class LambdaFactory {

    private static final Map<Class<?>, FastBean<?>> BEAN_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> FastBean<T> getFastBean(Class<T> clazz) {
        if (clazz == null) return null;
        return (FastBean<T>) BEAN_CACHE.computeIfAbsent(clazz, LambdaFactory::buildFastBean);
    }

    /**
     * 构建指定类的 FastBean 元信息与 lambda 快速调用器
     */
    public static <T> FastBean<T> buildFastBean(Class<T> clazz) {
        FastBeanCreator<T> creator = createConstructor(clazz);
        Map<String, FastPropertySetter> setters = new HashMap<>();
        Map<String, FastPropertyGetter> getters = new HashMap<>();
        Map<String, Class<?>> propertyTypes = new HashMap<>();

        Map<String, Field> fields = new HashMap<>();
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) {
                    continue;
                }
                fields.putIfAbsent(f.getName(), f);
            }
            curr = curr.getSuperclass();
        }

        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            String propName = entry.getKey();
            Field field = entry.getValue();
            Class<?> propType = field.getType();

            Method getter = findGetter(clazz, propName, propType);
            Method setter = findSetter(clazz, propName, propType);

            FastPropertySetter fastSetter = createSetter(clazz, setter, field);
            FastPropertyGetter fastGetter = createGetter(clazz, getter, field);

            registerPropertyAliases(propName, field, fastSetter, fastGetter, propType, setters, getters, propertyTypes);
        }

        // 亦扫描只有 Getter/Setter 没有对应字段的属性
        for (Method m : clazz.getMethods()) {
            if (Modifier.isStatic(m.getModifiers())) continue;
            String name = m.getName();
            if (name.startsWith("set") && name.length() > 3 && m.getParameterCount() == 1) {
                String propName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                if (!fields.containsKey(propName)) {
                    Class<?> pType = m.getParameterTypes()[0];
                    Method getter = findGetter(clazz, propName, pType);
                    FastPropertySetter fastSetter = createSetter(clazz, m, null);
                    FastPropertyGetter fastGetter = createGetter(clazz, getter, null);
                    registerPropertyAliases(propName, null, fastSetter, fastGetter, pType, setters, getters, propertyTypes);
                }
            }
        }

        return new FastBean<>(clazz, creator, setters, getters, propertyTypes);
    }

    private static void registerPropertyAliases(String propName, Field field,
                                                FastPropertySetter setter, FastPropertyGetter getter, Class<?> propType,
                                                Map<String, FastPropertySetter> setters,
                                                Map<String, FastPropertyGetter> getters,
                                                Map<String, Class<?>> propertyTypes) {
        String lower = propName.toLowerCase();
        String noUnderscore = lower.replace("_", "");
        String underline = toUnderline(propName);

        if (setter != null) {
            setters.put(lower, setter);
            setters.put(noUnderscore, setter);
            setters.put(underline, setter);
        }
        if (getter != null) {
            getters.put(lower, getter);
            getters.put(noUnderscore, getter);
            getters.put(underline, getter);
        }
        propertyTypes.put(lower, propType);
        propertyTypes.put(noUnderscore, propType);
        propertyTypes.put(underline, propType);

        if (field != null) {
            for (Annotation ann : field.getAnnotations()) {
                if (ann instanceof JoColumn) {
                    JoColumn jc = (JoColumn) ann;
                    String cName = !jc.value().isEmpty() ? jc.value() : jc.name();
                    if (!cName.isEmpty()) {
                        String cLower = cName.toLowerCase();
                        if (setter != null) setters.put(cLower, setter);
                        if (getter != null) getters.put(cLower, getter);
                        propertyTypes.put(cLower, propType);
                    }
                } else if (ann.annotationType().getName().endsWith(".Column")) {
                    try {
                        Method nameMethod = ann.annotationType().getMethod("name");
                        String n = (String) nameMethod.invoke(ann);
                        if (n != null && !n.isEmpty()) {
                            String cLower = n.toLowerCase();
                            if (setter != null) setters.put(cLower, setter);
                            if (getter != null) getters.put(cLower, getter);
                            propertyTypes.put(cLower, propType);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    /**
     * 通过 LambdaMetafactory 生成对象的构造器创建器（零反射）
     */
    @SuppressWarnings("unchecked")
    public static <T> FastBeanCreator<T> createConstructor(Class<T> clazz) {
        if (clazz == null) return null;
        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle mh = lookup.unreflectConstructor(ctor);
            CallSite site = LambdaMetafactory.metafactory(
                    lookup,
                    "create",
                    MethodType.methodType(FastBeanCreator.class),
                    MethodType.methodType(Object.class),
                    mh,
                    MethodType.methodType(clazz)
            );
            return (FastBeanCreator<T>) site.getTarget().invokeExact();
        } catch (Throwable t) {
            try {
                Constructor<T> ctor = clazz.getDeclaredConstructor();
                ctor.setAccessible(true);
                return () -> {
                    try {
                        return ctor.newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to instantiate " + clazz.getName(), e);
                    }
                };
            } catch (Exception e) {
                return () -> {
                    throw new RuntimeException("No default constructor found for " + clazz.getName(), e);
                };
            }
        }
    }

    /**
     * 通过 LambdaMetafactory / MethodHandle 生成属性 Setter 调用器（零反射）
     */
    public static FastPropertySetter createSetter(Class<?> clazz, Method setterMethod, Field field) {
        if (setterMethod != null) {
            try {
                setterMethod.setAccessible(true);
                Class<?> paramType = setterMethod.getParameterTypes()[0];
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle mh = lookup.unreflect(setterMethod);

                Class<?> targetClass = setterMethod.getDeclaringClass();
                if (!paramType.isPrimitive()) {
                    CallSite site = LambdaMetafactory.metafactory(
                            lookup,
                            "set",
                            MethodType.methodType(FastPropertySetter.class),
                            MethodType.methodType(void.class, Object.class, Object.class),
                            mh,
                            MethodType.methodType(void.class, targetClass, paramType)
                    );
                    return (FastPropertySetter) site.getTarget().invokeExact();
                } else {
                    // 基本类型适配（自动解包 unboxing）
                    MethodHandle adapted = mh.asType(MethodType.methodType(void.class, Object.class, Object.class));
                    return (target, value) -> {
                        try {
                            adapted.invokeExact(target, value);
                        } catch (Throwable t) {
                            throw new RuntimeException("Failed to invoke setter: " + setterMethod.getName(), t);
                        }
                    };
                }
            } catch (Throwable t) {
                return (target, value) -> {
                    try {
                        setterMethod.invoke(target, value);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to invoke setter: " + setterMethod.getName(), e);
                    }
                };
            }
        }

        if (field != null) {
            field.setAccessible(true);
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle mh = lookup.unreflectSetter(field);
                MethodHandle adapted = mh.asType(MethodType.methodType(void.class, Object.class, Object.class));
                return (target, value) -> {
                    try {
                        adapted.invokeExact(target, value);
                    } catch (Throwable t) {
                        try {
                            field.set(target, value);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
            } catch (Throwable t) {
                return (target, value) -> {
                    try {
                        field.set(target, value);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
            }
        }

        return null;
    }

    /**
     * 通过 LambdaMetafactory / MethodHandle 生成属性 Getter 调用器（零反射）
     */
    public static FastPropertyGetter createGetter(Class<?> clazz, Method getterMethod, Field field) {
        if (getterMethod != null) {
            try {
                getterMethod.setAccessible(true);
                Class<?> returnType = getterMethod.getReturnType();
                Class<?> targetClass = getterMethod.getDeclaringClass();
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle mh = lookup.unreflect(getterMethod);

                if (!returnType.isPrimitive()) {
                    CallSite site = LambdaMetafactory.metafactory(
                            lookup,
                            "get",
                            MethodType.methodType(FastPropertyGetter.class),
                            MethodType.methodType(Object.class, Object.class),
                            mh,
                            MethodType.methodType(returnType, targetClass)
                    );
                    return (FastPropertyGetter) site.getTarget().invokeExact();
                } else {
                    // 基本类型适配（自动装箱 boxing）
                    MethodHandle adapted = mh.asType(MethodType.methodType(Object.class, Object.class));
                    return target -> {
                        try {
                            return adapted.invokeExact(target);
                        } catch (Throwable t) {
                            throw new RuntimeException("Failed to invoke getter: " + getterMethod.getName(), t);
                        }
                    };
                }
            } catch (Throwable t) {
                return target -> {
                    try {
                        return getterMethod.invoke(target);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to invoke getter: " + getterMethod.getName(), e);
                    }
                };
            }
        }

        if (field != null) {
            field.setAccessible(true);
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle mh = lookup.unreflectGetter(field);
                MethodHandle adapted = mh.asType(MethodType.methodType(Object.class, Object.class));
                return target -> {
                    try {
                        return adapted.invokeExact(target);
                    } catch (Throwable t) {
                        try {
                            return field.get(target);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
            } catch (Throwable t) {
                return target -> {
                    try {
                        return field.get(target);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
            }
        }

        return null;
    }

    public static Method findGetter(Class<?> clazz, String propName, Class<?> propType) {
        String capitalized = Character.toUpperCase(propName.charAt(0)) + propName.substring(1);
        try {
            return clazz.getMethod("get" + capitalized);
        } catch (NoSuchMethodException e) {
            if (propType == boolean.class || propType == Boolean.class) {
                try {
                    return clazz.getMethod("is" + capitalized);
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return null;
    }

    public static Method findSetter(Class<?> clazz, String propName, Class<?> propType) {
        String capitalized = Character.toUpperCase(propName.charAt(0)) + propName.substring(1);
        try {
            return clazz.getMethod("set" + capitalized, propType);
        } catch (NoSuchMethodException e) {
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals("set" + capitalized) && m.getParameterCount() == 1) {
                    return m;
                }
            }
        }
        return null;
    }

    private static String toUnderline(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

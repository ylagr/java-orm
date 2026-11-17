package com.javaorm.core.converter;

import java.lang.reflect.Type;

/**
 * JSON 序列化与反序列化 SPI 接口。
 * 允许无缝插拔不同的 JSON 库（如 Jackson, Fastjson2, Gson）。
 */
public interface JsonProvider {

    /**
     * 将 Java 对象序列化为 JSON 字符串。
     *
     * @param obj Java 对象
     * @return JSON 字符串
     */
    String toJson(Object obj);

    /**
     * 将 JSON 字符串反序列化为 Java 对象（支持泛型 Type）。
     *
     * @param json JSON 字符串
     * @param type 目标类型，可以是 Class<?> 或 ParameterizedType
     * @param <T>  目标对象泛型
     * @return 还原的 Java 对象
     */
    <T> T fromJson(String json, Type type);
}

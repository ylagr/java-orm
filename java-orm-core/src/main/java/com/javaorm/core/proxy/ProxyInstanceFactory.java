package com.javaorm.core.proxy;

/**
 * 代理实例工厂接口。
 * 生成的实体代理子类实现此接口，用于零反射极速实例化（如创建自关联/多表别名的新代理对象）。
 */
public interface ProxyInstanceFactory<T> {

    /**
     * 创建该代理类自身的一个新实例（原生 new 指令分发，0 反射）。
     *
     * @return 代理新实例
     */
    T createNew();
}

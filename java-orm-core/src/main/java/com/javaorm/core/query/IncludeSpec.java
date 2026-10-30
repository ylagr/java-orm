package com.javaorm.core.query;

import com.javaorm.core.proxy.PropertyPath;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 关联加载规范定义（包含关联属性路径、目标实体查询过滤、排序及下级递归加载配置）。
 */
public class IncludeSpec {

    private final PropertyPath path;
    private final Consumer<SelectBuilder<?>> queryConfig;

    public IncludeSpec(PropertyPath path) {
        this(path, (Consumer<SelectBuilder<?>>) null);
    }

    public IncludeSpec(PropertyPath path, Consumer<SelectBuilder<?>> queryConfig) {
        this.path = path;
        this.queryConfig = queryConfig;
    }

    @SuppressWarnings("unchecked")
    public static <R> IncludeSpec ofBi(PropertyPath path, BiConsumer<SelectBuilder<R>, R> biConfig) {
        if (biConfig == null) {
            return new IncludeSpec(path);
        }
        return new IncludeSpec(path, q -> {
            SelectBuilder<R> targetQ = (SelectBuilder<R>) q;
            R proxy = targetQ.proxy();
            biConfig.accept(targetQ, proxy);
        });
    }

    public PropertyPath getPath() {
        return path;
    }

    public Consumer<SelectBuilder<?>> getQueryConfig() {
        return queryConfig;
    }

    public void applyConfig(SelectBuilder<?> targetQuery) {
        if (queryConfig != null && targetQuery != null) {
            queryConfig.accept(targetQuery);
        }
    }
}

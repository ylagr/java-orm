package com.javaorm.core.config;

import com.javaorm.core.connection.ConnectionProvider;
import com.javaorm.core.tenant.TenantContext;

import java.util.function.Supplier;

/**
 * 全局 ORM 配置
 */
public class OrmConfig {

    private NameConversion nameConversion = new UnderlinedNameConversion();
    private boolean printSql = false;
    private String defaultLogicDeleteProperty = null; // 全局逻辑删除字段名（若实体无注解时生效）
    private Object defaultLogicDeleteNormalValue = 0;
    private Object defaultLogicDeleteDeletedValue = 1;
    private ConnectionProvider connectionProvider = null;
    private Supplier<Object> tenantIdProvider = TenantContext::getTenantId;
    private boolean tenantStrict = false;

    public NameConversion getNameConversion() {
        return nameConversion;
    }

    public OrmConfig setNameConversion(NameConversion nameConversion) {
        if (nameConversion != null) {
            this.nameConversion = nameConversion;
        }
        return this;
    }

    public boolean isPrintSql() {
        return printSql;
    }

    public OrmConfig setPrintSql(boolean printSql) {
        this.printSql = printSql;
        return this;
    }

    public String getDefaultLogicDeleteProperty() {
        return defaultLogicDeleteProperty;
    }

    public OrmConfig setDefaultLogicDeleteProperty(String defaultLogicDeleteProperty) {
        this.defaultLogicDeleteProperty = defaultLogicDeleteProperty;
        return this;
    }

    public Object getDefaultLogicDeleteNormalValue() {
        return defaultLogicDeleteNormalValue;
    }

    public OrmConfig setDefaultLogicDeleteNormalValue(Object defaultLogicDeleteNormalValue) {
        this.defaultLogicDeleteNormalValue = defaultLogicDeleteNormalValue;
        return this;
    }

    public Object getDefaultLogicDeleteDeletedValue() {
        return defaultLogicDeleteDeletedValue;
    }

    public OrmConfig setDefaultLogicDeleteDeletedValue(Object defaultLogicDeleteDeletedValue) {
        this.defaultLogicDeleteDeletedValue = defaultLogicDeleteDeletedValue;
        return this;
    }

    public ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    public OrmConfig setConnectionProvider(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        return this;
    }

    public Supplier<Object> getTenantIdProvider() {
        return tenantIdProvider;
    }

    public OrmConfig setTenantIdProvider(Supplier<Object> tenantIdProvider) {
        this.tenantIdProvider = tenantIdProvider;
        return this;
    }

    public boolean isTenantStrict() {
        return tenantStrict;
    }

    public OrmConfig setTenantStrict(boolean tenantStrict) {
        this.tenantStrict = tenantStrict;
        return this;
    }

    public Object getCurrentTenantId() {
        return tenantIdProvider != null ? tenantIdProvider.get() : TenantContext.getTenantId();
    }

    private final java.util.Map<Class<?>, com.javaorm.core.metadata.EntityMetadata> metadataCache = new java.util.concurrent.ConcurrentHashMap<>();

    public com.javaorm.core.metadata.EntityMetadata getMetadata(Class<?> entityClass) {
        if (entityClass == null) return null;
        return metadataCache.computeIfAbsent(entityClass, c -> com.javaorm.core.metadata.EntityMetadataManager.get(c, this));
    }

    private final java.util.List<com.javaorm.core.converter.AutoValueConverter<?, ?>> autoConverters = new java.util.concurrent.CopyOnWriteArrayList<>();

    public java.util.List<com.javaorm.core.converter.AutoValueConverter<?, ?>> getAutoConverters() {
        return autoConverters;
    }

    public OrmConfig addAutoConverter(com.javaorm.core.converter.AutoValueConverter<?, ?> converter) {
        if (converter != null) {
            this.autoConverters.add(converter);
        }
        return this;
    }

    private com.javaorm.core.executor.SqlExecutor sqlExecutor = null;

    public com.javaorm.core.executor.SqlExecutor getSqlExecutor() {
        return sqlExecutor;
    }

    public OrmConfig setSqlExecutor(com.javaorm.core.executor.SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
        return this;
    }

    private com.javaorm.core.datasource.DataSourceManager dataSourceManager = null;

    public com.javaorm.core.datasource.DataSourceManager getDataSourceManager() {
        return dataSourceManager;
    }

    public OrmConfig setDataSourceManager(com.javaorm.core.datasource.DataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
        return this;
    }

    private final com.javaorm.core.interceptor.InterceptorChain interceptorChain = new com.javaorm.core.interceptor.InterceptorChain();

    public com.javaorm.core.interceptor.InterceptorChain getInterceptorChain() {
        return interceptorChain;
    }

    public OrmConfig addInterceptor(com.javaorm.core.interceptor.SqlInterceptor interceptor) {
        interceptorChain.addInterceptor(interceptor);
        return this;
    }

    private com.javaorm.core.codefirst.CodeFirstMode codeFirstMode = com.javaorm.core.codefirst.CodeFirstMode.NONE;
    private final java.util.Set<String> codeFirstPackageNames = new java.util.LinkedHashSet<>();
    private final java.util.Set<Class<?>> codeFirstEntities = new java.util.LinkedHashSet<>();
    private boolean codeFirstDropUnusedColumns = false;

    public com.javaorm.core.codefirst.CodeFirstMode getCodeFirstMode() {
        return codeFirstMode;
    }

    public OrmConfig setCodeFirstMode(com.javaorm.core.codefirst.CodeFirstMode codeFirstMode) {
        this.codeFirstMode = codeFirstMode != null ? codeFirstMode : com.javaorm.core.codefirst.CodeFirstMode.NONE;
        return this;
    }

    public java.util.Set<String> getCodeFirstPackageNames() {
        return codeFirstPackageNames;
    }

    public OrmConfig addCodeFirstPackages(String... packageNames) {
        if (packageNames != null) {
            for (String pkg : packageNames) {
                if (pkg != null && !pkg.trim().isEmpty()) {
                    this.codeFirstPackageNames.add(pkg.trim());
                }
            }
        }
        return this;
    }

    public java.util.Set<Class<?>> getCodeFirstEntities() {
        return codeFirstEntities;
    }

    public OrmConfig addCodeFirstEntities(Class<?>... entityClasses) {
        if (entityClasses != null) {
            for (Class<?> clazz : entityClasses) {
                if (clazz != null) {
                    this.codeFirstEntities.add(clazz);
                }
            }
        }
        return this;
    }

    public boolean isCodeFirstDropUnusedColumns() {
        return codeFirstDropUnusedColumns;
    }

    public OrmConfig setCodeFirstDropUnusedColumns(boolean codeFirstDropUnusedColumns) {
        this.codeFirstDropUnusedColumns = codeFirstDropUnusedColumns;
        return this;
    }
}

package com.javaorm.core.config;

import com.javaorm.core.connection.ConnectionProvider;
import com.javaorm.core.tenant.TenantContext;

import java.util.function.Supplier;

/**
 * 全局 ORM 配置
 */
public class OrmConfig {

    private NameConversion nameConversion = new UnderlinedNameConversion();
    private boolean customNameConversion = false;
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
            this.customNameConversion = true;
        }
        return this;
    }

    public boolean isCustomNameConversion() {
        return customNameConversion;
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

    private com.javaorm.core.handler.AutoFillHandler autoFillHandler = null;

    public com.javaorm.core.handler.AutoFillHandler getAutoFillHandler() {
        return autoFillHandler;
    }

    public OrmConfig setAutoFillHandler(com.javaorm.core.handler.AutoFillHandler autoFillHandler) {
        this.autoFillHandler = autoFillHandler;
        return this;
    }

    public OrmConfig autoFillHandler(com.javaorm.core.handler.AutoFillHandler autoFillHandler) {
        return setAutoFillHandler(autoFillHandler);
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

    public OrmConfig codeFirstMode(com.javaorm.core.codefirst.CodeFirstMode mode) {
        return setCodeFirstMode(mode);
    }

    public OrmConfig codeFirstPackageNames(String... packageNames) {
        return addCodeFirstPackages(packageNames);
    }

    public OrmConfig codeFirstEntities(Class<?>... entityClasses) {
        return addCodeFirstEntities(entityClasses);
    }

    public OrmConfig codeFirstDropUnusedColumns(boolean drop) {
        return setCodeFirstDropUnusedColumns(drop);
    }

    private int codeFirstAlterModeMask = com.javaorm.core.codefirst.CodeFirstAlterMode.NONE.getMask();

    public int getCodeFirstAlterModeMask() {
        return codeFirstAlterModeMask;
    }

    public OrmConfig setCodeFirstAlterModeMask(int mask) {
        this.codeFirstAlterModeMask = mask;
        return this;
    }

    public OrmConfig codeFirstAlterMode(com.javaorm.core.codefirst.CodeFirstAlterMode mode) {
        this.codeFirstAlterModeMask = mode != null ? mode.getMask() : com.javaorm.core.codefirst.CodeFirstAlterMode.NONE.getMask();
        return this;
    }

    public OrmConfig codeFirstAlterMode(com.javaorm.core.codefirst.CodeFirstAlterMode... modes) {
        this.codeFirstAlterModeMask = com.javaorm.core.codefirst.CodeFirstAlterMode.combine(modes);
        return this;
    }

    public OrmConfig codeFirstAlterMode(int bitmask) {
        this.codeFirstAlterModeMask = bitmask;
        return this;
    }

    private boolean autoTrack = false;
    private com.javaorm.core.track.TrackManager trackManager = new com.javaorm.core.track.TrackManager();

    public boolean isAutoTrack() {
        return autoTrack;
    }

    public OrmConfig setAutoTrack(boolean autoTrack) {
        this.autoTrack = autoTrack;
        return this;
    }

    public OrmConfig autoTrack(boolean autoTrack) {
        return setAutoTrack(autoTrack);
    }

    public com.javaorm.core.track.TrackManager getTrackManager() {
        return trackManager;
    }

    public OrmConfig setTrackManager(com.javaorm.core.track.TrackManager trackManager) {
        if (trackManager != null) {
            this.trackManager = trackManager;
        }
        return this;
    }

    public OrmConfig trackManager(com.javaorm.core.track.TrackManager trackManager) {
        return setTrackManager(trackManager);
    }

    private com.javaorm.core.datasource.MasterSlaveManager masterSlaveManager;

    public com.javaorm.core.datasource.MasterSlaveManager getMasterSlaveManager() {
        if (masterSlaveManager == null) {
            masterSlaveManager = new com.javaorm.core.datasource.MasterSlaveManager();
        }
        return masterSlaveManager;
    }

    public OrmConfig setMasterSlaveManager(com.javaorm.core.datasource.MasterSlaveManager masterSlaveManager) {
        this.masterSlaveManager = masterSlaveManager;
        return this;
    }

    public OrmConfig masterSlaveManager(com.javaorm.core.datasource.MasterSlaveManager masterSlaveManager) {
        return setMasterSlaveManager(masterSlaveManager);
    }

    /**
     * 批量操作是否强制严格保持原始顺序（默认为 false，即默认开启 EasyQuery 智能聚类分桶合并以获得最佳批量性能）。
     * 若配置为 true，全局的所有 batchInsert / batchUpdate 均会严格遵照输入实体的原始物理顺序执行。
     */
    private boolean batchForceOrder = false;

    public boolean isBatchForceOrder() {
        return batchForceOrder;
    }

    public OrmConfig setBatchForceOrder(boolean batchForceOrder) {
        this.batchForceOrder = batchForceOrder;
        return this;
    }

    public OrmConfig batchForceOrder(boolean batchForceOrder) {
        return setBatchForceOrder(batchForceOrder);
    }

    public boolean isBatchKeepOrder() {
        return batchForceOrder;
    }

    public OrmConfig setBatchKeepOrder(boolean batchKeepOrder) {
        return setBatchForceOrder(batchKeepOrder);
    }

    public OrmConfig batchKeepOrder(boolean batchKeepOrder) {
        return setBatchForceOrder(batchKeepOrder);
    }
}

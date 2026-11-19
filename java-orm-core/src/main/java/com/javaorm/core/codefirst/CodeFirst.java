package com.javaorm.core.codefirst;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * Code-First 数据库架构同步引擎（参考 EasyQuery 与 JPA 的 hbm2ddl.auto）。
 * 自动识别实体元数据，对比数据库中现存表与字段，按配置安全生成并执行 DDL。
 */
public class CodeFirst {

    private static final Logger log = LoggerFactory.getLogger(CodeFirst.class);

    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;

    public CodeFirst(DataSource dataSource, Dialect dialect, OrmConfig config) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config != null ? config : new OrmConfig();
    }

    /**
     * 执行指定实体类的架构同步
     */
    public void sync(Class<?>... entityClasses) {
        if (entityClasses == null || entityClasses.length == 0) return;
        sync(Arrays.asList(entityClasses));
    }

    /**
     * 执行实体列表的架构同步
     */
    public void sync(Collection<Class<?>> entityClasses) {
        CodeFirstMode mode = config.getCodeFirstMode();
        if (mode == null || mode == CodeFirstMode.NONE) {
            return;
        }
        if (entityClasses == null || entityClasses.isEmpty()) {
            return;
        }

        List<String> ddlList = generateDdl(entityClasses);
        if (ddlList.isEmpty()) {
            return;
        }

        if (mode == CodeFirstMode.VALIDATE) {
            throw new IllegalStateException("Database schema validation failed: missing " + ddlList.size() + " DDL operations: " + ddlList);
        }

        executeDdl(ddlList);
    }

    /**
     * 同步单个表结构
     */
    public void syncTable(Class<?> entityClass) {
        sync(Collections.singletonList(entityClass));
    }

    /**
     * 同步指定包下的所有实体表结构
     */
    public void syncPackage(String packageName) {
        List<Class<?>> classes = PackageScanner.scan(packageName);
        sync(classes);
    }

    /**
     * 执行全量架构同步（包含配置中指定的所有 package 和 entities）
     */
    public void syncAll() {
        Set<Class<?>> targets = new LinkedHashSet<>();
        if (config.getCodeFirstEntities() != null) {
            targets.addAll(config.getCodeFirstEntities());
        }
        if (config.getCodeFirstPackageNames() != null) {
            for (String pkg : config.getCodeFirstPackageNames()) {
                targets.addAll(PackageScanner.scan(pkg));
            }
        }
        sync(targets);
    }

    /**
     * 生成指定实体的 DDL 语句（不执行）
     */
    public List<String> generateDdl(Class<?>... entityClasses) {
        if (entityClasses == null || entityClasses.length == 0) return Collections.emptyList();
        return generateDdl(Arrays.asList(entityClasses));
    }

    /**
     * 生成实体列表的 DDL 语句（不执行）
     */
    public List<String> generateDdl(Collection<Class<?>> entityClasses) {
        if (entityClasses == null || entityClasses.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> ddlList = new ArrayList<>();
        CodeFirstMode mode = config.getCodeFirstMode() != null ? config.getCodeFirstMode() : CodeFirstMode.UPDATE;

        try (Connection conn = dataSource.getConnection()) {
            Set<String> existingTables = SchemaInspector.getExistingTables(conn, null);

            for (Class<?> clazz : entityClasses) {
                if (clazz == null || clazz.isInterface() || clazz.isEnum()) continue;
                EntityMetadata meta = EntityMetadataManager.get(clazz, config);
                if (meta == null || meta.getColumns().isEmpty()) continue;

                String tableName = meta.getTableName();
                boolean tableExists = existingTables.contains(tableName);

                if (mode == CodeFirstMode.DROP_CREATE) {
                    if (tableExists) {
                        ddlList.add(DdlBuilder.buildDropTable(dialect, tableName));
                    }
                    ddlList.add(DdlBuilder.buildCreateTable(dialect, meta));
                    continue;
                }

                if (!tableExists) {
                    // 表不存在，生成建表语句
                    ddlList.add(DdlBuilder.buildCreateTable(dialect, meta));
                } else if (mode == CodeFirstMode.UPDATE || mode == CodeFirstMode.VALIDATE) {
                    // 表已存在，检测缺失的列
                    Set<String> existingColumns = SchemaInspector.getExistingColumns(conn, meta.getSchema(), tableName);
                    for (ColumnMetadata col : meta.getColumns()) {
                        if (col.isIgnored()) continue;
                        if (!existingColumns.contains(col.getColumnName())) {
                            ddlList.add(DdlBuilder.buildAddColumn(dialect, meta, col));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to inspect schema for Code-First DDL generation", e);
        }

        return ddlList;
    }

    /**
     * 执行 DDL 语句列表
     */
    public void executeDdl(List<String> ddlList) {
        if (ddlList == null || ddlList.isEmpty()) return;

        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                for (String ddl : ddlList) {
                    if (config.isPrintSql()) {
                        log.info("==> [CodeFirst] Executing DDL: {}", ddl);
                    }
                    stmt.execute(ddl);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute Code-First DDL: " + e.getMessage(), e);
        }
    }
}

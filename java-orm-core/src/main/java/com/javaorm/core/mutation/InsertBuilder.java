package com.javaorm.core.mutation;

import com.javaorm.core.annotation.AutoFillType;
import com.javaorm.core.ast.InsertStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.javaorm.core.connection.ConnectionUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Date;

public class InsertBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(InsertBuilder.class);

    private final T entity;
    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;

    public InsertBuilder(T entity, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this.entity = entity;
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
    }

    public int execute() {
        if (entity == null) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entity.getClass();
        EntityMetadata metadata = EntityMetadataManager.get(entityClass, config);

        InsertStatement stmt = new InsertStatement(entityClass, metadata.getTableName());

        // 遍历所有列
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isIgnored()) {
                continue;
            }

            // 如果是主键且当前为 null 或 0，跳过（由数据库自增生成）
            if (col.isPrimaryKey()) {
                Object val = col.getValue(entity);
                if (val == null || (val instanceof Number && ((Number) val).longValue() == 0)) {
                    continue;
                }
            }

            // 处理逻辑删除默认值
            if (col.isLogicDelete()) {
                Object val = col.getValue(entity);
                if (val == null) {
                    val = col.getLogicDeleteNormalValue();
                    col.setValue(entity, val);
                }
            }

            // 处理多租户字段默认填充
            if (col.isTenant()) {
                Object val = col.getValue(entity);
                if (val == null) {
                    Object tenantId = config != null ? config.getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
                    if (tenantId != null) {
                        col.setValue(entity, tenantId);
                    } else if (config != null && config.isTenantStrict()) {
                        throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + entityClass.getName());
                    }
                }
            }

            // 自动填充日期/时间
            Object val = col.getValue(entity);
            if (val == null) {
                if (col.getPropertyType() == LocalDateTime.class) {
                    val = LocalDateTime.now();
                    col.setValue(entity, val);
                } else if (col.getPropertyType() == Date.class) {
                    val = new Date();
                    col.setValue(entity, val);
                }
            }

            stmt.addColumnValue(col.getColumnName(), val);
        }

        SqlAndParams sqlAndParams = dialect.buildInsert(stmt);
        if (config.isPrintSql()) {
            log.info("==>  Preparing: {}", sqlAndParams.getSql());
            log.info("==> Parameters: {}", sqlAndParams.getParams());
        }

        Connection conn = null;
        boolean isTx = false;
        try {
            conn = ConnectionUtils.getConnection(dataSource, config);
            isTx = ConnectionUtils.isTransactionConnection(conn);
            try (PreparedStatement ps = conn.prepareStatement(sqlAndParams.getSql(), Statement.RETURN_GENERATED_KEYS)) {

                for (int i = 0; i < sqlAndParams.getParams().size(); i++) {
                    ps.setObject(i + 1, sqlAndParams.getParams().get(i));
                }

                int affected = ps.executeUpdate();

                // 主键自增值回填
                if (metadata.getIdColumn() != null) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs != null && rs.next()) {
                            Object key = rs.getObject(1);
                            metadata.getIdColumn().setValue(entity, key);
                        }
                    }
                }

                return affected;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute insert statement", e);
        } finally {
            if (!isTx) ConnectionUtils.closeConnection(conn);
        }
    }
}

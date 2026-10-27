package com.javaorm.core;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.*;
import com.javaorm.core.mutation.DeleteBuilder;
import com.javaorm.core.mutation.InsertBuilder;
import com.javaorm.core.mutation.UpdateBuilder;
import com.javaorm.core.query.Query;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * JavaOrm 核心门面与统一操作入口。
 */
public class JavaOrm {

    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;

    public JavaOrm(DataSource dataSource, Dialect dialect, OrmConfig config) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource must not be null");
        }
        this.dataSource = dataSource;
        this.config = config != null ? config : new OrmConfig();
        this.dialect = dialect != null ? dialect : detectDialect(dataSource);
    }

    public static JavaOrm create(DataSource dataSource) {
        return new JavaOrm(dataSource, null, new OrmConfig());
    }

    public static JavaOrm create(DataSource dataSource, Dialect dialect) {
        return new JavaOrm(dataSource, dialect, new OrmConfig());
    }

    public static JavaOrm create(DataSource dataSource, Dialect dialect, OrmConfig config) {
        return new JavaOrm(dataSource, dialect, config);
    }

    /**
     * 开启流式查询：orm.select(Student.class).where().and(s -> s.getAge()).gt(18)...
     */
    public <T> Query<T> select(Class<T> entityClass) {
        return new Query<>(entityClass, dataSource, dialect, config);
    }

    /**
     * 实体级插入（自增主键自动回填）
     */
    public <T> int insert(T entity) {
        return new InsertBuilder<>(entity, dataSource, dialect, config).execute();
    }

    /**
     * 实体级选择性更新（按主键更新非 null 属性）
     */
    public <T> int update(T entity) {
        return UpdateBuilder.updateEntity(entity, dataSource, dialect, config, true);
    }

    /**
     * 实体级全量更新（按主键更新包括 null 在内的所有属性）
     */
    public <T> int updateAllColumns(T entity) {
        return UpdateBuilder.updateEntity(entity, dataSource, dialect, config, false);
    }

    /**
     * 开启流式条件更新：orm.update(Student.class).set(s -> s.getAge(), 20).where()...
     */
    public <T> UpdateBuilder<T> update(Class<T> entityClass) {
        return new UpdateBuilder<>(entityClass, dataSource, dialect, config);
    }

    /**
     * 实体级删除（按实体主键删除，支持逻辑删除自动重写）
     */
    public <T> int delete(T entity) {
        return DeleteBuilder.deleteEntity(entity, dataSource, dialect, config);
    }

    /**
     * 根据主键 ID 删除
     */
    public <T> int deleteById(Class<T> entityClass, Object id) {
        return DeleteBuilder.deleteById(entityClass, id, dataSource, dialect, config);
    }

    /**
     * 开启流式条件删除：orm.delete(Student.class).where().and(s -> s.getAge()).lt(10)...
     */
    public <T> DeleteBuilder<T> delete(Class<T> entityClass) {
        return new DeleteBuilder<>(entityClass, dataSource, dialect, config);
    }

    public DataSource getDataSource() { return dataSource; }
    public Dialect getDialect() { return dialect; }
    public OrmConfig getConfig() { return config; }

    private static Dialect detectDialect(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            String prodName = conn.getMetaData().getDatabaseProductName();
            if (prodName != null) {
                String lower = prodName.toLowerCase();
                if (lower.contains("mysql") || lower.contains("mariadb")) {
                    return new MySQLDialect();
                }
                if (lower.contains("postgres") || lower.contains("pgsql")) {
                    return new PostgreSQLDialect();
                }
                if (lower.contains("sqlite")) {
                    return new SQLiteDialect();
                }
                if (lower.contains("h2")) {
                    return new H2Dialect();
                }
            }
        } catch (Exception ignored) {
        }
        return new MySQLDialect();
    }
}

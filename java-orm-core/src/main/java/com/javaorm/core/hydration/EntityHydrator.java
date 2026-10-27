package com.javaorm.core.hydration;

import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;

public class EntityHydrator {

    private static final Logger log = LoggerFactory.getLogger(EntityHydrator.class);

    public static <T> List<T> queryList(Connection conn, SelectStatement stmt, EntityMetadata metadata, Dialect dialect, OrmConfig config) throws Exception {
        SqlAndParams sqlAndParams = dialect.buildSelect(stmt);
        if (config.isPrintSql()) {
            log.info("==>  Preparing: {}", sqlAndParams.getSql());
            log.info("==> Parameters: {}", sqlAndParams.getParams());
        }

        List<T> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sqlAndParams.getSql())) {
            for (int i = 0; i < sqlAndParams.getParams().size(); i++) {
                ps.setObject(i + 1, sqlAndParams.getParams().get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData rsmd = rs.getMetaData();
                int colCount = rsmd.getColumnCount();

                @SuppressWarnings("unchecked")
                Class<T> clazz = (Class<T>) metadata.getEntityClass();

                while (rs.next()) {
                    T entity = clazz.getDeclaredConstructor().newInstance();
                    for (int i = 1; i <= colCount; i++) {
                        String colLabel = rsmd.getColumnLabel(i);
                        if (colLabel == null || colLabel.isEmpty()) {
                            colLabel = rsmd.getColumnName(i);
                        }
                        ColumnMetadata col = metadata.getColumnByDbName(colLabel);
                        if (col != null) {
                            Object val = rs.getObject(i);
                            col.setValue(entity, val);
                        }
                    }
                    list.add(entity);
                }
            }
        }
        return list;
    }

    public static long queryCount(Connection conn, SelectStatement stmt, Dialect dialect, OrmConfig config) throws Exception {
        SqlAndParams sqlAndParams = dialect.buildSelect(stmt);
        if (config.isPrintSql()) {
            log.info("==> [Count] Preparing: {}", sqlAndParams.getSql());
            log.info("==> [Count] Parameters: {}", sqlAndParams.getParams());
        }

        try (PreparedStatement ps = conn.prepareStatement(sqlAndParams.getSql())) {
            for (int i = 0; i < sqlAndParams.getParams().size(); i++) {
                ps.setObject(i + 1, sqlAndParams.getParams().get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0L;
    }
}

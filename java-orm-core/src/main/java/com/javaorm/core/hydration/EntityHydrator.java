package com.javaorm.core.hydration;

import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;

public class EntityHydrator {

    private static final Logger log = LoggerFactory.getLogger(EntityHydrator.class);

    public static <T> List<T> queryList(Connection conn, SelectStatement stmt, EntityMetadata metadata, Dialect dialect, OrmConfig config) throws Exception {
        if (metadata == null) {
            @SuppressWarnings("unchecked")
            List<T> mapList = (List<T>) (List<?>) queryMaps(conn, stmt, dialect, config);
            return mapList;
        }
        SqlAndParams sqlAndParams = dialect.buildSelect(stmt);
        if (config.isPrintSql()) {
            log.info("==>  Preparing: {}", sqlAndParams.getSql());
            log.info("==> Parameters: {}", sqlAndParams.getParams());
        }

        List<T> list = new ArrayList<>();
        try (PreparedStatement ps = prepareStatement(conn, sqlAndParams, dialect, stmt != null && !stmt.getCtes().isEmpty())) {
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

    public static <DTO> List<DTO> queryDtoList(Connection conn, SelectStatement stmt, Class<DTO> dtoClass, Dialect dialect, OrmConfig config) throws Exception {
        SqlAndParams sqlAndParams = dialect.buildSelect(stmt);
        if (config.isPrintSql()) {
            log.info("==> [DTO] Preparing: {}", sqlAndParams.getSql());
            log.info("==> [DTO] Parameters: {}", sqlAndParams.getParams());
        }

        List<DTO> list = new ArrayList<>();
        Map<String, Field> fieldMap = new HashMap<>();
        Class<?> curr = dtoClass;
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                fieldMap.put(f.getName().toLowerCase(), f);
                fieldMap.put(f.getName().toLowerCase().replace("_", ""), f);
            }
            curr = curr.getSuperclass();
        }

        try (PreparedStatement ps = prepareStatement(conn, sqlAndParams, dialect, stmt != null && !stmt.getCtes().isEmpty())) {

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData rsmd = rs.getMetaData();
                int colCount = rsmd.getColumnCount();

                while (rs.next()) {
                    DTO dto = dtoClass.getDeclaredConstructor().newInstance();
                    for (int i = 1; i <= colCount; i++) {
                        String colLabel = rsmd.getColumnLabel(i);
                        if (colLabel == null || colLabel.isEmpty()) {
                            colLabel = rsmd.getColumnName(i);
                        }
                        String clean = colLabel.replace("\"", "").replace("`", "");
                        String key = clean.toLowerCase();
                        if (key.contains(".")) {
                            key = key.substring(key.lastIndexOf('.') + 1);
                        }
                        Field f = fieldMap.get(key);
                        if (f == null) {
                            f = fieldMap.get(key.replace("_", ""));
                        }
                        if (f != null) {
                            Object val = rs.getObject(i);
                            if (val != null) {
                                Object converted = ColumnMetadata.convertValue(f.getType(), val);
                                f.set(dto, converted);
                            }
                        }
                    }
                    list.add(dto);
                }
            }
        }
        return list;
    }

    public static List<Map<String, Object>> queryMaps(Connection conn, SelectStatement stmt, Dialect dialect, OrmConfig config) throws Exception {
        SqlAndParams sqlAndParams = dialect.buildSelect(stmt);
        if (config.isPrintSql()) {
            log.info("==> [Maps] Preparing: {}", sqlAndParams.getSql());
            log.info("==> [Maps] Parameters: {}", sqlAndParams.getParams());
        }

        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = prepareStatement(conn, sqlAndParams, dialect, stmt != null && !stmt.getCtes().isEmpty())) {

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData rsmd = rs.getMetaData();
                int colCount = rsmd.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        String colLabel = rsmd.getColumnLabel(i);
                        if (colLabel == null || colLabel.isEmpty()) {
                            colLabel = rsmd.getColumnName(i);
                        }
                        Object val = rs.getObject(i);
                        map.put(colLabel, val);
                        String clean = colLabel.replace("\"", "").replace("`", "");
                        map.putIfAbsent(clean, val);
                        map.putIfAbsent(clean.toLowerCase(), val);
                        map.putIfAbsent(clean.toUpperCase(), val);
                        String shortKey = clean.contains(".") ? clean.substring(clean.lastIndexOf('.') + 1) : clean;
                        map.putIfAbsent(shortKey, val);
                        map.putIfAbsent(shortKey.toLowerCase(), val);
                        map.putIfAbsent(shortKey.toUpperCase(), val);
                        map.putIfAbsent(toCamelCase(shortKey.toLowerCase()), val);
                        map.putIfAbsent(toUnderscore(shortKey), val);
                    }
                    list.add(map);
                }
            }
        }
        return list;
    }

    private static String toCamelCase(String s) {
        if (s == null || !s.contains("_")) return s;
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static String toUnderscore(String s) {
        if (s == null) return null;
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

    @SuppressWarnings("unchecked")
    public static <V> V queryScalar(Connection conn, SelectStatement stmt, Class<V> targetType, Dialect dialect, OrmConfig config) throws Exception {
        SqlAndParams sqlAndParams = dialect.buildSelect(stmt);
        if (config.isPrintSql()) {
            log.info("==> [Scalar] Preparing: {}", sqlAndParams.getSql());
            log.info("==> [Scalar] Parameters: {}", sqlAndParams.getParams());
        }

        try (PreparedStatement ps = prepareStatement(conn, sqlAndParams, dialect, stmt != null && !stmt.getCtes().isEmpty())) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object val = rs.getObject(1);
                    return (V) ColumnMetadata.convertValue(targetType, val);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <V> List<V> queryScalarList(Connection conn, SelectStatement stmt, Class<V> targetType, Dialect dialect, OrmConfig config) throws Exception {
        SqlAndParams sqlAndParams = dialect.buildSelect(stmt);
        if (config.isPrintSql()) {
            log.info("==> [ScalarList] Preparing: {}", sqlAndParams.getSql());
            log.info("==> [ScalarList] Parameters: {}", sqlAndParams.getParams());
        }

        List<V> list = new ArrayList<>();
        try (PreparedStatement ps = prepareStatement(conn, sqlAndParams, dialect, stmt != null && !stmt.getCtes().isEmpty())) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object val = rs.getObject(1);
                    list.add((V) ColumnMetadata.convertValue(targetType, val));
                }
            }
        }
        return list;
    }

    public static long queryCount(Connection conn, SelectStatement stmt, Dialect dialect, OrmConfig config) throws Exception {
        return queryCount(conn, dialect.buildSelect(stmt), config, stmt != null && !stmt.getCtes().isEmpty(), dialect);
    }

    public static long queryCount(Connection conn, SqlAndParams sqlAndParams, OrmConfig config) throws Exception {
        return queryCount(conn, sqlAndParams, config, false, null);
    }

    public static long queryCount(Connection conn, SqlAndParams sqlAndParams, OrmConfig config, boolean hasCte, Dialect dialect) throws Exception {
        if (config.isPrintSql()) {
            log.info("==> [Count] Preparing: {}", sqlAndParams.getSql());
            log.info("==> [Count] Parameters: {}", sqlAndParams.getParams());
        }

        try (PreparedStatement ps = prepareStatement(conn, sqlAndParams, dialect, hasCte)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0L;
    }

    public static String inlineParams(String sql, List<Object> params) {
        if (params == null || params.isEmpty()) {
            return sql;
        }
        StringBuilder sb = new StringBuilder();
        int paramIdx = 0;
        boolean inQuotes = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                inQuotes = !inQuotes;
                sb.append(c);
            } else if (c == '?' && !inQuotes && paramIdx < params.size()) {
                Object p = params.get(paramIdx++);
                if (p == null) {
                    sb.append("NULL");
                } else if (p instanceof Number || p instanceof Boolean) {
                    sb.append(p);
                } else {
                    sb.append("'").append(String.valueOf(p).replace("'", "''")).append("'");
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static PreparedStatement prepareStatement(Connection conn, SqlAndParams sqlAndParams, Dialect dialect, boolean hasCte) throws Exception {
        if (hasCte && dialect instanceof com.javaorm.core.dialect.H2Dialect && !sqlAndParams.getParams().isEmpty()) {
            String inlinedSql = inlineParams(sqlAndParams.getSql(), sqlAndParams.getParams());
            return conn.prepareStatement(inlinedSql);
        }
        PreparedStatement ps = conn.prepareStatement(sqlAndParams.getSql());
        for (int i = 0; i < sqlAndParams.getParams().size(); i++) {
            ps.setObject(i + 1, sqlAndParams.getParams().get(i));
        }
        return ps;
    }
}

package com.javaorm.core.hydration;

import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.fastbean.FastBean;
import com.javaorm.core.fastbean.FastPropertySetter;
import com.javaorm.core.fastbean.LambdaFactory;
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

                // 预解析每一列对应的 ColumnMetadata 映射，避免在每行循环内重复解析元数据与字符串处理
                ColumnMetadata[] colMapping = new ColumnMetadata[colCount + 1];
                for (int i = 1; i <= colCount; i++) {
                    String colLabel = rsmd.getColumnLabel(i);
                    if (colLabel == null || colLabel.isEmpty()) {
                        colLabel = rsmd.getColumnName(i);
                    }
                    colMapping[i] = metadata.getColumnByDbName(colLabel);
                }

                while (rs.next()) {
                    @SuppressWarnings("unchecked")
                    T entity = (T) metadata.newInstance();
                    for (int i = 1; i <= colCount; i++) {
                        ColumnMetadata col = colMapping[i];
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
        FastBean<DTO> fastBean = LambdaFactory.getFastBean(dtoClass);

        try (PreparedStatement ps = prepareStatement(conn, sqlAndParams, dialect, stmt != null && !stmt.getCtes().isEmpty())) {
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData rsmd = rs.getMetaData();
                int colCount = rsmd.getColumnCount();

                // 预解析每一列对应的 FastPropertySetter 与属性类型，消除行循环中的反射与字段查找
                FastPropertySetter[] setters = new FastPropertySetter[colCount + 1];
                Class<?>[] propTypes = new Class<?>[colCount + 1];

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
                    FastPropertySetter setter = fastBean.getSetter(key);
                    Class<?> pType = fastBean.getPropertyType(key);
                    if (setter == null && key.contains("_")) {
                        String noUnderscore = key.replace("_", "");
                        setter = fastBean.getSetter(noUnderscore);
                        pType = fastBean.getPropertyType(noUnderscore);
                    }
                    setters[i] = setter;
                    propTypes[i] = pType;
                }

                while (rs.next()) {
                    DTO dto = fastBean.create();
                    for (int i = 1; i <= colCount; i++) {
                        FastPropertySetter setter = setters[i];
                        if (setter != null) {
                            Object val = rs.getObject(i);
                            if (val != null) {
                                Object converted = ColumnMetadata.convertValue(propTypes[i], val);
                                setter.set(dto, converted);
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

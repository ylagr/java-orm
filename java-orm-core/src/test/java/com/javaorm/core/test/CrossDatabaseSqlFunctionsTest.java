package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.*;
import com.javaorm.core.ast.DateUnit;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.*;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.query.Query;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.javaorm.core.SQL.*;
import static org.junit.Assert.*;

/**
 * 跨数据库无痛切换与 SQL 函数（时间格式化、计算、数学、字符串、聚合）的全面测试。
 */
public class CrossDatabaseSqlFunctionsTest {

    private JdbcDataSource dataSource;
    private JavaOrm orm;

    @JoTable("jo_custom_record")
    public static class JoSampleEntity {
        @JoId
        @JoGeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JoColumn("record_title")
        private String title;

        @JoLogicDelete(value = "0", delval = "1")
        private Integer isDeleted;

        @JoTenantId
        private String tenantCode;

        @JoAutoFill(fillType = AutoFillType.INSERT)
        private String createBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Integer getIsDeleted() { return isDeleted; }
        public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
        public String getTenantCode() { return tenantCode; }
        public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
        public String getCreateBy() { return createBy; }
        public void setCreateBy(String createBy) { this.createBy = createBy; }
    }

    @JoTable("t_cross_record")
    public static class CrossRecord {
        @JoId
        private Long id;
        private String name;
        private Integer age;
        private Double score;
        private LocalDateTime createTime;

        @JoLogicDelete(normalValue = "0", deletedValue = "1")
        private Integer isDeleted;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
        public Integer getIsDeleted() { return isDeleted; }
        public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
    }

    @Before
    public void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:cross_db_test;DB_CLOSE_DELAY=-1;MODE=MySQL");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        OrmConfig config = new OrmConfig();
        config.setPrintSql(false);
        orm = JavaOrm.create(dataSource, new H2Dialect(), config);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"t_cross_record\"");
            stmt.execute("CREATE TABLE \"t_cross_record\" (" +
                    "\"id\" BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "\"name\" VARCHAR(100), " +
                    "\"age\" INT, " +
                    "\"score\" DOUBLE, " +
                    "\"create_time\" TIMESTAMP, " +
                    "\"is_deleted\" INT DEFAULT 0)");

            stmt.execute("INSERT INTO \"t_cross_record\" (\"id\", \"name\", \"age\", \"score\", \"create_time\", \"is_deleted\") VALUES " +
                    "(1, 'Alice', 20, 88.56, '2023-05-15 14:30:00', 0), " +
                    "(2, 'Bob', 22, 92.34, '2023-05-16 09:15:00', 0), " +
                    "(3, 'Charlie', 20, 75.12, '2023-05-17 18:45:00', 0), " +
                    "(4, 'Alice', 25, 95.88, '2023-05-18 11:00:00', 0)");
        }
    }

    @Test
    public void testJoAnnotationsMetadataParsing() {
        EntityMetadata meta = EntityMetadataManager.getTableMetadata(JoSampleEntity.class);
        assertNotNull(meta);
        assertEquals("jo_custom_record", meta.getTableName());
        assertEquals("id", meta.getIdColumn().getColumnName());
        assertEquals("record_title", meta.getColumnByProperty("title").getColumnName());
        assertTrue(meta.hasLogicDelete());
        assertEquals("isDeleted", meta.getLogicDeleteColumn().getPropertyName());
        assertEquals("0", meta.getLogicDeleteColumn().getLogicDeleteNormalValue());
        assertEquals("1", meta.getLogicDeleteColumn().getLogicDeleteDeletedValue());
        assertTrue(meta.hasTenant());
        assertEquals("tenantCode", meta.getTenantColumn().getPropertyName());
        assertEquals("tenant_code", meta.getTenantColumn().getColumnName());
        assertTrue(meta.getColumnByProperty("createBy").isAutoFill());
        assertEquals(AutoFillType.INSERT, meta.getColumnByProperty("createBy").getAutoFillType());
    }

    @Test
    public void testDateFormatCrossDatabaseTranslation() {
        CrossRecord s = proxy(CrossRecord.class);

        // 1. 标准 Java 格式 "yyyy-MM-dd HH:mm:ss"
        Query<CrossRecord> q1 = orm.select(CrossRecord.class).select(dateFormat(s.getCreateTime(), "yyyy-MM-dd HH:mm:ss"));
        
        q1.setDialect(new MySQLDialect());
        assertTrue(q1.toSql().contains("DATE_FORMAT(t0.`create_time`, '%Y-%m-%d %H:%i:%s')"));

        q1.setDialect(new PostgreSQLDialect());
        assertTrue(q1.toSql().contains("TO_CHAR(t0.\"create_time\", 'YYYY-MM-DD HH24:MI:SS')"));

        q1.setDialect(new OracleDialect());
        assertTrue(q1.toSql().contains("TO_CHAR(t0.\"create_time\", 'YYYY-MM-DD HH24:MI:SS')"));

        q1.setDialect(new SQLiteDialect());
        assertTrue(q1.toSql().contains("strftime('%Y-%m-%d %H:%M:%S', t0.\"create_time\")"));

        q1.setDialect(new H2Dialect());
        assertTrue(q1.toSql().contains("FORMATDATETIME(t0.\"create_time\", 'yyyy-MM-dd HH:mm:ss')"));

        // 2. POSIX 格式 "%Y-%m-%d"
        Query<CrossRecord> q2 = orm.select(CrossRecord.class).select(dateFormat(s.getCreateTime(), "%Y-%m-%d"));
        q2.setDialect(new MySQLDialect());
        assertTrue(q2.toSql().contains("DATE_FORMAT(t0.`create_time`, '%Y-%m-%d')"));

        q2.setDialect(new PostgreSQLDialect());
        assertTrue(q2.toSql().contains("TO_CHAR(t0.\"create_time\", 'YYYY-MM-DD')"));

        q2.setDialect(new SQLiteDialect());
        assertTrue(q2.toSql().contains("strftime('%Y-%m-%d', t0.\"create_time\")"));

        // 3. Oracle 风格格式 "YYYY-MM-DD"
        Query<CrossRecord> q3 = orm.select(CrossRecord.class).select(dateFormat(s.getCreateTime(), "YYYY-MM-DD"));
        q3.setDialect(new MySQLDialect());
        assertTrue(q3.toSql().contains("DATE_FORMAT(t0.`create_time`, '%Y-%m-%d')"));

        q3.setDialect(new PostgreSQLDialect());
        assertTrue(q3.toSql().contains("TO_CHAR(t0.\"create_time\", 'YYYY-MM-DD')"));

        q3.setDialect(new H2Dialect());
        assertTrue(q3.toSql().contains("FORMATDATETIME(t0.\"create_time\", 'yyyy-MM-dd')"));
    }

    @Test
    public void testStrToDateCrossDatabaseTranslation() {
        CrossRecord s = proxy(CrossRecord.class);
        Query<CrossRecord> q = orm.select(CrossRecord.class).select(strToDate(s.getName(), "yyyy-MM-dd"));

        q.setDialect(new MySQLDialect());
        assertTrue(q.toSql().contains("STR_TO_DATE(t0.`name`, '%Y-%m-%d')"));

        q.setDialect(new PostgreSQLDialect());
        assertTrue(q.toSql().contains("TO_TIMESTAMP(t0.\"name\", 'YYYY-MM-DD')"));

        q.setDialect(new OracleDialect());
        assertTrue(q.toSql().contains("TO_TIMESTAMP(t0.\"name\", 'YYYY-MM-DD')"));

        q.setDialect(new SQLiteDialect());
        assertTrue(q.toSql().contains("datetime(t0.\"name\")"));

        q.setDialect(new H2Dialect());
        assertTrue(q.toSql().contains("PARSEDATETIME(t0.\"name\", 'yyyy-MM-dd')"));
    }

    @Test
    public void testDateAddAndDateDiffCrossDatabaseTranslation() {
        CrossRecord s = proxy(CrossRecord.class);

        // DateAdd
        Query<CrossRecord> qAdd = orm.select(CrossRecord.class).select(dateAdd(s.getCreateTime(), 1, DateUnit.DAY));
        qAdd.setDialect(new MySQLDialect());
        assertTrue(qAdd.toSql().contains("DATE_ADD(t0.`create_time`, INTERVAL 1 DAY)"));

        qAdd.setDialect(new PostgreSQLDialect());
        assertTrue(qAdd.toSql().contains("(t0.\"create_time\" + (1 * INTERVAL '1 DAY'))"));

        qAdd.setDialect(new OracleDialect());
        assertTrue(qAdd.toSql().contains("(t0.\"create_time\" + 1)"));

        qAdd.setDialect(new SQLiteDialect());
        assertTrue(qAdd.toSql().contains("datetime(t0.\"create_time\", (1 || ' days'))"));

        qAdd.setDialect(new H2Dialect());
        assertTrue(qAdd.toSql().contains("DATEADD('DAY', 1, t0.\"create_time\")"));

        // DateDiff
        Query<CrossRecord> qDiff = orm.select(CrossRecord.class).select(dateDiff(s.getCreateTime(), now()));
        qDiff.setDialect(new MySQLDialect());
        assertTrue(qDiff.toSql().contains("DATEDIFF(t0.`create_time`, NOW())"));

        qDiff.setDialect(new PostgreSQLDialect());
        assertTrue(qDiff.toSql().contains("(CAST(t0.\"create_time\" AS DATE) - CAST(CURRENT_TIMESTAMP AS DATE))"));

        qDiff.setDialect(new OracleDialect());
        assertTrue(qDiff.toSql().contains("(TRUNC(t0.\"create_time\") - TRUNC(SYSDATE))"));

        qDiff.setDialect(new SQLiteDialect());
        assertTrue(qDiff.toSql().contains("CAST(julianday(t0.\"create_time\") - julianday(datetime('now', 'localtime')) AS INT)"));

        qDiff.setDialect(new H2Dialect());
        assertTrue(qDiff.toSql().contains("DATEDIFF('DAY', CURRENT_TIMESTAMP, t0.\"create_time\")"));
    }

    @Test
    public void testGroupConcatAndConcatWsCrossDatabaseTranslation() {
        CrossRecord s = proxy(CrossRecord.class);

        // ConcatWs
        Query<CrossRecord> qWs = orm.select(CrossRecord.class).select(concatWs("-", s.getName(), s.getAge()));
        qWs.setDialect(new MySQLDialect());
        assertTrue(qWs.toSql().contains("CONCAT_WS(?, t0.`name`, t0.`age`)"));

        qWs.setDialect(new PostgreSQLDialect());
        assertTrue(qWs.toSql().contains("CONCAT_WS(?, t0.\"name\", t0.\"age\")"));

        qWs.setDialect(new OracleDialect());
        assertTrue(qWs.toSql().contains("t0.\"name\" || ? || t0.\"age\""));

        qWs.setDialect(new SQLiteDialect());
        assertTrue(qWs.toSql().contains("t0.\"name\" || ? || t0.\"age\""));

        // GroupConcat
        Query<CrossRecord> qGc = orm.select(CrossRecord.class).select(groupConcat(s.getName(), ", "));
        qGc.setDialect(new MySQLDialect());
        assertTrue(qGc.toSql().contains("GROUP_CONCAT(t0.`name` SEPARATOR ', ')"));

        qGc.setDialect(new PostgreSQLDialect());
        assertTrue(qGc.toSql().contains("STRING_AGG(CAST(t0.\"name\" AS TEXT), ?)"));

        qGc.setDialect(new OracleDialect());
        assertTrue(qGc.toSql().contains("LISTAGG(t0.\"name\", ?) WITHIN GROUP (ORDER BY t0.\"name\")"));

        qGc.setDialect(new SQLiteDialect());
        assertTrue(qGc.toSql().contains("group_concat(t0.\"name\", ?)"));

        qGc.setDialect(new H2Dialect());
        assertTrue(qGc.toSql().contains("GROUP_CONCAT(t0.\"name\" SEPARATOR ', ')"));
    }

    @Test
    public void testDistinctAggregatesCrossDatabase() {
        CrossRecord s = proxy(CrossRecord.class);
        Query<CrossRecord> q = orm.select(CrossRecord.class)
                .select(countDistinct(s.getName()), sumDistinct(s.getScore()), avgDistinct(s.getScore()));

        q.setDialect(new MySQLDialect());
        String sqlMy = q.toSql();
        assertTrue(sqlMy.contains("COUNT(DISTINCT t0.`name`)"));
        assertTrue(sqlMy.contains("SUM(DISTINCT t0.`score`)"));
        assertTrue(sqlMy.contains("AVG(DISTINCT t0.`score`)"));

        q.setDialect(new OracleDialect());
        String sqlOra = q.toSql();
        assertTrue(sqlOra.contains("COUNT(DISTINCT t0.\"name\")"));
        assertTrue(sqlOra.contains("SUM(DISTINCT t0.\"score\")"));
        assertTrue(sqlOra.contains("AVG(DISTINCT t0.\"score\")"));
    }

    @Test
    public void testSeamlessDialectSwitchingOnSameQuery() {
        CrossRecord s = proxy(CrossRecord.class);
        Query<CrossRecord> query = orm.select(CrossRecord.class)
                .select(
                        col(s.getId()).as("sid"),
                        dateFormat(s.getCreateTime(), "yyyy-MM-dd").as("fmt_date"),
                        charLength(s.getName()).as("name_len"),
                        round(s.getScore(), 1).as("rnd_score"),
                        trunc(s.getScore(), 0).as("trc_score"),
                        nvl(s.getName(), "Unknown").as("safe_name")
                )
                .where(day(s.getCreateTime()), SqlOperator.GT, 0)
                .orderByDesc(s.getId())
                .limit(10, 5);

        // 1. MySQL
        query.setDialect(new MySQLDialect());
        String mySql = query.toSql();
        assertTrue(mySql.contains("DATE_FORMAT(t0.`create_time`, '%Y-%m-%d') AS `fmt_date`"));
        assertTrue(mySql.contains("LIMIT ? OFFSET ?"));

        // 2. PostgreSQL
        query.setDialect(new PostgreSQLDialect());
        String pgSql = query.toSql();
        assertTrue(pgSql.contains("TO_CHAR(t0.\"create_time\", 'YYYY-MM-DD') AS \"fmt_date\""));
        assertTrue(pgSql.contains("LIMIT ? OFFSET ?"));

        // 3. Oracle (12c+)
        query.setDialect(new OracleDialect());
        String oraSql = query.toSql();
        assertTrue(oraSql.contains("TO_CHAR(t0.\"create_time\", 'YYYY-MM-DD') AS \"fmt_date\""));
        assertTrue(oraSql.contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));

        // 4. Oracle 11g
        query.setDialect(new Oracle11Dialect());
        String ora11Sql = query.toSql();
        assertTrue(ora11Sql.contains("ROWNUM AS \"row_num\""));
        assertTrue(ora11Sql.contains("TO_CHAR(t0.\"create_time\", 'YYYY-MM-DD') AS \"fmt_date\""));

        // 5. SQLite
        query.setDialect(new SQLiteDialect());
        String sqSql = query.toSql();
        assertTrue(sqSql.contains("strftime('%Y-%m-%d', t0.\"create_time\") AS \"fmt_date\""));
        assertTrue(sqSql.contains("LIMIT ? OFFSET ?"));

        // 6. H2
        query.setDialect(new H2Dialect());
        String h2Sql = query.toSql();
        assertTrue(h2Sql.contains("FORMATDATETIME(t0.\"create_time\", 'yyyy-MM-dd') AS \"fmt_date\""));
    }

    @Test
    public void testH2RuntimeExecutionOfCrossDatabaseFunctions() {
        CrossRecord s = proxy(CrossRecord.class);

        // 执行查询并验证实际运行时结果
        List<Map<String, Object>> maps = orm.select(CrossRecord.class)
                .select(
                        col(s.getId()).as("id"),
                        dateFormat(s.getCreateTime(), "yyyy-MM-dd").as("day_str"),
                        charLength(s.getName()).as("name_len"),
                        round(s.getScore(), 1).as("r_score"),
                        trunc(s.getScore(), 0).as("t_score"),
                        dayOfWeek(s.getCreateTime()).as("dow"),
                        dayOfYear(s.getCreateTime()).as("doy")
                )
                .where(s.getId(), SqlOperator.EQ, 1L)
                .queryMaps();

        assertEquals(1, maps.size());
        Map<String, Object> row = maps.get(0);
        assertEquals("2023-05-15", row.get("day_str"));
        assertEquals(5, ((Number) row.get("name_len")).intValue()); // "Alice" -> 5
        assertEquals(88.6, ((Number) row.get("r_score")).doubleValue(), 0.01);
        assertEquals(88.0, ((Number) row.get("t_score")).doubleValue(), 0.01);

        // 验证聚合函数 DISTINCT
        List<Map<String, Object>> aggMaps = orm.select(CrossRecord.class)
                .select(
                        count().as("total_count"),
                        countDistinct(s.getName()).as("distinct_names"),
                        sumDistinct(s.getAge()).as("distinct_age_sum")
                )
                .queryMaps();

        assertEquals(1, aggMaps.size());
        Map<String, Object> aggRow = aggMaps.get(0);
        assertEquals(4L, ((Number) aggRow.get("total_count")).longValue());
        // 4 students: Alice, Bob, Charlie, Alice -> 3 distinct names
        assertEquals(3L, ((Number) aggRow.get("distinct_names")).longValue());
        // ages: 20, 22, 20, 25 -> distinct ages are 20, 22, 25 -> sum is 67
        assertEquals(67L, ((Number) aggRow.get("distinct_age_sum")).longValue());
    }
}

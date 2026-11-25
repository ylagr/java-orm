package com.javaorm.bridge.jpa.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.metadata.IndexMetadata;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;

public class JpaBridgeTest {

    private static DataSource dataSource;
    private static JavaOrm orm;

    @Entity
    @Table(name = "t_jpa_article", indexes = {
            @Index(name = "idx_jpa_author_status", columnList = "author_id, status DESC"),
            @Index(name = "uk_jpa_code", columnList = "article_code", unique = true)
    })
    public static class JpaArticle {
        @Id
        @Column(name = "id")
        private Long id;

        @Column(name = "author_id")
        private Long authorId;

        @Column(name = "status")
        private Integer status;

        @Column(name = "article_code")
        private String articleCode;

        @Transient
        private String ignoreField;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getAuthorId() { return authorId; }
        public void setAuthorId(Long authorId) { this.authorId = authorId; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public String getArticleCode() { return articleCode; }
        public void setArticleCode(String articleCode) { this.articleCode = articleCode; }
        public String getIgnoreField() { return ignoreField; }
        public void setIgnoreField(String ignoreField) { this.ignoreField = ignoreField; }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jpa_test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
        orm = JavaOrm.create(dataSource);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_jpa_article");
            stmt.execute("CREATE TABLE t_jpa_article (" +
                    "id BIGINT PRIMARY KEY, " +
                    "author_id BIGINT, " +
                    "status INT, " +
                    "article_code VARCHAR(50))");
        }
    }

    @Test
    public void testJpaMetadataExtraction() {
        EntityMetadata meta = EntityMetadataManager.get(JpaArticle.class);
        Assert.assertNotNull(meta);
        Assert.assertEquals("t_jpa_article", meta.getTableName());

        ColumnMetadata idCol = meta.getIdColumn();
        Assert.assertNotNull(idCol);
        Assert.assertEquals("id", idCol.getColumnName());
        Assert.assertTrue(idCol.isPrimaryKey());

        // @Transient 忽略字段
        Assert.assertNull(meta.getColumnByProperty("ignoreField"));

        // 索引验证
        Assert.assertEquals(2, meta.getIndexes().size());
        IndexMetadata idx1 = meta.getIndexes().stream()
                .filter(i -> "idx_jpa_author_status".equals(i.getName()))
                .findFirst()
                .orElse(null);
        Assert.assertNotNull(idx1);
        Assert.assertFalse(idx1.isUnique());
        Assert.assertEquals(Arrays.asList("author_id", "status"), idx1.getColumns());

        IndexMetadata idx2 = meta.getIndexes().stream()
                .filter(i -> "uk_jpa_code".equals(i.getName()))
                .findFirst()
                .orElse(null);
        Assert.assertNotNull(idx2);
        Assert.assertTrue(idx2.isUnique());
        Assert.assertEquals(Arrays.asList("article_code"), idx2.getColumns());
    }

    @Test
    public void testJpaCrudOperations() {
        JpaArticle article = new JpaArticle();
        article.setId(1001L);
        article.setAuthorId(2001L);
        article.setStatus(1);
        article.setArticleCode("ART-001");
        article.setIgnoreField("transient_value");

        int rows = orm.insert(article).exec();
        Assert.assertEquals(1, rows);

        JpaArticle queried = orm.findById(JpaArticle.class, 1001L);
        Assert.assertNotNull(queried);
        Assert.assertEquals(Long.valueOf(2001L), queried.getAuthorId());
        Assert.assertEquals("ART-001", queried.getArticleCode());
        Assert.assertNull(queried.getIgnoreField());
    }
}

package com.javaorm.bridge.easyquery.test;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.LogicDelete;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.annotation.Version;
import com.easy.query.core.basic.extension.version.VersionIntStrategy;
import com.javaorm.core.JavaOrm;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

public class EasyQueryBridgeTest {

    private static DataSource dataSource;
    private static JavaOrm orm;

    @Table("t_eq_product")
    public static class EqProduct {
        @Column(value = "prod_id", primaryKey = true, generatedKey = true)
        private Long id;

        @Column("prod_name")
        private String name;

        @Version(strategy = VersionIntStrategy.class)
        @Column("prod_version")
        private Integer version;

        @LogicDelete
        @Column("is_deleted")
        private Integer deleted;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public Integer getDeleted() { return deleted; }
        public void setDeleted(Integer deleted) { this.deleted = deleted; }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:eq_test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
        orm = JavaOrm.create(dataSource);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_eq_product");
            stmt.execute("CREATE TABLE t_eq_product (" +
                    "prod_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "prod_name VARCHAR(50), " +
                    "prod_version INT DEFAULT 1, " +
                    "is_deleted INT DEFAULT 0)");
        }
    }

    @Test
    public void testEasyQueryMetadataAndOptimisticLock() {
        EntityMetadata meta = EntityMetadataManager.get(EqProduct.class);
        Assert.assertNotNull(meta);
        Assert.assertEquals("t_eq_product", meta.getTableName());

        ColumnMetadata idCol = meta.getIdColumn();
        Assert.assertNotNull(idCol);
        Assert.assertEquals("prod_id", idCol.getColumnName());
        Assert.assertTrue(idCol.isPrimaryKey());
        Assert.assertTrue(idCol.isGenerated());

        EqProduct prod = new EqProduct();
        prod.setName("Phone");
        int rows = orm.insert(prod);
        Assert.assertEquals(1, rows);
        Assert.assertNotNull(prod.getId());
        Assert.assertEquals(Integer.valueOf(1), prod.getVersion());

        prod.setName("SmartPhone");
        int updated = orm.update(prod);
        Assert.assertEquals(1, updated);
        Assert.assertEquals(Integer.valueOf(2), prod.getVersion());

        orm.delete(prod);
        EqProduct found = orm.findById(EqProduct.class, prod.getId());
        Assert.assertNull(found);
    }
}

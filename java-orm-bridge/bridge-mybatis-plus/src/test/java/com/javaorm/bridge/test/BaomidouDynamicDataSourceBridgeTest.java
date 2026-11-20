package com.javaorm.bridge.test;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.javaorm.bridge.mp.BaomidouDynamicDataSourceProvider;
import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.GenerationType;
import com.javaorm.core.annotation.JoColumn;
import com.javaorm.core.annotation.JoGeneratedValue;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.datasource.DataSourceContext;
import com.javaorm.core.dialect.H2Dialect;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.Assert.*;

/**
 * Baomidou dynamic-datasource (@DS) 桥接无缝切换测试
 */
public class BaomidouDynamicDataSourceBridgeTest {

    private JdbcDataSource defaultDs;
    private JdbcDataSource tenantDs;
    private JavaOrm orm;

    @JoTable("t_tenant_info")
    public static class TenantInfo {
        @JoId
        @JoGeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JoColumn("info")
        private String info;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getInfo() { return info; }
        public void setInfo(String info) { this.info = info; }
    }

    @Before
    public void setUp() throws Exception {
        defaultDs = new JdbcDataSource();
        defaultDs.setURL("jdbc:h2:mem:baomidou_default;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL");
        defaultDs.setUser("sa");
        defaultDs.setPassword("");

        tenantDs = new JdbcDataSource();
        tenantDs.setURL("jdbc:h2:mem:baomidou_tenant;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL");
        tenantDs.setUser("sa");
        tenantDs.setPassword("");

        initDb(defaultDs, "FROM_DEFAULT");
        initDb(tenantDs, "FROM_TENANT");

        // 注册 SPI Provider（若由于单独类加载器未触发，可显式注册确保覆盖）
        DataSourceContext.registerProvider(new BaomidouDynamicDataSourceProvider());

        OrmConfig config = new OrmConfig();
        config.setPrintSql(true);
        orm = JavaOrm.create(defaultDs, new H2Dialect(), config);
        orm.registerDataSource("tenant_db", tenantDs);
    }

    @After
    public void tearDown() {
        DynamicDataSourceContextHolder.clear();
        DataSourceContext.clear();
    }

    private void initDb(JdbcDataSource ds, String infoVal) throws Exception {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_tenant_info");
            stmt.execute("CREATE TABLE t_tenant_info (id BIGINT AUTO_INCREMENT PRIMARY KEY, info VARCHAR(64))");
            stmt.execute("INSERT INTO t_tenant_info (id, info) VALUES (1, '" + infoVal + "')");
        }
    }

    @Test
    public void testBaomidouDynamicDataSourceSwitching() {
        // 1. 未切换时：走默认数据源
        TenantInfo defaultInfo = orm.select(TenantInfo.class).where().eq(TenantInfo::getId, 1L).first();
        assertNotNull(defaultInfo);
        assertEquals("FROM_DEFAULT", defaultInfo.getInfo());

        // 2. 模拟 Spring 方法上标注 @DS("tenant_db") 或手动 push
        DynamicDataSourceContextHolder.push("tenant_db");
        try {
            // 验证 DataSourceContext 能自动感知到外部切库
            assertEquals("tenant_db", DataSourceContext.getDataSource());

            // 验证 JavaORM 查询自动路由到 tenantDs
            TenantInfo tenantInfo = orm.select(TenantInfo.class).where().eq(TenantInfo::getId, 1L).first();
            assertNotNull(tenantInfo);
            assertEquals("通过 @DS 动态切库后查询必须路由到对应物理库", "FROM_TENANT", tenantInfo.getInfo());
        } finally {
            DynamicDataSourceContextHolder.poll();
        }

        // 3. 退出 @DS 上下文后，自动恢复默认数据源
        TenantInfo restoredInfo = orm.select(TenantInfo.class).where().eq(TenantInfo::getId, 1L).first();
        assertNotNull(restoredInfo);
        assertEquals("FROM_DEFAULT", restoredInfo.getInfo());
    }
}

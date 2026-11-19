package com.javaorm.bridge.mp;

import com.javaorm.bridge.mybatis.JavaOrmMyBatisSupport;
import com.javaorm.bridge.mybatis.MyBatisSqlExecutor;
import com.javaorm.core.JavaOrm;
import com.javaorm.core.config.OrmConfig;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import javax.sql.DataSource;

/**
 * MyBatis-Plus 一站式完整适配器（All-in-One Adapter）
 * <p>
 * 目标：在应用入口（如 Spring Boot 配置类）只需一行代码配置该 Adapter，
 * 即可完成 MyBatis-Plus 全家桶（注解、分页、枚举转换、动态表名/多租户/防全表更新插件、事务生命周期）的全部适配，
 * 后续业务代码 100% 仅使用 JavaOrm（如 {@code orm.select(...)}、{@code orm.insert(...)}、{@code orm.update(...)} 等）。
 * </p>
 *
 * <pre>{@code
 * // 示例 1：Spring Boot 配置类中创建统一 JavaOrm Bean
 * @Configuration
 * public class OrmConfiguration {
 *     @Bean
 *     public JavaOrm javaOrm(SqlSessionFactory sqlSessionFactory) {
 *         return MyBatisPlusAdapter.create(sqlSessionFactory);
 *     }
 * }
 *
 * // 示例 2：业务 Service 中 100% 纯粹使用 JavaOrm，后续无需再写 MyBatis / MP Mapper
 * @Service
 * public class UserService {
 *     @Autowired
 *     private JavaOrm orm;
 *
 *     public Page<User> queryUsers(int page, int size) {
 *         return orm.select(User.class)
 *                   .where()
 *                   .gt(User::getAge, 18)
 *                   .like(User::getName, "张%")
 *                   .toPage(page, size);
 *     }
 * }
 * }</pre>
 */
public class MyBatisPlusAdapter {

    private MyBatisPlusAdapter() {
    }

    /**
     * 根据 MyBatis-Plus / MyBatis 的 SqlSessionFactory 创建开箱即用的 JavaOrm 实例。
     * <p>
     * 自动完成：
     * 1. 提取并共享底层 DataSource 与事务管理器；
     * 2. 绑定 MyBatisSqlExecutor，支持参与 Spring @Transactional 事务；
     * 3. 自动扫描并挂载已配置的 MyBatis-Plus 插件（如多租户 TenantLine、动态表名 DynamicTableName、防全表更新 BlockAttack 等）；
     * 4. 自动激活 MP 实体注解（@TableName, @TableId, @TableField, @Version, @TableLogic）、枚举转换（IEnum, @EnumValue）与分页。
     * </p>
     *
     * @param sqlSessionFactory MyBatis-Plus / MyBatis 的 SqlSessionFactory
     * @return 完整适配后的 JavaOrm 客户端实例
     */
    public static JavaOrm create(SqlSessionFactory sqlSessionFactory) {
        return JavaOrmMyBatisSupport.from(sqlSessionFactory);
    }

    /**
     * 根据当前 SqlSession 创建专属 JavaOrm 实例（适用于手动操作 SqlSession 的场景）
     *
     * @param sqlSession 当前激活的 SqlSession
     * @return 绑定当前会话的 JavaOrm 实例
     */
    public static JavaOrm create(SqlSession sqlSession) {
        return JavaOrmMyBatisSupport.from(sqlSession);
    }

    /**
     * 将 MyBatis-Plus 的全套能力直接适配注入到已有的 OrmConfig 配置中。
     *
     * @param config            现有 JavaORM 配置对象
     * @param sqlSessionFactory MyBatis-Plus / MyBatis 的 SqlSessionFactory
     */
    public static void adapt(OrmConfig config, SqlSessionFactory sqlSessionFactory) {
        if (config == null || sqlSessionFactory == null) {
            return;
        }
        config.setSqlExecutor(new MyBatisSqlExecutor(sqlSessionFactory));
        // 自动探测并注册所有 MP 拦截器插件
        JavaOrmMyBatisSupport.autoRegisterInterceptors(config, sqlSessionFactory.getConfiguration());
    }

    /**
     * 将 MyBatis-Plus 的全套能力直接适配注入到指定 DataSource 和 OrmConfig 中
     */
    public static JavaOrm create(DataSource dataSource, OrmConfig config) {
        return JavaOrm.create(dataSource, config);
    }
}

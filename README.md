# Java-ORM：现代无 APT 字段感知与级联流式 ORM

[![Java 8+](https://img.shields.io/badge/Java-8%2B-blue.svg)](#)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Maven Central](https://img.shields.io/badge/Maven-1.0.0-green.svg)](#)
[![Zero-APT](https://img.shields.io/badge/APT-None-orange.svg)](#)

> **Java-ORM** 是一个面向 Java 8+ 的轻量级现代关系型数据库 ORM 框架。
> 它借鉴了 **Easy-Query** 的设计哲学，但**彻底摒弃了编译期 APT（代码生成器）技术**。
> 通过基于 **ByteBuddy 的运行时代理调用录制（Invocation Recording）**，实现了类似 `s.getTeacher().getAge()` 的多级级联字段感知、编译期类型安全与纯粹的 SQL 链式流，实体保持纯净 POJO。

---

## 核心特性一览

- ⚡ **高性能批量操作引擎（Batch Engine）**：参考 EasyQuery，原生支持 `insertBatch`、`updateBatch`、`deleteBatch`、`deleteByIds`，支持 `batchSize(n)` 批量分块切分，支持多值 VALUES 批量构造与 JDBC `addBatch()` 双模式，自动识别并分发到对应物理分库分表节点，支持主键安全回填与批量乐观锁。
- 🛡️ **多级乐观锁机制（Optimistic Locking）**：内置 `@JoVersion`，更新自动带上 `WHERE version = ?` 并递增 `version + 1`，并发冲突自动抛出强类型 `OptimisticLockException`。
- 🌉 **多框架无缝桥接模块（java-orm-bridge）**：
  - **注解支持列表动态获取与去重合并**：基于 SPI 动态加载外部元数据，核心模块零硬编码。引入桥接包后，同时支持 **MyBatis-Plus**（`@TableName`, `@TableId`, `@TableField`, `@Version`, `@TableLogic`）、**EasyQuery**（`@Table`, `@Column`, `@Version`, `@LogicDelete` 等）、**JPA**（`@Table`, `@Id`, `@Column`, `@Version`）等注解，多注解并存时按优先级智能去重合并为一个属性。
  - **MyBatis 深度执行器集成**：提供 `MyBatisSqlExecutor` 与 `JavaOrmMyBatisSupport`，Java-ORM 的流式 DSL 直接运行在 MyBatis 的 `SqlSession` / Spring 事务管理上下文中！
- 📄 **通用无侵入分页模式（Zero-Invasive Pagination）**：提供统一的 `toPage(pageNum, pageSize)` 入口，支持函数式工厂引用（如 `toPage(1, 10, MyCustomVoPage::new)`）零侵入返回业务自定义 Page 对象；通过 SPI 原生无缝适配 MyBatis-Plus 的 `IPage<T>` 实例传参并就地回写返回。
- 🔍 **SQL 切面与执行拦截器（SqlInterceptor）**：支持对所有查询与增删改操作进行 `beforeQuery`、`beforeUpdate`、`afterExecution` 环绕切面监控、SQL 动态改写与慢查询耗时统计，并提供 `MyBatisSqlInterceptorBridge` 无缝桥接 MyBatis 插件生态。
- 🎯 **第三方枚举原生桥接（IEnum & @EnumValue）**：自动识别 MyBatis-Plus 的 `IEnum` 接口与 `@EnumValue` 注解，预热静态反射缓存，实现 O(1) 零反射耗时持久化与反序列化。
- 🚀 **零 APT 编译依赖**：无需在 `pom.xml` 中配置任何 annotation processor，无须生成 `QUser` 或 `UserProxy` 冗余代码，代码随写随测，实体即改即用。
- 🔍 **多级字段感知与 IDE 原生重构**：直接写 `s -> s.getTeacher().getAge()` 或 `Student::getAge`，IDE 原生自动补全字段，重命名时全局安全重构。
- 🌊 **扁平流畅的 SQL 条件流**：支持 `where().eq([condition, ]column, value)`，自带布尔判断参数，无需繁复的 `if` 拼接。
- 🔗 **智能导航与多表自动 JOIN**：在条件或排序列中点号调用外键关联实体属性，框架自动基于外键关系生成对应的 `LEFT JOIN`。
- 📦 **高性能 Split/Batch IN 装配（Include）**：一对一/多对一/一对多关系通过 `.include()` 显式批量加载（IN 查询），从根源杜绝 SQL 笛卡尔积膨胀与分页失效问题。
- 🔄 **值转换器体系（ValueConverter）**：统一单层转换架构，支持属性级 `@JoColumn(converter = ...)` 以及全局自动匹配 `AutoValueConverter`。
- 🎯 **高性能枚举支持（@JoEnumValue & JoEnum）**：
  - 实体/枚举属性标注 `@JoEnumValue` 即可自动完成 Code/Name 双向映射。
  - 支持实现 `JoEnum` 接口。
  - 内置零反射快速查找缓存，O(1) 时间复杂度完成装配。
- 🔒 **字段级敏感数据加密（@JoEncrypt）**：内置标准 AES-CBC/Base64 加密策略基类，落库自动加密，入库/查询反序列化自动解密。
- 📄 **开箱即用 JSON 字段（@JoJson）**：
  - 零配置自动通过 SPI 探测 classpath 上的 JSON 库（Jackson、Fastjson2、Gson）。
  - 保留并解析 `java.lang.reflect.Type` 完整泛型，彻底解决 `List<MyVo>` 反序列化泛型擦除难题。
- 🧩 **DDD 扁平值对象（ValueObject）**：实体中通过 `@JoEmbedded` 嵌入值对象，数据库单表平铺展开存储，支持多层嵌套 VO 与 DDD 优雅 null 回填策略。
- 🌐 **分库分表与多数据源（Unified Sharding）**：
  - 单一类注解 `@JoSharding` 统辖分库与分表（支持静态从库、动态分库、动态分表、全表扫描防护 `allowFullScan`）。
  - 单一字段注解 `@JoShardingKey` 驱动库表路由；未标注时**自动回退 `@JoId`** 缺省作为分片键。
  - 多数据源管理器 `DataSourceManager` 与线程级切库上下文 `DataSourceContext`。
  - 笛卡尔积执行引擎：支持单库单表直接下推与多库多表广播/内存归并聚合。
- 🔌 **底层执行器解耦（SqlExecutor SPI）**：分片在 `SqlExecutor` 之前执行，底层无缝解耦并可插拔替换为原生 JDBC、MyBatis、Spring 等执行引擎。
- ☕ **向下兼容 Java 8**：基线面向 Java 8，无缝向上兼容运行于 Java 11 / 17 / 21 / 25。

---

## 快速上手

### 1. 引入 Maven 依赖

```xml
<dependency>
    <groupId>com.javaorm</groupId>
    <artifactId>java-orm-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 实体定义

```java
import com.javaorm.core.annotation.*;
import com.javaorm.core.sharding.*;
import java.time.LocalDateTime;
import java.util.List;

@JoTable(name = "t_teacher")
public class Teacher {
    @JoId
    private Long id;
    
    private String teacherName; // 自动映射为 teacher_name
    private Integer age;
    private String title;

    private List<Student> students; // 一对多关联

    // Getter / Setter ...
}

@JoTable(name = "t_student")
public class Student {
    @JoId
    private Long id;

    private String studentName; // 自动映射为 student_name
    private Integer age;
    private Long teacherId;

    private Teacher teacher; // 多对一关联（默认匹配 teacherId -> Teacher.id）

    @JoLogicDelete(normalValue = "0", deletedValue = "1")
    private Integer isDeleted;

    private LocalDateTime createTime;

    // Getter / Setter ...
}
```

### 3. 初始化客户端

```java
DataSource dataSource = ...; // 任何 JDBC DataSource (HikariCP / Druid / SQLite 等)
JavaOrm orm = JavaOrm.create(dataSource);
```

---

## 常用操作代码示例

### 1. 基础单表查询

```java
List<Student> list = orm.select(Student.class)
    .where()
        .ge(Student::getAge, 18)
        .like(Student::getStudentName, "张%")
    .orderBy(Student::getAge).desc()
    .page(1, 10)
    .toList();
```

### 2. 动态条件过滤（三参数布尔开关）

```java
String searchName = "张";
Integer minAge = 20;

List<Student> list = orm.select(Student.class)
    .where()
        .like(searchName != null, Student::getStudentName, searchName + "%")
        .ge(minAge != null, Student::getAge, minAge)
    .toList();
```

### 3. 级联导航字段过滤（自动隐式 LEFT JOIN）

直接点出两层实体关系，框架自动根据外键生成 `LEFT JOIN t_teacher t1 ON t0.teacher_id = t1.id`：

```java
List<Student> students = orm.select(Student.class)
    .where()
        .gt(s -> s.getTeacher().getAge(), 35)
    .toList();
```

### 4. 关联数据装配（Include 模式，避免笛卡尔积）

```java
// 多对一：查出学生，并装配每个学生的指导老师对象
List<Student> students = orm.select(Student.class)
    .include(Student::getTeacher)
    .where()
        .ge(Student::getAge, 18)
    .toList();

// 一对多：查出老师，并批量装配名下的学生集合 List<Student>
List<Teacher> teachers = orm.select(Teacher.class)
    .include(Teacher::getStudents)
    .toList();
```

### 5. 增删改（CUD 操作）

```java
// 实体级插入（自增主键自动回填）
Student s = new Student();
s.setStudentName("王小明");
s.setAge(20);
orm.insert(s);

// 实体级更新（仅更新非空字段）
s.setAge(21);
orm.update(s);

// 实体级删除（标有 @JoLogicDelete 则自动执行 UPDATE is_deleted = 1）
orm.delete(s);

// 纯 SQL 批量流式条件更新（支持直接代理属性、自增/自减计算与表达式）
orm.update(Student.class)
    .set(Student::getAge, increment(1))   // 原地自增 age = age + 1
    .set(Student::getStudentName, "新名字")
    .where()
        .eq(Student::getStudentName, "王大锤")
    .execute();

// 纯 SQL 条件删除（有 @JoLogicDelete 自动转逻辑删除）
orm.delete(Student.class)
    .where()
        .lt(Student::getAge, 10)
    .execute();
```

---

## 高级特性

### 1. 枚举与转换器（@JoEnumValue / ValueConverter）

```java
public enum Gender implements JoEnum<Integer> {
    MALE(1, "男"),
    FEMALE(2, "女");

    @JoEnumValue // 标明数据库存储该字段的值
    private final int code;
    private final String desc;

    @Override
    public Integer getValue() { return code; }
    // ...
}

@JoTable(name = "t_user")
public class User {
    @JoId private Long id;
    private Gender gender; // 零配置，自动以 code(1/2) 存入数据库，查询自动反序列化为枚举
}
```

### 2. JSON 字段自动映射（@JoJson）

```java
@JoTable(name = "t_user")
public class User {
    @JoId private Long id;

    @JoJson // 自动使用 classpath 上的 Jackson/Fastjson2/Gson 进行序列化与反序列化
    private List<AddressVo> addressList; // 完美保留泛型，绝不沦为 List<Map>
}
```

### 3. 字段敏感加密（@JoEncrypt）

```java
@JoTable(name = "t_user")
public class User {
    @JoId private Long id;

    @JoEncrypt(MyAesStrategy.class) // 落库自动 AES-CBC 加密，出库自动解密
    private String idCard;
}
```

### 4. DDD 扁平值对象（ValueObject）

```java
@JoEmbeddable
public class Address {
    private String province;
    private String city;
    // ...
}

@JoTable(name = "t_user")
public class User {
    @JoId private Long id;

    @JoEmbedded // 字段自动平铺映射至 t_user 表的 province, city 列
    private Address address;
}
```

### 5. 分库分表与多数据源（Unified Sharding）

```java
// 既分库又分表：按 tradeId % 2 路由数据源(ds_0/ds_1)，按 tradeId % 4 路由物理表(t_trade_0..3)
// 若省略 @JoShardingKey，默认使用 @JoId 主键字段作为分片键
@JoSharding(
    dataSourceRouter = ModDataSourceRouter.class,
    tableRouter = ModTableRouter.class,
    allowFullScan = false // 安全开关：禁止无分片键的全库全表扫描
)
@JoTable(name = "t_trade")
public class Trade {
    @JoId private Long id;
    @JoShardingKey private Long tradeId;
    private Double amount;
}

// 统一 Fluent 查询：根据 tradeId 自动定位物理库和物理表
Trade trade = orm.select(Trade.class)
    .where().eq(Trade::getTradeId, 200L)
    .firstOrNull();

// 线程级动态切库
orm.useDataSource("ds_slave", () -> {
    return orm.select(Report.class).toList();
});
```

### 6. 批量操作引擎与乐观锁（参考 EasyQuery）

```java
@JoTable(name = "t_product")
public class Product {
    @JoId private Long id;
    private String name;
    private Double price;

    @JoVersion // 乐观锁版本号
    private Integer version;
}

// 1. 批量插入（支持自动按 batchSize 分批分块，支持多库多分表自动分组分发）
List<Product> products = ...;
orm.insertBatch(products)
    .batchSize(500)
    .execute();

// 2. 批量更新（自动递增版本号，并发冲突校验）
for (Product p : products) {
    p.setPrice(p.getPrice() * 1.1);
}
orm.updateBatch(products)
    .batchSize(200)
    .execute(); // 若版本号不匹配，自动抛出 OptimisticLockException

// 3. 批量删除与根据 ID 列表批量删除
orm.deleteBatch(products).execute();
orm.deleteByIds(Product.class, Arrays.asList(1L, 2L, 3L)).batchSize(100).execute();
```

### 7. 多框架注解桥接与 MyBatis 深度集成（java-orm-bridge）

引入 `java-orm-bridge` 模块后，实体类可直接使用现有框架的注解，Java-ORM 通过 SPI 自动探测、动态提取并智能去重合并：

```java
// 实体完全复用 MyBatis-Plus / EasyQuery 注解，无需改造为 @Jo 注解！
@TableName("t_mp_user")
public class User {
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long id;

    @TableField("user_name")
    private String name;

    @com.baomidou.mybatisplus.annotation.Version // 支持 MyBatis-Plus / EasyQuery 乐观锁
    private Integer version;

    @TableLogic(value = "0", delval = "1") // 支持逻辑删除
    private Integer deleted;
}

// 统一复用 MyBatis 的事务与连接池：
try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
    JavaOrm mybatisOrm = JavaOrmMyBatisSupport.from(sqlSession);

    // 完全在 MyBatis 事务环境下执行流式 Java-ORM DSL！
    mybatisOrm.insert(user);
    List<User> list = mybatisOrm.select(User.class)
        .where()
        .gt(User::getAge, 18)
        .list();
}
```

### 8. 通用无侵入分页（Zero-Invasive Pagination）

Java-ORM 采用统一的入口模式，不强制绑定任何分页实现类：

```java
// 模式 1：开箱即用内置 PageResult（完全兼容 Page<T> 标准契约与 records / list 访问）
Page<User> page = orm.select(User.class)
    .where().gt(User::getAge, 18)
    .toPage(1, 10);

// 模式 2：零侵入返回业务自定义 VO / DTO 分页类（通过构造引用 (page, size, total, list) -> CustomPage）
public class MyVoPage<T> {
    public MyVoPage(long current, long size, long total, List<T> data) { ... }
}

MyVoPage<User> voPage = orm.select(User.class)
    .where().gt(User::getAge, 18)
    .toPage(1, 10, MyVoPage::new); // 零框架依赖，直接强类型返回！

// 模式 3：无缝对接 MyBatis-Plus 的 IPage 实例（就地回写，零侵入）
IPage<User> mpPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
IPage<User> result = orm.select(User.class)
    .where().gt(User::getAge, 18)
    .toPage(mpPage); // 框架通过 SPI 自动适配 IPage，回填 total/records 后返回
```

### 9. SQL 切面与拦截器（SqlInterceptor）

支持对查询、新增、修改、删除等所有 SQL 操作进行统一切面拦截与耗时监控：

```java
OrmConfig config = new OrmConfig();
config.addInterceptor(new SqlInterceptor() {
    @Override
    public SqlAndParams beforeQuery(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
        // 动态改写查询 SQL 或追加租户参数
        return sqlAndParams;
    }

    @Override
    public SqlAndParams beforeUpdate(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
        // 动态改写新增/修改/删除 SQL
        return sqlAndParams;
    }

    @Override
    public void afterExecution(String sql, List<Object> params, long elapsedMillis, Throwable throwable) {
        // 统计慢 SQL、输出耗时日志或接入链路追踪
        if (elapsedMillis > 1000) {
            System.err.println("Slow SQL: " + sql + ", cost: " + elapsedMillis + "ms");
        }
    }
});

JavaOrm orm = JavaOrm.create(dataSource, config);
```

---

## 开源协议与版权声明

本项目基于 [Apache License 2.0](LICENSE) 协议开源，完全支持个人及企业商业使用、自由分发与二次修改。

依据 Apache 2.0 规范，商业二次分发或引用时请保留原作者的版权声明及 [NOTICE](NOTICE) 文件。

- **作者 (Author)**: ssuiwp / ylagr
- **Gitee**: [https://gitee.com/ssuiwp](https://gitee.com/ssuiwp)
- **GitHub**: [https://github.com/ylagr](https://github.com/ylagr)

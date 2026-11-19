# Java-ORM：现代无 APT 字段感知与级联流式 ORM

[![Java 8+](https://img.shields.io/badge/Java-8%2B-blue.svg)](#)
[![Status](https://img.shields.io/badge/Status-Demo%20%2F%20Experimental-yellow.svg)](#)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Zero-APT](https://img.shields.io/badge/APT-None-orange.svg)](#)

> **Java-ORM** 是一个面向 Java 8+ 的轻量级现代关系型数据库 ORM 框架。
> 它借鉴了 **Easy-Query** 的设计哲学，但**彻底摒弃了编译期 APT（代码生成器）技术**。
> 通过基于 **ByteBuddy 的运行时代理调用录制（Invocation Recording）**，实现了类似 `s.getTeacher().getAge()` 的多级级联字段感知、编译期类型安全与纯粹的 SQL 链式流，实体保持纯净 POJO。

> [!WARNING]
> **项目状态声明（Project Status）**
> 本项目当前处于 **Demo 概念验证与技术架构探索阶段（Preview / Experimental）**，**尚未正式上线大规模生产环境（Production）**。
> 当前版本主要用于展示无 APT 运行时代理调用录制与现代流式 SQL 设计理念。欢迎各位开发者交流、体验、提出 Issue / PR 共同探讨；如需在生产环境使用，请务必经过充分的场景评估与完备的自动化回归测试。

---

## 核心特性一览

- 🌟 **SqlEnv 声明式全功能环境模式（首推核心模式）**：
  通过 `orm.sql(e -> { ... })` 开启强类型、显式受控的 SQL 环境。显式声明表变量与别名（如 `e.table(Student.class, "s")`），彻底终结多表联查别名冲突；原生支持同表自连接（Self Join）、复杂多表 JOIN、全量字段/聚合函数计算与任意 DTO 投影映射；内置流式条件 UPDATE 与 DELETE；线程级安全隔离，执行完毕自动清理上下文，零污染、零泄漏。
- ⚡ **高性能批量操作引擎（Batch Engine，参考 EasyQuery）**：
  原生支持 `insertBatch`、`updateBatch`、`deleteBatch`、`deleteByIds`，支持 `batchSize(n)` 批量分块分批，支持多值 VALUES 批量构造与 JDBC `addBatch()` 双模式，自动识别并分发到对应物理分库分表节点，支持主键安全回填与批量乐观锁。
- 🏗️ **智能 Code-First 结构同步引擎（参考 EasyQuery / JPA）**：
  支持实体驱动数据库架构自进化。系统自动反射或包扫描实体元数据，智能比对目标数据库元数据，**自动生成并执行建表 DDL（CREATE TABLE）与增量列补齐（ALTER TABLE ... ADD COLUMN ...）**。提供 `CodeFirstMode` 细粒度控制（`NONE`, `VALIDATE`, `CREATE_ONLY`, `UPDATE`, `DROP_CREATE`），支持跨方言 DDL（MySQL, PostgreSQL, H2, SQLite, Oracle 等），严格遵循安全生产原则（默认绝不执行危险的 DROP COLUMN 操作）。
- 🌉 **MyBatis-Plus 一站式完整适配器（MyBatisPlusAdapter）**：
  在应用启动时（如 Spring Boot 配置类中）仅需配置一次 `MyBatisPlusAdapter.create(sqlSessionFactory)`，即可无缝继承 MyBatis-Plus 的所有能力（动态表名、多租户行级隔离、防全表更新删除 BlockAttack 插件、Spring 声明式事务、枚举转换、分页插件与注解），后续业务代码 **100% 仅使用纯粹的 JavaORM**，无需再写 Mapper XML 或 BaseMapper！
- 🛡️ **多级乐观锁机制（Optimistic Locking）**：
  原生支持 `@JoVersion`，通过桥接同时支持 MyBatis-Plus `@Version` 与 EasyQuery `@Version`。更新时自动带上 `WHERE version = ?` 并递增 `version + 1`，并发冲突自动抛出强类型 `OptimisticLockException`。
- 📄 **通用无侵入分页模式（Zero-Invasive Pagination）**：
  提供统一的 `toPage(pageNum, pageSize)` 入口，支持开箱即用 `PageResult`，支持函数式构造引用（如 `toPage(1, 10, MyCustomVoPage::new)`）零侵入返回业务自定义 Page 对象；通过 SPI 原生无缝适配 MyBatis-Plus 的 `IPage<T>` 实例传参并就地回写返回。
- 🔍 **多框架注解桥接与智能去重合并（java-orm-bridge）**：
  基于 SPI 动态加载外部元数据，核心模块零硬编码。引入桥接包后，自动支持 MyBatis-Plus（`@TableName`, `@TableId`, `@TableField`, `@Version`, `@TableLogic`）、EasyQuery（`@Table`, `@Column`, `@Version`, `@LogicDelete` 等）、JPA（`@Table`, `@Id`, `@Column`, `@Version`）等注解，多注解并存时智能去重合并为一个属性。
- 🎯 **第三方枚举原生桥接（IEnum & @EnumValue）**：
  自动识别 MyBatis-Plus 的 `IEnum` 接口与 `@EnumValue` 注解，预热静态反射缓存，实现 O(1) 零反射耗时持久化与反序列化。
- 🔍 **SQL 切面与执行拦截器（SqlInterceptor）**：
  支持对所有查询与增删改操作进行 `beforeQuery`、`beforeUpdate`、`afterExecution` 环绕切面监控、SQL 动态改写与慢查询耗时统计，并提供 `MyBatisPlusInterceptorBridge` 无缝桥接 MP 插件生态。
- 🚀 **零 APT 编译依赖**：
  无需在 `pom.xml` 中配置任何 annotation processor，无须生成 `QUser` 或 `UserProxy` 冗余代码，代码随写随测，实体即改即用。
- 🔍 **多级字段感知与 IDE 原生重构**：
  直接写 `s.getTeacher().getAge()` 或 `Student::getAge`，IDE 原生自动补全字段，重命名时全局安全重构。
- 🌊 **扁平流畅的 SQL 条件流**：
  支持 `where().eq([condition, ]column, value)`，自带布尔判断参数，无需繁复的 `if` 拼接。
- 🔗 **智能导航与多表自动 JOIN**：
  在条件或排序列中点号调用外键关联实体属性，框架自动基于外键关系生成对应的 `LEFT JOIN`。
- 📦 **高性能 Split/Batch IN 装配（Include）**：
  一对一/多对一/一对多关系通过 `.include()` 显式批量加载（IN 查询），从根源杜绝 SQL 笛卡尔积膨胀与分页失效问题。
- 🔄 **值转换器体系（ValueConverter）**：
  统一单层转换架构，支持属性级 `@JoColumn(converter = ...)` 以及全局自动匹配 `AutoValueConverter`。
- 🔒 **字段级敏感数据加密（@JoEncrypt）**：
  内置标准 AES-CBC/Base64 加密策略基类，落库自动加密，入库/查询反序列化自动解密。
- 📄 **开箱即用 JSON 字段（@JoJson）**：
  零配置自动通过 SPI 探测 classpath 上的 JSON 库（Jackson、Fastjson2、Gson），保留并解析完整泛型，解决 `List<MyVo>` 反序列化泛型擦除难题。
- 🧩 **DDD 扁平值对象（ValueObject）**：
  实体中通过 `@JoEmbedded` 嵌入值对象，数据库单表平铺展开存储，支持多层嵌套 VO 与优雅 null 回填策略。
- 🌐 **分库分表与多数据源（Unified Sharding）**：
  单一类注解 `@JoSharding` 统辖分库与分表，单一字段注解 `@JoShardingKey` 驱动路由（未标注时自动回退 `@JoId` 主键）；支持单库单表下推与笛卡尔积广播聚合。
- ☕ **向下兼容 Java 8**：
  基线面向 Java 8，无缝向上兼容运行于 Java 11 / 17 / 21 / 25。

---

## 💡 AI 时代的核心价值：相比 MyBatis-Plus 的维度对比

在由大模型（LLM）与自主编程智能体（AI Coding Agent，如 Cursor、Devin、Antigravity、Copilot）主导的研发时代，ORM 框架的核心竞争力已经发生质的转变：**从过去“帮人类少敲几次键盘”，演变为“为 AI 提供强类型闭环、零幻觉惩罚、极低 Token 消耗、以及通过编译器反馈实现 100% 自我纠错（Self-Correction）的确定性契约”。**

### 核心维度对比一览

| 核心维度 | MyBatis / MyBatis-Plus | Java-ORM (现代无 APT 流式 ORM) | AI 时代的决定性差异 |
| :--- | :--- | :--- | :--- |
| **多表/联表类型安全** | 仅单表有简单 LambdaWrapper；多表/聚合**必须退化为手写 SQL 或 XML** | **全场景 100% 强类型方法感知**（`s.getTeacher().getAge()`） | AI 在手写 XML/SQL 时极易臆造字段或写错标签；Java-ORM 全链路受类型系统保护，**从根本杜绝字段幻觉** |
| **AI 自动化纠错与自愈** | **极差**。XML 语法错误或字段拼错无法被 `javac` 拦截，CI/CD 盲目通过，运行时报错崩溃 | **极强（秒级闭环）**。全链路纯 Java 语法，AI 生成代码后仅需一次本地 `javac` 即可**捕获错误并在下一轮无缝自愈** | 降低人类排错心智负担，AI 无需连接数据库即可在本地完成编译纠错闭环 |
| **Token 消耗与上下文窗口** | **臃肿冗长**。需同时生成/读取 Entity、Mapper 接口、Mapper XML、Service 继承树等 | **极致精简（直降 60%~80%）**。实体即全部元数据，所有业务内聚在一段 `orm.sql(...)` 中 | 节省庞大的 Prompt Token 成本，彻底消除长上下文导致的 AI 注意力漂移（Attention Loss） |
| **代码生成器（APT）摩擦** | 竞品（如 EasyQuery / QueryDSL）依赖 APT 编译生成 `Q` 类，类未生成导致符号报错 | **彻底零 APT**。基于运行时动态代理与调用录制，实体即改即用，代码随写随测 | 避免 AI 在新增/修改实体字段时陷入“找不到生成类”、“IDE 缓存不一致”的编译死循环 |
| **复杂 SQL（自连接/树形）心智** | 手写别名（`t1`, `t2`, `a`, `b`），AI 联表时常发生 `ON` 条件或 `WHERE` 逻辑颠倒 | `SqlEnv` 显式声明语义化变量（如 `parent`, `child`），由变量名天然驱动上下文 | 大模型底层注意力机制对有语义的表变量极其敏感，极大降低复杂 SQL 的逻辑颠倒率 |
| **存量工程平滑接轨** | 切换框架通常需要推翻重写历史 XML、实体注解与插件体系，阻力巨大 | 提供 `MyBatisPlusAdapter` 一行代码无缝继承历史注解、MP 插件与事务 | **存量 100% 保持不动，增量 100% 享受纯 Java-ORM**，企业零成本、零风险让 AI 落地 |

---

### 为什么说 Java-ORM 是为 AI 智能体量身打造的？

#### 1. 彻底斩断 AI 幻觉，建立“编译器自愈闭环”（Compiler Feedback Loop）
在 AI 编程流中，最可怕的不是编译报错，而是**“编译顺利通过，但运行时 SQL 语法或字段错误”**。MyBatis-Plus 在处理复杂查询时依赖 XML 或手写动态 SQL，AI 极易在 `<if test="...">` 或多表字符串中编造出不存在的列名。
在 Java-ORM 中，无论是单表、多表 JOIN、自连接、流式更新还是删除，**全链路皆为纯正合法 Java 代码**。一旦实体字段重命名或 AI 产生幻觉，Java 编译器会在毫秒级直接亮红拦截，AI Agent 借由编译器的精准报错信息（File, Line, Symbol Not Found），无需任何人工介入即可自主在下一轮修正代码。

#### 2. 告别庞大样板文件，单段 Java 代码实现业务全内聚
AI 面对 MyBatis-Plus 时，为了新增一个稍微复杂点的查询，不得不生成 `XxxMapper.java`，然后在 `resources/mapper/XxxMapper.xml` 中编写繁琐的 XML 声明。这不仅大幅占用了原本宝贵的上下文 Token，更容易在两处文件同步时出现失步。
Java-ORM 提倡**“实体即元数据”**，AI 仅需一段高内聚的 Java 代码即可闭环所有数据操作：
```java
// AI 编写的代码极其聚焦，零多余文件，零 XML 模板：
List<OrderVo> orders = orm.sql(e -> {
    Order o = e.table(Order.class, "o");
    User u = e.table(User.class, "u");
    e.select(OrderVo.class, o.getOrderNo(), u.getName(), o.getAmount())
     .from(o)
     .leftJoin(u).on(o.getUserId(), u.getId())
     .where().ge(o.getAmount(), 100);
}).toList();
```

#### 3. 零 APT（Zero-APT）带来丝滑的即写即测体验
许多主打类型安全的 ORM 依赖 APT（Annotation Processor）在编译期生成 `QUser.java` 冗余代码。但在 AI 自动化 Agent（如 Devin、Cursor Agent）的多轮交互中，AI 刚修改了实体类，生成的代理类往往还没生成或 IDE 索引尚未更新，导致 AI 反复遇到 `Cannot resolve symbol QUser` 的困境。
Java-ORM 采用 ByteBuddy 运行时代理与调用录制，**从根本上不依赖任何编译期代码生成器**。实体改动即生效，无需额外编译等待，完美融入现代 Agentic 极速开发流水线。

---

## 快速上手

### 1. 引入 Maven 依赖

```xml
<!-- 核心模块 -->
<dependency>
    <groupId>com.javaorm</groupId>
    <artifactId>java-orm-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- 桥接模块（可选：如需桥接 MyBatis-Plus / EasyQuery / JPA 注解与插件生态时引入） -->
<dependency>
    <groupId>com.javaorm</groupId>
    <artifactId>java-orm-bridge</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 实体定义

实体为纯净 POJO，既支持 Java-ORM 原生注解，也可**直接复用现有 MyBatis-Plus 注解**：

```java
import com.javaorm.core.annotation.*;
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

#### 方式 A：原生 JDBC 数据源模式
```java
DataSource dataSource = ...; // HikariCP / Druid / SQLite 等
JavaOrm orm = JavaOrm.create(dataSource);
```

#### 方式 B：MyBatis-Plus 一站式适配器模式（推荐在 Spring Boot + MP 项目中使用）
```java
// 在 Spring Boot 配置类中配置一次 Adapter，后续业务代码 100% 纯用 JavaOrm：
@Configuration
public class OrmConfiguration {
    @Bean
    public JavaOrm javaOrm(SqlSessionFactory sqlSessionFactory) {
        return MyBatisPlusAdapter.create(sqlSessionFactory);
    }
}
```

---

## 核心推荐模式：SqlEnv 声明式环境（主推）

> **为什么首推 `SqlEnv`？**
> 1. **显式受控别名**：通过 `e.table(Class, "alias")` 显式获得强类型代理，彻底杜绝多表联查与复杂 SQL 下的别名冲突。
> 2. **原生同表自连接（Self Join）**：同一个实体多次声明不同别名，完美解决树形上下级、层级汇报等经典痛点。
> 3. **全语句流式闭环**：单表/多表查询、DTO 映射、批量流式更新、流式删除均使用统一的声明式心智模型。
> 4. **零上下文泄漏**：线程级安全，执行完毕后在 `finally` 中严格清理录制上下文。

### 1. 基础单表查询与通用分页

```java
// 1.1 查询列表
List<Student> list = orm.sql(e -> {
    Student s = e.table(Student.class);
    e.select(s)
     .where()
         .ge(s.getAge(), 18)
         .like(s.getStudentName(), "张%")
     .orderBy(s.getId()).desc();
}).toList();

// 1.2 通用无侵入分页（返回开箱即用的 PageResult）
Page<Student> page = orm.sql(e -> {
    Student s = e.table(Student.class);
    e.select(s)
     .where().ge(s.getAge(), 18);
}).toPage(1, 10);

// 1.3 辅助终端操作
long count = orm.sql(e -> {
    Student s = e.table(Student.class);
    e.select(s).where().ge(s.getAge(), 18);
}).count();

Student first = orm.sql(e -> {
    Student s = e.table(Student.class);
    e.select(s).where().eq(s.getId(), 1L);
}).firstOrNull();
```

### 2. 多表关联查询（LEFT JOIN / INNER JOIN）与字段投影

```java
List<Map<String, Object>> rows = orm.sql(e -> {
    Student s = e.table(Student.class, "s");
    Teacher t = e.table(Teacher.class, "t");

    e.select(s.getStudentName(), t.getTeacherName())
     .from(s)
     .leftJoin(t).on(s.getTeacherId(), t.getId())
     .where()
         .ge(s.getAge(), 20)
         .like(t.getTeacherName(), "王%");
}).listMaps();
```

### 3. 经典同表自连接（Self Join 树形层级结构）

当查询部门上下级、员工直属领导、无限级分类等自连接结构时，`SqlEnv` 展现出无与伦比的优雅与直观：

```java
@JoTable(name = "t_department")
public class Department {
    @JoId private Long id;
    private Long parentId;
    private String name;
    // ...
}

// 查询 "研发部" 及其所有直接子部门：
List<Map<String, Object>> departments = orm.sql(e -> {
    Department parent = e.table(Department.class, "d_parent");
    Department child  = e.table(Department.class, "d_child");

    e.select(parent.getName(), child.getName())
     .from(parent)
     .leftJoin(child).on(child.getParentId(), parent.getId())
     .where()
         .eq(parent.getName(), "研发部")
     .orderBy(child.getId()).asc();
}).listMaps();
```

### 4. DTO / VO 聚合映射

无需在实体中增加无关冗余字段，直接强类型映射到任意业务 DTO：

```java
public class StudentDto {
    private Long id;
    private String studentName;
    private String teacherName;
    // Getter / Setter ...
}

List<StudentDto> dtoList = orm.sql(e -> {
    Student s = e.table(Student.class, "s");
    Teacher t = e.table(Teacher.class, "t");

    e.select(StudentDto.class, s.getId(), s.getStudentName(), t.getTeacherName())
     .from(s)
     .leftJoin(t).on(s.getTeacherId(), t.getId())
     .where().ge(s.getAge(), 18);
}).toList();
```

### 5. 流式条件 UPDATE 与动态 SET 赋值

```java
Integer newAge = null;
String newName = "张小明";

// 支持布尔条件开闭（三参数重载：满足条件才拼入 SET）
int updatedRows = orm.sql(e -> {
    Student s = e.table(Student.class);
    e.update(s)
     .set(newAge != null, s.getAge(), newAge)
     .set(newName != null, s.getStudentName(), newName)
     .where()
         .eq(s.getId(), 1L);
}).exec();
```

### 6. 流式条件 DELETE

```java
// 若实体标注了 @JoLogicDelete，自动重写为逻辑删除 UPDATE；否则物理删除
int deletedRows = orm.sql(e -> {
    Student s = e.table(Student.class);
    e.delete(s)
     .where()
         .lt(s.getAge(), 10);
}).exec();
```

---

## 快捷单表操作模式（Quick Single-Table Mode）

针对最常见的简单单表 CRUD，Java-ORM 也保留了极简的单表流式操作作为语法糖：

### 1. 单表查询与动态条件

```java
String searchName = "张";
Integer minAge = 20;

List<Student> list = orm.select(Student.class)
    .where()
        .like(searchName != null, Student::getStudentName, searchName + "%")
        .ge(minAge != null, Student::getAge, minAge)
    .orderBy(Student::getAge).desc()
    .toList();
```

### 2. 级联导航字段过滤（自动隐式 LEFT JOIN）

直接点出两层实体关系，框架自动根据外键生成 `LEFT JOIN t_teacher t1 ON t0.teacher_id = t1.id`：

```java
List<Student> students = orm.select(Student.class)
    .where()
        .gt(s -> s.getTeacher().getAge(), 35)
    .toList();
```

### 3. 关联数据批量装配（Include 模式，避免笛卡尔积）

```java
// 多对一：查出学生，并批量装配每个学生的指导老师对象
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

### 4. 实体级增删改（CUD 操作）

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
```

---

## 企业级高级特性

### 1. MyBatis-Plus 一站式完整适配器（MyBatisPlusAdapter）

在基于 Spring Boot + MyBatis-Plus 的企业项目中，通常希望**利用 Java-ORM 强大的流式 DSL 和无 APT 感知能力，同时复用已有 MP 的配置与插件**。

`MyBatisPlusAdapter` 提供了**一站式门面**：

```java
// 步骤 1：在配置类中定义统一 JavaOrm Bean（仅需这一行！）
@Configuration
public class OrmConfiguration {
    @Bean
    public JavaOrm javaOrm(SqlSessionFactory sqlSessionFactory) {
        return MyBatisPlusAdapter.create(sqlSessionFactory);
    }
}

// 步骤 2：在业务 Service 中 100% 纯粹使用 JavaOrm，无需定义任何 BaseMapper！
@Service
public class UserService {
    @Autowired
    private JavaOrm orm;

    public Page<User> queryUsers(int page, int size) {
        // 自动生效 MP 实体注解、多租户行级隔离、动态表名、枚举转换、防全表更新以及 Spring @Transactional 事务！
        return orm.sql(e -> {
            User u = e.table(User.class);
            e.select(u).where().gt(u.getAge(), 18);
        }).toPage(page, size);
    }

    @Transactional
    public void updateUser(Long userId, String newName) {
        orm.sql(e -> {
            User u = e.table(User.class);
            e.update(u).set(u.getName(), newName).where().eq(u.getId(), userId);
        }).exec();
    }
}
```

### 2. 批量操作引擎与乐观锁（参考 EasyQuery）

```java
@JoTable(name = "t_product")
public class Product {
    @JoId private Long id;
    private String name;
    private Double price;

    @JoVersion // 乐观锁版本号（同时兼容 MP 的 @Version 与 EasyQuery 的 @Version）
    private Integer version;
}

// 1. 批量插入（自动按 batchSize 分批分块，支持多库多分表自动分组分发，主键安全回填）
List<Product> products = ...;
orm.insertBatch(products)
    .batchSize(500)
    .execute();

// 2. 批量更新（自动递增版本号 version = version + 1，并发冲突校验）
for (Product p : products) {
    p.setPrice(p.getPrice() * 1.1);
}
orm.updateBatch(products)
    .batchSize(200)
    .execute(); // 若版本号不匹配，自动抛出强类型 OptimisticLockException

// 3. 批量删除与根据 ID 列表批量删除
orm.deleteBatch(products).execute();
orm.deleteByIds(Product.class, Arrays.asList(1L, 2L, 3L)).batchSize(100).execute();
```

### 3. 多框架注解桥接与智能去重合并（java-orm-bridge）

引入 `java-orm-bridge` 模块后，实体类可直接使用已有框架的注解，Java-ORM 通过 SPI 自动提取并按优先级智能去重合并：

```java
// 实体完全复用 MyBatis-Plus / EasyQuery 注解，无需改造为 @Jo 注解！
@TableName("t_mp_user")
public class User {
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long id;

    @TableField("user_name")
    private String name;

    @com.baomidou.mybatisplus.annotation.Version // 兼容 MyBatis-Plus 乐观锁
    private Integer version;

    @TableLogic(value = "0", delval = "1") // 兼容逻辑删除
    private Integer deleted;
}
```

### 4. 通用无侵入分页（Zero-Invasive Pagination）

Java-ORM 采用统一的入口模式，不强制绑定任何第三方分页实现类：

```java
// 模式 1：开箱即用内置 PageResult（完全兼容 Page<T> 标准契约与 records / list 访问）
Page<User> page = orm.sql(e -> {
    User u = e.table(User.class);
    e.select(u).where().gt(u.getAge(), 18);
}).toPage(1, 10);

// 模式 2：零侵入返回业务自定义 VO / DTO 分页类（通过构造引用 (page, size, total, list) -> CustomPage）
public class MyVoPage<T> {
    public MyVoPage(long current, long size, long total, List<T> data) { ... }
}

MyVoPage<User> voPage = orm.sql(e -> {
    User u = e.table(User.class);
    e.select(u).where().gt(u.getAge(), 18);
}).toPage(1, 10, MyVoPage::new); // 零框架依赖，直接强类型返回！

// 模式 3：无缝对接 MyBatis-Plus 的 IPage 实例（就地回写，零侵入）
IPage<User> mpPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
IPage<User> result = orm.sql(e -> {
    User u = e.table(User.class);
    e.select(u).where().gt(u.getAge(), 18);
}).toPage(mpPage); // 框架通过 SPI 自动适配 IPage，回填 total/records 后返回
```

### 5. SQL 切面与拦截器（SqlInterceptor & MP 插件桥接）

支持对所有操作进行 `beforeQuery`、`beforeUpdate`、`afterExecution` 环绕切面监控，并无缝适配 MyBatis-Plus 原生插件：

```java
// 1. 创建 MyBatis-Plus 原生插件（可组合任意 InnerInterceptor）
MybatisPlusInterceptor mpInterceptor = new MybatisPlusInterceptor();

// 1.1 动态表名插件
DynamicTableNameInnerInterceptor dynamicTableInterceptor = new DynamicTableNameInnerInterceptor();
dynamicTableInterceptor.setTableNameHandler((sql, tableName) -> "t_user".equals(tableName) ? "t_user_2026" : tableName);
mpInterceptor.addInnerInterceptor(dynamicTableInterceptor);

// 1.2 多租户行级隔离插件
mpInterceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
    @Override
    public Expression getTenantId() { return new LongValue(1001L); }
    @Override
    public String getTenantIdColumn() { return "tenant_id"; }
    @Override
    public boolean ignoreTable(String tableName) { return false; }
}));

// 1.3 防全表更新/删除攻击插件
mpInterceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

// 方式 A：独立使用 Java-ORM 时直接挂载
OrmConfig config = new OrmConfig();
config.addInterceptor(new MyBatisPlusInterceptorBridge(mpInterceptor));
JavaOrm orm = JavaOrm.create(dataSource, config);

// 方式 B：通过 MyBatisPlusAdapter 自动继承（零额外配置！）
JavaOrm orm = MyBatisPlusAdapter.create(sqlSessionFactory);
```

### 6. 枚举与转换器（@JoEnumValue / IEnum / @EnumValue）

```java
// 支持实现 MyBatis-Plus 的 IEnum，或者标注 @JoEnumValue / @EnumValue
public enum Gender implements IEnum<Integer> {
    MALE(1, "男"),
    FEMALE(2, "女");

    private final int value;
    private final String desc;

    Gender(int value, String desc) { this.value = value; this.desc = desc; }

    @Override
    public Integer getValue() { return value; }
}

@JoTable(name = "t_user")
public class User {
    @JoId private Long id;
    private Gender gender; // 零配置，自动以 code(1/2) 存入数据库，查询自动反序列化为枚举
}
```

### 7. JSON 字段自动映射（@JoJson）

```java
@JoTable(name = "t_user")
public class User {
    @JoId private Long id;

    @JoJson // 自动使用 classpath 上的 Jackson/Fastjson2/Gson 进行序列化与反序列化
    private List<AddressVo> addressList; // 完美保留泛型，绝不沦为 List<Map>
}
```

### 8. 字段敏感加密（@JoEncrypt）

```java
@JoTable(name = "t_user")
public class User {
    @JoId private Long id;

    @JoEncrypt(MyAesStrategy.class) // 落库自动 AES-CBC 加密，出库自动解密
    private String idCard;
}
```

### 9. DDD 扁平值对象（ValueObject）

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

### 10. 分库分表与多数据源（Unified Sharding）

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
Trade trade = orm.sql(e -> {
    Trade t = e.table(Trade.class);
    e.select(t).where().eq(t.getTradeId(), 200L);
}).firstOrNull();

// 线程级动态切库
orm.useDataSource("ds_slave", () -> {
    return orm.select(Report.class).toList();
});
```

### 11. 自动化 Code-First 架构同步（参考 EasyQuery / JPA）

Java-ORM 内置智能的 Code-First 架构迁移同步引擎，支持自动识别实体类、读取 `@JoTable` / `@JoColumn`（或 JPA / MyBatis-Plus 对应字段属性），与数据库系统元数据进行双向差分比对：
- **表不存在时**：自动生成对应方言的 `CREATE TABLE` 语句并执行创建（包含自增主键、列定义、字段注释等）。
- **表已存在时（增量补列）**：智能比对物理列，自动生成并执行 `ALTER TABLE ... ADD COLUMN ...` 补齐新增字段。
- **表已存在时（列属性演进）**：在 `UPDATE` 模式下，系统深度检测物理列属性差异，**自动检测并执行字段长度变更（如 VARCHAR(50) 扩容至 VARCHAR(150)）、数值精度/标度变更（DECIMAL）、默认值变更（DEFAULT）、可空性变更（NOT NULL / NULL）与注释更新**（自动适配 MySQL `MODIFY COLUMN`、PostgreSQL / H2 `ALTER COLUMN SET DATA TYPE / SET DEFAULT`、Oracle `MODIFY` 等）。
- **安全第一**：默认绝不执行危险的 `DROP COLUMN` 操作；支持独立配置开关与多级执行模式。

#### 同步模式（CodeFirstMode）
参考 JPA `hbm2ddl.auto` 与 EasyQuery 的设计，提供 5 种执行模式：
- `NONE`（默认）：完全关闭 Code-First 同步，零额外元数据探测与开销。
- `VALIDATE`：仅校验数据库表与列是否完整，缺少表、列或属性不符时抛出明确异常，绝不执行任何 DDL。
- `CREATE_ONLY`：仅当表不存在时执行建表 `CREATE TABLE`，已有表不执行任何修改。
- `UPDATE`（推荐开发环境使用）：表不存在时自动建表；表已存在时自动补齐缺失的新增列，并同步字段长度扩容、默认值变更与可空性约束。
- `DROP_CREATE`：危险模式（仅限全新临时测试），先 `DROP TABLE IF EXISTS`，再重新建表。

#### 配置方式（Fluent API / OrmConfig）
```java
OrmConfig config = OrmConfig.create()
    .setDataSource(dataSource)
    // 开启自动同步模式（开发与测试环境推荐 UPDATE，生产环境推荐 NONE 或 VALIDATE）
    .codeFirstMode(CodeFirstMode.UPDATE)
    // 配置实体类包扫描路径（支持递归扫描）
    .codeFirstPackageNames("com.example.entity", "com.example.domain")
    // 或者显式追加指定实体类
    .codeFirstEntities(Order.class, User.class);

// 初始化 JavaOrm 时自动按模式执行扫描与增量同步
JavaOrm orm = JavaOrm.create(config);
```

#### 手动执行与 DDL 预览（CodeFirst 门面）
你也可以在任意时刻通过 `orm.codeFirst()` 手动生成 DDL 预览、执行单表或特定包同步：
```java
// 1. 生成并预览增量 DDL 脚本（不直接执行）
List<String> ddlList = orm.codeFirst().generateDdl(Order.class);
ddlList.forEach(System.out::println);

// 2. 手动指定针对某个实体执行同步
orm.codeFirst().syncTable(Order.class);

// 3. 手动同步指定包名下的所有实体
orm.codeFirst().syncPackage("com.example.entity");
```

#### 实体定义与 DDL 属性定制
```java
@JoTable(name = "t_order", comment = "业务订单表")
public class Order {
    @JoId(autoIncrement = true)
    private Long id;

    @JoColumn(length = 64, nullable = false, comment = "订单编号")
    private String orderNo;

    @JoColumn(precision = 12, scale = 2, comment = "订单金额")
    private BigDecimal totalAmount;

    @JoColumn(defaultValue = "1", comment = "订单状态: 1-待支付 2-已支付")
    private Integer status;

    @JoColumn(name = "remark", columnDefinition = "TEXT", comment = "备注")
    private String remark;

    // 当实体类中新增字段（例如 email、age），启动或调用 sync 时会自动追加：
    // ALTER TABLE "t_order" ADD COLUMN "email" VARCHAR(128)
    @JoColumn(length = 128)
    private String email;
}
```

---

## 开源协议与版权声明

本项目基于 [Apache License 2.0](LICENSE) 协议开源，完全支持个人及企业商业使用、自由分发与二次修改。

依据 Apache 2.0 规范，商业二次分发或引用时请保留原作者的版权声明及 [NOTICE](NOTICE) 文件。

- **作者 (Author)**: ssuiwp / ylagr
- **Gitee**: [https://gitee.com/ssuiwp](https://gitee.com/ssuiwp)
- **GitHub**: [https://github.com/ylagr](https://github.com/ylagr)
